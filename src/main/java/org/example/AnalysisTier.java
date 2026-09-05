package org.example;

/**
 * Katman 2/3: Hesaplama ve Model Seviyesi Konfigürasyonu.
 */
public enum AnalysisTier {
    FAST("gemini-3.6-flash", "Hızlı ve düşük gecikmeli akış"),
    BALANCED("gemini-3.6-flash", "Dengeli cerrahi analiz ve üretim"),
    DEEP("gemini-3.1-pro", "Yüksek derinlikli akıl yürütme");

    private final String modelName;
    private final String description;

    AnalysisTier(String modelName, String description) {
        this.modelName = modelName;
        this.description = description;
    }

    public String getModelName() {
        return modelName;
    }

    public String getDescription() {
        return description;
    }
}