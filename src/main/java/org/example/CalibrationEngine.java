package org.example;

import com.google.gson.JsonObject;
import org.example.transport.GeminiClientConfig;
import org.example.transport.GeminiPayloadBuilder;
import org.example.transport.GeminiSseTransport;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Katman 3: İleriye Doğru Akış / Ekleme Motoru (Continuation Engine).
 * İmleç noktasından ileriye doğru üretimi dinamik model mimarisiyle yürütür.
 */
public class CalibrationEngine {

    private final String apiKey;

    public CalibrationEngine(String apiKey) {
        this.apiKey = apiKey;
    }

    public void streamNextDraft(
            String precedingContext,
            String directive,
            AnalysisTier tier,
            Consumer<String> onToken
    ) throws IOException {
        Objects.requireNonNull(onToken, "onToken must not be null");

        AnalysisTier effectiveTier = (tier != null) ? tier : AnalysisTier.BALANCED;
        NarrativeState state = LocalShadowCore.analyze(precedingContext, effectiveTier);

        String systemInstruction = buildContinuationConstraintMatrix(state, precedingContext);
        String userPrompt = String.format(
                "PRECEDING_CONTEXT:\n%s\n\nDIRECTIVE:\n%s",
                nullToEmpty(precedingContext),
                nullToEmpty(directive)
        );

        JsonObject payload = GeminiPayloadBuilder.build(
                systemInstruction,
                userPrompt,
                state.getRecommendedTemperature(),
                state.getRecommendedMaxTokens()
        );

        // Dinamik model tahsisi
        GeminiClientConfig config = new GeminiClientConfig(apiKey, effectiveTier.getModelName(), 30000, 60000);
        GeminiSseTransport transport = new GeminiSseTransport(config);

        transport.postAndStream(payload, onToken);
    }

    private String buildContinuationConstraintMatrix(NarrativeState state, String precedingContext) {
        String anchor = extractTailAnchor(precedingContext, 3);

        StringBuilder matrix = new StringBuilder();
        matrix.append("MODE: LINEAR_CONTINUATION_ENGINE\n");
        matrix.append("OUTPUT_CONTRACT: direct_continuation_only | no_preamble | no_conversational_filler\n\n");
        matrix.append("[PHYSICAL_CONSTRAINTS]\n");
        matrix.append("1. Continue precisely from the cursor position following PRECEDING_CONTEXT.\n");
        matrix.append("2. Do NOT repeat or echo the preceding lines.\n");
        matrix.append(String.format("3. Conform strictly to density metrics: ~%.1f words/sentence (variance tolerance: %.2f).\n",
                state.getAvgWordsPerSentence(), state.getSentenceLengthStdDev()));

        matrix.append("[SYNTAX_GEOMETRY_CONSTRAINTS]\n");
        if (state.hasInlineParenthetical()) {
            matrix.append("- PRESERVE inline parenthetical syntax structure where applicable.\n");
        }
        if (state.hasLowercaseStarter()) {
            matrix.append("- PERMIT lowercase lead-ins if continuation immediately follows an unclosed clause.\n");
        }
        if (state.isPureLineDialogue()) {
            matrix.append("- MAINTAIN single-line delimited structural cadence.\n");
        }

        matrix.append("[STRUCTURAL_ANCHOR]\n");
        matrix.append(anchor);

        return matrix.toString();
    }

    private String extractTailAnchor(String text, int linesCount) {
        if (text == null || text.isBlank()) return "(none)";
        String[] lines = text.split("\\r?\\n");
        int start = Math.max(0, lines.length - linesCount);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                sb.append(lines[i]).append("\n");
            }
        }
        String res = sb.toString().trim();
        return res.isEmpty() ? "(none)" : res;
    }

    private String nullToEmpty(String s) {
        return (s == null) ? "" : s;
    }
}