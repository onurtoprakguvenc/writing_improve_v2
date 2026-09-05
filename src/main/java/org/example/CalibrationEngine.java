package org.example;

import java.io.Serializable;
import java.util.Objects;

/**
 * Deterministic Buffer Calibration & Payload Engine.
 * <p>
 * Replaces static token calculators, regex length heuristics, and role-playing prompt
 * bureaucracies with a pure delimiter-based buffer continuation architecture.
 */
public final class CalibrationEngine {

    /**
     * Uncompromising, non-interactive execution invariant system instruction.
     */
    public static final String SYSTEM_INSTRUCTION =
            "You are a deterministic in-buffer text continuation engine. " +
                    "Output ONLY the raw continuation text that directly extends the active buffer from the last character. " +
                    "Never output explanations, conversational filler, markdown code block wrappers (```), or meta-labels (e.g., 'Reaction:', 'Continuation:', 'Scene:'). " +
                    "If data sections are missing or empty, proceed immediately without asking questions or pausing for clarification.";

    private static final int DEFAULT_STYLE_MIN_CHARS = 500;
    private static final int DEFAULT_STYLE_MAX_CHARS = 1000;

    private final String apiKey;

    public CalibrationEngine() {
        this(null);
    }

    public CalibrationEngine(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public static int resolveMaxOutputTokens(AnalysisTier tier) {
        if (tier == null) {
            return 4096;
        }
        switch (tier) {
            case FAST:
            case SURGICAL:
                return 2048;
            case DEEP:
                return 8192;
            case BALANCED:
            default:
                return 4096;
        }
    }

    public static String buildPrompt(String activeBuffer, String directive, String explicitStyle) {
        String sanitizedBuffer = sanitizeInput(activeBuffer);
        String sanitizedDirective = sanitizeDirective(directive);
        String resolvedStyle = resolveStyleReference(explicitStyle, sanitizedBuffer);

        return "[STYLE_REFERENCE]\n" +
                resolvedStyle + "\n" +
                "[/STYLE_REFERENCE]\n\n" +
                "[ACTIVE_BUFFER]\n" +
                sanitizedBuffer + "\n" +
                "[/ACTIVE_BUFFER]\n\n" +
                "[DIRECTIVE]\n" +
                sanitizedDirective + "\n" +
                "[/DIRECTIVE]\n\n" +
                "CONTINUATION:";
    }

    public static String buildPrompt(String activeBuffer, String directive) {
        return buildPrompt(activeBuffer, directive, null);
    }

    public static String buildPrompt(EditorState state, String directive, int dynamicContextRadius, String explicitStyle) {
        if (state == null) {
            return buildPrompt("", directive, explicitStyle);
        }
        String activeContext = state.hasSelection()
                ? state.getSelectedText()
                : (dynamicContextRadius > 0 ? state.getPrecedingContext(dynamicContextRadius) : state.getFullManuscript());

        return buildPrompt(activeContext, directive, explicitStyle);
    }

    public static String buildPrompt(EditorState state, String directive) {
        return buildPrompt(state, directive, 0, null);
    }

    public static CalibratedPayload calibrate(EditorState state, String directive, AnalysisTier tier) {
        return calibrate(state, directive, null, 0, tier);
    }

    public static CalibratedPayload calibrate(EditorState state, String directive, String explicitStyle, int dynamicContextRadius, AnalysisTier tier) {
        AnalysisTier selectedTier = (tier != null) ? tier : AnalysisTier.BALANCED;
        String prompt = buildPrompt(state, directive, dynamicContextRadius, explicitStyle);
        int maxTokens = resolveMaxOutputTokens(selectedTier);
        return new CalibratedPayload(SYSTEM_INSTRUCTION, prompt, maxTokens, selectedTier.getModelName(), selectedTier);
    }

    private static String resolveStyleReference(String explicitStyle, String activeBuffer) {
        if (explicitStyle != null && !explicitStyle.trim().isEmpty()) {
            return sanitizeInput(explicitStyle);
        }
        return extractDefaultStyle(activeBuffer);
    }

    public static String extractDefaultStyle(String buffer) {
        if (buffer == null) {
            return "(none)";
        }
        String trimmed = buffer.trim();
        if (trimmed.isEmpty()) {
            return "(none)";
        }

        if (trimmed.length() <= DEFAULT_STYLE_MAX_CHARS) {
            return trimmed;
        }

        int cutoff = DEFAULT_STYLE_MAX_CHARS;
        for (int i = DEFAULT_STYLE_MAX_CHARS - 1; i >= DEFAULT_STYLE_MIN_CHARS; i--) {
            char c = trimmed.charAt(i);
            if (c == '\n' || c == '.' || c == '!' || c == '?') {
                cutoff = i + 1;
                break;
            }
        }
        return trimmed.substring(0, cutoff).trim();
    }

    private static String sanitizeInput(String val) {
        if (val == null) {
            return "";
        }
        String s = val.trim();
        s = s.replace("{STYLE_SAMPLE_TEXT}", "")
                .replace("{ACTIVE_BUFFER_TEXT}", "")
                .replace("{USER_DIRECTIVE}", "")
                .trim();
        return s;
    }

    private static String sanitizeDirective(String directive) {
        String s = sanitizeInput(directive);
        if (s.isEmpty()) {
            return "Continue the text immediately from the last character.";
        }
        return s;
    }

    public static final class CalibratedPayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String systemInstruction;
        private final String userPrompt;
        private final int maxOutputTokens;
        private final String modelName;
        private final AnalysisTier tier;

        public CalibratedPayload(
                String systemInstruction,
                String userPrompt,
                int maxOutputTokens,
                String modelName,
                AnalysisTier tier
        ) {
            this.systemInstruction = Objects.requireNonNull(systemInstruction, "systemInstruction cannot be null");
            this.userPrompt = Objects.requireNonNull(userPrompt, "userPrompt cannot be null");
            this.maxOutputTokens = maxOutputTokens;
            this.modelName = Objects.requireNonNull(modelName, "modelName cannot be null");
            this.tier = (tier != null) ? tier : AnalysisTier.BALANCED;
        }

        public String getSystemInstruction() {
            return systemInstruction;
        }

        public String getUserPrompt() {
            return userPrompt;
        }

        public int getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public String getModelName() {
            return modelName;
        }

        public AnalysisTier getTier() {
            return tier;
        }

        @Override
        public String toString() {
            return "CalibratedPayload{" +
                    "tier=" + tier +
                    ", maxTokens=" + maxOutputTokens +
                    ", model='" + modelName + '\'' +
                    ", promptLength=" + userPrompt.length() +
                    '}';
        }
    }
}