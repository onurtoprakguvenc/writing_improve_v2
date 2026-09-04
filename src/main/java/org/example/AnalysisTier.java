package org.example;

/**
 * Operating depth of the local analysis pass. Colocated in org.example —
 * no dependency on com.example.engine.
 */
public enum AnalysisTier {
    K1_LIGHT,
    K2_BALANCED,
    K3_DEEP
}