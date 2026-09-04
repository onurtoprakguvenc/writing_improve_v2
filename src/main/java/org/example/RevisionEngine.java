package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Katman 3 sibling to {@link CalibrationEngine}: executes in-place
 * paragraph/sentence REVISIONS rather than forward plot continuations.
 *
 * Same model settings and transport as CalibrationEngine — identical
 * OkHttp client shape, identical thinkingConfig.thinkingBudget=0
 * suppression, identical SSE line-parsing logic — but a distinct
 * parametric constraint matrix that forbids advancing the story and
 * instead locks the model onto rewriting exactly the TARGET_PASSAGE
 * according to REVISION_INSTRUCTION.
 *
 * Pure JVM, org.example package, no cross-package dependencies beyond
 * the colocated AnalysisTier / NarrativeState / LocalShadowCore trio.
 */
public class RevisionEngine {

    private final String apiKey;
    private final String modelName;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private static final String DEFAULT_MODEL_NAME = "gemini-3.8-flash";
    private static final String BASE_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:streamGenerateContent?alt=sse";

    // Safe override bounds for maxOutputTokens. Revisions are typically
    // shorter than forward continuations (rewriting an existing passage,
    // not generating new plot), so the ceiling is tighter than
    // CalibrationEngine's; the floor still guards against mid-sentence
    // truncation on a complete rewrite.
    private static final int MAX_TOKENS_FLOOR = 400;
    private static final int MAX_TOKENS_CEILING = 700;

    // Cynicism threshold above which blunt-verb / anti-moralizing enforcement kicks in.
    private static final double CYNICISM_HARDNESS_THRESHOLD = 0.6;

    // Number of trailing non-empty lines pulled verbatim from
    // surroundingContext when targetPassage itself is blank and a
    // structural sample must be sourced from the fallback instead.
    private static final int STRUCTURAL_ANCHOR_LINE_WINDOW = 3;

    /**
     * Uses the default verified model (gemini-3.8-flash).
     */
    public RevisionEngine(String apiKey) {
        this(apiKey, DEFAULT_MODEL_NAME);
    }

