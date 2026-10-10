package com.example.aicook.reference;

import java.util.List;

/** Dictionary entries and verified rules as stored in the database. Ids are the table codes. */
public final class Rules {

    private Rules() {
    }

    /**
     * @param form                  measuring form: LIQUID, POWDER, PASTE or SOLID (category is only a label)
     * @param allergenReviewLevel   how far the ingredient's allergen mapping has been reviewed; null for
     *                              a draft: its known allergens are still warned about, the rest is
     *                              "needs checking"
     * @param allergenProductVaries processed ingredient whose contents differ by product (soy sauce, curry)
     */
    public record Ingredient(String code, String name, String category, String form, Sensitivity sensitivity,
                             boolean garnish, ReviewLevel allergenReviewLevel, boolean allergenProductVaries,
                             List<String> aliases) {
    }

    /** AI_RESEARCHED is a conservative mapping built from public sources; no person has confirmed it. */
    public enum ReviewLevel {
        HUMAN_VERIFIED,
        AI_RESEARCHED
    }

    /** hidden = the allergen is a component of the ingredient (soy sauce contains wheat). */
    public record AllergenRef(String code, boolean hidden) {
    }

    /** requiresHeat: shown only on a step that heats food (돼지고기 익힘 문구는 써는 단계에 붙지 않는다). */
    public record SafetyRule(String code, TriggerType triggerType, String triggerValue, String message,
                             String detailForLow, Placement placement, boolean requiresHeat) {
    }

    public enum TriggerType {
        INGREDIENT,
        ACTION,
        TOOL,
        HEAT
    }

    public enum Placement {
        STEP,
        SUMMARY,
        BOTH;

        public boolean onStep() {
            return this != SUMMARY;
        }

        public boolean onSummary() {
            return this != STEP;
        }
    }

    /** recipeId null = applies to every recipe. */
    public record IngredientRule(IngredientRuleType type, String ingredientCode, String substituteCode,
                                 String impact, String recipeId, int priority) {
    }

    public enum IngredientRuleType {
        OMIT,
        SUBSTITUTE,
        METHOD_CHANGE,
        SERVING_SCALE
    }

    /** The ingredient weighs {@code grams} when the tool is filled once ("밥그릇 1번"). Approximate. */
    public record WeightReference(String ingredientCode, String toolId, double grams) {

        /** The same reference for a tool that holds {@code ratio} times the representative volume. */
        public WeightReference scaledBy(double ratio) {
            return new WeightReference(ingredientCode, toolId, grams * ratio);
        }
    }

    public record ToolRule(String requiredToolId, String substituteToolId, String conditionNote, String recipeId,
                           String extraSafetyNote) {
    }
}
