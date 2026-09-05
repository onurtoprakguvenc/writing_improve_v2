package org.example;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Katman 2: Evrensel Yapısal Niyet ve Hedef Ayrıştırıcı.
 *
 * Sözlüksel etiketlemelerden, statik kelime listelerinden ve türe bağımlı
 * kısıtlardan tamamen arındırılmıştır. Sadece fiziksel tampon geometrisi,
 * seçim sınırları ve yapısal operatörlerle hedef belirler.
 */
public final class UserIntentAnalyzer {

    private static final Pattern DIFF_OPERATOR_PATTERN = Pattern.compile(".+\\s*(->|=>)\\s*.+");
    private static final Pattern SED_OPERATOR_PATTERN = Pattern.compile("^s/[^/]+/[^/]*/?[a-z]*$");

    public UserIntentAnalyzer() {
    }

    public AnalysisResult analyze(EditorState editorState, String rawPrompt) {
        return analyze(editorState, rawPrompt, null);
    }

    public AnalysisResult analyze(EditorState editorState, String rawPrompt, UserIntent explicitIntent) {
        EditorState state = (editorState != null) ? editorState : EditorState.empty();
        String prompt = (rawPrompt != null) ? rawPrompt.trim() : "";

        if (explicitIntent != null) {
            return resolveExplicitIntent(state, prompt, explicitIntent);
        }

        // 1. Durum: Kullanıcı metin seçmişse (E-posta cümlesi, roman repliği veya kod satırı)
        if (state.hasSelection()) {
            return new AnalysisResult(
                    UserIntent.REVISE,
                    prompt,
                    state.getSelectedText(),
                    state.getSelectionStart(),
                    state.getSelectionEnd(),
                    true,
                    state,
                    "Bounded buffer selection active; routing to in-place mutation."
            );
        }

        // 2. Durum: Açık yapısal dönüşüm sözdizimi (örn: a -> b veya s/eski/yeni/)
        if (isStructuralTransformation(prompt)) {
            TextBlock target = extractPrecedingBlock(state.getFullManuscript(), state.getCursorPosition());
            return new AnalysisResult(
                    UserIntent.REVISE,
                    prompt,
                    target.text,
                    target.start,
                    target.end,
                    true,
                    state,
                    "Explicit transformation operator detected; targeted immediate preceding block."
            );
        }

        // 3. Durum: Sorgulama / İstişare (Metin üzerinde değişiklik yapmadan akıl yürütme)
        if (isInterrogative(prompt)) {
            return new AnalysisResult(
                    UserIntent.CONSULT,
                    prompt,
                    "",
                    -1,
                    -1,
                    false,
                    state,
                    "Interrogative syntax without selection; routing to consultation."
            );
        }

        // 4. Durum: Varsayılan ileri yönlü akış (İmleç noktasından itibaren devam ettirme)
        int cursor = state.getCursorPosition();
        return new AnalysisResult(
                UserIntent.CONTINUE,
                prompt,
                "",
                cursor,
                cursor,
                true,
                state,
                "Linear cursor continuation; advancing buffer."
        );
    }

    private AnalysisResult resolveExplicitIntent(EditorState state, String prompt, UserIntent explicitIntent) {
        switch (explicitIntent) {
            case PROOFREAD:
            case REVISE:
                if (state.hasSelection()) {
                    return new AnalysisResult(
                            explicitIntent,
                            prompt,
                            state.getSelectedText(),
                            state.getSelectionStart(),
                            state.getSelectionEnd(),
                            true,
                            state,
                            "Explicit " + explicitIntent + " with selection."
                    );
                } else {
                    TextBlock target = extractPrecedingBlock(state.getFullManuscript(), state.getCursorPosition());
                    return new AnalysisResult(
                            explicitIntent,
                            prompt,
                            target.text,
                            target.start,
                            target.end,
                            true,
                            state,
                            "Explicit " + explicitIntent + " targeted preceding block."
                    );
                }

            case CONSULT:
                return new AnalysisResult(
                        UserIntent.CONSULT,
                        prompt,
                        "",
                        -1,
                        -1,
                        false,
                        state,
                        "Explicit CONSULT requested."
                );

            case CONTINUE:
            default:
                int cursor = state.getCursorPosition();
                return new AnalysisResult(
                        UserIntent.CONTINUE,
                        prompt,
                        "",
                        cursor,
                        cursor,
                        true,
                        state,
                        "Explicit CONTINUE at cursor."
                );
        }
    }

    private boolean isStructuralTransformation(String prompt) {
        if (prompt == null || prompt.isEmpty()) return false;
        return DIFF_OPERATOR_PATTERN.matcher(prompt).matches() || SED_OPERATOR_PATTERN.matcher(prompt).matches();
    }

