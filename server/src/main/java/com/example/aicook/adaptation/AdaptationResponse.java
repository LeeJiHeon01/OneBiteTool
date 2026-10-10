package com.example.aicook.adaptation;

import com.example.aicook.measure.MeasureResult.QuantityStatus;
import com.example.aicook.recipe.Recipe.HeatLevel;
import com.example.aicook.recipe.RecipeDtos.RecipeBadge;
import com.example.aicook.reference.Rules.AllergenRef;
import com.example.aicook.reference.Sensitivity;

import java.util.List;

/**
 * Response of POST /api/recipe-adaptations (spec 8.3, 8.3.1).
 *
 * @param ignoredCustomTools tools whose user-measured volume was not accepted, so their
 *                           representative volume was used (spec 4.8)
 */
public record AdaptationResponse(String id, Status status, RecipeBadge recipeBadge, String sourceUrl,
                                 List<AllergyWarning> allergyWarnings,
                                 List<String> allergyCheckNeededIngredientIds,
                                 List<PendingQuestion> pendingQuestions, List<Change> changes,
                                 List<RequiredItem> requiredItems, List<String> safetySummary,
                                 List<AdaptedIngredient> ingredients, List<AdaptedStep> steps,
                                 List<String> ignoredCustomTools) {

    /** The same answer under the id it is stored with. */
    AdaptationResponse withId(String newId) {
        return new AdaptationResponse(newId, status, recipeBadge, sourceUrl, allergyWarnings,
                allergyCheckNeededIngredientIds, pendingQuestions, changes, requiredItems, safetySummary,
                ingredients, steps, ignoredCustomTools);
    }

    /**
     * The same answer with the allergy lists of one request. A stored answer has none: they
     * depend on the user's allergies, which are never stored.
     */
    AdaptationResponse withAllergy(List<AllergyWarning> warnings, List<String> checkNeededIngredientIds,
                                   List<AdaptedIngredient> ingredientsWithAllergens) {
        return new AdaptationResponse(id, status, recipeBadge, sourceUrl, warnings, checkNeededIngredientIds,
                pendingQuestions, changes, requiredItems, safetySummary, ingredientsWithAllergens, steps,
                ignoredCustomTools);
    }

    public enum Status {
        COOKABLE,
        NEEDS_CONFIRMATION,
        NOT_COOKABLE
    }

    public record AllergyWarning(String allergenCode, String ingredientId, boolean hidden) {
    }

    public record PendingQuestion(String ingredientId, String substituteId, String substituteName) {
    }

    /** OMIT_BY_USER is no longer produced (2026-10-10): only listed ingredients are left out, as OMIT. */
    public enum ChangeType {
        OMIT,
        OMIT_BY_USER,
        SUBSTITUTE,
        TOOL_SUBSTITUTE,
        METHOD_CHANGE,
        SERVING_SCALE
    }

    /** target is the id of the ingredient or tool that changed. */
    public record Change(ChangeType type, String target, String description, String impact) {
    }

    public enum ItemKind {
        INGREDIENT,
        TOOL
    }

    public record RequiredItem(ItemKind kind, String id, String name, String reason) {
    }

    /**
     * allergenProductVaries: the allergens of this ingredient differ by product (soy sauce, curry).
     * allergens and allergenReviewed are filled in for each answer and are not part of a stored one.
     */
    public record AdaptedIngredient(String ingredientId, String name, Original original, Display display,
                                    QuantityStatus quantityStatus, Sensitivity sensitivity,
                                    boolean allergenProductVaries, List<AllergenRef> allergens,
                                    boolean allergenReviewed) {

        public AdaptedIngredient(String ingredientId, String name, Original original, Display display,
                                 QuantityStatus quantityStatus, Sensitivity sensitivity,
                                 boolean allergenProductVaries) {
            this(ingredientId, name, original, display, quantityStatus, sensitivity, allergenProductVaries,
                    List.of(), false);
        }

        /**
         * The same ingredient with what the allergen mapping says about it: every allergen it
         * contains, whoever the user is, and whether the mapping was reviewed. These are facts
         * about the ingredient; the app compares them with the allergies kept on the device.
         */
        AdaptedIngredient withAllergens(List<AllergenRef> allAllergens, boolean reviewed) {
            return new AdaptedIngredient(ingredientId, name, original, display, quantityStatus, sensitivity,
                    allergenProductVaries, allAllergens, reviewed);
        }
    }

    /** The source amount; always returned so the app can show it under the converted amount. */
    public record Original(Double quantity, String unit, String rawText) {
    }

    /**
     * count is the number of fills including the fraction (1.5 = one and a half).
     * userMeasured: the amount was worked out with the volume the user measured for this tool.
     */
    public record Display(String toolVariantId, String fillLevelKey, Double count, String text,
                          boolean tasteAdjust, boolean userMeasured) {

        public Display(String toolVariantId, String fillLevelKey, Double count, String text, boolean tasteAdjust) {
            this(toolVariantId, fillLevelKey, count, text, tasteAdjust, false);
        }
    }

    /**
     * actionKey is the kind of action ("action.stir_fry"); the app picks the step picture and label from it.
     * mergedFromStepIds lists the recipe steps a joined step came from; empty for a step shown as stored.
     * subItems is the checklist of a joined finishing step; empty otherwise.
     * durationMaxSeconds is the long end of a time range ("2~3분"); null when the time is not a range.
     */
    public record AdaptedStep(String stepId, int order, String action, String actionKey, String details,
                              List<String> ingredientIds,
                              List<String> toolIds, List<String> visualKeys, Integer durationSeconds,
                              String timerStartCondition, HeatLevel heatLevel, String heatRaw,
                              String deviceSettingRaw, String heatVisualKey, String completionCheck,
                              List<SafetyNote> safetyNotes, List<String> mergedFromStepIds,
                              List<SubItem> subItems, Integer durationMaxSeconds) {

        public AdaptedStep(String stepId, int order, String action, String actionKey, String details,
                           List<String> ingredientIds, List<String> toolIds, List<String> visualKeys,
                           Integer durationSeconds, String timerStartCondition, HeatLevel heatLevel, String heatRaw,
                           String deviceSettingRaw, String heatVisualKey, String completionCheck,
                           List<SafetyNote> safetyNotes, List<String> mergedFromStepIds, List<SubItem> subItems) {
            this(stepId, order, action, actionKey, details, ingredientIds, toolIds, visualKeys, durationSeconds,
                    timerStartCondition, heatLevel, heatRaw, deviceSettingRaw, heatVisualKey, completionCheck,
                    safetyNotes, mergedFromStepIds, subItems, null);
        }

        public AdaptedStep(String stepId, int order, String action, String actionKey, String details,
                           List<String> ingredientIds, List<String> toolIds, List<String> visualKeys,
                           Integer durationSeconds, String timerStartCondition, HeatLevel heatLevel, String heatRaw,
                           String deviceSettingRaw, String heatVisualKey, String completionCheck,
                           List<SafetyNote> safetyNotes, List<String> mergedFromStepIds) {
            this(stepId, order, action, actionKey, details, ingredientIds, toolIds, visualKeys, durationSeconds,
                    timerStartCondition, heatLevel, heatRaw, deviceSettingRaw, heatVisualKey, completionCheck,
                    safetyNotes, mergedFromStepIds, List.of());
        }

        /** Finishing steps (plating, garnishing) joined into one step with a checklist of what to do. */
        AdaptedStep platedWith(int newOrder, String newAction, List<String> allIngredientIds,
                               List<SafetyNote> allSafetyNotes, List<String> mergedIds, List<SubItem> items) {
            return new AdaptedStep(stepId, newOrder, newAction, actionKey, details, allIngredientIds, toolIds,
                    visualKeys, durationSeconds, timerStartCondition, heatLevel, heatRaw, deviceSettingRaw,
                    heatVisualKey, completionCheck, allSafetyNotes, mergedIds, items, durationMaxSeconds);
        }

        /** Same step at another position in the list. */
        AdaptedStep withOrder(int newOrder) {
            return new AdaptedStep(stepId, newOrder, action, actionKey, details, ingredientIds, toolIds, visualKeys,
                    durationSeconds, timerStartCondition, heatLevel, heatRaw, deviceSettingRaw, heatVisualKey,
                    completionCheck, safetyNotes, mergedFromStepIds, subItems, durationMaxSeconds);
        }

        /** This step joined with the same action on other ingredients; mergedIds lists every source step. */
        AdaptedStep mergedWith(int newOrder, String newAction, List<String> allIngredientIds,
                               List<SafetyNote> allSafetyNotes, List<String> mergedIds) {
            return new AdaptedStep(stepId, newOrder, newAction, actionKey, details, allIngredientIds, toolIds,
                    visualKeys, durationSeconds, timerStartCondition, heatLevel, heatRaw, deviceSettingRaw,
                    heatVisualKey, completionCheck, allSafetyNotes, mergedIds, List.of(), durationMaxSeconds);
        }

        /**
         * One action of this step when it is shown as several steps. Only the part that carries
         * the step's own action keeps the timer and the check; heat stays on that part and on other
         * parts cooked over heat. The ingredients and safety notes of the part are decided by the
         * server for that part (an ingredient or a caution the server cannot place stays on all).
         */
        AdaptedStep partOf(int number, String partAction, String partActionKey, String partDetails,
                           boolean keepsTimer, boolean keepsHeat, List<String> partIngredientIds,
                           List<SafetyNote> partSafetyNotes) {
            return new AdaptedStep(stepId + "-" + number, order, partAction, partActionKey, partDetails,
                    partIngredientIds, toolIds, visualKeys, keepsTimer ? durationSeconds : null,
                    keepsTimer ? timerStartCondition : null, keepsHeat ? heatLevel : null,
                    keepsHeat ? heatRaw : null, keepsTimer ? deviceSettingRaw : null,
                    keepsHeat ? heatVisualKey : null, keepsTimer ? completionCheck : null, partSafetyNotes,
                    mergedFromStepIds.isEmpty() ? List.of(stepId) : mergedFromStepIds, List.of(),
                    keepsTimer ? durationMaxSeconds : null);
        }

        /** Same step with other wording; time, heat, check and safety notes stay as they are. */
        AdaptedStep withText(String newAction, String newDetails) {
            return new AdaptedStep(stepId, order, newAction, actionKey, newDetails, ingredientIds, toolIds, visualKeys,
                    durationSeconds, timerStartCondition, heatLevel, heatRaw, deviceSettingRaw, heatVisualKey,
                    completionCheck, safetyNotes, mergedFromStepIds, subItems, durationMaxSeconds);
        }
    }

    public record SafetyNote(String message, String detail) {
    }

    /**
     * One thing to do inside a finishing step, shown as a checklist. Ticking it is never needed
     * to move on. ingredientIds lets the app repeat an allergy warning next to the item.
     */
    public record SubItem(String stepId, String text, List<String> ingredientIds) {
    }
}
