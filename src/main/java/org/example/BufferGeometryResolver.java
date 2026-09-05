package org.example;

import java.util.regex.Pattern;

/**
 * Katman 2: Tampon Geometrisi ve Sentaks Ayrıştırıcı.
 *
 * Doğal dil yönergeleri ile yapısal operatörleri ayrıştırır,
 * doğrudan fiziksel koordinat rotası üretir.
 */
public final class BufferGeometryResolver {

    private BufferGeometryResolver() {}

    // sed operatörü: s/eski/yeni/ veya s/eski/yeni/g
    private static final Pattern SED_OPERATOR = Pattern.compile("^s/[^/]+/[^/]*/?[a-z]*$");

    // Salt yapısal dönüşüm: sol taraf ve sağ taraf kısa sentaks olmalı, uzun doğal dil cümlesi olmamalı
    private static final Pattern DISCRETE_ARROW_OPERATOR = Pattern.compile("^[^\\n\\r]{1,50}\\s*(->|=>)\\s*[^\\n\\r]{1,50}$");

    private static final Pattern INTERROGATIVE_OR_ANALYSIS = Pattern.compile(
            "(?i)(\\?$|(\\b(mı|mi|mu|mü|mıdır|midir|mudur|müdür|nedir|nelerdir|nasıl|neden|niçin|kim|hangi|kaç)\\b)|\\b(analiz|incele|kontrol et|tutarlı mı|açıkla|check|analyze|explain|why|how|what|who)\\b)"
    );

    public static PipelineRoute resolve(EditorState state, String rawDirective) {
        String directive = (rawDirective != null) ? rawDirective.trim() : "";

        // 1. Fiziksel Vurgulama: Kullanıcı aralık seçtiyse kesinlikle mutasyondur
        if (state.hasSelection()) {
            return PipelineRoute.mutation(
                    state.getSelectedText(),
                    state.getSelectionStart(),
                    state.getSelectionEnd()
            );
        }

        // 2. Belirgin Yapısal Operatör (Sed veya Kısa Ok Sentaksı)
        if (isStructuralOperator(directive)) {
            TextBlock block = extractPrecedingBlock(state.getFullManuscript(), state.getCursorPosition());
            if (!block.isEmpty()) {
                return PipelineRoute.mutation(block.text, block.start, block.end);
            }
        }

        // 3. Salt Analitik / Soru Sentaksı: Tampona dokunulmaz
        if (INTERROGATIVE_OR_ANALYSIS.matcher(directive).find()) {
            return PipelineRoute.consultation();
        }

        // 4. Varsayılan Doğrusal Akış: İmleç noktasından itibaren devam
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