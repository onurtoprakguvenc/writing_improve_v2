package org.example.transport;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class GeminiSseTransport {

    private final GeminiClientConfig config;
    private final Gson gson;

    public GeminiSseTransport(GeminiClientConfig config) {
        this.config = config;
        this.gson = new Gson();
    }

    public void postAndStream(JsonObject payload, Consumer<String> onToken) throws IOException {
        byte[] payloadBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

        URL url = new URL(config.resolveEndpointUrl());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setDoOutput(true);
        conn.setConnectTimeout(config.getConnectTimeoutMs());
        conn.setReadTimeout(config.getReadTimeoutMs());

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payloadBytes);
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String err = readStreamFully(conn.getErrorStream());
            throw new IOException("Generative API Error (" + responseCode + "): " + err);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("data:")) {
                    String jsonChunk = line.substring(5).trim();
                    if (jsonChunk.isEmpty() || "[DONE]".equals(jsonChunk)) continue;

                    String text = extractText(jsonChunk);
                    if (text != null && !text.isEmpty()) {
                        onToken.accept(text);
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private String extractText(String jsonChunk) {
        try {
            JsonObject chunkObj = gson.fromJson(jsonChunk, JsonObject.class);
            if (!chunkObj.has("candidates")) return null;

            JsonArray candidates = chunkObj.getAsJsonArray("candidates");
            if (candidates.size() == 0) return null;

            JsonObject candidate = candidates.get(0).getAsJsonObject();
            if (!candidate.has("content")) return null;

            JsonObject content = candidate.getAsJsonObject("content");
            if (!content.has("parts")) return null;

            JsonArray parts = content.getAsJsonArray("parts");
            StringBuilder sb = new StringBuilder();
            for (JsonElement part : parts) {
                if (part.isJsonObject() && part.getAsJsonObject().has("text")) {
                    sb.append(part.getAsJsonObject().get("text").getAsString());
                }
            }
            return sb.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readStreamFully(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return "<unable to read error stream>";
        }
    }
}