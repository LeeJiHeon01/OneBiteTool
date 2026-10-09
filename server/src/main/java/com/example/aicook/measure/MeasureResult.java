package com.example.aicook.measure;

/**
 * How one ingredient amount is shown to the user.
 *
 * @param toolVariantId tool used for the converted amount, null when not converted
 * @param fillLevelKey  spoon picture to show (the fraction when there is one, otherwise "full");
 *                      null for cups and bowls, whose marks are computed, and when not converted
 * @param count         number of fills including the fraction (1.5 = one and a half), null when not converted
 * @param tasteAdjust   the user should taste and add more (seasoning is never rounded up)
 */
public record MeasureResult(String toolVariantId, String fillLevelKey, Double count, String text,
                            boolean tasteAdjust, QuantityStatus status) {

    public enum QuantityStatus {
        /** Shown as written in the source. */
        ORIGINAL,
        /** Converted with representative tool volumes; always approximate. */
        CONVERTED_APPROX,
        /** No reliable amount; the user has to check. */
        NEEDS_CHECK
    }

    static MeasureResult unconverted(String text, boolean tasteAdjust, QuantityStatus status) {
        return new MeasureResult(null, null, null, text, tasteAdjust, status);
    }
}
