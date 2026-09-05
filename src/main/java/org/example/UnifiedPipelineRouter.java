package org.example;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Katman 1: Birleşik İş Hattı Yönlendiricisi.
 *
 * Tampon geometrisine göre rotayı belirler, dinamik bağlam penceresini
 * arabellek hacmine göre ölçekler, motor akışını yönetir ve Katman 4
 * (DiffMergeEngine) üzerinden deterministik olarak yeni EditorState üretir.
 */
public class UnifiedPipelineRouter {

    private final CalibrationEngine calibrationEngine;
    private final RevisionEngine revisionEngine;
    private final ConsultationEngine consultationEngine;
    private final boolean mockMode;

    public UnifiedPipelineRouter(String apiKey) {
        this(apiKey, false);
    }

    public UnifiedPipelineRouter(String apiKey, boolean mockMode) {
        this.mockMode = mockMode;
        this.calibrationEngine = new CalibrationEngine(apiKey);
        this.revisionEngine = new RevisionEngine(apiKey);
        this.consultationEngine = new ConsultationEngine(apiKey);
    }

    /**
     * İş hattını yürütür, tokenları anlık akıtır ve işlem bittiğinde
     * güncellenmiş yeni EditorState nesnesini döndürür.
     */
    public EditorState dispatch(
            String directive,
            EditorState editorState,
            AnalysisTier tier,
            Consumer<String> tokenStreamListener
    ) throws IOException {
        Objects.requireNonNull(editorState, "editorState must not be null");
        Objects.requireNonNull(tokenStreamListener, "tokenStreamListener must not be null");

        AnalysisTier selectedTier = (tier != null) ? tier : AnalysisTier.BALANCED;
        String rawDirective = (directive != null) ? directive.trim() : "";

        PipelineRoute route = BufferGeometryResolver.resolve(editorState, rawDirective);
        StringBuilder accumulatedOutput = new StringBuilder();

        Consumer<String> interceptingListener = token -> {
            String sanitized = StreamSanitizer.sanitizeChunk(token);
            accumulatedOutput.append(sanitized);
            tokenStreamListener.accept(sanitized);
        };

        switch (route.getTarget()) {
            case SURGICAL_MUTATION:
                if (mockMode) {
                    String mockReplacement = "[MUTATION] " + route.getTargetText();
                    tokenStreamListener.accept(mockReplacement);
                    return DiffMergeEngine.applyReplacement(
                            editorState,
                            route.getTargetStart(),
                            route.getTargetEnd(),
                            mockReplacement
                    );
                }

                int contextRadius = calculateDynamicContextRadius(editorState, selectedTier);
                String surroundingContext = editorState.getSurroundingContext(contextRadius);

                revisionEngine.streamRevision(
                        route.getTargetText(),
                        rawDirective,
                        surroundingContext,
                        selectedTier,
                        interceptingListener
                );

                // Cerrahi dikiş: Doğrudan rotanın çözdüğü fiziksel koordinat sınırlarını kullanır
                return DiffMergeEngine.applyReplacement(
                        editorState,
                        route.getTargetStart(),
                        route.getTargetEnd(),
                        accumulatedOutput.toString()
                );

            case NON_DESTRUCTIVE_CONSULTATION:
                if (mockMode) {
                    tokenStreamListener.accept("[CONSULTATION] " + rawDirective);
                    return editorState;
                }

                // İstişare için arabelleğin tam bağlamı kullanılır (Kör budama yapılmaz)
                int consultationWindow = calculateConsultationWindow(editorState, selectedTier);
                String fullContext = editorState.getPrecedingContext(consultationWindow);

                consultationEngine.streamConsultation(
                        rawDirective,
                        fullContext,
                        selectedTier,
                        tokenStreamListener
                );

                return editorState;

            case LINEAR_CONTINUATION:
            default:
                if (mockMode) {
                    String mockContinuation = "[CONTINUATION] " + rawDirective;
                    tokenStreamListener.accept(mockContinuation);
                    return DiffMergeEngine.applyContinuation(editorState, mockContinuation);
                }

                int dynamicPrecedingWindow = calculateDynamicContextRadius(editorState, selectedTier);
                String precedingContext = editorState.getPrecedingContext(dynamicPrecedingWindow);

                calibrationEngine.streamNextDraft(
                        precedingContext,
                        rawDirective,
                        selectedTier,
                        interceptingListener
                );

                return DiffMergeEngine.applyContinuation(editorState, accumulatedOutput.toString());
        }
    }

    /**
     * Arabelleğin toplam büyüklüğüne ve tier seviyesine göre bağlam yarıçapını hesaplar.
     * Küçük metinleri asla budamaz; büyük tamponlarda oransal pencere açar.
     */
    private int calculateDynamicContextRadius(EditorState state, AnalysisTier tier) {
        int totalLen = state.getManuscriptLength();
        if (totalLen <= 0) return 0;

        switch (tier) {
            case FAST:
                // Hızlı akışta arabelleğin en fazla %40'ı veya minimum 3000 karakter
                return Math.max(3000, (int) (totalLen * 0.40));
            case BALANCED:
                // Dengeli modda metin 15.000 karaktere kadarsa tamamı, üzerindeyse %70'i
                return (totalLen <= 15000) ? totalLen : (int) (totalLen * 0.70);
            case DEEP:
            default:
                // Derin analizde hiçbir budama yapılmaz; tüm arabellek derin düşünceye verilir
                return totalLen;
        }
    }

    /**
     * Salt inceleme sorgularında tam metni korur; FAST modunda bile yapay sınır dayatmaz.
     */
    private int calculateConsultationWindow(EditorState state, AnalysisTier tier) {
        int totalLen = state.getManuscriptLength();
        if (tier == AnalysisTier.FAST && totalLen > 25000) {
            return 25000;
        }
        return totalLen;
    }
}