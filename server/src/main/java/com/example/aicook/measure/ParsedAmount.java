package com.example.aicook.measure;

/**
 * An ingredient amount as written in the source. {@code rawText} is always kept.
 *
 * @param vague       the source gives no number ("약간", "적당량")
 * @param primary     the main amount, null when it could not be read
 * @param alternative the amount in parentheses, e.g. "2g(1/3작은술)", or null
 */
public record ParsedAmount(String rawText, boolean vague, Amount primary, Amount alternative) {

    /** unit is null when the source gives a bare number. */
    public record Amount(double quantity, String unit) {
    }
}
