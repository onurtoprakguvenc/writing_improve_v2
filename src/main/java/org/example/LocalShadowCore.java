package org.example;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Katman 2: Yerel Gölge Analiz Çekirdeği.
 *
 * Sıfır sözlük, sıfır yapay etiket. Tamamen sürekli matematiksel metrikler
 * ve evrensel Unicode sentaks tespiti ile çalışır.
 */
public final class LocalShadowCore {

    private LocalShadowCore() {}

    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("(?<=[.!?])\\s+|\\n+");
    private static final Pattern WORD_SPLIT_PATTERN = Pattern.compile("[\\s.,!?;:\\[\\]\"\u201C\u201D\u00AB\u00BB\\-()]+");

    private static final Pattern INLINE_PARENTHETICAL_PATTERN = Pattern.compile("\"[^\"]*\\(.*?\\)[^\"]*\"|\u201C[^\u201D]*\\(.*?\\)[^\u201D]*\u201D|\\(.*?\\)");
    private static final Pattern LOWERCASE_LINE_STARTER_PATTERN = Pattern.compile("(?m)^[\"\u201C\u00AB\\s]*\\p{Ll}");

    public static NarrativeState analyze(String rawText, AnalysisTier tier) {
        String text = (rawText == null) ? "" : rawText.trim();
        if (text.isEmpty()) {
            return NarrativeState.defaultState(tier);
        }

        List<String> words = tokenizeWords(text);
        if (words.isEmpty()) {
            return NarrativeState.defaultState(tier);
        }

        List<String> sentences = splitSentences(text);
        int sentenceCount = Math.max(1, sentences.size());
        double avgWords = (double) words.size() / sentenceCount;

        // Cümle uzunlukları standart sapması
        List<Integer> lengths = sentences.stream()
                .map(s -> tokenizeWords(s).size())
                .collect(Collectors.toList());
        double variance = lengths.stream()
                .mapToDouble(l -> (l - avgWords) * (l - avgWords))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        boolean hasInlineParenthetical = INLINE_PARENTHETICAL_PATTERN.matcher(text).find();
        boolean hasLowercaseStarter = LOWERCASE_LINE_STARTER_PATTERN.matcher(text).find();
        boolean isPureLineDialogue = detectStructuredLines(text);

        float temperature = deriveDynamicTemperature(tier, avgWords, stdDev);
        int maxTokens = deriveDynamicMaxTokens(tier, avgWords);

        return new NarrativeState(
                roundTo(avgWords, 1),
                roundTo(stdDev, 2),
                temperature,
                maxTokens,
                hasInlineParenthetical,
                hasLowercaseStarter,
                isPureLineDialogue
        );
    }

    private static boolean detectStructuredLines(String text) {
        String[] lines = text.split("\\r?\\n");
        int formattedLines = 0;
        int nonEmptyLines = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            nonEmptyLines++;
            if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                    (trimmed.startsWith("\u201C") && trimmed.endsWith("\u201D")) ||
                    (trimmed.startsWith("\u00AB") && trimmed.endsWith("\u00BB")) ||
                    trimmed.startsWith("-") || trimmed.startsWith("*")) {
                formattedLines++;
            }
        }

        if (nonEmptyLines == 0) return false;
        return ((double) formattedLines / nonEmptyLines) >= 0.70;
    }

    public static boolean detectAntiCollapseLock(String rawText, int windowSize) {
        List<String> sentences = splitSentences(rawText == null ? "" : rawText.trim());
        if (sentences.size() < windowSize) return false;

        List<Integer> lengths = sentences.subList(sentences.size() - windowSize, sentences.size())
                .stream()
                .map(s -> tokenizeWords(s).size())
                .collect(Collectors.toList());

        double mean = lengths.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double variance = lengths.stream().mapToDouble(l -> (l - mean) * (l - mean)).average().orElse(0.0);
        double stdDev = Math.sqrt(variance);

        return stdDev < 0.6 && mean > 2.0;
    }

    private static List<String> splitSentences(String text) {
        return Arrays.stream(SENTENCE_SPLIT_PATTERN.split(text))
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.toList());
    }

    private static List<String> tokenizeWords(String text) {
        return Arrays.stream(WORD_SPLIT_PATTERN.split(text))
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.toList());
    }

    private static float deriveDynamicTemperature(AnalysisTier tier, double avgWords, double stdDev) {
        // Kısa/keskin cümlelerde sıcaklık düşer; uzun/dalgalı cümlelerde kontrollü artar
        float baseTemp = (avgWords <= 7.0) ? 0.20f : 0.40f;
        if (stdDev < 1.0) {
            baseTemp -= 0.05f; // Yüksek mekanik tutarlılık
        }

        switch (tier) {
            case FAST:
                return Math.max(0.10f, baseTemp + 0.05f);
            case BALANCED:
                return Math.max(0.10f, baseTemp);
            case DEEP:
            default:
                return Math.max(0.05f, baseTemp - 0.10f);
        }
    }

    private static int deriveDynamicMaxTokens(AnalysisTier tier, double avgWords) {
        int tokenBase = (avgWords <= 7.0) ? 150 : 350;
        if (tier == AnalysisTier.DEEP) {
            return tokenBase + 100;
        }
        return tokenBase;
    }

    private static double roundTo(double value, int decimals) {
        double factor = Math.pow(10.0, decimals);
        return Math.round(value * factor) / factor;
    }
}