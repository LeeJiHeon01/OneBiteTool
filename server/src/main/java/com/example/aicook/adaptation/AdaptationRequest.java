package com.example.aicook.adaptation;

import com.example.aicook.recipe.Recipe.SkillLevel;

import java.util.List;

/** Request of POST /api/recipe-adaptations (spec 8.3.1). */
public record AdaptationRequest(String recipeId, Integer servings, List<String> missingIngredientIds,
                                List<SubstituteAnswer> substituteAnswers, Profile profile) {

    /** The user's answer to "do you have this substitute?". */
    public record SubstituteAnswer(String ingredientId, String substituteId, Boolean hasIt) {
    }

    /** Volume of one of the user's own tools, measured against a 500 mL water bottle (spec 4.8). */
    public record CustomToolVolume(String toolCode, Double volumeMl) {
    }

    /**
     * User conditions sent with each request while there are no accounts. Allergy codes are
     * health data: used for this request only, never stored and never logged. User-measured
     * tool volumes are likewise used for this request only and never stored.
     */
    public record Profile(SkillLevel skillLevel, List<String> toolVariantIds, String defaultHeatSource,
                          List<String> allergenCodes, List<CustomToolVolume> customToolVolumes) {

        public Profile(SkillLevel skillLevel, List<String> toolVariantIds, String defaultHeatSource,
                       List<String> allergenCodes) {
            this(skillLevel, toolVariantIds, defaultHeatSource, allergenCodes, List.of());
        }

        // Keeps allergy codes out of logs even if the request is printed by accident.
        @Override
        public String toString() {
            return "Profile[skillLevel=" + skillLevel + ", toolVariantIds=" + toolVariantIds
                    + ", defaultHeatSource=" + defaultHeatSource + ", allergenCodes=<hidden>]";
        }
    }
}
