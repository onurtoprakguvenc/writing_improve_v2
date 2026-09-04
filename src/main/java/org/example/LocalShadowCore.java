package org.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Katman 2: Yerel Gölge Analiz Çekirdeği (Local Shadow Analysis Core).
 * Colocated in org.example — plain Java port of the verified Kotlin core,
 * identical heuristics, no cross-package dependency.
 *
 * Pure JVM, zero network, zero UI dependencies.
 */
public final class LocalShadowCore {

    private LocalShadowCore() {
        // static utility, no instances
    }

    private static final List<String> CORE_BANNED_STEMS = Arrays.asList(
            "suddenly", "gently", "felt", "seemed", "realized", "couldn't help but",
            "a wave of", "a testament to", "little did he know", "in that moment",
            "shiver down", "palpable",
            "aniden", "yavaşça", "hissetti", "görünüyordu", "fark etti",
            "adeta", "ürperti", "derin bir nefes", "içten içe"
    );

    private static final List<String> EXTENDED_BANNED_STEMS = Arrays.asList(
            "a mix of", "a sense of", "couldn't shake", "eyes widened",
            "heart pounded", "breath caught", "as if", "somehow",
            "büyük bir şaşkınlıkla", "kalbi yerinden fırlayacak", "gözlerine inanamadı",
            "bir anlığına", "içi burkuldu"
    );

    private static final java.util.Set<String> SENSORY_KEYWORDS = new java.util.HashSet<>(Arrays.asList(
            "cold", "sweat", "dust", "iron", "steel", "rust", "wire", "concrete",
            "blood", "damp", "smoke", "copper", "glass", "asphalt", "rain", "mud",
            "soğuk", "ter", "toz", "demir", "çelik", "pas", "tel", "beton",
            "kan", "nem", "duman", "bakır", "cam", "asfalt", "yağmur", "çamur"
    ));

    private static final java.util.Set<String> CYNICAL_ACTION_VERBS = new java.util.HashSet<>(Arrays.asList(
            "cut", "slammed", "fired", "shoved", "dropped", "snapped", "jammed", "dragged",
            "stared", "spit", "broke", "locked", "kicked", "pulled", "stepped",
            "vurdu", "çekti", "bastı", "fırlattı", "kırdı", "kilitledi",
            "sustu", "baktı", "fısıldadı", "tuttu", "ezdi", "kesti", "itti", "çatırdadı"
    ));

    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("(?<=[.!?])\\s+|\\n+");
    private static final Pattern WORD_SPLIT_PATTERN = Pattern.compile("[\\s.,!?;:\\[\\]\"\u201C\u201D\u00AB\u00BB\\-()]+");
    private static final Pattern STAGE_DIRECTION_PATTERN = Pattern.compile("\\[(.*?)\\]|\\((.*?)\\)");
    private static final Pattern DIALOGUE_PATTERN = Pattern.compile("\"([^\"]*)\"|\u201C([^\u201D]*)\u201D|\u00AB([^\u00BB]*)\u00BB");

    /**
     * Returns the tier-appropriate banned stem list without running a full analysis.
     */
    public static List<String> baseBannedStems(AnalysisTier tier) {
        if (tier == AnalysisTier.K1_LIGHT) {
            return CORE_BANNED_STEMS;
        }
        List<String> combined = new ArrayList<>(CORE_BANNED_STEMS);
        for (String stem : EXTENDED_BANNED_STEMS) {
            if (!combined.contains(stem)) combined.add(stem);
        }
        return combined;
    }

