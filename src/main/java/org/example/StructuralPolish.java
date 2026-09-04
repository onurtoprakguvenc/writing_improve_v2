package org.example;

/**
 * Lightweight post-processing guard that resolves hanging syntax left by a
 * generation that ends mid-token or gets cut at a maxOutputTokens boundary.
 * Pure JVM, zero dependencies. Only ever appends closing characters — never
 * removes or rewrites content the model actually produced.
 */
public final class StructuralPolish {

    private StructuralPolish() {
        // static utility, no instances
    }

    /**
     * Returns {@code text} with any unmatched trailing quote or unmatched
     * bracket/paren closed off.
     * - Unclosed '(' or '[' -> appends the matching ')' or ']', respecting nesting order.
     * - Unclosed '"' or '\u201C' (curly open quote) -> appends the matching closing quote.
     */
    public static String closeHangingSyntax(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        // Close an unmatched straight double quote.
        long straightQuoteCount = result.chars().filter(c -> c == '"').count();
        if (straightQuoteCount % 2 != 0) {
            result = result + "\"";
        }

        // Close an unmatched curly quote pair (open “ without close ”).
        long openCurly = result.chars().filter(c -> c == '\u201C').count();
        long closeCurly = result.chars().filter(c -> c == '\u201D').count();
        if (openCurly > closeCurly) {
            result = result + "\u201D";
        }

        // Close unmatched brackets/parens in correct nesting order via a stack.
        StringBuilder closer = new StringBuilder();
        java.util.ArrayDeque<Character> stack = new java.util.ArrayDeque<>();
        for (int i = 0; i < result.length(); i++) {
            char ch = result.charAt(i);
            if (ch == '[' || ch == '(') {
                stack.addLast(ch);
            } else if (ch == ']') {
                if (!stack.isEmpty() && stack.peekLast() == '[') {
                    stack.removeLast();
                }
            } else if (ch == ')') {
                if (!stack.isEmpty() && stack.peekLast() == '(') {
                    stack.removeLast();
                }
            }
        }
        while (!stack.isEmpty()) {
            char open = stack.removeLast();
            closer.append(open == '[' ? ']' : ')');
        }

        return result + closer;
    }
}