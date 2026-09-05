package org.example;

import java.util.ArrayDeque;
import java.util.Deque;

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
     * bracket/paren closed off in correct syntactic order.
     */
    public static String closeHangingSyntax(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Deque<Character> delimiterStack = new ArrayDeque<>();
        boolean insideStraightQuote = false;
        int unclosedCurlyQuotes = 0;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            // Escape edilmiş tırnakları (\") atla
            if (ch == '\\' && i + 1 < text.length()) {
                i++;
                continue;
            }

            if (ch == '"') {
                insideStraightQuote = !insideStraightQuote;
            } else if (ch == '\u201C') { // “
                unclosedCurlyQuotes++;
            } else if (ch == '\u201D') { // ”
                if (unclosedCurlyQuotes > 0) unclosedCurlyQuotes--;
            } else if (ch == '(' || ch == '[') {
                delimiterStack.addLast(ch);
            } else if (ch == ')') {
                if (!delimiterStack.isEmpty() && delimiterStack.peekLast() == '(') {
                    delimiterStack.removeLast();
                }
            } else if (ch == ']') {
                if (!delimiterStack.isEmpty() && delimiterStack.peekLast() == '[') {
                    delimiterStack.removeLast();
                }
            }
        }

        StringBuilder closer = new StringBuilder();

        // Önce açık kalan parantezler kapatılır (çünkü genellikle tırnak içindedir)
        while (!delimiterStack.isEmpty()) {
            char open = delimiterStack.removeLast();
            closer.append(open == '(' ? ')' : ']');
        }

        // Açık kalan curly quote varsa kapatılır
        while (unclosedCurlyQuotes > 0) {
            closer.append('\u201D');
            unclosedCurlyQuotes--;
        }

        // En son dış tırnak kapatılır
        if (insideStraightQuote) {
            closer.append('"');
        }

        return text + closer.toString();
    }
}