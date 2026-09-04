package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of the text's stylometric profile. Colocated in
 * org.example — no dependency on com.example.engine.
 */
public final class NarrativeState {

    private final CadenceType cadence;
    private final double avgWordsPerSentence;
    private final FormatMode formatMode;
    private final float dialogueRatio;
    private final float stageDirectionRatio;
    private final int sensoryBudget;
    private final float cynicismIndex;
    private final List<String> bannedStems;
    private final float recommendedTemperature;
    private final int recommendedMaxTokens;

    public NarrativeState(
            CadenceType cadence,
            double avgWordsPerSentence,
            FormatMode formatMode,
            float dialogueRatio,
            float stageDirectionRatio,
            int sensoryBudget,
            float cynicismIndex,
            List<String> bannedStems,
            float recommendedTemperature,
            int recommendedMaxTokens
    ) {
        this.cadence = cadence;
        this.avgWordsPerSentence = avgWordsPerSentence;
        this.formatMode = formatMode;
        this.dialogueRatio = dialogueRatio;
        this.stageDirectionRatio = stageDirectionRatio;
        this.sensoryBudget = sensoryBudget;
        this.cynicismIndex = cynicismIndex;
        this.bannedStems = (bannedStems != null)
                ? Collections.unmodifiableList(new ArrayList<>(bannedStems))
                : Collections.emptyList();
        this.recommendedTemperature = recommendedTemperature;
        this.recommendedMaxTokens = recommendedMaxTokens;
    }

    public CadenceType getCadence() { return cadence; }
    public double getAvgWordsPerSentence() { return avgWordsPerSentence; }
    public FormatMode getFormatMode() { return formatMode; }
    public float getDialogueRatio() { return dialogueRatio; }
    public float getStageDirectionRatio() { return stageDirectionRatio; }
    public int getSensoryBudget() { return sensoryBudget; }
    public float getCynicismIndex() { return cynicismIndex; }
    public List<String> getBannedStems() { return bannedStems; }
    public float getRecommendedTemperature() { return recommendedTemperature; }
    public int getRecommendedMaxTokens() { return recommendedMaxTokens; }

    /**
     * Safe default state for empty, blank, or degenerate input.
     */
    public static NarrativeState defaultState(AnalysisTier tier) {
        return new NarrativeState(
                CadenceType.STACCATO,
                0.0,
                FormatMode.PURE_PROSE,
                0f,
                0f,
                1,
                0.5f,
                LocalShadowCore.baseBannedStems(tier),
                0.25f,
                600
        );
    }
}