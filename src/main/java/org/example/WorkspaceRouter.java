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
     * @param rawPrompt      Sağ panel komut satırına yazılan metin
     * @param selectedText   Sol editörde seçili metin (yoksa null veya boş)
     * @param fullEditorText Sol editörün tüm içeriği
     * @param scope          Bağlam penceresi derinliği
     * @param tier           Yerel gölge çekirdek analiz katmanı
     * @param onToken        Akış halindeki token'ları yakalayan tüketici
     * @return Yapılan analizin operasyonel sonucu
     * @throws IOException Ağ veya SSE akış hatalarında fırlatılır
     */
    public UserIntentAnalyzer.AnalysisResult dispatch(
            String rawPrompt,
            String selectedText,
            String fullEditorText,
            UserIntentAnalyzer.ContextScope scope,
            AnalysisTier tier,
            Consumer<String> onToken
    ) throws IOException {

        UserIntentAnalyzer.AnalysisResult analysis = intentAnalyzer.analyze(
                rawPrompt,
                selectedText,
                fullEditorText,
                scope
        );

        switch (analysis.getIntent()) {
            case CONTINUE:
                // İleriye dönük olay örgüsü yürütme
                calibrationEngine.streamNextDraft(
                        analysis.getEffectiveContext(),
                        analysis.getCleanInstruction(),
                        tier,
                        onToken
                );
                break;

            case REVISE:
            case PROOFREAD:
                // Yerinde dönüştürme veya mekanik imla düzeltme
                String targetPassage = analysis.getTargetPassage();
                if (targetPassage == null || targetPassage.isBlank()) {
                    targetPassage = analysis.getEffectiveContext();
                }

                revisionEngine.streamRevision(
                        targetPassage,
                        analysis.getCleanInstruction(),
                        analysis.getEffectiveContext(),
                        tier,
                        onToken
                );
                break;

            case CONSULT:
                // Metne dokunmadan nesnel analiz, tutarlılık veya lore kontrolü
                consultationEngine.streamConsultation(
                        analysis.getCleanInstruction(),
                        analysis.getEffectiveContext(),
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