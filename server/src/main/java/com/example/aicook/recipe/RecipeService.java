package com.example.aicook.recipe;

import com.example.aicook.common.ApiException;
import com.example.aicook.recipe.RecipeDtos.Detail;
import com.example.aicook.recipe.RecipeDtos.DetailIngredient;
import com.example.aicook.recipe.RecipeDtos.DetailStep;
import com.example.aicook.recipe.RecipeDtos.RecipeBadge;
import com.example.aicook.recipe.RecipeDtos.SearchResult;
import com.example.aicook.recipe.RecipeDtos.ToolNeed;
import com.example.aicook.reference.ReferenceData;
import com.example.aicook.reference.Rules.AllergenRef;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecipeService {

    private static final int SUGGESTION_COUNT = 6;

    private final RecipeRepository recipes;
    private final ReferenceData reference;

    public RecipeService(RecipeRepository recipes, ReferenceData reference) {
        this.recipes = recipes;
        this.reference = reference;
    }

    /** Stored recipes whose name contains the query. An empty list means "not found". */
    public List<SearchResult> search(String query) {
        String needle = RecipeRepository.normalize(query);
        if (needle.isEmpty()) {
            throw ApiException.badRequest("EMPTY_QUERY", "검색어를 입력해 주세요.");
        }
        return recipes.searchByName(needle).stream().map(this::toSearchResult).toList();
    }

    /** Recipes already stored, for the home screen: they open at once, without a web import. */
    public List<SearchResult> suggestions() {
        return recipes.findStored(SUGGESTION_COUNT).stream().map(this::toSearchResult).toList();
    }

    public Detail detail(String id) {
        Recipe recipe = find(id);
        return new Detail(recipe.id(), recipe.name(), badgeOf(recipe), recipe.sourceUrl(), recipe.sourceDomain(),
                recipe.baseServings(), recipe.totalMinutes(),
                ToolNeeds.of(recipe, reference).stream()
                        .map(need -> new ToolNeed(need.id(), need.label(), need.anyOf()))
                        .toList(),
                recipe.ingredients().stream().map(this::toDetailIngredient).toList(),
                recipe.steps().stream().map(step -> new DetailStep(step.order(), step.action())).toList());
    }

    public Recipe find(String id) {
        return recipes.findById(id)
                .orElseThrow(() -> ApiException.notFound("RECIPE_NOT_FOUND", "레시피를 찾지 못했어요."));
    }

    public static RecipeBadge badgeOf(Recipe recipe) {
        if (recipe.source() == Recipe.Source.WEB_SEARCH) {
            return RecipeBadge.WEB_SEARCH;
        }
        return switch (recipe.verificationStatus()) {
            case DATA_CHECKED, COOK_TESTED -> RecipeBadge.VERIFIED;
            case UNVERIFIED, IMPORTED -> RecipeBadge.IMPORTED;
        };
    }

    private SearchResult toSearchResult(Recipe recipe) {
        List<String> allergenCodes = recipe.ingredients().stream()
                .flatMap(ingredient -> reference.allergensOf(ingredient.dictionaryCode()).stream())
                .flatMap(List::stream)
                .map(AllergenRef::code)
                .distinct()
                .sorted()
                .toList();
        boolean checkNeeded = recipe.ingredients().stream()
                .anyMatch(ingredient -> !reference.isAllergenReviewed(ingredient.dictionaryCode()));
        return new SearchResult(recipe.id(), recipe.name(), recipe.ingredients().size(), recipe.steps().size(),
                recipe.totalMinutes(), badgeOf(recipe), recipe.sourceUrl(), recipe.sourceDomain(),
                allergenCodes, checkNeeded);
    }

    private DetailIngredient toDetailIngredient(Recipe.Ingredient ingredient) {
        var allergens = reference.allergensOf(ingredient.dictionaryCode());
        // Shown next to the recipe's own word so the user recognises what they already have.
        String dictionaryName = reference.ingredient(ingredient.dictionaryCode())
                .map(entry -> entry.name())
                .filter(name -> !ingredient.name().contains(name))
                .orElse(null);
        return new DetailIngredient(ingredient.id(), ingredient.name(), dictionaryName,
                reference.units().readable(ingredient.amountText(), ingredient.unit()),
                allergens.orElse(List.of()), !reference.isAllergenReviewed(ingredient.dictionaryCode()),
                reference.allergenReviewLevel(ingredient.dictionaryCode()),
                reference.allergenProductVaries(ingredient.dictionaryCode()));
    }
}
