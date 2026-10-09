package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationRequest.Profile;
import com.example.aicook.adaptation.AdaptationRequest.SubstituteAnswer;
import com.example.aicook.common.ApiException;
import com.example.aicook.recipe.Recipe;
import com.example.aicook.recipe.Recipe.SkillLevel;
import com.example.aicook.reference.ReferenceData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A validated adaptation request. Every id has been checked against the recipe and the
 * reference data, so the planners can trust it.
 *
 * @param answers "ingredientId/substituteId" -> whether the user has the substitute
 */
record Conditions(SkillLevel skillLevel, Set<String> toolIds, String heatSource, Set<String> allergenCodes,
                  Set<String> missingIngredientIds, Map<String, Boolean> answers) {

    private static final String GAS = "gas_stove";
    private static final String INDUCTION = "induction";

    static Conditions from(AdaptationRequest request, Recipe recipe, ReferenceData reference) {
        Profile profile = request.profile();
        if (profile == null || profile.skillLevel() == null || profile.toolVariantIds() == null) {
            throw ApiException.badRequest("INVALID_PROFILE", "실력과 도구 정보가 필요해요.");
        }
        // TODO(spec): serving changes need verified per-recipe rules (spec 4.5); none exist yet.
        if (request.servings() == null || request.servings().doubleValue() != recipe.baseServings()) {
            throw ApiException.badRequest("UNSUPPORTED_SERVINGS", "이 레시피는 인분을 바꿔서 안내할 수 없어요.");
        }
        Set<String> toolIds = toSet(profile.toolVariantIds());
        requireKnown(toolIds, id -> reference.tool(id).isPresent(), "UNKNOWN_TOOL", "알 수 없는 도구가 있어요.");
        Set<String> allergenCodes = toSet(orEmpty(profile.allergenCodes()));
        requireKnown(allergenCodes, reference::isKnownAllergen, "UNKNOWN_ALLERGEN", "알 수 없는 알레르기 항목이 있어요.");

        Set<String> recipeIngredientIds = recipe.ingredients().stream().map(Recipe.Ingredient::id).collect(Collectors.toSet());
        Set<String> missing = toSet(orEmpty(request.missingIngredientIds()));
        requireKnown(missing, recipeIngredientIds::contains, "UNKNOWN_INGREDIENT", "레시피에 없는 재료가 있어요.");

        return new Conditions(profile.skillLevel(), toolIds, resolveHeatSource(toolIds, profile.defaultHeatSource()),
                allergenCodes, missing, toAnswers(orEmpty(request.substituteAnswers()), recipeIngredientIds));
    }

    Boolean answerFor(String ingredientId, String substituteId) {
        return answers.get(answerKey(ingredientId, substituteId));
    }

    private static Map<String, Boolean> toAnswers(List<SubstituteAnswer> answers, Set<String> recipeIngredientIds) {
        Map<String, Boolean> result = new HashMap<>();
        for (SubstituteAnswer answer : answers) {
            boolean valid = answer != null && answer.hasIt() != null && answer.substituteId() != null
                    && recipeIngredientIds.contains(answer.ingredientId());
            if (!valid) {
                throw ApiException.badRequest("INVALID_ANSWER", "대체 재료 응답이 올바르지 않아요.");
            }
            result.put(answerKey(answer.ingredientId(), answer.substituteId()), answer.hasIt());
        }
        return result;
    }

    private static String answerKey(String ingredientId, String substituteId) {
        return ingredientId + "/" + substituteId;
    }

    /** The stove whose heat pictures are shown; null when the user owns neither. */
    private static String resolveHeatSource(Set<String> toolIds, String preferred) {
        boolean hasGas = toolIds.contains(GAS);
        boolean hasInduction = toolIds.contains(INDUCTION);
        if (hasGas && hasInduction) {
            return INDUCTION.equals(preferred) ? INDUCTION : GAS;
        }
        if (hasGas) {
            return GAS;
        }
        return hasInduction ? INDUCTION : null;
    }

    private static Set<String> toSet(List<String> values) {
        if (values.stream().anyMatch(Objects::isNull)) {
            throw ApiException.badRequest("INVALID_BODY", "요청 형식이 올바르지 않아요.");
        }
        return Set.copyOf(values);
    }

    private static void requireKnown(Set<String> ids, Predicate<String> known, String code, String message) {
        if (!ids.stream().allMatch(known)) {
            throw ApiException.badRequest(code, message);
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    // Allergy codes must not reach logs through an accidental toString().
    @Override
    public String toString() {
        return "Conditions[skillLevel=" + skillLevel + ", toolIds=" + toolIds + ", heatSource=" + heatSource
                + ", missingIngredientIds=" + missingIngredientIds + ", allergenCodes=<hidden>]";
    }
}
