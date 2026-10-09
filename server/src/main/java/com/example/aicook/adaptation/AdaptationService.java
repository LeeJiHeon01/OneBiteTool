package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationResponse.AdaptedIngredient;
import com.example.aicook.adaptation.AdaptationResponse.AdaptedStep;
import com.example.aicook.adaptation.AdaptationResponse.AllergyWarning;
import com.example.aicook.adaptation.AdaptationResponse.Change;
import com.example.aicook.adaptation.AdaptationResponse.Display;
import com.example.aicook.adaptation.AdaptationResponse.Original;
import com.example.aicook.adaptation.AdaptationResponse.RequiredItem;
import com.example.aicook.adaptation.AdaptationResponse.Status;
import com.example.aicook.adaptation.IngredientPlanner.Kind;
import com.example.aicook.measure.IngredientForm;
import com.example.aicook.measure.MeasureConverter;
import com.example.aicook.measure.MeasureResult;
import com.example.aicook.measure.ParsedAmount;
import com.example.aicook.measure.ParsedAmount.Amount;
import com.example.aicook.recipe.Recipe;
import com.example.aicook.recipe.Recipe.SkillLevel;
import com.example.aicook.recipe.Recipe.UnitKind;
import com.example.aicook.recipe.RecipeService;
import com.example.aicook.reference.ReferenceData;
import com.example.aicook.reference.Rules.AllergenRef;
import com.example.aicook.reference.Rules.Ingredient;
import com.example.aicook.reference.Sensitivity;
import com.example.aicook.reference.Tools.Variant;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Adapts a recipe to the user's tools, missing ingredients and allergies using only
 * reference data and server code. Allergy codes are used in memory and never stored or logged.
 */
@Service
public class AdaptationService {

    /** Dictionary code of water, whose sensitivity depends on what it is mixed with. */
    private static final String WATER_CODE = "water";
    private static final String NO_AMOUNT_TEXT = "레시피에 양이 적혀 있지 않아요";

    private final RecipeService recipeService;
    private final ReferenceData reference;
    private final MeasureConverter measureConverter;
    private final IngredientPlanner ingredientPlanner;
    private final ToolPlanner toolPlanner;
    private final StepAssembler stepAssembler;
    private final StepRewriter stepRewriter;

    public AdaptationService(RecipeService recipeService, ReferenceData reference,
                             MeasureConverter measureConverter, StepRewriter stepRewriter) {
        this.stepRewriter = stepRewriter;
        this.recipeService = recipeService;
        this.reference = reference;
        this.measureConverter = measureConverter;
        this.ingredientPlanner = new IngredientPlanner(reference);
        this.toolPlanner = new ToolPlanner(reference);
        this.stepAssembler = new StepAssembler(reference);
    }

    public AdaptationResponse adapt(AdaptationRequest request) {
        Recipe recipe = recipeService.find(request.recipeId());
        Conditions conditions = Conditions.from(request, recipe, reference);
        IngredientPlanner.Plan ingredientPlan = ingredientPlanner.plan(recipe, conditions);
        ToolPlanner.Plan toolPlan = toolPlanner.plan(recipe, conditions.toolIds());

        List<RequiredItem> requiredItems = concat(ingredientPlan.requiredItems(), toolPlan.requiredItems());
        Status status = statusOf(requiredItems, ingredientPlan);
        if (status != Status.COOKABLE) {
            // Nothing is confirmed yet, so no amounts or steps are returned.
            return new AdaptationResponse(newId(), status, RecipeService.badgeOf(recipe), recipe.sourceUrl(),
                    List.of(), List.of(), ingredientPlan.pendingQuestions(), List.of(), requiredItems, List.of(),
                    List.of(), List.of());
        }

        StepAssembler.Result steps = stepAssembler.assemble(recipe, conditions, ingredientPlan, toolPlan);
        List<Change> changes = concat(ingredientPlan.changes(), toolPlan.changes());
        List<AdaptedIngredient> ingredients = adaptIngredients(recipe, conditions, ingredientPlan);
        // The AI only rewords the finished steps; amounts, time, heat and safety notes are final here.
        List<AdaptedStep> rewordedSteps = stepRewriter.rewrite(conditions.skillLevel(), steps.steps(), ingredients,
                changes);
        return new AdaptationResponse(newId(), status, RecipeService.badgeOf(recipe), recipe.sourceUrl(),
                allergyWarnings(recipe, conditions, ingredientPlan),
                allergyCheckNeeded(recipe, conditions, ingredientPlan), List.of(), changes, List.of(),
                steps.safetySummary(), ingredients, rewordedSteps);
    }

    private Status statusOf(List<RequiredItem> requiredItems, IngredientPlanner.Plan plan) {
        if (!requiredItems.isEmpty()) {
            return Status.NOT_COOKABLE;
        }
        return plan.pendingQuestions().isEmpty() ? Status.COOKABLE : Status.NEEDS_CONFIRMATION;
    }

    private List<AdaptedIngredient> adaptIngredients(Recipe recipe, Conditions conditions,
                                                     IngredientPlanner.Plan plan) {
        // Sorted so equally good tools are always picked in the same order.
        List<Variant> ownedTools = conditions.toolIds().stream()
                .sorted()
                .map(id -> reference.tool(id).orElseThrow())
                .toList();
        return usedIngredients(recipe, plan)
                .map(ingredient -> adaptIngredient(recipe, ingredient, plan, ownedTools, conditions.skillLevel()))
                .toList();
    }

