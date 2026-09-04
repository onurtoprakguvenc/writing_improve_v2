package org.example;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JVM-yerel, sıfır-gecikmeli niyet ve dinamik bağlam görüş alanı (FOV) motoru.
 * Kelime sınırları ile yanlış pozitifleri önler, tırnak içi alıntıları tam metinde arar
 * ve meta yönlendiricileri temiz talimattan ayıklar.
 */
public class UserIntentAnalyzer {

    public enum IntentType {
        CONTINUE,   // İleriye doğru tek beat yürüt
        REVISE,     // Seçili/kapsam içindeki parçayı dönüştür
        PROOFREAD,  // Üsluba dokunmadan yazım kusurlarını temizle
        CONSULT     // Metne dokunma; mantık/lore analizi yap
    }

    public enum ContextScope {
        FOCUSED(1500),      // Optimum / Önerilen: Hızlı, keskin, son ~300 kelime
        EXPANDED(5000),     // Geniş: Yakın geçmişi ve sahne girişini kapsar
        FULL_MANUSCRIPT(-1);// Samanlıkta İğne: Tüm metin taranır

        private final int maxChars;

        ContextScope(int maxChars) {
            this.maxChars = maxChars;
        }

        public int getMaxChars() {
            return maxChars;
        }
    }

    public static class AnalysisResult {
        private final IntentType intent;
        private final String cleanInstruction;
        private final String targetPassage;
        private final String effectiveContext;
        private final double confidence;

        public AnalysisResult(IntentType intent, String cleanInstruction, String targetPassage, String effectiveContext, double confidence) {
            this.intent = intent;
            this.cleanInstruction = cleanInstruction;
            this.targetPassage = targetPassage;
            this.effectiveContext = effectiveContext;
            this.confidence = confidence;
        }

