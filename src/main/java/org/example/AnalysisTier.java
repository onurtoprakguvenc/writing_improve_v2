package org.example;

/**
 * Execution tiers defining latency, model reasoning depth, and deterministic token ceilings.
 */
public enum AnalysisTier {
    /**
     * Rapid execution tier with lower token ceiling (2048 tokens).
     */
    FAST("gemini-3.6-flash", 2048, "Fast streaming execution"),

    /**
     * Alias for low-latency fast surgical operations (2048 tokens).
     */
    SURGICAL("gemini-3.6-flash", 2048, "Low-latency surgical execution"),

    /**
     * Balanced tier for standard prose continuation (4096 tokens).
     */
    BALANCED("gemini-3.6-flash", 4096, "Balanced narrative continuation"),

    /**
     * High-depth tier for complex stylistic and thematic expansions (8192 tokens).
     */
    DEEP("gemini-3.1-pro", 8192, "High-depth reasoning and prose continuation");

    private final String modelName;
    private final int defaultMaxOutputTokens;
    private final String description;

    AnalysisTier(String modelName, int defaultMaxOutputTokens, String description) {
        this.modelName = modelName;
        this.defaultMaxOutputTokens = defaultMaxOutputTokens;
        this.description = description;
    }

    public String getModelName() {
        return modelName;
    }

    public int getDefaultMaxOutputTokens() {
        return defaultMaxOutputTokens;
    }

    public String getDescription() {
        return description;
    }
}