    /**
     * Analyzes rawText at the given tier and returns the resulting
     * Narrative State Vector. Never throws; degenerate input yields a safe default.
     */
    public static NarrativeState analyze(String rawText, AnalysisTier tier) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            return NarrativeState.defaultState(tier);
        }

        List<String> words = tokenizeWords(text);
        if (words.isEmpty()) {
            return NarrativeState.defaultState(tier);
        }

        List<String> sentences = splitSentences(text);
        int sentenceCount = Math.max(1, sentences.size());
        int totalWords = words.size();

        double avgWords = (double) totalWords / sentenceCount;

        CadenceType cadence = (avgWords <= 6.0) ? CadenceType.STACCATO : CadenceType.FLUID_RELAXED;

        int dialogueCharCount = 0;
        int dialogueMatches = 0;
        Matcher dm = DIALOGUE_PATTERN.matcher(text);
        while (dm.find()) {
            dialogueCharCount += dm.group().length();
            dialogueMatches++;
        }

        int stageDirectionCharCount = 0;
        int stageDirectionMatches = 0;
        Matcher sm = STAGE_DIRECTION_PATTERN.matcher(text);
        while (sm.find()) {
            stageDirectionCharCount += sm.group().length();
            stageDirectionMatches++;
        }

        int textLength = Math.max(1, text.length());
        float dialogueRatio = (float) Math.min(1.0, dialogueCharCount / (double) textLength);
        float stageDirectionRatio = (float) Math.min(1.0, stageDirectionCharCount / (double) textLength);

        FormatMode formatMode;
        if (stageDirectionMatches > 0 && dialogueMatches > 0) {
            formatMode = FormatMode.STAGE_DIRECTION_INTERLEAVED;
        } else if (dialogueMatches >= 2) {
            formatMode = FormatMode.DIALOGUE_HEAVY;
        } else {
            formatMode = FormatMode.PURE_PROSE;
        }

        long sensoryMatches = words.stream().filter(w -> SENSORY_KEYWORDS.contains(w.toLowerCase())).count();
        int sensoryBudget = deriveSensoryBudget(tier, cadence, (int) sensoryMatches);

        long activeVerbMatches = words.stream().filter(w -> CYNICAL_ACTION_VERBS.contains(w.toLowerCase())).count();
        float cynicismIndex = deriveCynicismIndex(tier, avgWords, (int) activeVerbMatches);

        List<String> bannedStems = baseBannedStems(tier);

        float temperature = deriveTemperature(tier, cadence);
        int maxTokens = deriveMaxTokens(tier, cadence);

        return new NarrativeState(
                cadence,
                roundTo(avgWords, 1),
                formatMode,
                roundTo(dialogueRatio, 2),
                roundTo(stageDirectionRatio, 2),
                sensoryBudget,
                roundTo(cynicismIndex, 2),
                bannedStems,
                temperature,
                maxTokens
        );
    }

    /**
     * Anti-Collapse Watchdog: flags whether the last N sentence lengths have
     * converged onto a single repetitive syntax pattern (near-zero variance).
     */
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

    // ---- Internal heuristics -------------------------------------------------

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

    private static int deriveSensoryBudget(AnalysisTier tier, CadenceType cadence, int sensoryMatches) {
        int base;
        switch (tier) {
            case K1_LIGHT:
                base = 1;
                break;
            case K2_BALANCED:
                base = Math.max(1, Math.min(2, sensoryMatches));
                break;
            case K3_DEEP:
            default:
                base = Math.max(1, Math.min(3, sensoryMatches));
                break;
        }
        int cadenceAdjusted = (cadence == CadenceType.STACCATO) ? Math.min(base, 2) : base;
        return Math.max(0, Math.min(3, cadenceAdjusted));
    }

    private static float deriveCynicismIndex(AnalysisTier tier, double avgWords, int activeVerbMatches) {
        double raw;
        switch (tier) {
            case K1_LIGHT:
                raw = 0.5;
                break;
            case K2_BALANCED:
                raw = 0.5 + (activeVerbMatches * 0.06) - (avgWords * 0.015);
                break;
            case K3_DEEP:
            default:
                raw = 0.5 + (activeVerbMatches * 0.08) - (avgWords * 0.02);
                break;
        }
        return (float) Math.max(0.0, Math.min(1.0, raw));
    }

    /**
     * Cadence + tier -> recommended temperature. K3 tightens further per spec.
     */
    private static float deriveTemperature(AnalysisTier tier, CadenceType cadence) {
        if (cadence == CadenceType.STACCATO) {
            switch (tier) {
                case K1_LIGHT: return 0.30f;
                case K2_BALANCED: return 0.25f;
                case K3_DEEP:
                default: return 0.20f;
            }
        } else {
            switch (tier) {
                case K1_LIGHT: return 0.60f;
                case K2_BALANCED: return 0.55f;
                case K3_DEEP:
                default: return 0.45f;
            }
        }
    }

    /**
     * Cadence + tier -> recommended max output tokens (pre-clamp; the caller
     * applies the safe override window on top of this).
     */
    private static int deriveMaxTokens(AnalysisTier tier, CadenceType cadence) {
        if (cadence == CadenceType.STACCATO) {
            switch (tier) {
                case K1_LIGHT: return 150;
                case K2_BALANCED: return 150;
                case K3_DEEP:
                default: return 100;
            }
        } else {
            switch (tier) {
                case K1_LIGHT: return 300;
                case K2_BALANCED: return 300;
                case K3_DEEP:
                default: return 350;
            }
        }
    }

    private static double roundTo(double value, int decimals) {
        double factor = Math.pow(10.0, decimals);
        return Math.round(value * factor) / factor;
    }

    private static float roundTo(float value, int decimals) {
        return (float) roundTo((double) value, decimals);
    }
}