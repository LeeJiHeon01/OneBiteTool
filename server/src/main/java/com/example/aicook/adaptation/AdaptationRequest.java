package com.example.aicook.adaptation;

import com.example.aicook.recipe.Recipe.SkillLevel;

import java.util.List;

/** Request of POST /api/recipe-adaptations (spec 8.3.1). */
public record AdaptationRequest(String recipeId, Integer servings, List<String> missingIngredientIds,
                                List<SubstituteAnswer> substituteAnswers, Profile profile) {

    /** The user's answer to "do you have this substitute?". */
    public record SubstituteAnswer(String ingredientId, String substituteId, Boolean hasIt) {
    }

    /**
     * User conditions sent with each request while there are no accounts. Allergy codes are
     * health data: used for this request only, never stored and never logged.
     */
    public record Profile(SkillLevel skillLevel, List<String> toolVariantIds, String defaultHeatSource,
                          List<String> allergenCodes) {

        // Keeps allergy codes out of logs even if the request is printed by accident.
        @Override
        public String toString() {
            return "Profile[skillLevel=" + skillLevel + ", toolVariantIds=" + toolVariantIds
                    + ", defaultHeatSource=" + defaultHeatSource + ", allergenCodes=<hidden>]";
        }
    }
}
