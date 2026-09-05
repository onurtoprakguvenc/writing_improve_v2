package org.example;

import java.util.regex.Pattern;

/**
 * Katman 4: Anlık Akış Filtresi ve Güvenlik Valfi.
 *
 * LLM tarafında nadiren de olsa sızabilecek markdown kod bloklarını
 * ve sistem öneklerini arabelleğe ulaşmadan önce ayıklar.
 */
public final class StreamSanitizer {

    private StreamSanitizer() {}

    private static final Pattern MARKDOWN_CODE_FENCE = Pattern.compile("^```[a-zA-Z]*\\r?\\n?|```$");

    public static String sanitizeChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        // Markdown çitlerini akıştan düşür
        if (chunk.contains("```")) {
            return MARKDOWN_CODE_FENCE.matcher(chunk).replaceAll("");
        }
        return chunk;
    }
}