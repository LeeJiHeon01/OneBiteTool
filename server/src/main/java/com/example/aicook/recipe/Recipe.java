package com.example.aicook.recipe;

import java.util.List;

/**
 * A stored recipe (tables recipe, recipe_ingredient, recipe_step and their links).
 * Ids are the 20-character database ids, e.g. "RECIPE_0000000000001".
 */
public record Recipe(String id, String name, Source source, String sourceUrl, String sourceDomain,
                     VerificationStatus verificationStatus, double baseServings, Integer totalMinutes,
                     List<Ingredient> ingredients, List<Step> steps) {

    public enum Source {
        WEB_SEARCH,
        FOOD_SAFETY_KR,
        CURATED,
        FIXTURE
    }

    public enum VerificationStatus {
        UNVERIFIED,
        IMPORTED,
        DATA_CHECKED,
        COOK_TESTED
    }

    public enum SkillLevel {
        LOW,
        MID,
        HIGH
    }

    public enum HeatLevel {
        OFF,
        LOW,
        MEDIUM_LOW,
        MEDIUM,
        MEDIUM_HIGH,
        HIGH;

        public boolean usesHeat() {
            return this != OFF;
        }
    }

    public enum UnitKind {
        VOLUME,
        WEIGHT,
        COUNT,
        VAGUE
    }

    /**
     * @param dictionaryCode code in the ingredient dictionary; null when the name did not match,
     *                       which makes the ingredient "needs checking" for allergies
     * @param rawText        the source text, name included ("간장 1큰술"); always kept
     * @param quantity       null for vague amounts ("약간")
     */
    public record Ingredient(String id, String dictionaryCode, String name, String rawText, Double quantity,
                             String unit, Double altQuantity, String altUnit, UnitKind unitKind,
                             boolean required) {

        /** The amount part of the source text ("1큰술"). */
        public String amountText() {
            return rawText.startsWith(name) ? rawText.substring(name.length()).trim() : rawText;
        }
    }

    /**
     * One step with a single main action. Time and heat are only what the source states.
     *
     * @param actionKey picture key of the action ("action.stir_fry"), also matched by safety rules
     * @param heatLevel        null when the step uses no burner heat
     * @param deviceSettingRaw oven, air fryer or microwave setting exactly as the source states it
     *                         ("180°C 예열", "700W"); never converted
     */
    /** durationMaxSeconds is the long end of a time range ("2~3분" is 120 and 180); null without a range. */
    public record Step(String id, int order, String action, String actionKey, Integer durationSeconds,
                       Integer durationMaxSeconds, String timerStartCondition, HeatLevel heatLevel, String heatRaw, String deviceSettingRaw,
                       String completionCheck,
                       List<String> ingredientIds, List<String> toolIds) {

        public boolean usesHeat() {
            return heatLevel != null && heatLevel.usesHeat();
        }
    }
}