        public IntentType getIntent() { return intent; }
        public String getCleanInstruction() { return cleanInstruction; }
        public String getTargetPassage() { return targetPassage; }
        public String getEffectiveContext() { return effectiveContext; }
        public double getConfidence() { return confidence; }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "Intent: %s (Confidence: %.2f)\nTarget Passage: [%s]\nContext Window Length: %d chars\nClean Instruction: \"%s\"",
                    intent, confidence,
                    (targetPassage == null ? "NONE" : targetPassage),
                    (effectiveContext == null ? 0 : effectiveContext.length()),
                    cleanInstruction);
        }
    }

    // Kelime sınırı ile derlenmiş istişare kalıpları
    private static final List<Pattern> CONSULT_PATTERNS = compileBoundaryPatterns(Arrays.asList(
            "sence", "mantıklı mı", "uyumlu mu", "çelişki", "hatırlıyor musun", "kimdi",
            "neden", "nasıl", "ne dersin", "alternatif", "fikir ver", "öneri", "hızlı mı",
            "tempo", "tutarlı mı", "why", "how", "is it consistent", "does it make sense",
            "brainstorm", "options", "analyze", "check"
    ));

    // Salt imla denetimi kalıpları
    private static final List<Pattern> PROOFREAD_PATTERNS = compileBoundaryPatterns(Arrays.asList(
            "yazım hatası", "yazım hatalarını", "imla", "typo", "typos", "düzelt",
            "harf hatası", "proofread", "fix spelling", "clean typos"
    ));

    // Revizyon eylem kalıpları
    private static final List<Pattern> REVISION_PATTERNS = compileBoundaryPatterns(Arrays.asList(
            "değiştir", "yeniden yaz", "revize et", "daha karanlık yap", "daha sert yap",
            "şöyle olsun", "sil", "yerine", "paragrafı yap", "cümleyi yap", "tonu arttır",
            "rewrite", "revise", "make it darker", "replace"
    ));

    // Talimattan temizlenecek meta yönlendiriciler
    private static final Pattern META_TARGET_CLEANER = Pattern.compile(
            "(?i)\\b(son cümleyi|son paragrafı|bu kısmı|şu kısmı|seçili yeri|last sentence|previous line)\\b[,:]?\\s*"
    );

    public AnalysisResult analyze(String rawPrompt, String selectedText, String fullEditorText) {
        return analyze(rawPrompt, selectedText, fullEditorText, ContextScope.FOCUSED);
    }

    public AnalysisResult analyze(String rawPrompt, String selectedText, String fullEditorText, ContextScope scope) {
        String effectiveContext = sliceContextByScope(fullEditorText, scope);

        if (rawPrompt == null || rawPrompt.trim().isEmpty()) {
            return new AnalysisResult(IntentType.CONTINUE, "", null, effectiveContext, 1.0);
        }

        String prompt = rawPrompt.trim();
        boolean hasExplicitSelection = (selectedText != null && !selectedText.trim().isEmpty());

        // 1. Durum: Kullanıcı fareyle metin seçmişse (Açık Hedef)
        if (hasExplicitSelection) {
            if (matchesAny(prompt, PROOFREAD_PATTERNS)) {
                return new AnalysisResult(IntentType.PROOFREAD, stripMetaTokens(prompt), selectedText.trim(), effectiveContext, 0.95);
            }
            if (isConsultationQuery(prompt)) {
                return new AnalysisResult(IntentType.CONSULT, prompt, selectedText.trim(), effectiveContext, 0.90);
            }
            return new AnalysisResult(IntentType.REVISE, stripMetaTokens(prompt), selectedText.trim(), effectiveContext, 0.95);
        }

        // 2. Durum: İstişare / Soru (Metin değişmez)
        if (isConsultationQuery(prompt)) {
            return new AnalysisResult(IntentType.CONSULT, prompt, null, effectiveContext, 0.90);
        }

        // 3. Durum: Salt İmla Temizliği
        if (matchesAny(prompt, PROOFREAD_PATTERNS)) {
            return new AnalysisResult(IntentType.PROOFREAD, stripMetaTokens(prompt), effectiveContext, effectiveContext, 0.85);
        }

        // 4. Durum: Revizyon (Hedef arama ve talimat temizleme)
        if (matchesAny(prompt, REVISION_PATTERNS) || promptContainsQuotedSnippet(prompt)) {
            String target = resolveTarget(prompt, fullEditorText, effectiveContext);
            String cleanInstruction = stripMetaTokens(prompt);
            return new AnalysisResult(IntentType.REVISE, cleanInstruction, target, effectiveContext, 0.85);
        }

        // 5. Durum: İleriye Yürütme (CONTINUE)
        return new AnalysisResult(IntentType.CONTINUE, prompt, null, effectiveContext, 0.90);
    }

    /**
     * Tırnak içinde alıntı varsa tüm metinde arar; yoksa aktif görüş alanını baz alır.
     */
    private String resolveTarget(String rawPrompt, String fullEditorText, String effectiveContext) {
        if (fullEditorText == null || fullEditorText.trim().isEmpty()) {
            return null;
        }

        // 1. Kullanıcı prompt içinde tırnakla spesifik bir yer belirtmiş mi?
        Matcher quoteMatcher = Pattern.compile("\"([^\"]+)\"").matcher(rawPrompt);
        if (quoteMatcher.find()) {
            String quotedSnippet = quoteMatcher.group(1).trim();
            if (fullEditorText.contains(quotedSnippet)) {
                return quotedSnippet;
            }
        }

        // 2. Özel anahtar kelimeler ("son cümle", "son paragraf")
        String lower = rawPrompt.toLowerCase(Locale.ROOT);
        if (lower.contains("son cümle") || lower.contains("last sentence")) {
            return extractLastSentence(effectiveContext);
        }

        if (lower.contains("son paragraf") || lower.contains("last paragraph")) {
            String[] blocks = effectiveContext.trim().split("\\r?\\n{2,}");
            return blocks[blocks.length - 1].trim();
        }

        // 3. Varsayılan hedef aktif kapsam penceresidir
        return effectiveContext;
    }

    private String stripMetaTokens(String prompt) {
        String cleaned = META_TARGET_CLEANER.matcher(prompt).replaceAll("").trim();
        return cleaned.isEmpty() ? prompt : cleaned;
    }

    private boolean promptContainsQuotedSnippet(String prompt) {
        return prompt.contains("\"") && Pattern.compile("\"[^\"]+\"").matcher(prompt).find();
    }

    private String sliceContextByScope(String fullText, ContextScope scope) {
        if (fullText == null || fullText.trim().isEmpty()) return "";
        String trimmed = fullText.trim();
        if (scope == ContextScope.FULL_MANUSCRIPT || scope.getMaxChars() <= 0) return trimmed;
        if (trimmed.length() <= scope.getMaxChars()) return trimmed;

        int startIndex = trimmed.length() - scope.getMaxChars();
        int cleanStart = trimmed.indexOf('\n', startIndex);
        if (cleanStart != -1 && cleanStart < trimmed.length() - 100) {
            return trimmed.substring(cleanStart + 1).trim();
        }
        return trimmed.substring(startIndex).trim();
    }

    private String extractLastSentence(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String trimmed = text.trim();
        int lastPeriod = Math.max(trimmed.lastIndexOf('.'), Math.max(trimmed.lastIndexOf('!'), trimmed.lastIndexOf('?')));
        if (lastPeriod > 0) {
            int secondLastPeriod = Math.max(
                    trimmed.lastIndexOf('.', lastPeriod - 1),
                    Math.max(trimmed.lastIndexOf('!', lastPeriod - 1), trimmed.lastIndexOf('?', lastPeriod - 1))
            );
            if (secondLastPeriod != -1) {
                return trimmed.substring(secondLastPeriod + 1).trim();
            }
        }
        return trimmed;
    }

    private boolean isConsultationQuery(String prompt) {
        if (prompt.contains("?")) return true;
        return matchesAny(prompt, CONSULT_PATTERNS);
    }

    private static List<Pattern> compileBoundaryPatterns(List<String> rawPatterns) {
        List<Pattern> compiled = new ArrayList<>();
        for (String raw : rawPatterns) {
            compiled.add(Pattern.compile("(?i)\\b" + Pattern.quote(raw) + "\\b"));
        }
        return compiled;
    }

    private boolean matchesAny(String input, List<Pattern> patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }
}