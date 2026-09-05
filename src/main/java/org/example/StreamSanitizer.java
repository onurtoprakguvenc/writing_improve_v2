package org.example;

import java.util.regex.Pattern;

/**
 * Katman 4: Anlık Akış ve Tampon Güvenlik Valfi.
 *
 * Akış esnasında bayt bütünlüğünü korumak için chunk'ları bölmez;
 * model sızıntılarını birleştirme öncesinde tam metin düzeyinde ayıklar.
 */
public final class StreamSanitizer {

    private StreamSanitizer() {}

    private static final Pattern MARKDOWN_CODE_FENCE = Pattern.compile("^```[a-zA-Z]*\\r?\\n?|```$", Pattern.MULTILINE);

    /**
     * Canlı akışta UTF-8 ve hece bütünlüğünü bozmamak adına ham token doğrudan iletilir.
     */
    public static String sanitizeChunk(String chunk) {
        return (chunk != null) ? chunk : "";
    }

    /**
     * Akış bittiğinde birikmiş çıktının tamamını temizler.
     */
    public static String sanitizeFullOutput(String fullOutput) {
        if (fullOutput == null || fullOutput.isEmpty()) {
            return "";
        }
        return MARKDOWN_CODE_FENCE.matcher(fullOutput).replaceAll("").trim();
    }
}