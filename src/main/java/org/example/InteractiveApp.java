package org.example;

import java.util.Scanner;

/**
 * Terminal tabanlı minimalist prompt ve editör arayüzü.
 * Sol editör metnini ve sağ prompt çubuğunu simüle eder.
 */
public class InteractiveApp {

    private static String editorManuscript =
            "(intense fight moment)\n" +
                    "\"so sister.how does it feel to...(slices a bit of her face and drops blood)\"\n" +
                    "\"you are a mo-mo-monster.\"\n" +
                    "\"ı know.who denies that.know,back to your back.\"";

    public static void main(String[] args) {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "AQ.Ab8RN6IabMVCrjOiZDvl4xj7n-wexbnIS-yKA9ZJwaYoqLnbXA";
        }

        WorkspaceRouter router = new WorkspaceRouter(apiKey);
        Scanner scanner = new Scanner(System.in);

        System.out.println("==================================================");
        System.out.println("   YAZAR ÇALIŞMA ALANI (JVM INTERACTIVE CONSOLE)  ");
        System.out.println("==================================================");
        System.out.println("Komutlar:");
        System.out.println(" - ':exit' -> Çıkış");
        System.out.println(" - ':show' -> Mevcut editör metnini göster");
        System.out.println(" - ':clear' -> Editör metnini sıfırla");
        System.out.println("--------------------------------------------------\n");

        while (true) {
            System.out.println("\n[AKTİF EDİTÖR METNİ SONU]:");
            printLastLines(editorManuscript, 3);
            System.out.print("\nPROMPT GİRİN > ");

            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase(":exit")) {
                System.out.println("Çalışma alanı kapatıldı.");
                break;
            } else if (input.equalsIgnoreCase(":show")) {
                System.out.println("\n--- TÜM EDİTÖR İÇERİĞİ ---");
                System.out.println(editorManuscript);
                System.out.println("--------------------------");
                continue;
            } else if (input.equalsIgnoreCase(":clear")) {
                editorManuscript = "";
                System.out.println("[Editör temizlendi]");
                continue;
            }

            if (input.isEmpty()) {
                continue;
            }

            System.out.println("\n[İstek Analiz Ediliyor ve API Çağrılıyor...]");
            StringBuilder responseAccumulator = new StringBuilder();
            long startMs = System.currentTimeMillis();
            boolean[] firstToken = {false};

            try {
                // Lambda öncesinde niyeti belirle (değişken kapsam hatasını önler)
                UserIntentAnalyzer.AnalysisResult analysis = router.getIntentAnalyzer().analyze(
                        input,
                        null,
                        editorManuscript,
                        UserIntentAnalyzer.ContextScope.FOCUSED
                );

                router.dispatch(
                        input,
                        null,
                        editorManuscript,
                        UserIntentAnalyzer.ContextScope.FOCUSED,
                        AnalysisTier.K2_BALANCED,
                        token -> {
                            if (!firstToken[0]) {
                                System.out.println("[İlk Token Gecikmesi: " + (System.currentTimeMillis() - startMs) + "ms]");
                                System.out.println("--- ÇIKTI AKIŞI (" + analysis.getIntent() + ") ---");
                                firstToken[0] = true;
                            }
                            System.out.print(token);
                            System.out.flush();
                            responseAccumulator.append(token);
                        }
                );

                System.out.println("\n--- Akış Sonu (Toplam: " + (System.currentTimeMillis() - startMs) + "ms) ---");

                // Metin güncelleme mekanizması
                String generated = responseAccumulator.toString().trim();
                if (!generated.isEmpty()) {
                    if (analysis.getIntent() == UserIntentAnalyzer.IntentType.CONTINUE) {
                        editorManuscript += "\n" + generated;
                        System.out.println("[Metin editörün sonuna eklendi]");
                    } else if (analysis.getIntent() == UserIntentAnalyzer.IntentType.REVISE ||
                            analysis.getIntent() == UserIntentAnalyzer.IntentType.PROOFREAD) {
                        String target = analysis.getTargetPassage();
                        if (target != null && editorManuscript.contains(target)) {
                            editorManuscript = editorManuscript.replace(target, generated);
                            System.out.println("[Hedef blok yerinde güncellendi]");
                        } else {
                            System.out.println("[Hedef metin ayrıştırılamadı, çıktı konsolda gösterildi]");
                        }
                    } else if (analysis.getIntent() == UserIntentAnalyzer.IntentType.CONSULT) {
                        System.out.println("[İstişare modu: Editör metnine dokunulmadı]");
                    }
                }

            } catch (Exception e) {
                System.err.println("Çağrı sırasında hata: " + e.getMessage());
            }
        }

        scanner.close();
    }

    private static void printLastLines(String text, int maxLines) {
        if (text == null || text.isBlank()) {
            System.out.println("(Editör boş)");
            return;
        }
        String[] lines = text.split("\\r?\\n");
        int start = Math.max(0, lines.length - maxLines);
        for (int i = start; i < lines.length; i++) {
            System.out.println("  " + lines[i]);
        }
    }
}