package com.example.aicook.adaptation;

import com.example.aicook.measure.MeasureResult.QuantityStatus;
import com.example.aicook.recipe.Recipe.HeatLevel;
import com.example.aicook.recipe.RecipeDtos.RecipeBadge;
import com.example.aicook.reference.Sensitivity;

import java.util.List;

/** Response of POST /api/recipe-adaptations (spec 8.3, 8.3.1). */
public record AdaptationResponse(String id, Status status, RecipeBadge recipeBadge, String sourceUrl,
                                 List<AllergyWarning> allergyWarnings,
                                 List<String> allergyCheckNeededIngredientIds,
                                 List<PendingQuestion> pendingQuestions, List<Change> changes,
                                 List<RequiredItem> requiredItems, List<String> safetySummary,
                                 List<AdaptedIngredient> ingredients, List<AdaptedStep> steps) {

    public enum Status {
        COOKABLE,
        NEEDS_CONFIRMATION,
        NOT_COOKABLE
    }

    public record AllergyWarning(String allergenCode, String ingredientId, boolean hidden) {
    }

    public record PendingQuestion(String ingredientId, String substituteId, String substituteName) {
    }

    public enum ChangeType {
        OMIT,
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

    /** allergenProductVaries: the allergens of this ingredient differ by product (soy sauce, curry). */
    public record AdaptedIngredient(String ingredientId, String name, Original original, Display display,
                                    QuantityStatus quantityStatus, Sensitivity sensitivity,
                                    boolean allergenProductVaries) {
    }

    /** The source amount; always returned so the app can show it under the converted amount. */
    public record Original(Double quantity, String unit, String rawText) {
    }

    /** count is the number of fills including the fraction (1.5 = one and a half). */
    public record Display(String toolVariantId, String fillLevelKey, Double count, String text,
                          boolean tasteAdjust) {
    }

    /** actionKey is the kind of action ("action.stir_fry"); the app picks the step picture and label from it. */
    public record AdaptedStep(String stepId, int order, String action, String actionKey, String details,
                              List<String> ingredientIds,
                              List<String> toolIds, List<String> visualKeys, Integer durationSeconds,
                              String timerStartCondition, HeatLevel heatLevel, String heatRaw,
                              String deviceSettingRaw, String heatVisualKey, String completionCheck, List<SafetyNote> safetyNotes) {

        /** Same step with other wording; time, heat, check and safety notes stay as they are. */
        AdaptedStep withText(String newAction, String newDetails) {
            return new AdaptedStep(stepId, order, newAction, actionKey, newDetails, ingredientIds, toolIds, visualKeys,
                    durationSeconds, timerStartCondition, heatLevel, heatRaw, deviceSettingRaw, heatVisualKey,
                    completionCheck, safetyNotes);
        }
    }

    public record SafetyNote(String message, String detail) {
    }
}
