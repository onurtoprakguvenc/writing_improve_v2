package org.example;

import com.google.gson.JsonObject;
import org.example.transport.GeminiClientConfig;
import org.example.transport.GeminiPayloadBuilder;
import org.example.transport.GeminiSseTransport;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Katman 3: Tahribatsız Dinamik Analitik Danışma ve İnceleme Motoru.
 *
 * Arabelleğe dokunmadan, bağlamı veri odası olarak kullanarak
 * doğrudan teknik ve mantıksal analiz çıktısı üretir.
 */
public class ConsultationEngine {

    private final String apiKey;

    public static final String SYSTEM_INSTRUCTION =
            "MODE: OBJECTIVE_ANALYSIS_ENGINE\n" +
                    "OUTPUT_CONTRACT: direct_analysis_only | no_preamble | no_conversational_filler | no_flattery\n\n" +
                    "[CORE_CONSTRAINTS]\n" +
                    "1. Treat REFERENCE_CONTEXT strictly as immutable background evidence.\n" +
                    "2. DO NOT rewrite prose or generate story continuation unless explicitly requested.\n" +
                    "3. Answer the QUERY with absolute technical and analytical precision.\n" +
                    "4. Identify contradictions, structural gaps, or verification facts plainly without polite padding.";

    public ConsultationEngine(String apiKey) {
        this.apiKey = apiKey;
    }

    public void streamConsultation(
            String query,
            String referenceContext,
            AnalysisTier tier,
            Consumer<String> onToken
    ) throws IOException {
        Objects.requireNonNull(onToken, "onToken must not be null");

        AnalysisTier effectiveTier = (tier != null) ? tier : AnalysisTier.BALANCED;
        String cleanQuery = (query == null || query.isBlank()) ? "Analyze context for logical inconsistencies." : query.trim();
        String cleanContext = (referenceContext == null || referenceContext.isBlank()) ? "(none)" : referenceContext.trim();

        double temperature = deriveDynamicTemperature(effectiveTier);
        int maxTokens = CalibrationEngine.resolveMaxOutputTokens(effectiveTier);

        String userPrompt = String.format(
                "[REFERENCE_CONTEXT]\n%s\n[/REFERENCE_CONTEXT]\n\n" +
                        "[QUERY]\n%s\n[/QUERY]\n\n" +
                        "ANALYSIS:",
                cleanContext,
                cleanQuery
        );

        JsonObject payload = GeminiPayloadBuilder.build(
                SYSTEM_INSTRUCTION,
                userPrompt,
                temperature,
                maxTokens
        );

        GeminiClientConfig config = new GeminiClientConfig(apiKey, effectiveTier.getModelName(), 15000, 90000);
        GeminiSseTransport transport = new GeminiSseTransport(config);

        transport.postAndStream(payload, onToken);
    }

    private double deriveDynamicTemperature(AnalysisTier tier) {
        switch (tier) {
            case FAST:
            case SURGICAL:
                return 0.1;
            case DEEP:
                return 0.3;
            case BALANCED:
            default:
                return 0.2;
        }
    }
}