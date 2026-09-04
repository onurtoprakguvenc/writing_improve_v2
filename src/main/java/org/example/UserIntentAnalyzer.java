package org.example;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserIntentAnalyzer {

    public enum IntentType {
        CONTINUE,
        REVISE,
        PROOFREAD,
        CONSULT
    }

    public enum ContextScope {
        FOCUSED(1500),
        EXTENDED(4000),
        FULL_MANUSCRIPT(Integer.MAX_VALUE);

        private final int charBudget;
        ContextScope(int charBudget) { this.charBudget = charBudget; }
        public int getCharBudget() { return charBudget; }
    }

    public static class AnalysisResult {
        private final IntentType intent;
        private final String cleanInstruction;
        private final String targetPassage;
        private final String effectiveContext;
        private final boolean isComplexSequence;
        private final boolean isFreshScene;

        public AnalysisResult(IntentType intent, String cleanInstruction, String targetPassage,
                              String effectiveContext, boolean isComplexSequence, boolean isFreshScene) {
            this.intent = intent;
            this.cleanInstruction = cleanInstruction;
            this.targetPassage = targetPassage;
            this.effectiveContext = effectiveContext;
            this.isComplexSequence = isComplexSequence;
            this.isFreshScene = isFreshScene;
        }

        public IntentType getIntent() { return intent; }
        public String getCleanInstruction() { return cleanInstruction; }
        public String getTargetPassage() { return targetPassage; }
        public String getEffectiveContext() { return effectiveContext; }
        public boolean isComplexSequence() { return isComplexSequence; }
        public boolean isFreshScene() { return isFreshScene; }
    }

    private static final Pattern CONSULT_PATTERN = Pattern.compile(
            "\\b(sence|nasıl|neden|mantıklı mı|çelişki|tutarlı mı|hata var mı|tempo|ritim|fikir|who|what|why|how|should|does it make sense)\\b|\\?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern PROOFREAD_PATTERN = Pattern.compile(
            "\\b(düzelt|yazım|imla|harf|typo|noktalama|grammar|proofread)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern REVISE_PATTERN = Pattern.compile(
            "\\b(değiştir|yeniden yaz|daha sert yap|yumuşat|şöyle de|yerine|kaldır|rewrite|revise|change|replace)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // Sahne sınırı kontrolü (***, ---, [yeni sahne] vb.)
    private static final Pattern SCENE_BREAK_PATTERN = Pattern.compile(
            "(\\*{3,}|-{3,}|#{2,}|(?i)\\[(yeni sahne|scene break|chapter)\\])"
    );

    // Çoklu olay/sekans sayacı (virgül, 'sonra', 'and then', 'ardından')
    private static final Pattern MULTI_STEP_PATTERN = Pattern.compile(
            "(,|\\bve\\b|\\bsonra\\b|\\bardından\\b|\\band then\\b|\\bthen\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    public AnalysisResult analyze(String rawPrompt, String selectedText, String fullManuscript, ContextScope scope) {
        String trimmedPrompt = rawPrompt == null ? "" : rawPrompt.trim();
        boolean hasSelection = selectedText != null && !selectedText.isBlank();
        String context = resolveContext(fullManuscript, scope);

        // 1. Sahne Geçiş Kontrolü
        boolean isFreshScene = false;
        if (context != null && SCENE_BREAK_PATTERN.matcher(context).find()) {
            String[] segments = SCENE_BREAK_PATTERN.split(context);
            if (segments.length > 1) {
                context = segments[segments.length - 1].trim();
                isFreshScene = true;
            }
        }

        // 2. Çoklu Eylem Sekansı Kontrolü
        Matcher stepMatcher = MULTI_STEP_PATTERN.matcher(trimmedPrompt);
        int steps = 0;
        while (stepMatcher.find()) steps++;
        boolean isComplexSequence = steps >= 2;

        // 3. İstişare Taraması
        if (CONSULT_PATTERN.matcher(trimmedPrompt).find() && !hasSelection) {
            return new AnalysisResult(IntentType.CONSULT, trimmedPrompt, null, context, isComplexSequence, isFreshScene);
        }

        // 4. İmla Taraması
        if (PROOFREAD_PATTERN.matcher(trimmedPrompt).find()) {
            String target = hasSelection ? selectedText : extractSafeFallbackTarget(context);
            return new AnalysisResult(IntentType.PROOFREAD, trimmedPrompt, target, context, isComplexSequence, isFreshScene);
        }

        // 5. Revizyon Taraması
        if (hasSelection || REVISE_PATTERN.matcher(trimmedPrompt).find()) {
            String target = hasSelection ? selectedText : extractSafeFallbackTarget(context);
            return new AnalysisResult(IntentType.REVISE, trimmedPrompt, target, context, isComplexSequence, isFreshScene);
        }

        // 6. Varsayılan Akış: Olay Örgüsü Devamı
        return new AnalysisResult(IntentType.CONTINUE, trimmedPrompt, null, context, isComplexSequence, isFreshScene);
    }

    private String resolveContext(String fullText, ContextScope scope) {
        if (fullText == null || fullText.isBlank()) return "";
        if (scope == ContextScope.FULL_MANUSCRIPT) return fullText;
        int budget = scope.getCharBudget();
        if (fullText.length() <= budget) return fullText;
        return fullText.substring(fullText.length() - budget);
    }

    // Seçimsiz revizyonda tüm bloğu ezmek yerine sadece son diyalog veya cümleyi hedef alma
    private String extractSafeFallbackTarget(String text) {
        if (text == null || text.isBlank()) return "";
        String[] lines = text.trim().split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                return line;
            }
        }
        return text;
    }
}