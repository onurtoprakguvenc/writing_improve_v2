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
 *
 * Belirlenen hedef metin dilimini (TARGET_PASSAGE), çevre bağlamın
 * üslup ve sözdizimi dokusunu bozmadan yönerge doğrultusunda doğrudan yeniden yazar.
 */
public class RevisionEngine {

    private final String apiKey;

    public static final String SYSTEM_INSTRUCTION =
            "You are a deterministic, zero-friction in-buffer text mutation engine. " +
                    "Your output will atomically replace the TARGET_PASSAGE inside the active document. " +
                    "CORE EXECUTION INVARIANTS:\n" +
                    "1. Output ONLY the raw replacement text that directly substitutes TARGET_PASSAGE.\n" +
                    "2. Never emit explanations, markdown code block wrappers (```), or meta-labels (e.g., 'Replacement:', 'Revision:').\n" +
                    "3. Mirror the precise stylistic tone, syntax geometry, punctuation habits, and casing of the SURROUNDING_CONTEXT.\n" +
                    "4. Execute the MUTATION_DIRECTIVE fully without taking lazy shortcuts or summarizing.";

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
        int maxTokens = CalibrationEngine.resolveMaxOutputTokens(effectiveTier);

        String cleanTarget = (targetPassage != null) ? targetPassage.trim() : "";
        String cleanDirective = (revisionInstruction != null && !revisionInstruction.isBlank())
                ? revisionInstruction.trim()
                : "Revise to improve impact while maintaining style.";
        String cleanContext = (surroundingContext != null && !surroundingContext.isBlank())
                ? surroundingContext.trim()
                : "(isolated passage)";

        String userPrompt = String.format(
                "[SURROUNDING_CONTEXT]\n%s\n[/SURROUNDING_CONTEXT]\n\n" +
                        "[TARGET_PASSAGE]\n%s\n[/TARGET_PASSAGE]\n\n" +
                        "[MUTATION_DIRECTIVE]\n%s\n[/MUTATION_DIRECTIVE]\n\n" +
                        "REPLACEMENT:",
                cleanContext,
                cleanTarget,
                cleanDirective
        );

        JsonObject payload = GeminiPayloadBuilder.build(
                SYSTEM_INSTRUCTION,
                userPrompt,
                0.7,
                maxTokens
        );

        GeminiClientConfig config = new GeminiClientConfig(apiKey, effectiveTier.getModelName(), 30000, 60000);
        GeminiSseTransport transport = new GeminiSseTransport(config);

        transport.postAndStream(payload, onToken);
    }
}