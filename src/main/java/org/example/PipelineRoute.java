package org.example;

import java.io.Serializable;

/**
 * Katman 2: Saf Fiziksel Yönlendirme Koordinatı.
 *
 * Etiket veya kategori içermez; doğrudan tampon sınırlarını ve
 * arabelleğe yazma iznini taşır.
 */
public final class PipelineRoute implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum ExecutionTarget {
        SURGICAL_MUTATION,
        LINEAR_CONTINUATION,
        NON_DESTRUCTIVE_CONSULTATION
    }

    private final ExecutionTarget target;
    private final String targetText;
    private final int targetStart;
    private final int targetEnd;
    private final boolean modifiesBuffer;

    private PipelineRoute(ExecutionTarget target, String targetText, int targetStart, int targetEnd, boolean modifiesBuffer) {
        this.target = target;
        this.targetText = (targetText != null) ? targetText : "";
        this.targetStart = targetStart;
        this.targetEnd = targetEnd;
        this.modifiesBuffer = modifiesBuffer;
    }

    public static PipelineRoute mutation(String targetText, int start, int end) {
        return new PipelineRoute(ExecutionTarget.SURGICAL_MUTATION, targetText, start, end, true);
    }

    public static PipelineRoute continuation(int cursor) {
        return new PipelineRoute(ExecutionTarget.LINEAR_CONTINUATION, "", cursor, cursor, true);
    }

    public static PipelineRoute consultation() {
        return new PipelineRoute(ExecutionTarget.NON_DESTRUCTIVE_CONSULTATION, "", -1, -1, false);
    }

    public ExecutionTarget getTarget() { return target; }
    public String getTargetText() { return targetText; }
    public int getTargetStart() { return targetStart; }
    public int getTargetEnd() { return targetEnd; }
    public boolean modifiesBuffer() { return modifiesBuffer; }
}