package org.example;

/**
 * Surgical Splice Manager.
 * <p>
 * Pure, stateless, thread-safe string mutation utility responsible for deterministically
 * merging streamed generated chunks or complete replacement blocks into the buffer.
 * <p>
 * Guarantees physical buffer immutability outside of the explicit target offset or slice.
 */
public final class DiffMergeEngine {

    public DiffMergeEngine() {
        // Stateless utility instance
    }

    /**
     * Slices and inserts a generated text chunk at the specified physical buffer offset.
     * Guarantees prefix and suffix immutability around the insertion point.
     *
     * @param manuscript      The original manuscript text buffer.
     * @param insertionOffset The character offset where insertion occurs.
     * @param generatedChunk  The text chunk to insert.
     * @return The resulting manuscript string with the chunk spliced in.
     */
    public String applyInsertion(String manuscript, int insertionOffset, String generatedChunk) {
        String base = (manuscript != null) ? manuscript : "";
        String chunk = (generatedChunk != null) ? generatedChunk : "";

        if (chunk.isEmpty()) {
            return base;
        }

        int offset = clamp(insertionOffset, 0, base.length());
        StringBuilder sb = new StringBuilder(base.length() + chunk.length());
        sb.append(base, 0, offset);
        sb.append(chunk);
        sb.append(base, offset, base.length());
        return sb.toString();
    }

    /**
     * Surgically replaces the physical slice [start, end] in the manuscript buffer
     * with the replacement text.
     * <p>
     * All characters before 'start' and after 'end' remain physically invariant.
     *
     * @param manuscript  The original manuscript text buffer.
     * @param start       The start offset of the range to replace (inclusive).
     * @param end         The end offset of the range to replace (exclusive).
     * @param replacement The replacement text to splice in.
     * @return The resulting manuscript string with the slice atomically replaced.
     */
    public String applySelectionReplacement(String manuscript, int start, int end, String replacement) {
        String base = (manuscript != null) ? manuscript : "";
        String rep = (replacement != null) ? replacement : "";

        int len = base.length();
        int s = clamp(Math.min(start, end), 0, len);
        int e = clamp(Math.max(start, end), 0, len);

        StringBuilder sb = new StringBuilder(len - (e - s) + rep.length());
        sb.append(base, 0, s);
        sb.append(rep);
        sb.append(base, e, len);
        return sb.toString();
    }

    private static int clamp(int val, int min, int max) {
        if (val < min) return min;
        if (val > max) return max;
        return val;
    }
}