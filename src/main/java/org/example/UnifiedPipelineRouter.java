package org.example;

import com.google.gson.JsonObject;
import org.example.transport.GeminiClientConfig;
import org.example.transport.GeminiPayloadBuilder;
import org.example.transport.GeminiSseTransport;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Layer 1: Unified Pipeline Router.
 */
public class UnifiedPipelineRouter {

    private final String apiKey;
    private final RevisionEngine revisionEngine;
    private final ConsultationEngine consultationEngine;
    private final DiffMergeEngine diffMergeEngine;
    private final boolean mockMode;

    public UnifiedPipelineRouter(String apiKey) {
        this(apiKey, false);
    }

    public UnifiedPipelineRouter(String apiKey, boolean mockMode) {
        this.apiKey = apiKey;
        this.mockMode = mockMode;
        this.revisionEngine = new RevisionEngine(apiKey);
        this.consultationEngine = new ConsultationEngine(apiKey);
        this.diffMergeEngine = new DiffMergeEngine();
    }

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
            case SURGICAL_MUTATION: {
                if (mockMode) {
                    String mockReplacement = "[MUTATION] " + route.getTargetText();
                    tokenStreamListener.accept(mockReplacement);
                    DiffMergeEngine.SpliceResult result = diffMergeEngine.applySelectionReplacementWithBoundaryCleaning(
                            editorState.getFullManuscript(),
                            route.getTargetStart(),
                            route.getTargetEnd(),
                            mockReplacement
                    );
                    return new EditorState(
                            result.getUpdatedManuscript(),
                            result.getNewCursorPosition(),
                            result.getNewCursorPosition(),
                            result.getNewCursorPosition()
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

                String cleanedPayload = StreamSanitizer.sanitizeFullOutput(accumulatedOutput.toString());
                DiffMergeEngine.SpliceResult result = diffMergeEngine.applySelectionReplacementWithBoundaryCleaning(
                        editorState.getFullManuscript(),
                        route.getTargetStart(),
                        route.getTargetEnd(),
                        cleanedPayload
                );
                return new EditorState(
                        result.getUpdatedManuscript(),
                        result.getNewCursorPosition(),
                        result.getNewCursorPosition(),
                        result.getNewCursorPosition()
                );
            }

            case NON_DESTRUCTIVE_CONSULTATION: {
                if (mockMode) {
                    tokenStreamListener.accept("[CONSULTATION] " + rawDirective);
                    return editorState;
                }

                int consultationWindow = calculateConsultationWindow(editorState, selectedTier);
                String fullContext = editorState.getPrecedingContext(consultationWindow);

                consultationEngine.streamConsultation(
                        rawDirective,
                        fullContext,
                        selectedTier,
                        tokenStreamListener
                );

                return editorState;
            }

            case LINEAR_CONTINUATION:
            default: {
                int dynamicPrecedingWindow = calculateDynamicContextRadius(editorState, selectedTier);

                if (mockMode) {
                    String mockContinuation = "[CONTINUATION] " + rawDirective;
                    tokenStreamListener.accept(mockContinuation);
                    DiffMergeEngine.SpliceResult result = diffMergeEngine.applyInsertionWithBoundaryCleaning(
                            editorState.getFullManuscript(),
                            editorState.getCursorPosition(),
                            mockContinuation
                    );
                    return new EditorState(
                            result.getUpdatedManuscript(),
                            result.getNewCursorPosition(),
                            result.getNewCursorPosition(),
                            result.getNewCursorPosition()
                    );
                }

                CalibrationEngine.CalibratedPayload payload = CalibrationEngine.calibrate(
                        editorState,
                        rawDirective,
                        null,
                        dynamicPrecedingWindow,
                        selectedTier
                );

                JsonObject geminiPayload = GeminiPayloadBuilder.build(
                        payload.getSystemInstruction(),
                        payload.getUserPrompt(),
                        0.7,
                        payload.getMaxOutputTokens()
                );

                GeminiClientConfig config = new GeminiClientConfig(
                        apiKey,
                        payload.getModelName(),
                        30000,
                        60000
                );
                GeminiSseTransport transport = new GeminiSseTransport(config);
                transport.postAndStream(geminiPayload, interceptingListener);

                String cleanedPayload = StreamSanitizer.sanitizeFullOutput(accumulatedOutput.toString());
                DiffMergeEngine.SpliceResult result = diffMergeEngine.applyInsertionWithBoundaryCleaning(
                        editorState.getFullManuscript(),
                        editorState.getCursorPosition(),
                        cleanedPayload
                );
                return new EditorState(
                        result.getUpdatedManuscript(),
                        result.getNewCursorPosition(),
                        result.getNewCursorPosition(),
                        result.getNewCursorPosition()
                );
            }
        }
    }

    private int calculateDynamicContextRadius(EditorState state, AnalysisTier tier) {
        int totalLen = state.getManuscriptLength();
        if (totalLen <= 0) return 0;

        switch (tier) {
            case FAST:
                return Math.max(3000, (int) (totalLen * 0.40));
            case BALANCED:
                return (totalLen <= 15000) ? totalLen : (int) (totalLen * 0.70);
            case DEEP:
            default:
                return totalLen;
        }
    }

    private int calculateConsultationWindow(EditorState state, AnalysisTier tier) {
        int totalLen = state.getManuscriptLength();
        if (tier == AnalysisTier.FAST && totalLen > 25000) {
            return 25000;
        }
        return totalLen;
    }
}