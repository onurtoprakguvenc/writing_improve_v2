package org.example;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Katman 1: Birleşik İş Hattı Yönlendiricisi (Unified Pipeline Router).
 *
 * Dinamik bağlam boyutu hesaplar, motorları tek tip API imzasıyla yönetir
 * ve istişare/analiz ayrımını yapısal sentaks analiziyle gerçekleştirir.
 */
public class UnifiedPipelineRouter {

    private final CalibrationEngine calibrationEngine;
    private final RevisionEngine revisionEngine;
    private final ConsultationEngine consultationEngine;
    private final boolean mockMode;

    // Soru ve analiz niyetlerini dilden bağımsız olarak yakalayan yapısal kalıp
    private static final Pattern INTERROGATIVE_OR_ANALYSIS_PATTERN = Pattern.compile(
            "(?i)(\\?$|(\\b(mı|mi|mu|mü|mıdır|midir|mudur|müdür|nedir|nelerdir|nasıl|neden|niçin|kim|hangi|kaç)\\b)|\\b(analiz|incele|kontrol et|tutarlı mı|açıkla|check|analyze|explain|why|how|what|who)\\b)"
    );

    public UnifiedPipelineRouter(String apiKey, boolean mockMode) {
        this.mockMode = mockMode;
        this.calibrationEngine = new CalibrationEngine(apiKey);
        this.revisionEngine = new RevisionEngine(apiKey);
        this.consultationEngine = new ConsultationEngine(apiKey);
    }

    public void dispatch(
            String directive,
            EditorState editorState,
            AnalysisTier tier,
            Consumer<String> tokenStreamListener
    ) throws IOException {
        Objects.requireNonNull(editorState, "editorState must not be null");
        Objects.requireNonNull(tokenStreamListener, "tokenStreamListener must not be null");

        AnalysisTier selectedTier = (tier != null) ? tier : AnalysisTier.BALANCED;
        String rawDirective = (directive != null) ? directive.trim() : "";

        // 1. Durum: Arabellekte aktif bir seçim aralığı varsa -> Doğrudan Yerinde Mutasyon
        if (editorState.hasSelection()) {
            if (mockMode) {
                tokenStreamListener.accept("[MUTATION_SELECTION] " + editorState.getSelectedText().trim());
                return;
            }

            int contextRadius = deriveDynamicContextRadius(selectedTier);
            String surroundingContext = editorState.getSurroundingContext(contextRadius);

            revisionEngine.streamRevision(
                    editorState.getSelectedText(),
                    rawDirective,
                    surroundingContext,
                    selectedTier,
                    tokenStreamListener
            );
            return;
        }

        // 2. Durum: Seçim yok ve yönerge bir soru, mantık denetimi veya analiz talebi içeriyorsa -> İstişare Motoru
        if (isAnalyticalOrInterrogative(rawDirective)) {
            if (mockMode) {
                tokenStreamListener.accept("[CONSULTATION] " + rawDirective);
                return;
            }

            int dynamicWindow = deriveDynamicContextRadius(selectedTier) * 2;
            String fullContext = editorState.getPrecedingContext(dynamicWindow);

            consultationEngine.streamConsultation(
                    rawDirective,
                    fullContext,
                    selectedTier,
                    tokenStreamListener
            );
            return;
        }

        // 3. Durum: Seçim yok ve soru değilse -> İmleç noktasından itibaren ileriye doğru akış
        if (mockMode) {
            tokenStreamListener.accept("[CONTINUATION] " + rawDirective);
            return;
        }

        int dynamicPrecedingWindow = deriveDynamicContextRadius(selectedTier);
        String precedingContext = editorState.getPrecedingContext(dynamicPrecedingWindow);

        calibrationEngine.streamNextDraft(
                precedingContext,
                rawDirective,
                selectedTier,
                tokenStreamListener
        );
    }

    private boolean isAnalyticalOrInterrogative(String directive) {
        if (directive == null || directive.isBlank()) {
            return false;
        }
        return INTERROGATIVE_OR_ANALYSIS_PATTERN.matcher(directive).find();
    }

    private int deriveDynamicContextRadius(AnalysisTier tier) {
        switch (tier) {
            case FAST:
                return 2500;   // Düşük gecikme ve dar odak
            case BALANCED:
                return 6000;   // Standart dengeli arabellek penceresi
            case DEEP:
            default:
                return 16000;  // Derin bağlamsal akıl yürütme penceresi
        }
    }
}