package org.example;

import java.util.regex.Pattern;

/**
 * Layer 2: Buffer Geometry and Syntax Resolver.
 *
 * Separates natural language directives from discrete structural operators,
 * generating deterministic physical coordinate routing.
 */
public final class BufferGeometryResolver {

    private BufferGeometryResolver() {}

    // sed operator: s/old/new/ or s/old/new/g
    private static final Pattern SED_OPERATOR = Pattern.compile("^s/[^/]+/[^/]*/?[a-z]*$");

    // Discrete transformation: short syntax on left and right, not full paragraphs
    private static final Pattern DISCRETE_ARROW_OPERATOR = Pattern.compile("^[^\\n\\r]{1,50}\\s*(->|=>)\\s*[^\\n\\r]{1,50}$");

    private static final Pattern INTERROGATIVE_OR_ANALYSIS = Pattern.compile(
            "(\\?$|(\\b(mı|mi|mu|mü|mıdır|midir|mudur|müdür|nedir|nelerdir|nasıl|neden|niçin|kim|hangi|kaç)\\b)|\\b(analiz|incele|kontrol et|tutarlı mı|açıkla|check|analyze|explain|why|how|what|who)\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    public static PipelineRoute resolve(EditorState state, String rawDirective) {
        String directive = (rawDirective != null) ? rawDirective.trim() : "";

        // 1. Physical Highlighting: Explicit selection enforces mutation mode
        if (state.hasSelection()) {
            return PipelineRoute.mutation(
                    state.getSelectedText(),
                    state.getSelectionStart(),
                    state.getSelectionEnd()
            );
        }

        // 2. Structural Operator (Sed or short arrow notation)
        if (isStructuralOperator(directive)) {
            TextBlock block = extractPrecedingBlock(state.getFullManuscript(), state.getCursorPosition());
            if (!block.isEmpty()) {
                return PipelineRoute.mutation(block.text, block.start, block.end);
            }
        }

        // 3. Interrogative or pure analytical inquiry: Buffer left untouched
        if (INTERROGATIVE_OR_ANALYSIS.matcher(directive).find()) {
            return PipelineRoute.consultation();
        }

        // 4. Default Linear Continuation: Splice forward from active cursor
        return PipelineRoute.continuation(state.getCursorPosition());
    }

    private static boolean isStructuralOperator(String directive) {
        if (directive.isEmpty()) return false;
        return SED_OPERATOR.matcher(directive).matches() || DISCRETE_ARROW_OPERATOR.matcher(directive).matches();
    }

    public static TextBlock extractPrecedingBlock(String manuscript, int cursor) {
        if (manuscript == null || manuscript.isEmpty()) {
            return new TextBlock("", 0, 0);
        }

        int len = manuscript.length();
        int anchor = (cursor <= 0 || cursor > len) ? len : cursor;

        int end = anchor;
        while (end > 0 && Character.isWhitespace(manuscript.charAt(end - 1))) {
            end--;
        }

        if (end == 0) return new TextBlock("", 0, 0);

        int start = end;
        while (start > 0) {
            char c = manuscript.charAt(start - 1);
            if (c == '\n') {
                if (start >= 2 && manuscript.charAt(start - 2) == '\n') break;
                if (start >= 3 && manuscript.charAt(start - 2) == '\r' && manuscript.charAt(start - 3) == '\n') break;
            }
            start--;
        }

        while (start < end && Character.isWhitespace(manuscript.charAt(start))) start++;
        while (end > start && Character.isWhitespace(manuscript.charAt(end - 1))) end--;

        if (start >= end) return new TextBlock("", start, start);
        return new TextBlock(manuscript.substring(start, end), start, end);
    }

    public static final class TextBlock {
        public final String text;
        public final int start;
        public final int end;

        public TextBlock(String text, int start, int end) {
            this.text = (text != null) ? text : "";
            this.start = start;
            this.end = end;
        }

        public boolean isEmpty() { return text.isEmpty(); }
    }
}