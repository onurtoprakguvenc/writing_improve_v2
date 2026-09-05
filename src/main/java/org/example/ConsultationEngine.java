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
 * Katman 2 (AnalysisTier) seviyesine ve sorgu hacmine göre modelini,
 * sıcaklığını ve token bütçesini dinamik olarak ölçekler.
 */
public class ConsultationEngine {

    private final String apiKey;

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
        String cleanQuery = (query == null) ? "" : query.trim();
        String cleanContext = (referenceContext == null || referenceContext.isBlank()) ? "[NONE]" : referenceContext.trim();

        // 1. Dinamik Model Seçimi (Tier seviyesine göre taşıyıcı oluşturulur)
        GeminiClientConfig config = new GeminiClientConfig(
                apiKey,
                effectiveTier.getModelName(),
                15000,
                90000
        );
        GeminiSseTransport transport = new GeminiSseTransport(config);

        // 2. Dinamik Sıcaklık ve Token Bütçesi
        double temperature = deriveDynamicTemperature(effectiveTier);
        int maxTokens = deriveDynamicMaxTokens(effectiveTier, cleanQuery, cleanContext);

        String systemInstruction =
                "MODE: OBJECTIVE_ANALYSIS_ENGINE\n" +
                        "OUTPUT_CONTRACT: direct_analysis_only | no_preamble | no_conversational_filler | no_flattery\n\n" +
                        "[CORE_CONSTRAINTS]\n" +
                        "1. Treat REFERENCE_CONTEXT strictly as immutable background data.\n" +
                        "2. DO NOT mimic the typographical rhythm, casing, or internal syntax of the reference text.\n" +
                        "3. DO NOT generate continuation, speculative filler, or prose rewrites.\n" +
                        "4. Provide direct, objective, and analytically rigorous answers addressing the QUERY.\n" +
                        "5. State logical contradictions, structural gaps, or verification facts plainly without polite preambles.";

        String userPromptText = String.format(
                "REFERENCE_CONTEXT:\n%s\n\nQUERY / ANALYSIS_DIRECTIVE:\n%s",
                cleanContext,
                cleanQuery
        );

        JsonObject payload = GeminiPayloadBuilder.build(
                systemInstruction,
                userPromptText,
                temperature,
                maxTokens
        );

        transport.postAndStream(payload, onToken);
    }

    private double deriveDynamicTemperature(AnalysisTier tier) {
        switch (tier) {
            case FAST: return 0.1;      // Hızlı, kesin, sıfır sapma
            case BALANCED: return 0.2;  // Standart analitik denge
            case DEEP:
            default: return 0.3;        // Kapsamlı sentez ve derin çıkarım
        }
    }

    private int deriveDynamicMaxTokens(AnalysisTier tier, String query, String context) {
        int base = (query.length() > 200 || context.length() > 2000) ? 1200 : 600;
        if (tier == AnalysisTier.DEEP) {
            return base * 2; // Derin analiz için bütçe genişletilir
        }
        return base;
    }
}