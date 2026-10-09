package com.example.aicook.reference;

/**
 * How strictly an ingredient's amount is handled. HIGH marks seasoning that decides saltiness
 * or sweetness: it is never rounded up. RATIO marks ingredients whose proportion decides the
 * result (flour, starch, rice): they go to the nearest amount but with a tighter tolerance.
 */
public enum Sensitivity {
    HIGH,
    RATIO,
    NORMAL
}
