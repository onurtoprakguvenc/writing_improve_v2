package org.example;

import java.io.Serializable;

/**
 * Katman 2: Saf Metin Geometrisi ve Fiziksel Koordinat Konteyneri.
 *
 * Yapay ritim enum'ları ve sözlük safralarından tamamen arındırılmıştır.
 * Sadece hesaplanan istatistiksel dağılımı ve sentaks bayraklarını taşır.
 */
public final class NarrativeState implements Serializable {
    private static final long serialVersionUID = 4L;

    private final double avgWordsPerSentence;
    private final double sentenceLengthStdDev;
    private final float recommendedTemperature;
    private final int recommendedMaxTokens;

    // Sentaks Geometrisi Bayrakları
    private final boolean hasInlineParenthetical;
    private final boolean hasLowercaseStarter;
    private final boolean isPureLineDialogue;

    public NarrativeState(
            double avgWordsPerSentence,
            double sentenceLengthStdDev,
            float recommendedTemperature,
            int recommendedMaxTokens,
            boolean hasInlineParenthetical,
            boolean hasLowercaseStarter,
            boolean isPureLineDialogue
    ) {
        this.avgWordsPerSentence = avgWordsPerSentence;
        this.sentenceLengthStdDev = sentenceLengthStdDev;
        this.recommendedTemperature = recommendedTemperature;
        this.recommendedMaxTokens = recommendedMaxTokens;
        this.hasInlineParenthetical = hasInlineParenthetical;
        this.hasLowercaseStarter = hasLowercaseStarter;
        this.isPureLineDialogue = isPureLineDialogue;
    }

    public static NarrativeState defaultState(AnalysisTier tier) {
        return new NarrativeState(
                12.0,
                3.0,
                0.35f,
                300,
                false,
                false,
                false
        );
    }

    public double getAvgWordsPerSentence() { return avgWordsPerSentence; }
    public double getSentenceLengthStdDev() { return sentenceLengthStdDev; }
    public float getRecommendedTemperature() { return recommendedTemperature; }
    public int getRecommendedMaxTokens() { return recommendedMaxTokens; }
    public boolean hasInlineParenthetical() { return hasInlineParenthetical; }
    public boolean hasLowercaseStarter() { return hasLowercaseStarter; }
    public boolean isPureLineDialogue() { return isPureLineDialogue; }
}