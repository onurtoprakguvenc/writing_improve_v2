package org.example;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * Independent CLI workspace for daily drafting.
 * Ingests text from console or file, processes it, and copies result to clipboard upon exit.
 */
public class InteractiveApp {

    private static EditorState currentState;

    public static void main(String[] args) {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "gemini apı key";
        }

        UnifiedPipelineRouter router = new UnifiedPipelineRouter(apiKey, false);
        Scanner scanner = new Scanner(System.in);

        System.out.println("==================================================");
        System.out.println("        WRITER RAPID INTERVENTION TERMINAL        ");
        System.out.println("==================================================");
        System.out.println("1. Enter text to work on.");
        System.out.println("   (Paste text and press ENTER on an empty line, or provide a .txt path directly)");
        System.out.print("Text / File Path: ");

        StringBuilder initialText = new StringBuilder();
        boolean firstLine = true;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            // Direct file ingestion check
            if (firstLine && line.trim().endsWith(".txt") && Files.exists(Path.of(line.trim()))) {
                try {
                    initialText.append(Files.readString(Path.of(line.trim())));
                    System.out.println("[File loaded successfully]");
                    break;
                } catch (IOException e) {
                    System.err.println("Failed to read file: " + e.getMessage());
                }
            }
            firstLine = false;

            // Empty line terminates multi-line paste
            if (line.trim().isEmpty() && initialText.length() > 0) {
                break;
            }

            initialText.append(line).append("\n");
        }

        String rawText = initialText.toString().trim();
        int initialCursor = rawText.length();
        currentState = new EditorState(rawText, initialCursor, -1, -1);

        if (rawText.isEmpty()) {
            System.out.println("[Initialized with empty buffer]");
        }

        System.out.println("\n--------------------------------------------------");
        System.out.println("Commands: ':exit' (Copy and quit), ':show', ':copy'");
        System.out.println("--------------------------------------------------");

        while (true) {
            System.out.println("\n[CURRENT RECENT LINES]:");
            printLastLines(currentState.getFullManuscript(), 3);
            System.out.print("\nCOMMAND / PROMPT > ");

            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase(":exit")) {
                copyToClipboard(currentState.getFullManuscript());
                System.out.println("[Final text copied to clipboard. Exiting.]");
                break;
            } else if (input.equalsIgnoreCase(":show")) {
                System.out.println("\n--- FULL EDITOR BUFFER ---\n" + currentState.getFullManuscript() + "\n--------------------------");
                continue;
            } else if (input.equalsIgnoreCase(":copy")) {
                copyToClipboard(currentState.getFullManuscript());
                System.out.println("[Current text copied to clipboard]");
                continue;
            }

            if (input.isEmpty()) continue;

            System.out.println("\n[Processing...]");
            long startMs = System.currentTimeMillis();
            boolean[] firstToken = {false};

            // Atomic rollback snapshot in case stream fails mid-flight
            EditorState rollbackSnapshot = currentState;

            try {
                // Dispatch directly updates the state via DiffMergeEngine
                currentState = router.dispatch(
                        input,
                        currentState,
                        AnalysisTier.BALANCED,
                        (String token) -> {
                            if (!firstToken[0]) {
                                System.out.println("[First Token: " + (System.currentTimeMillis() - startMs) + "ms]");
                                System.out.println("--- OUTPUT ---");
                                firstToken[0] = true;
                            }
                            System.out.print(token);
                            System.out.flush();
                        }
                );

                System.out.println("\n--- Completed (" + (System.currentTimeMillis() - startMs) + "ms) ---");
                System.out.println("[Buffer spliced and syntax validated]");

            } catch (Exception e) {
                // Restore pristine state on network or parsing failure
                currentState = rollbackSnapshot;
                System.err.println("Execution failure (reverted to previous buffer state): " + e.getMessage());
            }
        }

        scanner.close();
    }

    private static void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        } catch (Exception ignored) {
            // Silently skip in headless runtime environments
        }
    }

    private static void printLastLines(String text, int maxLines) {
        if (text == null || text.isBlank()) {
            System.out.println("  (Buffer is empty)");
            return;
        }
        String[] lines = text.split("\\r?\\n");
        int start = Math.max(0, lines.length - maxLines);
        for (int i = start; i < lines.length; i++) {
            System.out.println("  " + lines[i]);
        }
    }
}