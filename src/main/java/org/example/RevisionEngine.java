package org.example;

import com.google.gson.JsonObject;
import org.example.transport.GeminiClientConfig;
import org.example.transport.GeminiPayloadBuilder;
import org.example.transport.GeminiSseTransport;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Katman 3: Yerinde Cerrahi Mutasyon Motoru.
 * İstek katmanına (AnalysisTier) göre dinamik model ve parametre tahsis eder.
 */
public class RevisionEngine {

    private final String apiKey;

    public RevisionEngine(String apiKey) {
        this.apiKey = apiKey;
    }

    public void streamRevision(
            String targetPassage,
            String revisionInstruction,
            String surroundingContext,
            AnalysisTier tier,
            Consumer<String> onToken
    ) throws IOException {
        Objects.requireNonNull(onToken, "onToken must not be null");

        AnalysisTier effectiveTier = (tier != null) ? tier : AnalysisTier.BALANCED;
        String analysisSource = (surroundingContext != null && !surroundingContext.trim().isEmpty())
                ? surroundingContext
                : targetPassage;

        NarrativeState state = LocalShadowCore.analyze(analysisSource, effectiveTier);
        String systemInstruction = buildRevisionConstraintMatrix(state);

        String userPrompt = String.format(
                "TARGET_PASSAGE:\n%s\n\nREVISION_INSTRUCTION:\n%s\n\nSURROUNDING_CONTEXT (reference only, do not rewrite):\n%s",
                nullToEmpty(targetPassage),
                nullToEmpty(revisionInstruction),
                nullToEmpty(surroundingContext)
        );

        JsonObject payload = GeminiPayloadBuilder.build(
                systemInstruction,
                userPrompt,
                state.getRecommendedTemperature(),
                state.getRecommendedMaxTokens()
        );

        // Dinamik model tahsisi: Seçilen tier seviyesindeki model kullanılır
        GeminiClientConfig config = new GeminiClientConfig(apiKey, effectiveTier.getModelName(), 30000, 60000);
        GeminiSseTransport transport = new GeminiSseTransport(config);

        transport.postAndStream(payload, onToken);
    }

    private String buildRevisionConstraintMatrix(NarrativeState state) {
        StringBuilder matrix = new StringBuilder();
        matrix.append("MODE: SURGICAL_MUTATION_ENGINE\n");
        matrix.append("OUTPUT_CONTRACT: direct_replacement_only | no_preamble | no_conversational_filler\n\n");
        matrix.append("[PHYSICAL_CONSTRAINTS]\n");
        matrix.append("1. Mutate ONLY the TARGET_PASSAGE. Do not append text outside target boundaries.\n");
        matrix.append(String.format("2. Conform strictly to density metrics: ~%.1f words/sentence (variance tolerance: %.2f).\n",
                state.getAvgWordsPerSentence(), state.getSentenceLengthStdDev()));
        matrix.append("3. Replacement must splice seamlessly into SURROUNDING_CONTEXT without seam lines.\n");

        matrix.append("[SYNTAX_GEOMETRY_CONSTRAINTS]\n");
        if (state.hasInlineParenthetical()) {
            matrix.append("- PRESERVE inline parenthetical syntax structure.\n");
        }
        if (state.hasLowercaseStarter()) {
            matrix.append("- PRESERVE lowercase or non-capitalized starting conventions of targeted blocks.\n");
        }
        if (state.isPureLineDialogue()) {
            matrix.append("- PRESERVE isolated line-delimited structural rhythm.\n");
        }
        if (!state.hasInlineParenthetical() && !state.hasLowercaseStarter() && !state.isPureLineDialogue()) {
            matrix.append("- Follow the exact capitalization and block mechanics of the provided context.\n");
        }

        return matrix.toString();
    }

    private String nullToEmpty(String s) {
        return (s == null) ? "" : s;
    }
}