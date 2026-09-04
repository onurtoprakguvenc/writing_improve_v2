package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CalibrationEngine {

    private final String apiKey;
    private final String modelName;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private static final String DEFAULT_MODEL_NAME = "gemini-3.6-flash";
    private static final String BASE_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:streamGenerateContent?alt=sse";

    private static final int MAX_TOKENS_FLOOR = 600;
    private static final int MAX_TOKENS_CEILING = 800;

    private static final double CYNICISM_HARDNESS_THRESHOLD = 0.6;
    private static final int STRUCTURAL_ANCHOR_LINE_WINDOW = 3;

    public CalibrationEngine(String apiKey) {
        this(apiKey, DEFAULT_MODEL_NAME);
    }

    public CalibrationEngine(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = (modelName != null && !modelName.isBlank()) ? modelName : DEFAULT_MODEL_NAME;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    private String resolveBaseUrl() {
        return String.format(BASE_URL_TEMPLATE, modelName);
    }

    public String generateNextDraft(String userContext, String plotPoint) throws IOException {
        return generateNextDraft(userContext, plotPoint, AnalysisTier.K2_BALANCED);
    }

    public String generateNextDraft(String rawUserContext, String plotPoint, AnalysisTier tier) throws IOException {
        AnalysisTier effectiveTier = (tier != null) ? tier : AnalysisTier.K2_BALANCED;
        NarrativeState state = LocalShadowCore.analyze(rawUserContext, effectiveTier);

        JsonObject rootJson = buildRequestPayload(state, rawUserContext, plotPoint);

        RequestBody body = RequestBody.create(
                rootJson.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(resolveBaseUrl())
                .addHeader("x-goog-api-key", this.apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("API Hatası: " + response.code() + " - " + err);
            }

            StringBuilder fullResponse = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("data:")) {
                        String jsonChunk = line.substring(5).trim();
                        if (jsonChunk.isEmpty() || jsonChunk.equals("[DONE]")) continue;

                        try {
                            JsonObject chunkObj = gson.fromJson(jsonChunk, JsonObject.class);
                            if (chunkObj.has("candidates")) {
                                JsonArray candidates = chunkObj.getAsJsonArray("candidates");
                                if (candidates.size() > 0) {
                                    JsonObject candidate = candidates.get(0).getAsJsonObject();
                                    if (candidate.has("content")) {
                                        JsonObject content = candidate.getAsJsonObject("content");
                                        if (content.has("parts")) {
                                            JsonArray parts = content.getAsJsonArray("parts");
                                            for (JsonElement part : parts) {
                                                if (part.isJsonObject() && part.getAsJsonObject().has("text")) {
                                                    fullResponse.append(part.getAsJsonObject().get("text").getAsString());
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

            return fullResponse.toString().trim();
        }
    }

    public void streamNextDraft(String rawUserContext, String plotPoint, AnalysisTier tier, Consumer<String> onToken) throws IOException {
        AnalysisTier effectiveTier = (tier != null) ? tier : AnalysisTier.K2_BALANCED;
        NarrativeState state = LocalShadowCore.analyze(rawUserContext, effectiveTier);

        JsonObject rootJson = buildRequestPayload(state, rawUserContext, plotPoint);

        RequestBody body = RequestBody.create(
                rootJson.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(resolveBaseUrl())
                .addHeader("x-goog-api-key", this.apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("API Hatası: " + response.code() + " - " + err);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("data:")) {
                        String jsonChunk = line.substring(5).trim();
                        if (jsonChunk.isEmpty() || jsonChunk.equals("[DONE]")) continue;

                        try {
                            JsonObject chunkObj = gson.fromJson(jsonChunk, JsonObject.class);
                            if (chunkObj.has("candidates")) {
                                JsonArray candidates = chunkObj.getAsJsonArray("candidates");
                                if (candidates.size() > 0) {
                                    JsonObject candidate = candidates.get(0).getAsJsonObject();
                                    if (candidate.has("content")) {
                                        JsonObject content = candidate.getAsJsonObject("content");
                                        if (content.has("parts")) {
                                            JsonArray parts = content.getAsJsonArray("parts");
                                            for (JsonElement part : parts) {
                                                if (part.isJsonObject() && part.getAsJsonObject().has("text")) {
                                                    String delta = part.getAsJsonObject().get("text").getAsString();
                                                    if (!delta.isEmpty()) {
                                                        onToken.accept(delta);
                                                    }
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

    private JsonObject buildRequestPayload(NarrativeState state, String rawUserContext, String plotPoint) {
        String systemInstructionText = buildParametricConstraintMatrix(state, rawUserContext);
        String userPromptText = String.format("ACTIVE CONTEXT:\n%s\n\nNEXT PLOT POINT:\n%s", rawUserContext, plotPoint);

        JsonObject rootJson = new JsonObject();

        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemPart = new JsonObject();
        systemPart.addProperty("text", systemInstructionText);
        systemParts.add(systemPart);
        systemInstruction.add("parts", systemParts);
        rootJson.add("systemInstruction", systemInstruction);

        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray userParts = new JsonArray();
        JsonObject userText = new JsonObject();
        userText.addProperty("text", userPromptText);
        userParts.add(userText);
        contentObj.add("parts", userParts);
        contents.add(contentObj);
        rootJson.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", state.getRecommendedTemperature());
        generationConfig.addProperty("maxOutputTokens", resolveMaxOutputTokens(state));

        JsonObject thinkingConfig = new JsonObject();
        thinkingConfig.addProperty("thinkingBudget", 0);
        generationConfig.add("thinkingConfig", thinkingConfig);

        rootJson.add("generationConfig", generationConfig);

        return rootJson;
    }

    private int resolveMaxOutputTokens(NarrativeState state) {
        int recommended = state.getRecommendedMaxTokens();
        if (recommended < MAX_TOKENS_FLOOR) return MAX_TOKENS_FLOOR;
        if (recommended > MAX_TOKENS_CEILING) return MAX_TOKENS_CEILING;
        return recommended;
    }

    private String buildParametricConstraintMatrix(NarrativeState state, String rawUserContext) {
        StringBuilder sb = new StringBuilder();

        sb.append("MODE: MECHANICAL_CONTINUATION_ENGINE\n");
        sb.append("OUTPUT_CONTRACT: raw_story_text_only | no_preface | no_meta_commentary | single_paragraph | single_beat\n\n");

        sb.append("[CONSTRAINT_MATRIX]\n");

        // Sert kelime sınırı yerine tam cümle ve parantezli eyleme izin veren nefes aralığı
        // UserIntentAnalyzer'dan gelen çoklu eylem sinyali kontrol edilir
        int baseVelocity = Math.max(12, (int) Math.round(state.getAvgWordsPerSentence() * 3.0));
        if (rawUserContext.contains("---") || rawUserContext.contains("***")) {
            sb.append("SCENE_TRANSITION=detected | RULE=reset_emotional_inertia\n");
        }

// Birden fazla eylem isteniyorsa kelime tavanını iki katına çıkar ve çoklu adıma izin ver
        sb.append("TARGET_VELOCITY=").append(baseVelocity * 2).append(" | RULE=allow_complete_action_and_dialogue_clause\n");
        sb.append("ALLOW_MULTI_STEP_RESOLUTION=true\n");

        sb.append("BALANCE.DIALOGUE_RATIO=").append(formatRatio(state.getDialogueRatio()))
                .append(" | BALANCE.ACTION_RATIO=").append(formatRatio(state.getStageDirectionRatio()))
                .append(" | RULE=match_observed_proportions\n");

        sb.append("SENSORY_BUDGET=").append(state.getSensoryBudget())
                .append(" | RULE=max_physical_descriptors_per_beat\n");

        double cynicism = state.getCynicismIndex();
        sb.append("CYNICISM_INDEX=").append(formatRatio(cynicism));
        if (cynicism > CYNICISM_HARDNESS_THRESHOLD) {
            sb.append(" | RULE=enforce_blunt_verbs+prohibit_moralizing+prohibit_polite_resolution\n");
        } else {
            sb.append(" | RULE=standard_tonal_register\n");
        }

        sb.append("CADENCE=").append(state.getCadence().name()).append("\n");
        sb.append("FORMAT_MODE=").append(state.getFormatMode().name()).append("\n");

        List<String> bannedStems = state.getBannedStems();
        sb.append("[NEGATIVE_CONSTRAINTS]\n");
        if (bannedStems != null && !bannedStems.isEmpty()) {
            sb.append("SUPPRESS_ABSOLUTE=[").append(String.join("|", bannedStems)).append("]\n");
        } else {
            sb.append("SUPPRESS_ABSOLUTE=[]\n");
        }
        sb.append("TOLERANCE=zero\n\n");

        String anchorSample = extractStructuralAnchor(rawUserContext);
        sb.append("[STRUCTURAL_ANCHOR]\n");
        sb.append("SAMPLE (verbatim, last ").append(STRUCTURAL_ANCHOR_LINE_WINDOW).append(" lines from author):\n");
        sb.append("<<<\n").append(anchorSample).append("\n>>>\n");
        sb.append("RULE: Replicate the EXACT syntax, quote style, casing, and intra-dialogue bracket/parenthesis rhythm shown in SAMPLE. ");
        sb.append("If SAMPLE embeds an action cue inside a quoted line (e.g. dialogue containing an inline parenthetical), preserve that exact embedding — do NOT extract it into a separate stage-direction line, and do NOT normalize it into standard literary prose. ");
        sb.append("Match punctuation placement and letter casing exactly as written, including any lowercase or unconventional styling. Continue the pattern; do not correct it. ");
        sb.append("MANDATORY: Always close open quotation marks and finish the parenthetical action completely before ending output.\n\n");

        sb.append("[HARD_STOP_RULES]\n");
        sb.append("NO_CONVERSATIONAL_COMMENTARY=true\n");
        sb.append("NO_PREFACE=true\n");
        sb.append("NO_MULTI_PARAGRAPH_DRIFT=true\n");
        sb.append("ADVANCE_ONLY_IMMEDIATE_BEAT=true\n");
        sb.append("RESPECT_TOKEN_ENVELOPE=true\n");

        return sb.toString();
    }

    private String extractStructuralAnchor(String rawUserContext) {
        if (rawUserContext == null || rawUserContext.trim().isEmpty()) {
            return "(no prior lines available — no anchor sample)";
        }

        String[] rawLines = rawUserContext.trim().split("\\r?\\n");

        List<String> nonEmptyLines = new java.util.ArrayList<>();
        for (String line : rawLines) {
            if (!line.trim().isEmpty()) {
                nonEmptyLines.add(line);
            }
        }

        if (nonEmptyLines.isEmpty()) {
            return "(no prior lines available — no anchor sample)";
        }

        int windowStart = Math.max(0, nonEmptyLines.size() - STRUCTURAL_ANCHOR_LINE_WINDOW);
        List<String> window = nonEmptyLines.subList(windowStart, nonEmptyLines.size());

        return String.join("\n", window);
    }

    private String formatRatio(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}