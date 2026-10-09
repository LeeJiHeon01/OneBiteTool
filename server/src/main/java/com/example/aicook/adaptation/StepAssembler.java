package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationResponse.AdaptedStep;
import com.example.aicook.adaptation.AdaptationResponse.SafetyNote;
import com.example.aicook.adaptation.IngredientPlanner.Kind;
import com.example.aicook.recipe.Recipe;
import com.example.aicook.recipe.Recipe.SkillLevel;
import com.example.aicook.recipe.ToolNeeds;
import com.example.aicook.reference.ReferenceData;
import com.example.aicook.reference.Rules.SafetyRule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the cooking steps for a plan. Time, heat and the completion check are copied from
 * the recipe unchanged; skill level only decides how much explanation is added.
 */
class StepAssembler {

    private static final Map<String, String> HEAT_KEY_BY_SOURCE = Map.of("gas_stove", "gas", "induction", "induction");
    /** Particle pairs that follow a noun: the one used after a final consonant, then the other. */
    private static final String[][] PARTICLES = {{"을", "를"}, {"은", "는"}, {"이", "가"}, {"과", "와"}};

    record Result(List<AdaptedStep> steps, List<String> safetySummary) {
    }

    private final ReferenceData reference;

    StepAssembler(ReferenceData reference) {
        this.reference = reference;
    }

    Result assemble(Recipe recipe, Conditions conditions, IngredientPlanner.Plan ingredients, ToolPlanner.Plan tools) {
        List<AdaptedStep> steps = new ArrayList<>();
        Set<String> summary = new LinkedHashSet<>();

        for (Recipe.Step step : recipe.steps()) {
            List<String> keptIngredientIds = step.ingredientIds().stream()
                    .filter(id -> ingredients.of(id).kind() != Kind.OMIT)
                    .toList();
            // A step whose ingredients were all omitted has nothing left to do.
            if (!step.ingredientIds().isEmpty() && keptIngredientIds.isEmpty()) {
                continue;
            }
            List<String> recipeToolIds = ToolNeeds.stepTools(step, reference);
            List<String> toolIds = recipeToolIds.stream().map(tools::toolFor).toList();
            List<SafetyRule> rules = matchingRules(recipe, step, keptIngredientIds, toolIds, ingredients);
            rules.stream().filter(rule -> rule.placement().onSummary()).forEach(rule -> summary.add(rule.message()));

            List<SafetyNote> notes = new ArrayList<>(toNotes(rules, conditions.skillLevel()));
            notes.addAll(substitutionNotes(recipeToolIds, tools));
            // details stays empty here; StepRewriter adds the per-skill explanation.
            steps.add(new AdaptedStep(step.id(), steps.size() + 1, actionOf(recipe, step, ingredients),
                    step.actionKey(), null,
                    keptIngredientIds, toolIds, List.of(), step.durationSeconds(), step.timerStartCondition(),
                    step.heatLevel(), step.heatRaw(), step.deviceSettingRaw(),
                    heatVisualKey(step, conditions.heatSource()),
                    step.completionCheck(), notes));
        }
        return new Result(steps, List.copyOf(summary));
    }

    /** Replaces the name of a substituted ingredient in the step text and fixes the particle after it. */
    private String actionOf(Recipe recipe, Recipe.Step step, IngredientPlanner.Plan ingredients) {
        String action = step.action();
        for (String ingredientId : step.ingredientIds()) {
            IngredientPlanner.Resolution resolution = ingredients.of(ingredientId);
            if (resolution.kind() != Kind.SUBSTITUTE) {
                continue;
            }
            String original = ingredientOf(recipe, ingredientId).name();
            String substitute = reference.ingredient(resolution.substituteCode()).orElseThrow().name();
            action = replaceName(action, original, substitute);
        }
        return action;
    }

    static String replaceName(String text, String original, String substitute) {
        boolean originalEndsInConsonant = endsInConsonant(original);
        boolean substituteEndsInConsonant = endsInConsonant(substitute);
        if (originalEndsInConsonant != substituteEndsInConsonant) {
            for (String[] pair : PARTICLES) {
                String from = originalEndsInConsonant ? pair[0] : pair[1];
                String to = substituteEndsInConsonant ? pair[0] : pair[1];
                text = text.replace(original + from, substitute + to);
            }
        }
        return text.replace(original, substitute);
    }

    private static boolean endsInConsonant(String word) {
        char last = word.charAt(word.length() - 1);
        return last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
    }

    /** The picture key always comes from the step's own heat level, so picture and label agree. */
    private String heatVisualKey(Recipe.Step step, String heatSource) {
        if (step.heatLevel() == null || heatSource == null) {
            return null;
        }
        return "heat." + HEAT_KEY_BY_SOURCE.get(heatSource) + "." + step.heatLevel().name();
    }

    private List<SafetyRule> matchingRules(Recipe recipe, Recipe.Step step, List<String> keptIngredientIds,
                                           List<String> toolIds, IngredientPlanner.Plan ingredients) {
        Set<String> dictionaryCodes = keptIngredientIds.stream()
                .map(id -> effectiveDictionaryCode(recipe, id, ingredients))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return reference.safetyRules().stream()
                .filter(rule -> triggers(rule, step, toolIds, dictionaryCodes))
                .toList();
    }

    private boolean triggers(SafetyRule rule, Recipe.Step step, List<String> toolIds, Set<String> dictionaryCodes) {
        String value = rule.triggerValue();
        return switch (rule.triggerType()) {
            case TOOL -> toolIds.contains(value);
            case ACTION -> value.equals(step.actionKey());
            case INGREDIENT -> dictionaryCodes.contains(value);
            case HEAT -> step.heatLevel() != null && value.equals(step.heatLevel().name());
        };
    }

    /** Dictionary code of what is actually used: the substitute when one was chosen. May be null. */
    static String effectiveDictionaryCode(Recipe recipe, String ingredientId, IngredientPlanner.Plan ingredients) {
        IngredientPlanner.Resolution resolution = ingredients.of(ingredientId);
        if (resolution.kind() == Kind.SUBSTITUTE) {
            return resolution.substituteCode();
        }
        return ingredientOf(recipe, ingredientId).dictionaryCode();
    }

    private static Recipe.Ingredient ingredientOf(Recipe recipe, String ingredientId) {
        return recipe.ingredients().stream()
                .filter(ingredient -> ingredient.id().equals(ingredientId))
                .findFirst()
                .orElseThrow();
    }

    // Safety messages are shown at every skill level; only the extra explanation is LOW-only.
    private List<SafetyNote> toNotes(List<SafetyRule> rules, SkillLevel skillLevel) {
        return rules.stream()
                .filter(rule -> rule.placement().onStep())
                .map(rule -> new SafetyNote(rule.message(), skillLevel == SkillLevel.LOW ? rule.detailForLow() : null))
                .toList();
    }

    /** Extra caution written on a tool substitution rule, shown on the steps that use the substitute. */
    private List<SafetyNote> substitutionNotes(List<String> recipeToolIds, ToolPlanner.Plan tools) {
        return recipeToolIds.stream()
                .map(toolId -> tools.substitutions().get(toolId))
                .filter(rule -> rule != null && rule.extraSafetyNote() != null)
                .map(rule -> new SafetyNote(rule.extraSafetyNote(), null))
                .toList();
    }
}
