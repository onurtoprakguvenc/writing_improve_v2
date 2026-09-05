package org.example.transport;

public class GeminiClientConfig {

    private final String apiKey;
    private final String modelName;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    // URL'den key parametresi söküldü
    private static final String BASE_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:streamGenerateContent?alt=sse";

    public GeminiClientConfig(String apiKey, String modelName) {
        this(apiKey, modelName, 30000, 60000);
    }

    public GeminiClientConfig(String apiKey, String modelName, int connectTimeoutMs, int readTimeoutMs) {
        if (apiKey != null && !apiKey.isBlank()) {
            this.apiKey = apiKey.trim();
        } else {
            String envKey = System.getenv("GEMINI_API_KEY");
            this.apiKey = (envKey != null && !envKey.isBlank()) ? envKey.trim() : "";
        }
        this.modelName = (modelName != null && !modelName.isBlank()) ? modelName : "gemini-3.8-flash";
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public String resolveEndpointUrl() {
        return String.format(BASE_URL_TEMPLATE, modelName);
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }
}