package org.example;

import java.io.Serializable;
import java.util.Objects;

/**
 * Pure, Deterministic In-Buffer Splice Engine.
 *
 * Executes atomic splicing and boundary hygiene; returns updated text
 * and exact resulting cursor coordinates via SpliceResult.
 */
public final class DiffMergeEngine {

    public DiffMergeEngine() {
        // Stateless utility instance
    }

    public static final class SpliceResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String updatedManuscript;
        private final int newCursorPosition;

        public SpliceResult(String updatedManuscript, int newCursorPosition) {
            this.updatedManuscript = Objects.requireNonNull(updatedManuscript, "updatedManuscript cannot be null");
            this.newCursorPosition = newCursorPosition;
        }

        public String getUpdatedManuscript() {
            return updatedManuscript;
        }

        public int getNewCursorPosition() {
            return newCursorPosition;
        }
    }

    public String applyInsertion(String manuscript, int insertionOffset, String generatedChunk) {
        String base = (manuscript != null) ? manuscript : "";
        String chunk = (generatedChunk != null) ? generatedChunk : "";

        if (chunk.isEmpty()) {
            return base;
        }

        int offset = clamp(insertionOffset, 0, base.length());
        StringBuilder sb = new StringBuilder(base.length() + chunk.length());
        sb.append(base, 0, offset);
        sb.append(chunk);
        sb.append(base, offset, base.length());
        return sb.toString();
    }

    public String applySelectionReplacement(String manuscript, int start, int end, String replacement) {
        String base = (manuscript != null) ? manuscript : "";
        String rep = (replacement != null) ? replacement : "";

        int len = base.length();
        int s = clamp(Math.min(start, end), 0, len);
        int e = clamp(Math.max(start, end), 0, len);

        StringBuilder sb = new StringBuilder(len - (e - s) + rep.length());
        sb.append(base, 0, s);
        sb.append(rep);
        sb.append(base, e, len);
        return sb.toString();
    }

    public String cleanBoundary(String generated, String precedingText, String trailingText) {
        if (generated == null || generated.isEmpty()) {
            return "";
        }

        String cleaned = cleanLeakedFences(generated);
        cleaned = cleanDuplicateBoundaryQuotes(cleaned, precedingText, trailingText);
        cleaned = ensureBoundarySpacing(cleaned, precedingText, trailingText);
        return cleaned;
    }

    public String cleanLeakedFences(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String result = text;

        if (result.startsWith("```")) {
            int newlineIndex = result.indexOf('\n');
            if (newlineIndex != -1 && newlineIndex <= 20) {
                result = result.substring(newlineIndex + 1);
            } else {
                result = result.replaceFirst("^```[a-zA-Z0-9_-]*", "");
            }
        }

        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
            if (result.endsWith("\r\n")) {
                result = result.substring(0, result.length() - 2);
            } else if (result.endsWith("\n") || result.endsWith("\r")) {
                result = result.substring(0, result.length() - 1);
            }
        }

        return result;
    }

    public String cleanDuplicateBoundaryQuotes(String text, String precedingText, String trailingText) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String result = text;

        if (precedingText != null && !precedingText.isEmpty()) {
            char lastChar = precedingText.charAt(precedingText.length() - 1);
            if (isOpeningQuoteOrDash(lastChar)) {
                if (!result.isEmpty() && isOpeningQuoteOrDash(result.charAt(0))) {
                    result = result.substring(1);
                }
            }
        }

        if (trailingText != null && !trailingText.isEmpty()) {
            char firstChar = trailingText.charAt(0);
            if (isClosingQuote(firstChar)) {
                if (!result.isEmpty() && isClosingQuote(result.charAt(result.length() - 1))) {
                    result = result.substring(0, result.length() - 1);
                }
            }
        }

        return result;
    }

    /**
     * Prevents text sticking together when previous text ends with quotes, periods, or words
     * and model does not start with whitespace or newline.
     */
    private String ensureBoundarySpacing(String text, String precedingText, String trailingText) {
        if (text == null || text.isEmpty() || precedingText == null || precedingText.isEmpty()) {
            return text;
        }

        char lastPreceding = precedingText.charAt(precedingText.length() - 1);
        char firstGenerated = text.charAt(0);

        // If preceding ends with a non-whitespace character (quote, punctuation, letter)
        // and generated text starts with a non-whitespace character (not space, not newline), inject a space
        if (!Character.isWhitespace(lastPreceding) && !Character.isWhitespace(firstGenerated)) {
            // Check if generated starts with punctuation that naturally attaches to words (e.g. comma, period)
            if (firstGenerated != ',' && firstGenerated != '.' && firstGenerated != ';' && firstGenerated != ':') {
                return " " + text;
            }
        }

        return text;
    }

    public SpliceResult applyInsertionWithBoundaryCleaning(String manuscript, int insertionOffset, String generated) {
        String base = (manuscript != null) ? manuscript : "";
        int offset = clamp(insertionOffset, 0, base.length());

        String prefix = base.substring(0, offset);
        String suffix = base.substring(offset);

        String cleaned = cleanBoundary(generated, prefix, suffix);
        String updated = applyInsertion(base, offset, cleaned);
        int newCursor = offset + cleaned.length();

        return new SpliceResult(updated, newCursor);
    }

    public SpliceResult applySelectionReplacementWithBoundaryCleaning(String manuscript, int start, int end, String replacement) {
        String base = (manuscript != null) ? manuscript : "";
        int len = base.length();
        int s = clamp(Math.min(start, end), 0, len);
        int e = clamp(Math.max(start, end), 0, len);

        String prefix = base.substring(0, s);
        String suffix = base.substring(e, len);

        String cleaned = cleanBoundary(replacement, prefix, suffix);
        String updated = applySelectionReplacement(base, s, e, cleaned);
        int newCursor = s + cleaned.length();

        return new SpliceResult(updated, newCursor);
    }

    private static boolean isOpeningQuoteOrDash(char c) {
        return c == '"' || c == '\u201C' || c == '\u00AB' || c == '\u2018' ||
                c == '\u2014' || c == '\u2013' || c == '-';
    }

    private static boolean isClosingQuote(char c) {
        return c == '"' || c == '\u201D' || c == '\u00BB' || c == '\u2019';
    }

    private static int clamp(int val, int min, int max) {
        if (val < min) return min;
        if (val > max) return max;
        return val;
    }
}