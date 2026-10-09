package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationResponse.Change;
import com.example.aicook.adaptation.AdaptationResponse.ChangeType;
import com.example.aicook.adaptation.AdaptationResponse.ItemKind;
import com.example.aicook.adaptation.AdaptationResponse.PendingQuestion;
import com.example.aicook.adaptation.AdaptationResponse.RequiredItem;
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
 * Decides what happens to each ingredient the user does not have. Only rules stored in
 * adaptation_rule are applied; without a rule the ingredient is required and the recipe
 * cannot be cooked.
 */
class IngredientPlanner {

    private static final String NO_RULE_REASON = "꼭 필요한 재료예요";

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
            Outcome outcome = resolve(ingredient, rules, conditions);
            resolutions.put(ingredient.id(), outcome.resolution());
            collect(ingredient, outcome, changes, questions, required);
        }
        return new Plan(resolutions, changes, questions, required);
    }

    private record Outcome(Resolution resolution, IngredientRule rule) {
    }

    /** Tries the rules in priority order; a declined substitute falls through to the next rule. */
    private Outcome resolve(Recipe.Ingredient ingredient, List<IngredientRule> rules, Conditions conditions) {
        for (IngredientRule rule : rules) {
            if (rule.type() == IngredientRuleType.OMIT) {
                return new Outcome(new Resolution(Kind.OMIT, null), rule);
            }
            Boolean hasSubstitute = conditions.answerFor(ingredient.id(), rule.substituteCode());
            if (hasSubstitute == null) {
                return new Outcome(new Resolution(Kind.ASK, rule.substituteCode()), rule);
            }
            if (hasSubstitute) {
                return new Outcome(new Resolution(Kind.SUBSTITUTE, rule.substituteCode()), rule);
            }
        }
        return new Outcome(new Resolution(Kind.REQUIRED, null), null);
    }

    private void collect(Recipe.Ingredient ingredient, Outcome outcome, List<Change> changes,
                         List<PendingQuestion> questions, List<RequiredItem> required) {
        IngredientRule rule = outcome.rule();
        switch (outcome.resolution().kind()) {
            case OMIT -> changes.add(
                    new Change(ChangeType.OMIT, ingredient.id(), omitText(ingredient), rule.impact()));
            case SUBSTITUTE -> changes.add(new Change(ChangeType.SUBSTITUTE, ingredient.id(),
                    Korean.topic(ingredient.name()) + " " + Korean.with(nameOf(rule.substituteCode())) + " 바꿔요.",
                    rule.impact()));
            case ASK -> questions.add(
                    new PendingQuestion(ingredient.id(), rule.substituteCode(), nameOf(rule.substituteCode())));
            case REQUIRED -> required.add(
                    new RequiredItem(ItemKind.INGREDIENT, ingredient.id(), ingredient.name(), NO_RULE_REASON));
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
