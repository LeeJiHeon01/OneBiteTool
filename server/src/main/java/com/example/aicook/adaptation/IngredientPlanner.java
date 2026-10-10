package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationResponse.Change;
import com.example.aicook.adaptation.AdaptationResponse.ChangeType;
import com.example.aicook.adaptation.AdaptationResponse.ItemKind;
import com.example.aicook.adaptation.AdaptationResponse.PendingQuestion;
import com.example.aicook.adaptation.AdaptationResponse.RequiredItem;
import com.example.aicook.common.Json;
import com.example.aicook.common.Korean;
import com.example.aicook.recipe.Recipe;
import com.example.aicook.reference.ReferenceData;
import com.example.aicook.reference.Rules.Ingredient;
import com.example.aicook.reference.Rules.IngredientRule;
import com.example.aicook.reference.Rules.IngredientRuleType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides what happens to each ingredient the user does not have. Verified rules stored in
 * adaptation_rule come first. Without a rule, an ingredient the dish is built around is
 * required; any other one is left out at the user's choice and reported as unverified.
 */
class IngredientPlanner {

    private static final String NAMED_REASON = "요리 이름에 들어간 재료라 꼭 필요해요";
    private static final String MAIN_REASON = "이 요리의 중심 재료라 꼭 필요해요";
    private static final String UNVERIFIED_IMPACT = "빼도 되는지 확인된 규칙이 없어요. 맛이나 식감이 달라질 수 있어요.";
    /** Categories that can be the centre of a dish (data/seed/adaptation-rules.json). */
    private static final List<String> MAIN_CATEGORIES =
            Json.readResource("seed/adaptation-rules.json", MainCategories.class).mainIngredientCategories();

    record MainCategories(List<String> mainIngredientCategories) {
    }

    enum Kind {
        KEEP,
        OMIT,
        SUBSTITUTE,
        REQUIRED,
        ASK
    }

    /** substituteCode is the dictionary code of the replacement, set for SUBSTITUTE and ASK. */
    record Resolution(Kind kind, String substituteCode) {

        static final Resolution KEEP = new Resolution(Kind.KEEP, null);
    }

    record Plan(Map<String, Resolution> resolutions, List<Change> changes, List<PendingQuestion> pendingQuestions,
                List<RequiredItem> requiredItems) {

        Resolution of(String ingredientId) {
            return resolutions.get(ingredientId);
        }
    }

    private final ReferenceData reference;

    IngredientPlanner(ReferenceData reference) {
        this.reference = reference;
    }

    Plan plan(Recipe recipe, Conditions conditions) {
        Map<String, Resolution> resolutions = new LinkedHashMap<>();
        List<Change> changes = new ArrayList<>();
        List<PendingQuestion> questions = new ArrayList<>();
        List<RequiredItem> required = new ArrayList<>();

        for (Recipe.Ingredient ingredient : recipe.ingredients()) {
            if (!conditions.missingIngredientIds().contains(ingredient.id())) {
                resolutions.put(ingredient.id(), Resolution.KEEP);
                continue;
            }
            List<IngredientRule> rules = reference.ingredientRulesFor(recipe.id(), ingredient.dictionaryCode());
            Outcome outcome = resolve(recipe, ingredient, rules, conditions);
            resolutions.put(ingredient.id(), outcome.resolution());
            collect(ingredient, outcome, changes, questions, required);
        }
        return new Plan(resolutions, changes, questions, required);
    }

    /** requiredReason is set when the ingredient cannot be left out. */
    private record Outcome(Resolution resolution, IngredientRule rule, String requiredReason) {

        Outcome(Resolution resolution, IngredientRule rule) {
            this(resolution, rule, null);
        }
    }

    /** Tries the rules in priority order; a declined substitute falls through to the next rule. */
    private Outcome resolve(Recipe recipe, Recipe.Ingredient ingredient, List<IngredientRule> rules,
                            Conditions conditions) {
        for (IngredientRule rule : rules) {
            if (rule.type() == IngredientRuleType.OMIT) {
                return new Outcome(new Resolution(Kind.OMIT, null), rule);
            }
            if (!mayOffer(recipe, rule.substituteCode(), conditions)) {
                continue;
            }
            Boolean hasSubstitute = conditions.answerFor(ingredient.id(), rule.substituteCode());
            if (hasSubstitute == null) {
                return new Outcome(new Resolution(Kind.ASK, rule.substituteCode()), rule);
            }
            if (hasSubstitute) {
                return new Outcome(new Resolution(Kind.SUBSTITUTE, rule.substituteCode()), rule);
            }
        }
        String requiredReason = requiredReason(recipe, ingredient);
        if (requiredReason != null) {
            return new Outcome(new Resolution(Kind.REQUIRED, null), null, requiredReason);
        }
        // No rule and not central to the dish: the user cooks without it, at their own choice.
        return new Outcome(new Resolution(Kind.OMIT, null), null);
    }

