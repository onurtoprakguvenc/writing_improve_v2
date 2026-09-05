package org.example;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * Günlük kullanım için bağımsız CLI çalışma alanı.
 * Metni konsoldan veya dosyadan alır, işler ve panoya kopyalayarak kapatır.
 */
public class InteractiveApp {

    private static String editorManuscript = "";

    public static void main(String[] args) {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "AQ.Ab8RN6JI41Cl9YOo2WXXua2kFldq9bV4gdgNEKOMVSzMMH6_eQ"; // Geçerli AIzaSy anahtarını buraya koy
        }

        // Router başlatma: Router String alacak şekilde güncellendiğinde doğrudan apiKey'i geçirir.
        UnifiedPipelineRouter router = new UnifiedPipelineRouter(apiKey, false);
        Scanner scanner = new Scanner(System.in);

        System.out.println("==================================================");
        System.out.println("         YAZAR HIZLI MÜDAHALE TERMİNALİ          ");
        System.out.println("==================================================");
        System.out.println("1. Üzerinde çalışılacak metni girin.");
        System.out.println("   (Metni yapıştırıp boş bir satırda ENTER'a basın veya doğrudan bir .txt yolu girin)");
        System.out.print("Metin / Dosya Yolu: ");

        StringBuilder initialText = new StringBuilder();
        boolean firstLine = true;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            // İlk satırda doğrudan geçerli bir .txt dosyası girildiyse dosyayı oku ve bitir
            if (firstLine && line.trim().endsWith(".txt") && Files.exists(Path.of(line.trim()))) {
                try {
                    initialText.append(Files.readString(Path.of(line.trim())));
                    System.out.println("[Dosya başarıyla yüklendi]");
                    break;
                } catch (IOException e) {
                    System.err.println("Dosya okunamadı: " + e.getMessage());
                }
            }
            firstLine = false;

            // Boş bir satır girildiğinde yapıştırma işlemi tamamlanır
            if (line.trim().isEmpty() && initialText.length() > 0) {
                break;
            }

            initialText.append(line).append("\n");
        }

        editorManuscript = initialText.toString().trim();
        if (editorManuscript.isEmpty()) {
            System.out.println("[Boş metin ile başlatıldı]");
        }

        System.out.println("\n--------------------------------------------------");
        System.out.println("Komutlar: ':exit' (Panoya kopyalar ve çıkar), ':show', ':copy'");
        System.out.println("--------------------------------------------------");

        while (true) {
            System.out.println("\n[MEVCUT SON SATIRLAR]:");
            printLastLines(editorManuscript, 3);
            System.out.print("\nKOMUT / PROMPT GİRİN > ");

            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase(":exit")) {
                copyToClipboard(editorManuscript);
                System.out.println("[Nihai metin panoya kopyalandı. Program kapatılıyor.]");
                break;
            } else if (input.equalsIgnoreCase(":show")) {
                System.out.println("\n--- TÜM EDİTÖR İÇERİĞİ ---\n" + editorManuscript + "\n--------------------------");
                continue;
            } else if (input.equalsIgnoreCase(":copy")) {
                copyToClipboard(editorManuscript);
                System.out.println("[Mevcut metin panoya kopyalandı]");
                continue;
            }

            if (input.isEmpty()) continue;

            System.out.println("\n[İşleniyor...]");
            StringBuilder responseAccumulator = new StringBuilder();
            long startMs = System.currentTimeMillis();
            boolean[] firstToken = {false};

            try {
                int cursor = editorManuscript.length();
                EditorState state = new EditorState(editorManuscript, cursor, -1, -1);

                router.dispatch(
                        input,
                        state,
                        AnalysisTier.BALANCED,
                        (String token) -> {
                            if (!firstToken[0]) {
                                System.out.println("[İlk Token: " + (System.currentTimeMillis() - startMs) + "ms]");
                                System.out.println("--- ÇIKTI ---");
                                firstToken[0] = true;
                            }
                            System.out.print(token);
                            System.out.flush();
                            responseAccumulator.append(token);
                        }
                );

                System.out.println("\n--- Tamamlandı (" + (System.currentTimeMillis() - startMs) + "ms) ---");

                String generated = responseAccumulator.toString().trim();
                if (!generated.isEmpty()) {
                    // Askıda kalan tırnak veya parantezleri güvenli şekilde kapat
                    generated = StructuralPolish.closeHangingSyntax(generated);

                    if (!editorManuscript.isEmpty()) {
                        editorManuscript += "\n" + generated;
                    } else {
                        editorManuscript = generated;
                    }
                    System.out.println("[Metne eklendi ve sentaks doğrulandı]");
                }

            } catch (Exception e) {
                System.err.println("İşlem hatası: " + e.getMessage());
            }
        }

        scanner.close();
    }

    private static void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        } catch (Exception ignored) {
            // Headless ortamlarda sessizce geç
        }
    }

    private static void printLastLines(String text, int maxLines) {
        if (text == null || text.isBlank()) {
            System.out.println("  (Metin boş)");
            return;
        }
        String[] lines = text.split("\\r?\\n");
        int start = Math.max(0, lines.length - maxLines);
        for (int i = start; i < lines.length; i++) {
            System.out.println("  " + lines[i]);
        }
    }
}