    private boolean isInterrogative(String prompt) {
        if (prompt == null || prompt.isEmpty()) return false;
        int i = prompt.length() - 1;
        while (i >= 0) {
            char c = prompt.charAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '\'' || c == '»' || c == '”' || c == '’' || c == ')' || c == ']') {
                i--;
            } else {
                break;
            }
        }
        if (i < 0) return false;
        char c = prompt.charAt(i);
        return c == '?' || c == '\uFF1F' || c == '\u061F' || prompt.startsWith("¿");
    }

    /**
     * İmlecin hemen gerisindeki bağımsız yapısal kütleyi (paragraf, satır veya kod bloğu)
     * türünden bağımsız olarak sınırlarından yakalar.
     */
    public TextBlock extractPrecedingBlock(String manuscript, int cursor) {
        if (manuscript == null || manuscript.isEmpty()) {
            return new TextBlock("", 0, 0);
        }

        int len = manuscript.length();
        int anchor = (cursor <= 0 || cursor > len) ? len : cursor;

        // İmleç gerisindeki boşlukları temizle
        int end = anchor;
        while (end > 0 && Character.isWhitespace(manuscript.charAt(end - 1))) {
            end--;
        }

        if (end == 0) return new TextBlock("", 0, 0);

        // Çift satır sonu (\n\n) veya tek satırlık kütle sınırını geriye doğru ara
        int start = end;
        while (start > 0) {
            char c = manuscript.charAt(start - 1);
            if (c == '\n') {
                if (start >= 2 && manuscript.charAt(start - 2) == '\n') {
                    break; // İki ardışık satır sonu: paragraf sınırı
                }
                if (start >= 3 && manuscript.charAt(start - 2) == '\r' && manuscript.charAt(start - 3) == '\n') {
                    break; // Windows formatı: \r\n\r\n sınırı
                }
            }
            start--;
        }

        // Başlangıç ve bitişteki boşlukları ayıkla
        while (start < end && Character.isWhitespace(manuscript.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(manuscript.charAt(end - 1))) {
            end--;
        }

        if (start >= end) return new TextBlock("", start, start);

        return new TextBlock(manuscript.substring(start, end), start, end);
    }

    public static final class TextBlock implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String text;
        public final int start;
        public final int end;

        public TextBlock(String text, int start, int end) {
            this.text = (text != null) ? text : "";
            this.start = start;
            this.end = end;
        }

        public int length() { return end - start; }
        public boolean isEmpty() { return text.isEmpty(); }

        @Override
        public String toString() {
            return "TextBlock{start=" + start + ", end=" + end + ", text='" + text + "'}";
        }
    }

    public static final class AnalysisResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private final UserIntent intent;
        private final String prompt;
        private final String targetText;
        private final int targetStart;
        private final int targetEnd;
        private final boolean manuscriptModificationAllowed;
        private final EditorState editorState;
        private final String routingReason;

        public AnalysisResult(
                UserIntent intent,
                String prompt,
                String targetText,
                int targetStart,
                int targetEnd,
                boolean manuscriptModificationAllowed,
                EditorState editorState,
                String routingReason
        ) {
            this.intent = Objects.requireNonNull(intent, "Intent cannot be null");
            this.prompt = (prompt != null) ? prompt : "";
            this.targetText = (targetText != null) ? targetText : "";
            this.targetStart = targetStart;
            this.targetEnd = targetEnd;
            this.manuscriptModificationAllowed = manuscriptModificationAllowed;
            this.editorState = editorState;
            this.routingReason = (routingReason != null) ? routingReason : "";
        }

        public UserIntent getIntent() { return intent; }
        public String getPrompt() { return prompt; }
        public String getTargetText() { return targetText; }
        public int getTargetStart() { return targetStart; }
        public int getTargetEnd() { return targetEnd; }
        public boolean isManuscriptModificationAllowed() { return manuscriptModificationAllowed; }
        public boolean hasTargetRange() { return targetStart >= 0 && targetEnd >= targetStart; }
        public EditorState getEditorState() { return editorState; }
        public String getRoutingReason() { return routingReason; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AnalysisResult that = (AnalysisResult) o;
            return targetStart == that.targetStart &&
                    targetEnd == that.targetEnd &&
                    manuscriptModificationAllowed == that.manuscriptModificationAllowed &&
                    intent == that.intent &&
                    Objects.equals(prompt, that.prompt) &&
                    Objects.equals(targetText, that.targetText) &&
                    Objects.equals(routingReason, that.routingReason);
        }

        @Override
        public int hashCode() {
            return Objects.hash(intent, prompt, targetText, targetStart, targetEnd, manuscriptModificationAllowed, routingReason);
        }

        @Override
        public String toString() {
            return "AnalysisResult{" +
                    "intent=" + intent +
                    ", targetRange=[" + targetStart + ", " + targetEnd + "]" +
                    ", targetLength=" + targetText.length() +
                    ", modificationAllowed=" + manuscriptModificationAllowed +
                    ", reason='" + routingReason + '\'' +
                    '}';
        }
    }
}