    /**
     * A substitute is never offered when it would bring in an allergen the user avoids, when
     * its allergens are unchecked for a user with allergies, or when the recipe already uses it.
     */
    private boolean mayOffer(Recipe recipe, String substituteCode, Conditions conditions) {
        boolean alreadyInRecipe = recipe.ingredients().stream()
                .anyMatch(other -> substituteCode.equals(other.dictionaryCode()));
        if (alreadyInRecipe) {
            return false;
        }
        if (conditions.allergenCodes().isEmpty()) {
            return true;
        }
        boolean containsAllergen = reference.allergensOf(substituteCode).orElse(List.of()).stream()
                .anyMatch(allergen -> conditions.allergenCodes().contains(allergen.code()));
        return reference.isAllergenReviewed(substituteCode) && !containsAllergen;
    }

    /**
     * Why an ingredient without a rule cannot be left out, or null when it can.
     * An ingredient named in the dish name is required whatever its category (고추장불고기의 고추장).
     * When no main-category ingredient is named, every main-category ingredient is required,
     * because the server cannot tell which one the dish is built around.
     */
    private String requiredReason(Recipe recipe, Recipe.Ingredient ingredient) {
        if (isNamedInDish(recipe, ingredient)) {
            return NAMED_REASON;
        }
        boolean mainIngredientNamed = recipe.ingredients().stream()
                .anyMatch(other -> isMain(other) && isNamedInDish(recipe, other));
        return isMain(ingredient) && !mainIngredientNamed ? MAIN_REASON : null;
    }

    private boolean isMain(Recipe.Ingredient ingredient) {
        return reference.ingredient(ingredient.dictionaryCode())
                .map(entry -> MAIN_CATEGORIES.contains(entry.category()))
                .orElse(false);
    }

    /**
     * True when the dish name contains one of the ingredient's names ("제육볶음" and 돼지고기's
     * alias 제육). A short name inside a longer one of another ingredient does not count:
     * 김 is not named by "김치전" when the recipe also uses 김치.
     */
    private boolean isNamedInDish(Recipe recipe, Recipe.Ingredient ingredient) {
        String dish = compact(recipe.name());
        List<String> otherNames = recipe.ingredients().stream()
                .filter(other -> !other.id().equals(ingredient.id()))
                .flatMap(other -> namesOf(other).stream())
                .filter(dish::contains)
                .toList();
        return namesOf(ingredient).stream()
                .filter(dish::contains)
                .anyMatch(name -> otherNames.stream()
                        .noneMatch(other -> other.length() > name.length() && other.contains(name)));
    }

    /** The name written in the recipe plus the dictionary name and aliases, without whitespace. */
    private List<String> namesOf(Recipe.Ingredient ingredient) {
        List<String> names = new ArrayList<>();
        names.add(compact(ingredient.name()));
        reference.ingredient(ingredient.dictionaryCode()).ifPresent(entry -> {
            names.add(compact(entry.name()));
            entry.aliases().forEach(alias -> names.add(compact(alias)));
        });
        return names.stream().filter(name -> !name.isEmpty()).distinct().toList();
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    private void collect(Recipe.Ingredient ingredient, Outcome outcome, List<Change> changes,
                         List<PendingQuestion> questions, List<RequiredItem> required) {
        IngredientRule rule = outcome.rule();
        switch (outcome.resolution().kind()) {
            case OMIT -> changes.add(rule == null
                    // Not a verified omission: the app says so instead of presenting it as safe to skip.
                    ? new Change(ChangeType.OMIT_BY_USER, ingredient.id(),
                            ingredient.name() + " 없이 만들어요.", UNVERIFIED_IMPACT)
                    : new Change(ChangeType.OMIT, ingredient.id(), omitText(ingredient), rule.impact()));
            case SUBSTITUTE -> changes.add(new Change(ChangeType.SUBSTITUTE, ingredient.id(),
                    Korean.topic(ingredient.name()) + " " + Korean.with(nameOf(rule.substituteCode())) + " 바꿔요.",
                    rule.impact()));
            case ASK -> questions.add(
                    new PendingQuestion(ingredient.id(), rule.substituteCode(), nameOf(rule.substituteCode())));
            case REQUIRED -> required.add(
                    new RequiredItem(ItemKind.INGREDIENT, ingredient.id(), ingredient.name(),
                            outcome.requiredReason()));
            case KEEP -> {
            }
        }
    }

    private String omitText(Recipe.Ingredient ingredient) {
        boolean garnish = reference.ingredient(ingredient.dictionaryCode()).map(Ingredient::garnish).orElse(false);
        return Korean.topic(ingredient.name()) + (garnish ? " 장식용이라 생략해요." : " 없어서 생략해요.");
    }

    private String nameOf(String dictionaryCode) {
        return reference.ingredient(dictionaryCode).orElseThrow().name();
    }
}
