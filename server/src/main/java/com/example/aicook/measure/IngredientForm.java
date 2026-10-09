package com.example.aicook.measure;

/**
 * Physical form of an ingredient, stored per ingredient in the dictionary. It decides which
 * tools make sense: a solid is never measured by the volume of a cup or bowl.
 */
public enum IngredientForm {
    LIQUID,
    POWDER,
    PASTE,
    SOLID;

    /** Ingredients without a stored form (not in the dictionary) are treated as solid. */
    public static IngredientForm of(String form) {
        return form == null ? SOLID : valueOf(form);
    }
}
