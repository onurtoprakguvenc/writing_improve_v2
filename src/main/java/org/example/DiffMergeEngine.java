package org.example;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Katman 4: Deterministik Metin Birleştirme ve Cerrahi Dikiş Motoru.
 *
 * Açık koordinatlar üzerinden tampon birleştirmesi yapar.
 * Seçim olsun ya da olmasın, verilen fiziksel aralığı hatasız mutate eder.
 */
public final class DiffMergeEngine {

    private DiffMergeEngine() {
    }

    private static final Pattern CODE_BLOCK_FENCE = Pattern.compile("^```[a-zA-Z]*\\r?\\n?|```$", Pattern.MULTILINE);

    /**
     * EditorState içindeki mevcut seçili bölgeyi değiştirir.
     */
    public static EditorState applyReplacement(EditorState currentState, String replacementText) {
        Objects.requireNonNull(currentState, "currentState must not be null");
        return applyReplacement(currentState, currentState.getSelectionStart(), currentState.getSelectionEnd(), replacementText);
    }

    /**
     * Tampon üzerindeki açık fiziksel koordinat aralığını [start, end] yeni metinle değiştirir.
     */
    public static EditorState applyReplacement(EditorState currentState, int start, int end, String replacementText) {
        Objects.requireNonNull(currentState, "currentState must not be null");
        String cleanReplacement = (replacementText == null) ? "" : sanitizeSurgicalOutput(replacementText);

        String original = currentState.getFullManuscript();
        int len = original.length();

        int s = Math.max(0, Math.min(start, len));
        int e = Math.max(0, Math.min(end, len));
        int actualStart = Math.min(s, e);
        int actualEnd = Math.max(s, e);

        // Cerrahi dikiş: [0...actualStart] + cleanReplacement + [actualEnd...len]
        StringBuilder buffer = new StringBuilder(original.length() + cleanReplacement.length());
        buffer.append(original, 0, actualStart);
        buffer.append(cleanReplacement);
        buffer.append(original, actualEnd, len);

        int newCursor = actualStart + cleanReplacement.length();
        return new EditorState(buffer.toString(), newCursor, newCursor, newCursor);
    }

    /**
     * İmleç noktasına yeni akışı ekler.
     */
    public static EditorState applyContinuation(EditorState currentState, String continuationText) {
        Objects.requireNonNull(currentState, "currentState must not be null");
        String cleanContinuation = (continuationText == null) ? "" : sanitizeSurgicalOutput(continuationText);

        String original = currentState.getFullManuscript();
        int cursor = Math.max(0, Math.min(currentState.getCursorPosition(), original.length()));

        StringBuilder buffer = new StringBuilder(original.length() + cleanContinuation.length());
        buffer.append(original, 0, cursor);
        buffer.append(cleanContinuation);
        buffer.append(original, cursor, original.length());

        int newCursor = cursor + cleanContinuation.length();
        return new EditorState(buffer.toString(), newCursor, newCursor, newCursor);
    }

    /**
     * Dış tırnakları ve sızan markdown bloklarını temizler.
     */
    /**
     * Dış tırnakları, sızan markdown bloklarını ve kapatılmamış / boş parantezleri temizler.
     */
    public static String sanitizeSurgicalOutput(String text) {
        if (text == null || text.isBlank()) return "";
        String sanitized = CODE_BLOCK_FENCE.matcher(text.trim()).replaceAll("").trim();

        if (sanitized.length() >= 2) {
            // Tamamen tırnak içindeyse dış tırnakları at
            if ((sanitized.startsWith("\"") && sanitized.endsWith("\"")) ||
                    (sanitized.startsWith("\u201C") && sanitized.endsWith("\u201D"))) {
                sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
            }
        }

        // Boş parantezleri temizle
        sanitized = sanitized.replace("()", "").trim();

        // Eğer açılmış ama kapatılmamış parantez varsa (veya sadece sonda asılı kalmışsa) temizle
        if (sanitized.startsWith("(") && !sanitized.endsWith(")")) {
            sanitized = sanitized.substring(1).trim();
        } else if (sanitized.endsWith(")") && !sanitized.contains("(")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1).trim();
        } else if (sanitized.startsWith("(") && sanitized.endsWith(")")) {
            // Eğer tamamı parantez içindeyse parantezleri kaldır (düz metne dönüştür)
            sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
        }

        return sanitized;
    }
}