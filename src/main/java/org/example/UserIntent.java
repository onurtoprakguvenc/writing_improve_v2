package org.example;

/**
 * Deterministic intent classifications for the writing assistant engine.
 */
public enum UserIntent {
    /**
     * Plot advancement / story beat execution.
     * Appends narrative continuation at the cursor position without altering existing text.
     */
    CONTINUE,

    /**
     * In-place semantic or stylistic manuscript transformation.
     * Operates on selected text or a safely extracted trailing block.
     */
    REVISE,

    /**
     * In-place mechanical error correction, spelling, punctuation, or grammar polish.
     * Operates strictly on highlighted text.
     */
    PROOFREAD,

    /**
     * Advisory, non-destructive consultation query.
     * Strictly read-only; leaves the manuscript buffer untouched.
     */
    CONSULT
}