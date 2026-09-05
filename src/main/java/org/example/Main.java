package org.example;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * JVM console verification harness for real-time SSE streaming.
 * Verifies the UnifiedPipelineRouter and pure continuation architecture.
 */
public class Main {

    private static final String DEFAULT_CONTEXT =
            "(intense fight moment)\n" +
                    "\"so sister.how does it feel to...(slices a bit of her face and drops blood)\"\n" +
                    "\"you are a mo-mo-monster.\"\n" +
                    "\"i know.who denies that.know,back to your back.\"";

    private static final String DEFAULT_PLOT_POINT =
            "extend the text.the sister kicks her knee, grabs the dropped blade from the puddle.1 paragraph long";

    public static void main(String[] args) {
        // Enforce UTF-8 on standard console out/err to avoid encoding mismatches
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "AQ.Ab8RN6JzgLWbrc3Avb8Rvz8ZCrHI84JDtwUGpHX0hne-LO5_wg";
        }

        if (apiKey.isBlank()) {
            System.err.println("Error: GEMINI_API_KEY is not defined.");
            return;
        }

        String context = (args.length > 0 && !args[0].isBlank()) ? args[0] : DEFAULT_CONTEXT;
        String plotPoint = (args.length > 1 && !args[1].isBlank()) ? args[1] : DEFAULT_PLOT_POINT;
        AnalysisTier tier = (args.length > 2) ? parseTier(args[2]) : AnalysisTier.BALANCED;

        UnifiedPipelineRouter router = new UnifiedPipelineRouter(apiKey);
        EditorState state = EditorState.insertion(context, context.length());

        long startedAt = System.currentTimeMillis();
        boolean[] firstTokenReceived = {false};

        System.out.println("--- Generating Draft (Live SSE Stream Active) ---");

        try {
            EditorState updatedState = router.dispatch(plotPoint, state, tier, token -> {
                if (!firstTokenReceived[0]) {
                    long ttfb = System.currentTimeMillis() - startedAt;
                    System.out.println("[First Token Latency (TTFB): " + ttfb + "ms]");
                    firstTokenReceived[0] = true;
                }
                System.out.print(token);
                System.out.flush();
            });

            long totalMs = System.currentTimeMillis() - startedAt;
            System.out.println();
            System.out.println("--- Stream Completed (Total: " + totalMs + "ms) ---");

            System.out.println("\n=== SPLICED BUFFER CONTENT (EDITOR STATE) ===");
            System.out.println(updatedState.getFullManuscript());

        } catch (Exception e) {
            System.err.println("Execution failure encountered:");
            e.printStackTrace();
        }
    }

    private static AnalysisTier parseTier(String raw) {
        if (raw == null) return AnalysisTier.BALANCED;
        switch (raw.toUpperCase(Locale.ROOT)) {
            case "K1":
            case "FAST":
                return AnalysisTier.FAST;
            case "K3":
            case "DEEP":
                return AnalysisTier.DEEP;
            default:
                return AnalysisTier.BALANCED;
        }
    }
}