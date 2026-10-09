package com.example.aicook.reference;

import com.example.aicook.reference.Rules.AllergenRef;
import com.example.aicook.reference.Rules.Ingredient;
import com.example.aicook.reference.Rules.IngredientRule;
import com.example.aicook.reference.Rules.IngredientRuleType;
import com.example.aicook.reference.Rules.ReviewLevel;
import com.example.aicook.reference.Rules.SafetyRule;
import com.example.aicook.reference.Rules.ToolRule;
import com.example.aicook.reference.Rules.WeightReference;
import com.example.aicook.reference.Tools.Category;
import com.example.aicook.reference.Tools.Variant;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reference data read from the database once at startup (after the seed files are synced).
 * All conversion, allergy, safety and substitution decisions are made from this data only.
 */
public class ReferenceData {

    private final List<Category> toolCategories;
    private final Units units;
    private final Map<String, Variant> toolsById;
    private final Map<String, Ingredient> ingredientsByCode;
    private final Set<String> allergenCodes;
    private final Map<String, List<AllergenRef>> allergensByIngredient;
    private final List<SafetyRule> safetyRules;
    private final List<IngredientRule> ingredientRules;
    private final List<ToolRule> toolRules;
    private final List<WeightReference> weightReferences;

    public ReferenceData(List<Category> toolCategories, Units units, List<Ingredient> ingredients,
                         Set<String> allergenCodes, Map<String, List<AllergenRef>> allergensByIngredient,
                         List<SafetyRule> safetyRules, List<IngredientRule> ingredientRules, List<ToolRule> toolRules,
                         List<WeightReference> weightReferences) {
        this.toolCategories = toolCategories;
        this.units = units;
        this.toolsById = toolCategories.stream()
                .flatMap(category -> category.variants().stream())
                .collect(Collectors.toMap(Variant::id, Function.identity()));
        this.ingredientsByCode = ingredients.stream().collect(Collectors.toMap(Ingredient::code, Function.identity()));
        this.allergenCodes = allergenCodes;
        this.allergensByIngredient = allergensByIngredient;
        this.safetyRules = safetyRules;
        this.ingredientRules = ingredientRules;
        this.toolRules = toolRules;
        this.weightReferences = weightReferences;
    }

    public List<Category> toolCategories() {
        return toolCategories;
    }

    public List<Variant> tools() {
        return toolCategories.stream().flatMap(category -> category.variants().stream()).toList();
    }

    /**
     * Names used to check that AI-written text mentions nothing outside a step. One-letter
     * ingredient names (밥, 물, 파) are left out: they also occur inside ordinary words.
     */
    public Set<String> ingredientAndToolNames() {
        Set<String> names = tools().stream().map(Variant::name).collect(Collectors.toSet());
        ingredientsByCode.values().stream()
                .map(Ingredient::name)
                .filter(name -> name.length() > 1)
                .forEach(names::add);
        return names;
    }

    public Units units() {
        return units;
    }

    public Optional<Variant> tool(String id) {
        return Optional.ofNullable(toolsById.get(id));
    }

    /** Empty for a null code (the recipe ingredient did not match the dictionary). */
    public Optional<Ingredient> ingredient(String code) {
        return Optional.ofNullable(code).map(ingredientsByCode::get);
    }

    public boolean isKnownAllergen(String code) {
        return allergenCodes.contains(code);
    }

    /**
     * Known allergens of a dictionary ingredient, hidden components included; used for warnings.
     * An empty list is NOT a statement that the ingredient is safe: see {@link #isAllergenReviewed}.
     * Empty Optional means the ingredient is not in the dictionary.
     */
    public Optional<List<AllergenRef>> allergensOf(String ingredientCode) {
        return ingredient(ingredientCode).map(found -> allergensByIngredient.getOrDefault(found.code(), List.of()));
    }

    /**
     * True when the ingredient's allergen mapping has a review level. In phase 1 both
     * HUMAN_VERIFIED and AI_RESEARCHED count (CLAUDE.md rule 13-1). Everything else (not in the
     * dictionary, or a draft) is shown as "needs checking", never as safe.
     */
    public boolean isAllergenReviewed(String ingredientCode) {
        return allergenReviewLevel(ingredientCode) != null;
    }

    /** Null for an ingredient outside the dictionary or with a draft mapping. */
    public ReviewLevel allergenReviewLevel(String ingredientCode) {
        return ingredient(ingredientCode).map(Ingredient::allergenReviewLevel).orElse(null);
    }

    public boolean allergenProductVaries(String ingredientCode) {
        return ingredient(ingredientCode).map(Ingredient::allergenProductVaries).orElse(false);
    }

    /** Ingredients outside the dictionary are treated like seasoning so they are never rounded up. */
    // TODO(spec): confirm the sensitivity of ingredients missing from the dictionary.
    public Sensitivity sensitivityOf(String ingredientCode) {
        return ingredient(ingredientCode).map(Ingredient::sensitivity).orElse(Sensitivity.HIGH);
    }

    /** Tool-based weight references of an ingredient; empty when grams cannot become a tool amount. */
    public List<WeightReference> weightReferencesOf(String ingredientCode) {
        return weightReferences.stream()
                .filter(reference -> reference.ingredientCode().equals(ingredientCode))
                .toList();
    }

    public List<SafetyRule> safetyRules() {
        return safetyRules;
    }

    /** Omit/substitute rules for one ingredient of one recipe, recipe-specific and generic, by priority. */
    public List<IngredientRule> ingredientRulesFor(String recipeId, String ingredientCode) {
        if (ingredientCode == null) {
            return List.of();
        }
        return ingredientRules.stream()
                .filter(rule -> rule.type() == IngredientRuleType.OMIT || rule.type() == IngredientRuleType.SUBSTITUTE)
                .filter(rule -> rule.ingredientCode().equals(ingredientCode))
                .filter(rule -> rule.recipeId() == null || rule.recipeId().equals(recipeId))
                .sorted(Comparator.comparingInt(IngredientRule::priority))
                .toList();
    }

    public List<ToolRule> toolRulesFor(String recipeId, String requiredToolId) {
        return toolRules.stream()
                .filter(rule -> rule.requiredToolId().equals(requiredToolId))
                .filter(rule -> rule.recipeId() == null || rule.recipeId().equals(recipeId))
                .toList();
    }
}
