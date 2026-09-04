package org.example;

import java.util.Locale;

/**
 * JVM console verification harness for real-time SSE streaming.
 * Supports both CLI arguments and fallback test samples.
 */
public class Main {

    // Yeni ritim ve parantez içi aksiyon test örnekleri
    private static final String DEFAULT_CONTEXT =
            "(intense fight moment)\n" +
                    "\"so sister.how does it feel to...(slices a bit of her face and drops blood)\"\n" +
                    "\"you are a mo-mo-monster.\"\n" +
                    "\"ı know.who denies that.know,back to your back.\"";

    private static final String DEFAULT_PLOT_POINT =
            "the sister kicks her knee, grabs the dropped blade from the puddle";

    public static void main(String[] args) {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "AQ.Ab8RN6IabMVCrjOiZDvl4xj7n-wexbnIS-yKA9ZJwaYoqLnbXA";
        }

        if (apiKey.equals("YOUR_API_KEY_HERE") || apiKey.isBlank()) {
            System.err.println("Hata: GEMINI_API_KEY tanımlı değil.");
            return;
        }

        // CLI parametresi varsa onu al, yoksa test örneklerini kullan
        String context = (args.length > 0 && !args[0].isBlank()) ? args[0] : DEFAULT_CONTEXT;
        String plotPoint = (args.length > 1 && !args[1].isBlank()) ? args[1] : DEFAULT_PLOT_POINT;
        AnalysisTier tier = (args.length > 2) ? parseTier(args[2]) : AnalysisTier.K2_BALANCED;

        CalibrationEngine engine = new CalibrationEngine(apiKey);

        StringBuilder aggregated = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        boolean[] firstTokenReceived = {false};

        System.out.println("--- Taslak Üretiliyor (Canlı Akış Başlatıldı) ---");

        try {
            engine.streamNextDraft(context, plotPoint, tier, token -> {
                if (!firstTokenReceived[0]) {
                    long ttfb = System.currentTimeMillis() - startedAt;
                    System.out.println("[İlk Token Gecikmesi (TTFB): " + ttfb + "ms]");
                    firstTokenReceived[0] = true;
                }
                System.out.print(token);
                System.out.flush();
                aggregated.append(token);
            });

            long totalMs = System.currentTimeMillis() - startedAt;
            System.out.println();
            System.out.println("--- Akış Tamamlandı (Toplam: " + totalMs + "ms) ---");

            String rawOutput = aggregated.toString();
            String polished = StructuralPolish.closeHangingSyntax(rawOutput);

            if (!polished.equals(rawOutput)) {
                System.out.println("--- Sözdizimi Düzeltmesi Uygulandı (Kapanış Eklendi) ---");
                System.out.println(polished);
            }

            // -------------------------------------------------------------
            // RevisionEngine Doğrulama Testi
            // -------------------------------------------------------------
            System.out.println("\n--- RevisionEngine Testi Başlatılıyor ---");
            RevisionEngine revisionEngine = new RevisionEngine(apiKey);

            String targetPassage = "\"ı am not done yet.(kicks her knee hard and grabs the dropped blade from the puddle)\"";
            String revisionInstruction = "make her fingers slip on the muddy blade first, missing the grab once before securing it";

            long revStartedAt = System.currentTimeMillis();
            boolean[] revFirstTokenReceived = {false};

            revisionEngine.streamRevision(targetPassage, revisionInstruction, context, tier, token -> {
                if (!revFirstTokenReceived[0]) {
                    long ttfb = System.currentTimeMillis() - revStartedAt;
                    System.out.println("[Revizyon İlk Token Gecikmesi (TTFB): " + ttfb + "ms]");
                    revFirstTokenReceived[0] = true;
                }
                System.out.print(token);
                System.out.flush();
            });

            long revTotalMs = System.currentTimeMillis() - revStartedAt;
            System.out.println();
            System.out.println("--- Revizyon Akışı Tamamlandı (Toplam: " + revTotalMs + "ms) ---");

        } catch (Exception e) {
            System.err.println("Akış sırasında hata oluştu:");
            e.printStackTrace();
        }
    }

    private static AnalysisTier parseTier(String raw) {
        if (raw == null) return AnalysisTier.K2_BALANCED;
        switch (raw.toUpperCase(Locale.ROOT)) {
            case "K1": return AnalysisTier.K1_LIGHT;
            case "K3": return AnalysisTier.K3_DEEP;
            default: return AnalysisTier.K2_BALANCED;
        }
    }
}