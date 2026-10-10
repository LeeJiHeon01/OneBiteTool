package com.example.aicook.recipe;

import com.example.aicook.reference.Rules.AllergenRef;
import com.example.aicook.reference.Rules.ReviewLevel;

import java.util.List;

/** Responses of the recipe search and detail endpoints (spec 8.2, 8.3.1). */
public final class RecipeDtos {

    private RecipeDtos() {
    }

    public enum RecipeBadge {
        VERIFIED,
        IMPORTED,
        WEB_SEARCH
    }

    /**
     * @param allergenCodes      every allergen in the recipe, hidden components included; the app
     *                           compares them with the allergies stored on the device
     * @param allergyCheckNeeded an ingredient has no reviewed allergen mapping, so the list
     *                           above may be incomplete
     */
    public record SearchResult(String id, String name, int ingredientCount, int stepCount, Integer minutes,
                               RecipeBadge recipeBadge, String sourceUrl, String sourceDomain,
                               List<String> allergenCodes, boolean allergyCheckNeeded) {
    }

    public record Detail(String id, String name, RecipeBadge recipeBadge, String sourceUrl, String sourceDomain,
                         double baseServings, Integer minutes, List<ToolNeed> requiredTools,
                         List<DetailIngredient> ingredients, List<DetailStep> steps) {
    }

    public record ToolNeed(String id, String label, List<String> toolIds) {
    }

    /**
     * rawText is the amount as written in the source ("1큰술").
     *
     * @param dictionaryName        the common name when the recipe uses another word for the same
     *                              ingredient ("식물성 오일" is 식용유); null when it is the same or unknown
     * @param allergenReviewLevel   null when the mapping is not reviewed (same as allergyCheckNeeded);
     *                              AI_RESEARCHED is shown with "check the product label"
     * @param allergenProductVaries the allergens differ by product
     */
    public record DetailIngredient(String id, String name, String dictionaryName, String rawText,
                                   List<AllergenRef> allergens,
                                   boolean allergyCheckNeeded, ReviewLevel allergenReviewLevel,
                                   boolean allergenProductVaries) {
    }

    public record DetailStep(int order, String action) {
    }
}