    /**
     * Explicit key + model configuration, for dynamic switching between
     * verified endpoints without editing source.
     */
    public RevisionEngine(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = (modelName != null && !modelName.isBlank()) ? modelName : DEFAULT_MODEL_NAME;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    private String resolveBaseUrl() {
        return String.format(BASE_URL_TEMPLATE, modelName);
    }

    /**
     * Streams an in-place revision of {@code targetPassage} according to
     * {@code revisionInstruction}. {@code surroundingContext} is used only
     * to (a) drive local NarrativeState analysis for temperature/token
     * binding, and (b) as a structural-anchor fallback when
     * {@code targetPassage} itself is blank — it is never itself rewritten
     * or advanced.
     *
     * Same transport contract as {@link CalibrationEngine#streamNextDraft}:
     * thinkingConfig.thinkingBudget is forced to 0, each SSE text delta is
     * pushed to {@code onToken} the instant it is decoded, and this call
     * throws IOException on network failure or a non-2xx response rather
     * than swallowing errors or emitting fallback text.
     *
     * Blocking call — the caller dispatches this onto a background thread
     * if invoked from a UI context.
     *
     * @param targetPassage       the exact passage to rewrite in place
     * @param revisionInstruction the author's instruction describing the desired change
     * @param surroundingContext  nearby text used for style analysis and anchor fallback only
     * @param tier                local analysis depth; defaults to K2_BALANCED when null
     * @param onToken             invoked synchronously for every non-empty text delta parsed from the SSE stream
     */
    public void streamRevision(
            String targetPassage,
            String revisionInstruction,
            String surroundingContext,
            AnalysisTier tier,
            Consumer<String> onToken
    ) throws IOException {
        AnalysisTier effectiveTier = (tier != null) ? tier : AnalysisTier.K2_BALANCED;

        // NarrativeState is derived from surroundingContext (the broader
        // stylistic window), not targetPassage alone — a single short
        // passage is too small a sample for reliable cadence/ratio metrics.
        String analysisSource = (surroundingContext != null && !surroundingContext.trim().isEmpty())
                ? surroundingContext
                : targetPassage;
        NarrativeState state = LocalShadowCore.analyze(analysisSource, effectiveTier);

        JsonObject rootJson = buildRequestPayload(state, targetPassage, revisionInstruction, surroundingContext);

        RequestBody body = RequestBody.create(
                rootJson.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(resolveBaseUrl())
                .addHeader("x-goog-api-key", this.apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("API Hatası: " + response.code() + " - " + err);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("data:")) {
                        String jsonChunk = line.substring(5).trim();
                        if (jsonChunk.isEmpty() || jsonChunk.equals("[DONE]")) continue;

                        try {
                            JsonObject chunkObj = gson.fromJson(jsonChunk, JsonObject.class);
                            if (chunkObj.has("candidates")) {
                                JsonArray candidates = chunkObj.getAsJsonArray("candidates");
                                if (candidates.size() > 0) {
                                    JsonObject candidate = candidates.get(0).getAsJsonObject();
                                    if (candidate.has("content")) {
                                        JsonObject content = candidate.getAsJsonObject("content");
                                        if (content.has("parts")) {
                                            JsonArray parts = content.getAsJsonArray("parts");
                                            for (JsonElement part : parts) {
                                                if (part.isJsonObject() && part.getAsJsonObject().has("text")) {
                                                    String delta = part.getAsJsonObject().get("text").getAsString();
                                                    if (!delta.isEmpty()) {
                                                        onToken.accept(delta);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                            // Parse edilemeyen SSE satırlarını atla
                        }
                    }
                }
            }
        }
    }

    /**
     * Blocking (non-streaming) variant, for parity with
     * CalibrationEngine's dual blocking/streaming surface. Buffers the
     * full SSE response before returning.
     */
    public String generateRevision(
            String targetPassage,
            String revisionInstruction,
            String surroundingContext,
            AnalysisTier tier
    ) throws IOException {
        StringBuilder aggregated = new StringBuilder();
        streamRevision(targetPassage, revisionInstruction, surroundingContext, tier, aggregated::append);
        return aggregated.toString().trim();
    }

    /**
     * Assembles the request payload: revision-specific parametric
     * constraint matrix as system_instruction, TARGET_PASSAGE +
     * REVISION_INSTRUCTION + SURROUNDING_CONTEXT as the user content,
     * and generationConfig with thinkingConfig.thinkingBudget forced to 0.
     */
    private JsonObject buildRequestPayload(
            NarrativeState state,
            String targetPassage,
            String revisionInstruction,
            String surroundingContext
    ) {
        String systemInstructionText = buildRevisionConstraintMatrix(state, targetPassage, surroundingContext);

        String userPromptText = String.format(
                "TARGET_PASSAGE:\n%s\n\nREVISION_INSTRUCTION:\n%s\n\nSURROUNDING_CONTEXT (reference only, do not rewrite):\n%s",
                nullToEmpty(targetPassage),
                nullToEmpty(revisionInstruction),
                nullToEmpty(surroundingContext)
        );

        JsonObject rootJson = new JsonObject();

        // 1. Sistem Kısıtları (revision-specific parametric constraint matrix)
        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemPart = new JsonObject();
        systemPart.addProperty("text", systemInstructionText);
        systemParts.add(systemPart);
        systemInstruction.add("parts", systemParts);
        rootJson.add("system_instruction", systemInstruction);

        // 2. Kullanıcı Girdisi
        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray userParts = new JsonArray();
        JsonObject userPart = new JsonObject();
        userPart.addProperty("text", userPromptText);
        userParts.add(userPart);
        contentObj.add("parts", userParts);
        contents.add(contentObj);
        rootJson.add("contents", contents);

        // 3. Üretim Parametreleri — dynamically bound to NarrativeState,
        //    with thinkingConfig forced to zero budget (engine parity with
        //    CalibrationEngine — eliminates reasoning-token TTFB stall).
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", state.getRecommendedTemperature());
        generationConfig.addProperty("maxOutputTokens", resolveMaxOutputTokens(state));

        JsonObject thinkingConfig = new JsonObject();
        thinkingConfig.addProperty("thinkingBudget", 0);
        generationConfig.add("thinkingConfig", thinkingConfig);

        rootJson.add("generationConfig", generationConfig);

        return rootJson;
    }

    /**
     * Clamps the locally-recommended token budget into the revision-scoped
     * safe override window (400–700).
     */
    private int resolveMaxOutputTokens(NarrativeState state) {
        int recommended = state.getRecommendedMaxTokens();
        if (recommended < MAX_TOKENS_FLOOR) return MAX_TOKENS_FLOOR;
        if (recommended > MAX_TOKENS_CEILING) return MAX_TOKENS_CEILING;
        return recommended;
    }

    // ------------------------------------------------------------------
    // Revision Parametric Constraint Matrix
    // ------------------------------------------------------------------

    /**
     * Compiles the revision-mode constraint lattice. Distinct from
     * CalibrationEngine's continuation matrix in two structural ways:
     * (1) MODE / OUTPUT_CONTRACT explicitly forbid plot advancement and
     * scope the model strictly to rewriting TARGET_PASSAGE; (2) the
     * structural anchor is sourced from TARGET_PASSAGE itself (falling
     * back to SURROUNDING_CONTEXT only when the target is blank), since
     * the passage being revised is the definitive style sample, not
     * whatever precedes it.
     */
    private String buildRevisionConstraintMatrix(NarrativeState state, String targetPassage, String surroundingContext) {
        StringBuilder sb = new StringBuilder();

        sb.append("MODE: MECHANICAL_REVISION_ENGINE\n");
        sb.append("OUTPUT_CONTRACT: revised_text_only | no_preface | no_meta_commentary | strict_in_place_rewrite\n\n");

        sb.append("[REVISION_SCOPE]\n");
        sb.append("TASK=rewrite_target_passage_only\n");
        sb.append("RULE=Apply REVISION_INSTRUCTION strictly to TARGET_PASSAGE. ");
        sb.append("Do NOT advance the plot, do NOT introduce new events, do NOT continue the scene beyond the passage's existing endpoint. ");
        sb.append("SURROUNDING_CONTEXT is reference only — never rewrite or extend it.\n\n");

        sb.append("[CONSTRAINT_MATRIX]\n");

        int velocityCeiling = Math.max(3, (int) Math.round(state.getAvgWordsPerSentence()));
        sb.append("VELOCITY=").append(velocityCeiling).append(" | RULE=hard_ceiling_words_per_sentence\n");

        sb.append("BALANCE.DIALOGUE_RATIO=").append(formatRatio(state.getDialogueRatio()))
                .append(" | BALANCE.ACTION_RATIO=").append(formatRatio(state.getStageDirectionRatio()))
                .append(" | RULE=match_observed_proportions\n");

        sb.append("SENSORY_BUDGET=").append(state.getSensoryBudget())
                .append(" | RULE=max_physical_descriptors_per_beat\n");

        double cynicism = state.getCynicismIndex();
        sb.append("CYNICISM_INDEX=").append(formatRatio(cynicism));
        if (cynicism > CYNICISM_HARDNESS_THRESHOLD) {
            sb.append(" | RULE=enforce_blunt_verbs+prohibit_moralizing+prohibit_polite_resolution\n");
        } else {
            sb.append(" | RULE=standard_tonal_register\n");
        }

        sb.append("CADENCE=").append(state.getCadence().name()).append("\n");
        sb.append("FORMAT_MODE=").append(state.getFormatMode().name()).append("\n");

        List<String> bannedStems = state.getBannedStems();
        sb.append("[NEGATIVE_CONSTRAINTS]\n");
        if (bannedStems != null && !bannedStems.isEmpty()) {
            sb.append("SUPPRESS_ABSOLUTE=[").append(String.join("|", bannedStems)).append("]\n");
        } else {
            sb.append("SUPPRESS_ABSOLUTE=[]\n");
        }
        sb.append("TOLERANCE=zero\n\n");

        String anchorSample = extractStructuralAnchor(targetPassage, surroundingContext);
        sb.append("[STRUCTURAL_ANCHOR]\n");
        sb.append("SAMPLE (verbatim source for style/syntax replication):\n");
        sb.append("<<<\n").append(anchorSample).append("\n>>>\n");
        sb.append("RULE: Replicate the EXACT syntax, quote style, casing, and intra-dialogue bracket/parenthesis rhythm shown in SAMPLE. ");
        sb.append("If SAMPLE embeds an action cue inside a quoted line (e.g. dialogue containing an inline parenthetical), preserve that exact embedding — do NOT extract it into a separate stage-direction line, and do NOT normalize it into standard literary prose. ");
        sb.append("Match punctuation placement and letter casing exactly as written, including any lowercase or unconventional styling. ");
        sb.append("The revised output must read as though the original author wrote it this way from the start.\n\n");

        sb.append("[HARD_STOP_RULES]\n");
        sb.append("NO_CONVERSATIONAL_COMMENTARY=true\n");
        sb.append("NO_PREFACE=true\n");
        sb.append("NO_MORALIZING=true\n");
        sb.append("NO_EDITORIAL_COMMENTARY=true\n");
        sb.append("NO_PLOT_ADVANCEMENT=true\n");
        sb.append("NO_SCENE_EXTENSION=true\n");
        sb.append("OUTPUT_ONLY_REVISED_TARGET_PASSAGE=true\n");
        sb.append("RESPECT_TOKEN_ENVELOPE=true\n");

        return sb.toString();
    }

    /**
     * Extracts a verbatim structural sample: prefers targetPassage itself
     * (the passage actually being rewritten is the truest style sample),
     * falling back to the trailing non-empty lines of surroundingContext
     * only when targetPassage is blank. No classification, no regex — the
     * exact text is preserved, mirroring CalibrationEngine's sliding-window
     * approach.
     */
    private String extractStructuralAnchor(String targetPassage, String surroundingContext) {
        if (targetPassage != null && !targetPassage.trim().isEmpty()) {
            return targetPassage.trim();
        }

        if (surroundingContext == null || surroundingContext.trim().isEmpty()) {
            return "(no target passage or surrounding context available — no anchor sample)";
        }

        String[] rawLines = surroundingContext.trim().split("\\r?\\n");
        List<String> nonEmptyLines = new ArrayList<>();
        for (String line : rawLines) {
            if (!line.trim().isEmpty()) {
                nonEmptyLines.add(line);
            }
        }

        if (nonEmptyLines.isEmpty()) {
            return "(no target passage or surrounding context available — no anchor sample)";
        }

        int windowStart = Math.max(0, nonEmptyLines.size() - STRUCTURAL_ANCHOR_LINE_WINDOW);
        List<String> window = nonEmptyLines.subList(windowStart, nonEmptyLines.size());
        return String.join("\n", window);
    }

    private String formatRatio(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}