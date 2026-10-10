package com.example.aicook.recipeimport;

import com.example.aicook.recipe.Recipe.HeatLevel;

import java.util.List;

/** Answers of the search and extract prompts (prompts/schemas/recipe-search.json, recipe-extract.json). */
final class Extract {

    private Extract() {
    }

    record SearchAnswer(List<Candidate> candidates) {
    }

    record Candidate(String url, String title, String reason, boolean needsOtherTools) {
    }

    /** evidence fields quote the page; they are compared with the page text and then dropped. */
    /** sourceStepCount is how many steps the page itself has; null when it cannot be counted. */
    record Page(boolean isRecipe, String name, Double servings, String servingsEvidence, Integer totalMinutes,
                Integer sourceStepCount, List<Ingredient> ingredients, List<Step> steps) {

        Page(boolean isRecipe, String name, Double servings, String servingsEvidence, Integer totalMinutes,
             List<Ingredient> ingredients, List<Step> steps) {
            this(isRecipe, name, servings, servingsEvidence, totalMinutes, null, ingredients, steps);
        }
    }

    record Ingredient(String name, String amountText, String section, String evidence) {
    }

    record Step(String action, String actionKey, List<String> ingredientNames, List<String> toolCodes,
                Integer durationSeconds, String durationEvidence, String timerStartCondition, HeatLevel heatLevel,
                String heatRaw, String deviceSettingRaw, String completionCheck, String evidence) {
    }
}