    private AdaptedIngredient adaptIngredient(Recipe recipe, Recipe.Ingredient ingredient,
                                              IngredientPlanner.Plan plan, List<Variant> ownedTools,
                                              SkillLevel skillLevel) {
        String dictionaryCode = StepAssembler.effectiveDictionaryCode(recipe, ingredient.id(), plan);
        Optional<Ingredient> entry = reference.ingredient(dictionaryCode);
        boolean substituted = plan.of(ingredient.id()).kind() == Kind.SUBSTITUTE;
        String name = substituted ? entry.orElseThrow().name() : ingredient.name();
        Sensitivity sensitivity = sensitivityIn(recipe, ingredient, plan, dictionaryCode);
        IngredientForm form = IngredientForm.of(entry.map(Ingredient::form).orElse(null));
        // Beginners get a weight as a tool amount with the grams in brackets; others see the grams.
        boolean weightAsTool = skillLevel == SkillLevel.LOW;

        MeasureResult measure = measureConverter.convert(new MeasureConverter.Request(toAmount(ingredient),
                sensitivity, form, weightAsTool, ownedTools, reference.weightReferencesOf(dictionaryCode)));
        // The source amount is always returned next to the converted one.
        Original original = new Original(ingredient.quantity(), ingredient.unit(), ingredient.amountText());
        // A recipe that states no amount gets a plain notice, never a made-up amount.
        String text = measure.text() == null || measure.text().isBlank() ? NO_AMOUNT_TEXT : measure.text();
        Display display = new Display(measure.toolVariantId(), measure.fillLevelKey(), measure.count(),
                text, measure.tasteAdjust());
        return new AdaptedIngredient(ingredient.id(), name, original, display, measure.status(), sensitivity,
                reference.allergenProductVaries(dictionaryCode));
    }

    /**
     * Water is NORMAL by itself, but in a step that also uses an ingredient whose proportion
     * matters (rice, flour, starch) the amount of water decides the result, so it is held to
     * the same tighter tolerance.
     */
    // TODO(spec): steaming (계란찜의 물) has no ratio-sensitive partner ingredient and is not covered.
    private Sensitivity sensitivityIn(Recipe recipe, Recipe.Ingredient ingredient, IngredientPlanner.Plan plan,
                                      String dictionaryCode) {
        Sensitivity own = reference.sensitivityOf(dictionaryCode);
        if (!WATER_CODE.equals(dictionaryCode)) {
            return own;
        }
        boolean withRatioIngredient = recipe.steps().stream()
                .filter(step -> step.ingredientIds().contains(ingredient.id()))
                .flatMap(step -> step.ingredientIds().stream())
                .filter(id -> plan.of(id).kind() != Kind.OMIT)
                .map(id -> StepAssembler.effectiveDictionaryCode(recipe, id, plan))
                .anyMatch(code -> code != null && reference.sensitivityOf(code) == Sensitivity.RATIO);
        return withRatioIngredient ? Sensitivity.RATIO : own;
    }

    /** Rebuilds the parsed amount from the stored columns; a VAGUE amount never has a number. */
    private ParsedAmount toAmount(Recipe.Ingredient ingredient) {
        boolean vague = ingredient.unitKind() == UnitKind.VAGUE || ingredient.quantity() == null;
        Amount primary = vague ? null : new Amount(ingredient.quantity(), ingredient.unit());
        Amount alternative = ingredient.altQuantity() == null
                ? null : new Amount(ingredient.altQuantity(), ingredient.altUnit());
        return new ParsedAmount(ingredient.amountText(), vague, primary, alternative);
    }

    /** Warnings come from the ingredient-allergen mapping only, hidden components included. */
    private List<AllergyWarning> allergyWarnings(Recipe recipe, Conditions conditions,
                                                 IngredientPlanner.Plan plan) {
        List<AllergyWarning> warnings = new ArrayList<>();
        usedIngredients(recipe, plan).forEach(ingredient ->
                allergensOf(recipe, ingredient, plan).orElse(List.of()).stream()
                        .filter(allergen -> conditions.allergenCodes().contains(allergen.code()))
                        .forEach(allergen -> warnings.add(
                                new AllergyWarning(allergen.code(), ingredient.id(), allergen.hidden()))));
        return warnings;
    }

    /** Ingredients without a reviewed allergen mapping are "needs checking" for anyone with an allergy. */
    private List<String> allergyCheckNeeded(Recipe recipe, Conditions conditions, IngredientPlanner.Plan plan) {
        if (conditions.allergenCodes().isEmpty()) {
            return List.of();
        }
        return usedIngredients(recipe, plan)
                .filter(ingredient -> !reference.isAllergenReviewed(
                        StepAssembler.effectiveDictionaryCode(recipe, ingredient.id(), plan)))
                .map(Recipe.Ingredient::id)
                .toList();
    }

    private Optional<List<AllergenRef>> allergensOf(Recipe recipe, Recipe.Ingredient ingredient,
                                                    IngredientPlanner.Plan plan) {
        return reference.allergensOf(StepAssembler.effectiveDictionaryCode(recipe, ingredient.id(), plan));
    }

    private Stream<Recipe.Ingredient> usedIngredients(Recipe recipe, IngredientPlanner.Plan plan) {
        return recipe.ingredients().stream().filter(ingredient -> plan.of(ingredient.id()).kind() != Kind.OMIT);
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
