package org.example.transport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class GeminiPayloadBuilder {

    public static JsonObject build(String systemInstructionText, String userPromptText, double temperature) {
        return build(systemInstructionText, userPromptText, temperature, null);
    }

    public static JsonObject build(
            String systemInstructionText,
            String userPromptText,
            double temperature,
            Integer maxOutputTokens
    ) {
        JsonObject rootJson = new JsonObject();

        if (systemInstructionText != null && !systemInstructionText.isBlank()) {
            JsonObject systemInstruction = new JsonObject();
            JsonArray systemParts = new JsonArray();
            JsonObject systemPart = new JsonObject();
            systemPart.addProperty("text", systemInstructionText);
            systemParts.add(systemPart);
            systemInstruction.add("parts", systemParts);
            rootJson.add("systemInstruction", systemInstruction);
        }

        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray userParts = new JsonArray();
        JsonObject userText = new JsonObject();
        userText.addProperty("text", userPromptText != null ? userPromptText : "");
        userParts.add(userText);
        contentObj.add("parts", userParts);
        contents.add(contentObj);
        rootJson.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", temperature);
        if (maxOutputTokens != null && maxOutputTokens > 0) {
            generationConfig.addProperty("maxOutputTokens", maxOutputTokens);
        }
        rootJson.add("generationConfig", generationConfig);

        return rootJson;
    }
}