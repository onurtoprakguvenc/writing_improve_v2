package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Katman 3 Analitik İstişare Motoru.
 * Hikaye metnine dokunmaz, üslup sentaksını taklit etmez.
 * Doğrudan tutarlılık, mantık, lore veya tempo analizi yapar.
 */
public class ConsultationEngine {

    private final String apiKey;
    private final String modelName;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private static final String DEFAULT_MODEL = "gemini-3.6-flash";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    public ConsultationEngine(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public ConsultationEngine(String apiKey, String modelName) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey null olamaz");
        this.modelName = (modelName == null || modelName.isBlank()) ? DEFAULT_MODEL : modelName;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(15))
                .build();
    }

    public void streamConsultation(String query, String storyContext, Consumer<String> onToken) throws IOException {
        String endpoint = BASE_URL + modelName + ":streamGenerateContent?alt=sse&key=" + apiKey;

        String systemInstruction =
                "MODE: ANALYTICAL_STORY_PARTNER\n" +
                        "OUTPUT_CONTRACT: direct_analysis_only | no_polite_openers | no_sycophancy | no_unsolicited_moralizing\n" +
                        "RULES:\n" +
                        "- DO NOT mimic the author's punctuation rhythm, lowercase casing, or inline bracket syntax.\n" +
                        "- DO NOT generate novel continuation or rewrite story prose.\n" +
                        "- Analyze the provided story context strictly as reference data to answer continuity, spatial logic, character consistency, or pacing inquiries.\n" +
                        "- Be concise, direct, and candid. Omit praise, apologies, and small talk.";

        JsonObject payload = new JsonObject();

        JsonObject sysInstObj = new JsonObject();
        JsonArray sysParts = new JsonArray();
        JsonObject sysText = new JsonObject();
        sysText.addProperty("text", systemInstruction);
        sysParts.add(sysText);
        sysInstObj.add("parts", sysParts);
        payload.add("systemInstruction", sysInstObj);

        JsonArray contents = new JsonArray();
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        JsonArray userParts = new JsonArray();
        JsonObject userText = new JsonObject();

        String promptBody = "ACTIVE STORY CONTEXT:\n" +
                (storyContext == null || storyContext.isBlank() ? "[NONE]" : storyContext) +
                "\n\nQUERY / ANALYSIS REQUEST:\n" + query;

        userText.addProperty("text", promptBody);
        userParts.add(userText);
        userContent.add("parts", userParts);
        contents.add(userContent);
        payload.add("contents", contents);

        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("temperature", 0.3);
        genConfig.addProperty("maxOutputTokens", 800);

        JsonObject thinkingConfig = new JsonObject();
        thinkingConfig.addProperty("thinkingBudget", 0);
        genConfig.add("thinkingConfig", thinkingConfig);

        payload.add("generationConfig", genConfig);

        RequestBody body = RequestBody.create(
                payload.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(endpoint)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "No body";
                throw new IOException("API Hatasi HTTP " + response.code() + ": " + err);
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("HTTP yanit govdesi bos.");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if (data.isEmpty() || "[DONE]".equals(data)) continue;

                        try {
                            JsonObject json = gson.fromJson(data, JsonObject.class);
                            if (json.has("candidates")) {
                                JsonArray candidates = json.getAsJsonArray("candidates");
                                if (!candidates.isEmpty()) {
                                    JsonObject cand = candidates.get(0).getAsJsonObject();
                                    if (cand.has("content")) {
                                        JsonObject content = cand.getAsJsonObject("content");
                                        if (content.has("parts")) {
                                            JsonArray parts = content.getAsJsonArray("parts");
                                            for (int i = 0; i < parts.size(); i++) {
                                                JsonObject part = parts.get(i).getAsJsonObject();
                                                if (part.has("text")) {
                                                    onToken.accept(part.get("text").getAsString());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }
}