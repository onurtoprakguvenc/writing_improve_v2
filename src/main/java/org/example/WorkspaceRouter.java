package org.example;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Kullanıcı girdisini, arayüz seçim durumunu ve aktif bağlamı değerlendirerek
 * doğru motora (Calibration, Revision, Consultation) yönlendiren ana orkestrasyon sınıfı.
 */
public class WorkspaceRouter {

    private final CalibrationEngine calibrationEngine;
    private final RevisionEngine revisionEngine;
    private final ConsultationEngine consultationEngine;
    private final UserIntentAnalyzer intentAnalyzer;

    public WorkspaceRouter(String apiKey) {
        this(apiKey, "gemini-3.8-flash");
    }

    public WorkspaceRouter(String apiKey, String modelName) {
        this.calibrationEngine = new CalibrationEngine(apiKey, modelName);
        this.revisionEngine = new RevisionEngine(apiKey, modelName);
        this.consultationEngine = new ConsultationEngine(apiKey, modelName);
        this.intentAnalyzer = new UserIntentAnalyzer();
    }

    /**
     * Gelen isteği yönlendirir ve SSE token akışını onToken tüketicisine iletir.
     *
     * @param rawPrompt Kullanıcı aksiyon/revizyon komutu
     * @param state     Editörün anlık fiziksel durumu (metin, imleç, seçim aralığı)
     * @param tier      Yerel gölge çekirdek analiz katmanı
     * @param onToken   Akış halindeki token'ları yakalayan tüketici
     * @return Yapılan analizin operasyonel sonucu
     * @throws IOException Ağ veya SSE akış hatalarında fırlatılır
     */
    public UserIntentAnalyzer.AnalysisResult dispatch(
            String rawPrompt,
            EditorState state,
            AnalysisTier tier,
            Consumer<String> onToken
    ) throws IOException {

        UserIntentAnalyzer.AnalysisResult analysis = intentAnalyzer.analyze(state, rawPrompt);
        String fullManuscript = state.getFullManuscript();
        String prompt = analysis.getPrompt();

        switch (analysis.getIntent()) {
            case CONTINUE:
                // İleriye dönük olay örgüsü yürütme
                calibrationEngine.streamNextDraft(
                        fullManuscript,
                        prompt,
                        tier,
                        onToken
                );
                break;

            case REVISE:
            case PROOFREAD:
                // Yerinde dönüştürme veya mekanik imla düzeltme
                String targetPassage = analysis.getTargetText();
                if (targetPassage == null || targetPassage.isBlank()) {
                    targetPassage = fullManuscript;
                }

                revisionEngine.streamRevision(
                        targetPassage,
                        prompt,
                        fullManuscript,
                        tier,
                        onToken
                );
                break;

            case CONSULT:
                // Metne dokunmadan nesnel analiz, tutarlılık veya lore kontrolü
                consultationEngine.streamConsultation(
                        prompt,
                        fullManuscript,
                        onToken
                );
                break;
        }

        return analysis;
    }

    public UserIntentAnalyzer getIntentAnalyzer() {
        return intentAnalyzer;
    }
}