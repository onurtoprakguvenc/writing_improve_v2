package org.example;

import com.google.gson.JsonObject;
import org.example.transport.GeminiClientConfig;
import org.example.transport.GeminiPayloadBuilder;
import org.example.transport.GeminiSseTransport;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Katman 3: İleriye Doğru Akış Motoru (Continuation Engine).
 *
 * Sohbet formu simülasyonu yapmaz. Arabellek akışını doğrudan
 * bir sonraki bayttan itibaren devam ettirir.
 */
public class CalibrationEngine {

    private final String apiKey;
    private static final Pattern LENGTH_HINT_PATTERN = Pattern.compile("(?i)\\b(\\d+)\\s*(paragraph|paragraf|sentence|cümle|page|sayfa|word|kelime)\\b");

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

        int maxTokens = resolveDynamicMaxTokens(directive, state);
        double temperature = state.getRecommendedTemperature();

        // 1. Görev formu etiketleri yerine doğrudan fiziksel sınır tanımları
        String systemInstruction = buildPureContinuationSystemInstruction();

        // 2. Form doldurma şablonu (KEY: VALUE) kaldırıldı; ham bağlam ve açık talimat ayrıldı
        String userPrompt = buildBufferExtensionPrompt(precedingContext, directive);

        JsonObject payload = GeminiPayloadBuilder.build(
                systemInstruction,
                userPrompt,
                temperature,
                maxTokens
        );

        GeminiClientConfig config = new GeminiClientConfig(apiKey, effectiveTier.getModelName(), 30000, 60000);
        GeminiSseTransport transport = new GeminiSseTransport(config);

        transport.postAndStream(payload, onToken);
    }

    /**
     * Roleplay veya kurgusal kısıt dayatmaz. Yalnızca çıktının doğrudan
     * tampona dikileceğini ve etiket basılmaması gerektiğini dikte eder.
     */
    private String buildPureContinuationSystemInstruction() {
        return "You are a deterministic text buffer continuation engine.\n"
                + "The text you generate will be stitched directly into the user's active editor buffer starting immediately from the last character.\n"
                + "Rules:\n"
                + "1. Generate only the raw continuation text.\n"
                + "2. Never output conversational pleasantries, markdown code fences, or section headers/labels (such as 'Reaction:', 'Continuation:', 'Output:').\n"
                + "3. Adhere strictly to the requested scope and length instructed by the user.";
    }

    /**
     * Modeli form doldurmaya teşvik etmeyen, sınırları açık metin bağlamı.
     */
    private String buildBufferExtensionPrompt(String precedingContext, String directive) {
        StringBuilder sb = new StringBuilder();
        if (precedingContext != null && !precedingContext.isEmpty()) {
            sb.append("--- CURRENT BUFFER START ---\n")
                    .append(precedingContext)
                    .append("\n--- CURRENT BUFFER END ---\n\n");
        }

        sb.append("Instruction: Continue the text from CURRENT BUFFER END, fulfilling this directive: ")
                .append((directive != null && !directive.isEmpty()) ? directive : "continue naturally");

        return sb.toString();
    }

    /**
     * Yönergedeki uzunluk talebini analiz eder. Keyfi sabit (magic number)
     * yerine fiziksel hacim ihtiyacını çözer.
     */
    private int resolveDynamicMaxTokens(String directive, NarrativeState state) {
        if (directive == null || directive.isEmpty()) {
            return state.getRecommendedMaxTokens();
        }

        Matcher m = LENGTH_HINT_PATTERN.matcher(directive);
        if (m.find()) {
            int count = Integer.parseInt(m.group(1));
            String unit = m.group(2).toLowerCase();

            switch (unit) {
                case "paragraph":
                case "paragraf":
                    // Ortalama 1 paragraf ~ 150-250 token
                    return Math.min(4096, Math.max(512, count * 350));
                case "page":
                case "sayfa":
                    // 1 sayfa ~ 700-900 token
                    return Math.min(8192, Math.max(1024, count * 1000));
                case "sentence":
                case "cümle":
                    return Math.min(2048, Math.max(128, count * 40));
                case "word":
                case "kelime":
                    return Math.min(4096, (int) (count * 1.5));
                default:
                    break;
            }
        }

        // Açık bir sayısal hacim yoksa state'in taban değerini korur
        return Math.max(512, state.getRecommendedMaxTokens());
    }
}