package org.example;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable data container representing the real-time physical buffer coordinates:
 * full manuscript text, selection boundary coordinates, and cursor offset.
 * <p>
 * Strictly thread-safe with index clamping to prevent StringIndexOutOfBoundsException.
 */
public final class EditorState implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String fullManuscript;
    private final int cursorPosition;
    private final int selectionStart;
    private final int selectionEnd;

    /**
     * Primary constructor with strict boundary clamping and index normalization.
     *
     * @param fullManuscript The full text content of the manuscript (null defaults to empty string).
     * @param cursorPosition Active cursor position offset.
     * @param selectionStart Selection start offset (inclusive).
     * @param selectionEnd   Selection end offset (exclusive).
     */
    public EditorState(String fullManuscript, int cursorPosition, int selectionStart, int selectionEnd) {
        this.fullManuscript = (fullManuscript != null) ? fullManuscript : "";
        int len = this.fullManuscript.length();

        int s = clamp(selectionStart, 0, len);
        int e = clamp(selectionEnd, 0, len);

        // Normalize selection indices so start <= end is invariant
        this.selectionStart = Math.min(s, e);
        this.selectionEnd = Math.max(s, e);

        this.cursorPosition = clamp(cursorPosition, 0, len);
    }

    /**
     * Convenience constructor for insertion state (no selection).
     */
    public EditorState(String fullManuscript, int cursorPosition) {
        this(fullManuscript, cursorPosition, cursorPosition, cursorPosition);
    }

    /**
     * Convenience constructor for selection state with cursor anchored at selection end.
     */
    public EditorState(String fullManuscript, int selectionStart, int selectionEnd) {
        this(fullManuscript, Math.max(selectionStart, selectionEnd), selectionStart, selectionEnd);
    }

    public static EditorState empty() {
        return new EditorState("", 0, 0, 0);
    }

    public static EditorState insertion(String fullManuscript, int cursorPosition) {
        return new EditorState(fullManuscript, cursorPosition);
    }

    public static EditorState selection(String fullManuscript, int selectionStart, int selectionEnd) {
        return new EditorState(fullManuscript, selectionStart, selectionEnd);
    }

    private static int clamp(int val, int min, int max) {
        if (val < min) return min;
        if (val > max) return max;
        return val;
    }

    public String getFullManuscript() {
        return fullManuscript;
    }

    public int getCursorPosition() {
        return cursorPosition;
    }

    public int getSelectionStart() {
        return selectionStart;
    }

    public int getSelectionEnd() {
        return selectionEnd;
    }

    /**
     * Returns true if the user has highlighted a non-empty physical slice of the manuscript buffer.
     */
    public boolean hasSelection() {
        return selectionStart < selectionEnd;
    }

    /**
     * Extracts the exact substring defined by [selectionStart, selectionEnd].
     * Returns an empty string if hasSelection() is false.
     */
    public String getSelectedText() {
        if (!hasSelection()) {
            return "";
        }
        return fullManuscript.substring(selectionStart, selectionEnd);
    }

    /**
     * Extracts up to maxChars immediately preceding the anchor.
     * In Selection Mode, the anchor is selectionStart.
     * In Insertion Mode, the anchor is cursorPosition.
     */
    public String getPrecedingContext(int maxChars) {
        int anchor = hasSelection() ? selectionStart : cursorPosition;
        int clampedMax = Math.max(0, maxChars);
        int start = Math.max(0, anchor - clampedMax);
        return fullManuscript.substring(start, anchor);
    }

    /**
     * Extracts up to maxChars immediately following the anchor.
     * In Selection Mode, the anchor is selectionEnd.
     * In Insertion Mode, the anchor is cursorPosition.
     */
    public String getTrailingContext(int maxChars) {
        int anchor = hasSelection() ? selectionEnd : cursorPosition;
        int clampedMax = Math.max(0, maxChars);
        int end = Math.min(fullManuscript.length(), anchor + clampedMax);
        return fullManuscript.substring(anchor, end);
    }

    public int getSelectionLength() {
        return selectionEnd - selectionStart;
    }

    public int getManuscriptLength() {
        return fullManuscript.length();
    }

    public boolean isEmpty() {
        return fullManuscript.isEmpty();
    }

    public EditorState withManuscript(String newManuscript) {
        return new EditorState(newManuscript, cursorPosition, selectionStart, selectionEnd);
    }

    public EditorState withCursor(int newCursor) {
        return new EditorState(fullManuscript, newCursor, newCursor, newCursor);
    }

    public EditorState withSelection(int newStart, int newEnd) {
        return new EditorState(fullManuscript, Math.max(newStart, newEnd), newStart, newEnd);
    }

    /**
     * Extracts surrounding context symmetrically around the current anchor/selection.
     * Captures up to radiusChars before the selection and up to radiusChars after the selection.
     */
    public String getSurroundingContext(int radiusChars) {
        int clampedRadius = Math.max(0, radiusChars);
        int startAnchor = hasSelection() ? selectionStart : cursorPosition;
        int endAnchor = hasSelection() ? selectionEnd : cursorPosition;

        int start = Math.max(0, startAnchor - clampedRadius);
        int end = Math.min(fullManuscript.length(), endAnchor + clampedRadius);

        return fullManuscript.substring(start, end);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EditorState that = (EditorState) o;
        return cursorPosition == that.cursorPosition &&
                selectionStart == that.selectionStart &&
                selectionEnd == that.selectionEnd &&
                Objects.equals(fullManuscript, that.fullManuscript);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullManuscript, cursorPosition, selectionStart, selectionEnd);
    }

    @Override
    public String toString() {
        return "EditorState{" +
                "length=" + fullManuscript.length() +
                ", cursor=" + cursorPosition +
                ", selection=[" + selectionStart + ", " + selectionEnd + "]" +
                ", hasSelection=" + hasSelection() +
                '}';
    }
}