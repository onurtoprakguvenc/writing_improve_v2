package org.example;

public enum AnalysisTier {
    K1_LIGHT("gemini-3.6-flash", "Lightweight tier"),
    K2_BALANCED("gemini-3.6-flash", "Standard balanced tier"),
    K3_DEEP("gemini-3.1-pro", "Deep reasoning tier"),
    SURGICAL("gemini-3.6-flash", "Surgical execution"),
    DEEP("gemini-3.1-pro", "High-depth reasoning"),
    FAST("gemini-3.6-flash", "Rapid streaming");

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