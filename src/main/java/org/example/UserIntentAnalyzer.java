package org.example;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class UserIntentAnalyzer {

    private static final char[] DIALOGUE_LEAD_CHARS = {
            '"', '\'', '\u201C', '\u201D', '\u2018', '\u2019',
            '\u00AB', '\u00BB', '\u2014', '\u2013', '-', '\u2012', '\u2015'
    };

    private static final Pattern TYPO_MARKER_PATTERN = Pattern.compile("^\\*\\p{L}+|\\p{L}+\\*$");
    private static final Pattern DIFF_REPLACEMENT_PATTERN = Pattern.compile(".+\\s*(->|=>)\\s*.+");
    private static final Pattern SED_SUBSTITUTION_PATTERN = Pattern.compile("^s/[^/]+/[^/]*/?[a-z]*$");

    private static final String[] PROOFREAD_GRAMMATICAL_STEMS = {
            "typo", "spell", "gramm", "punct", "ortho", "correct", "fix", "err",
            "comma", "period", "semicolon", "colon", "apostrophe", "hyphen", "dash",
            "yazım", "imla", "imlâ", "noktala", "virgül", "nokta", "kesme", "hata", "dilbilgi"
    };

    private static final Set<String> INTERROGATIVE_STRUCTURAL_LEADS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "what", "why", "how", "where", "when", "who", "whom", "whose", "which"
    )));

    private static final Set<String> AUXILIARY_INVERSION_VERBS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "is", "are", "am", "was", "were", "can", "could", "should", "would",
            "will", "shall", "do", "does", "did", "have", "has", "had", "may", "might", "must"
    )));

    private static final Set<String> INVERSION_SUBJECTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "i", "you", "he", "she", "it", "we", "they", "this", "that", "these", "those",
            "the", "a", "an", "there", "our", "my", "your", "their", "his", "her"
    )));

    private static final Pattern TURKISH_INTERROGATIVE_CLITIC = Pattern.compile(
            "\\b(m[ıiuü](sin|sın|sun|sün|siniz|sınız|sunuz|sünüz|yiz|yız|yuz|yüz|dir|dır|dur|dür|di|dı|du|dü|miş|mış|muş|müş)?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );

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

        if (state.hasSelection()) {
            if (isProofreadStructuralCue(prompt)) {
                return new AnalysisResult(
                        UserIntent.PROOFREAD,
                        prompt,
                        state.getSelectedText(),
                        state.getSelectionStart(),
                        state.getSelectionEnd(),
                        true,
                        state,
                        "Active selection; structural correction cues detected."
                );
            } else {
                return new AnalysisResult(
                        UserIntent.REVISE,
                        prompt,
                        state.getSelectedText(),
                        state.getSelectionStart(),
                        state.getSelectionEnd(),
                        true,
                        state,
                        "Active selection; locked to in-place revision."
                );
            }
        }

        if (isStructuralTransformationSyntax(prompt)) {
            TextBlock safeTarget = extractSafeTarget(state.getFullManuscript(), state.getCursorPosition());
            return new AnalysisResult(
                    UserIntent.REVISE,
                    prompt,
                    safeTarget.text,
                    safeTarget.start,
                    safeTarget.end,
                    true,
                    state,
                    "Structural transformation syntax; dynamically targeting trailing block."
            );
        }

        if (isInterrogativeStructure(prompt)) {
            return new AnalysisResult(
                    UserIntent.CONSULT,
                    prompt,
                    "",
                    -1,
                    -1,
                    false,
                    state,
                    "Interrogative structure without selection; advisory consultation mode."
            );
        }

        int insertionPoint = state.getCursorPosition();
        return new AnalysisResult(
                UserIntent.CONTINUE,
                prompt,
                "",
                insertionPoint,
                insertionPoint,
                true,
                state,
                "No selection or question marker; default beat advancement."
        );
    }

    private AnalysisResult resolveExplicitIntent(EditorState state, String prompt, UserIntent explicitIntent) {
        switch (explicitIntent) {
            case PROOFREAD:
                if (state.hasSelection()) {
                    return new AnalysisResult(
                            UserIntent.PROOFREAD,
                            prompt,
                            state.getSelectedText(),
                            state.getSelectionStart(),
                            state.getSelectionEnd(),
                            true,
                            state,
                            "Explicit PROOFREAD with selection."
                    );
                } else {
                    TextBlock safeTarget = extractSafeTarget(state.getFullManuscript(), state.getCursorPosition());
                    return new AnalysisResult(
                            UserIntent.PROOFREAD,
                            prompt,
                            safeTarget.text,
                            safeTarget.start,
                            safeTarget.end,
                            true,
                            state,
                            "Explicit PROOFREAD targeted trailing block."
                    );
                }

            case REVISE:
                if (state.hasSelection()) {
                    return new AnalysisResult(
                            UserIntent.REVISE,
                            prompt,
                            state.getSelectedText(),
                            state.getSelectionStart(),
                            state.getSelectionEnd(),
                            true,
                            state,
                            "Explicit REVISE with selection."
                    );
                } else {
                    TextBlock safeTarget = extractSafeTarget(state.getFullManuscript(), state.getCursorPosition());
                    return new AnalysisResult(
                            UserIntent.REVISE,
                            prompt,
                            safeTarget.text,
                            safeTarget.start,
                            safeTarget.end,
                            true,
                            state,
                            "Explicit REVISE targeted trailing block."
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
                int insertionPoint = state.getCursorPosition();
                return new AnalysisResult(
                        UserIntent.CONTINUE,
                        prompt,
                        "",
                        insertionPoint,
                        insertionPoint,
                        true,
                        state,
                        "Explicit CONTINUE at cursor."
                );
        }
    }

    public boolean isProofreadStructuralCue(String prompt) {
        if (prompt == null || prompt.isEmpty()) return false;

        if (TYPO_MARKER_PATTERN.matcher(prompt).find()) return true;
        if (DIFF_REPLACEMENT_PATTERN.matcher(prompt).matches() || SED_SUBSTITUTION_PATTERN.matcher(prompt).matches()) return true;

        String clean = prompt.toLowerCase().replaceAll("[^\\p{L}\\s]", " ");
        String[] tokens = clean.split("\\s+");
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            for (String stem : PROOFREAD_GRAMMATICAL_STEMS) {
                if (token.contains(stem)) return true;
            }
        }
        return false;
    }

    public boolean isInterrogativeStructure(String prompt) {
        if (prompt == null || prompt.isEmpty()) return false;

        if (hasTerminalQuestionPunctuation(prompt)) return true;
        if (prompt.startsWith("¿")) return true;
        if (TURKISH_INTERROGATIVE_CLITIC.matcher(prompt).find()) return true;

        String normalized = prompt.trim();
        String[] words = normalized.split("\\s+");
        if (words.length == 0) return false;

        String firstWord = words[0].toLowerCase().replaceAll("[^\\p{L}]", "");
        if (firstWord.isEmpty()) return false;

        if (INTERROGATIVE_STRUCTURAL_LEADS.contains(firstWord)) return true;

        if (words.length >= 2 && AUXILIARY_INVERSION_VERBS.contains(firstWord)) {
            String secondWord = words[1].toLowerCase().replaceAll("[^\\p{L}]", "");
            if (INVERSION_SUBJECTS.contains(secondWord)) return true;
        }

        return false;
    }

    private boolean hasTerminalQuestionPunctuation(String text) {
        int i = text.length() - 1;
        while (i >= 0) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '\'' || c == '»' || c == '”' || c == '’' || c == ')' || c == ']') {
                i--;
            } else {
                break;
            }
        }
        if (i < 0) return false;
        char c = text.charAt(i);
        return c == '?' || c == '\uFF1F' || c == '\u061F';
    }

    private boolean isStructuralTransformationSyntax(String prompt) {
        if (prompt == null || prompt.isEmpty()) return false;
        return DIFF_REPLACEMENT_PATTERN.matcher(prompt).matches() || SED_SUBSTITUTION_PATTERN.matcher(prompt).matches();
    }

    public TextBlock extractSafeTarget(String manuscript, int cursor) {
        if (manuscript == null || manuscript.isEmpty()) {
            return new TextBlock("", 0, 0);
        }

        int len = manuscript.length();
        int anchor = (cursor <= 0 || cursor > len) ? len : cursor;

        int end = anchor;
        while (end > 0 && Character.isWhitespace(manuscript.charAt(end - 1))) {
            end--;
        }

        if (end == 0) {
            end = len;
            while (end > 0 && Character.isWhitespace(manuscript.charAt(end - 1))) {
                end--;
            }
            if (end == 0) return new TextBlock("", 0, 0);
        }

        if (anchor < len && !Character.isWhitespace(manuscript.charAt(anchor))) {
            int forward = anchor;
            while (forward < len && manuscript.charAt(forward) != '\n' && manuscript.charAt(forward) != '\r') {
                forward++;
            }
            end = Math.max(end, forward);
        }

        int lineStart = end;
        while (lineStart > 0 && manuscript.charAt(lineStart - 1) != '\n' && manuscript.charAt(lineStart - 1) != '\r') {
            lineStart--;
        }

        boolean isDialogue = isDialogueLine(manuscript, lineStart, end);
        int blockStart = lineStart;

        if (!isDialogue) {
            while (blockStart > 0) {
                int prevLineEnd = blockStart - 1;
                if (prevLineEnd > 0 && manuscript.charAt(prevLineEnd) == '\n' && manuscript.charAt(prevLineEnd - 1) == '\r') {
                    prevLineEnd--;
                }

                if (prevLineEnd >= 0 && (manuscript.charAt(prevLineEnd) == '\n' || manuscript.charAt(prevLineEnd) == '\r')) {
                    break;
                }

                int prevLineStart = prevLineEnd;
                while (prevLineStart > 0 && manuscript.charAt(prevLineStart - 1) != '\n' && manuscript.charAt(prevLineStart - 1) != '\r') {
                    prevLineStart--;
                }

                if (isLineBlank(manuscript, prevLineStart, prevLineEnd + 1) || isDialogueLine(manuscript, prevLineStart, prevLineEnd + 1)) {
                    break;
                }

                blockStart = prevLineStart;
            }
        }

        while (blockStart < end && Character.isWhitespace(manuscript.charAt(blockStart))) {
            blockStart++;
        }
        while (end > blockStart && Character.isWhitespace(manuscript.charAt(end - 1))) {
            end--;
        }

        if (blockStart >= end) return new TextBlock("", blockStart, blockStart);

        return new TextBlock(manuscript.substring(blockStart, end), blockStart, end);
    }

    private boolean isDialogueLine(String manuscript, int start, int end) {
        int i = start;
        while (i < end && (manuscript.charAt(i) == ' ' || manuscript.charAt(i) == '\t')) {
            i++;
        }
        if (i >= end) return false;

        char c = manuscript.charAt(i);
        for (char lead : DIALOGUE_LEAD_CHARS) {
            if (c == lead) return true;
        }
        return false;
    }

    private boolean isLineBlank(String manuscript, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = manuscript.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                return false;
            }
        }
        return true;
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