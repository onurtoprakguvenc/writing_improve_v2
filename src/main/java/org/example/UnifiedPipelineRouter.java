package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fiziksel Tampon Geometrisi Protokolü ile çalışan deterministik SSE motoru.
 * Mock/simülasyon içermez; doğrudan ham API ile çalışır.
 */
public class UnifiedPipelineRouter {

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int DEFAULT_CONTEXT_WINDOW_CHARS = 4000;
    private static final double DEFAULT_TEMPERATURE = 0.4;
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 1024;

    private final String apiKey;
    private final String baseUrl;
    private final int contextWindowChars;
    private final double temperature;
    private final int maxOutputTokens;

    public UnifiedPipelineRouter() {
        this(resolveApiKey(), DEFAULT_BASE_URL, DEFAULT_CONTEXT_WINDOW_CHARS, DEFAULT_TEMPERATURE, DEFAULT_MAX_OUTPUT_TOKENS);
    }

    public UnifiedPipelineRouter(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, DEFAULT_CONTEXT_WINDOW_CHARS, DEFAULT_TEMPERATURE, DEFAULT_MAX_OUTPUT_TOKENS);
    }

    public UnifiedPipelineRouter(
            String apiKey,
            String baseUrl,
            int contextWindowChars,
            double temperature,
            int maxOutputTokens
    ) {
        this.apiKey = (apiKey != null) ? apiKey.trim() : "";
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_BASE_URL;
        this.contextWindowChars = contextWindowChars > 0 ? contextWindowChars : DEFAULT_CONTEXT_WINDOW_CHARS;
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens > 0 ? maxOutputTokens : DEFAULT_MAX_OUTPUT_TOKENS;
    }

    private static String resolveApiKey() {
        String env = System.getenv("GEMINI_API_KEY");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        String prop = System.getProperty("gemini.api.key");
        return (prop != null) ? prop.trim() : "";
    }

    public void dispatch(
            String instruction,
            EditorState editorState,
            AnalysisTier tier,
            Consumer<String> tokenStreamListener
    ) throws IOException {
        Objects.requireNonNull(editorState, "editorState boş olamaz");
        Objects.requireNonNull(tokenStreamListener, "tokenStreamListener boş olamaz");

        if (this.apiKey.isEmpty()) {
            throw new IllegalStateException("Geçerli bir API anahtarı bulunamadı. Lütfen GEMINI_API_KEY ortam değişkenini veya yapılandırmayı kontrol edin.");
        }

        AnalysisTier selectedTier = (tier != null) ? tier : AnalysisTier.SURGICAL;
        String rawInstruction = (instruction != null) ? instruction.trim() : "";

        String systemInstruction;
        String userContent;

        if (editorState.hasSelection()) {
            // Sınırlı Mutasyon Modu: Yalnızca seçili aralık hedef alınır
            systemInstruction = buildBoundedMutationSystemInstruction();
            userContent = buildBoundedMutationUserContent(editorState, rawInstruction);
        } else {
            // Ekleme Modu: İmleçten ileriye doğru saf devam yolu
            systemInstruction = buildInsertionSystemInstruction();
            userContent = buildInsertionUserContent(editorState, rawInstruction);
        }

        executeGeminiSseStream(selectedTier.getModelName(), systemInstruction, userContent, tokenStreamListener);
    }

    private String buildInsertionSystemInstruction() {
        return "You are a physical mechanical narrative continuation engine embedded in a low-latency text editor.\n" +
                "PHYSICAL BUFFER GEOMETRY: INSERTION MODE (hasSelection == false).\n\n" +
                "EXECUTION CONTRACT & INVARIANTS:\n" +
                "1. INVARIANT PRECEDING BUFFER: You must NEVER regurgitate, repeat, quote, or rewrite any character from the preceding manuscript.\n" +
                "2. IMMEDIATE ADVANCEMENT: Your output will be spliced directly at the cursor position. Output MUST begin immediately with the next characters/words to follow the cursor.\n" +
                "3. SYNTACTIC CONTINUITY: Seamlessly match the narrative voice, tense, cadence, formatting, and dialogue syntax of the preceding anchor.\n" +
                "4. DIRECTIVE ADVANCE: Synthesize the user's action directive to drive the scene forward.\n" +
                "5. ZERO CHATTER: Output raw manuscript continuation text ONLY. Do NOT include conversational filler, explanations, markdown quotes, or notes.";
    }

    private String buildInsertionUserContent(EditorState state, String instruction) {
        String preceding = state.getPrecedingContext(this.contextWindowChars);
        return "[PRECEDING MANUSCRIPT BUFFER (INVARIANT ANCHOR)]:\n" +
                preceding + "\n\n" +
                "[CURSOR POSITION OFFSET: " + state.getCursorPosition() + "]\n\n" +
                "[ACTION DIRECTIVE]:\n" +
                instruction + "\n\n" +
                "Continue the manuscript immediately from the cursor forward:";
    }

    private String buildBoundedMutationSystemInstruction() {
        return "You are a precision manuscript transformation engine embedded in a low-latency text editor.\n" +
                "PHYSICAL BUFFER GEOMETRY: BOUNDED MUTATION MODE (hasSelection == true).\n\n" +
                "EXECUTION CONTRACT & INVARIANTS:\n" +
                "1. ISOLATED TARGET REWRITE: Rewrite SOLELY the target excerpt according to the user's instruction.\n" +
                "2. STRICT PHYSICAL BOUNDARIES: Your generated output will replace ONLY the highlighted slice [selectionStart, selectionEnd].\n" +
                "3. NO BUFFER SPILLOVER: Do NOT output or repeat the surrounding unselected prefix or suffix text buffers.\n" +
                "4. BOUNDARY COHERENCE: Ensure the replacement text fits seamlessly between the preceding prefix and following suffix.\n" +
                "5. ZERO CHATTER: Output raw replacement text ONLY. Do NOT include conversational filler, explanations, or quotes.";
    }

    private String buildBoundedMutationUserContent(EditorState state, String instruction) {
        int halfWindow = this.contextWindowChars / 2;
        String preceding = state.getPrecedingContext(halfWindow);
        String selected = state.getSelectedText();
        String trailing = state.getTrailingContext(halfWindow);

        return "[PRECEDING CONTEXT (IMMUTABLE)]:\n" +
                preceding + "\n\n" +
                "[TARGET EXCERPT TO TRANSFORM (BOUNDED SELECTION)]:\n" +
                selected + "\n\n" +
                "[TRAILING CONTEXT (IMMUTABLE)]:\n" +
                trailing + "\n\n" +
                "[TRANSFORMATION INSTRUCTION]:\n" +
                instruction + "\n\n" +
                "Provide solely the replacement text for the target excerpt:";
    }

    private void executeGeminiSseStream(
            String modelName,
            String systemInstruction,
            String userContent,
            Consumer<String> tokenStreamListener
    ) throws IOException {
        String cleanBase = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        String endpoint = cleanBase + modelName + ":streamGenerateContent?alt=sse";

        URL url = URI.create(endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "text/event-stream");
        // Hem standart REST parametresini hem de Vertex/Auth formatlarını kapsayan başlıklar
        conn.setRequestProperty("x-goog-api-key", this.apiKey);
        conn.setRequestProperty("Authorization", "Bearer " + this.apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);

        String jsonPayload = buildJsonRequestBody(systemInstruction, userContent);
        byte[] inputBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(inputBytes);
            os.flush();
        }

        int statusCode = conn.getResponseCode();
        if (statusCode != HttpURLConnection.HTTP_OK) {
            String errorResponse = readStreamFully(conn.getErrorStream());
            throw new IOException("Gemini API Hatası (" + statusCode + "): " + errorResponse);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String dataJson = line.substring(6).trim();
                    if (!dataJson.isEmpty() && !dataJson.equals("[DONE]")) {
                        String textChunk = extractCandidateText(dataJson);
                        if (textChunk != null && !textChunk.isEmpty()) {
                            tokenStreamListener.accept(textChunk);
                        }
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private String buildJsonRequestBody(String systemInstruction, String userContent) {
        return "{\n" +
                "  \"systemInstruction\": {\n" +
                "    \"parts\": [{\"text\": \"" + escapeJson(systemInstruction) + "\"}]\n" +
                "  },\n" +
                "  \"contents\": [\n" +
                "    {\n" +
                "      \"role\": \"user\",\n" +
                "      \"parts\": [{\"text\": \"" + escapeJson(userContent) + "\"}]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"generationConfig\": {\n" +
                "    \"temperature\": " + this.temperature + ",\n" +
                "    \"maxOutputTokens\": " + this.maxOutputTokens + "\n" +
                "  }\n" +
                "}";
    }

    static String extractCandidateText(String json) {
        int textKeyIndex = json.indexOf("\"text\":");
        if (textKeyIndex == -1) return null;

        int firstQuote = json.indexOf('"', textKeyIndex + 7);
        if (firstQuote == -1) return null;

        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        int i = firstQuote + 1;
        int len = json.length();

        while (i < len) {
            char c = json.charAt(i);
            if (escape) {
                switch (c) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'u':
                        if (i + 4 < len) {
                            String hex = json.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException ignored) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                        break;
                    default:
                        sb.append(c);
                        break;
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
            i++;
        }
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 32);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
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
            return "<hata akışı okunamadı>";
        }
    }
}