package com.example.aicook.recipe;

import com.example.aicook.common.ApiException;
import com.example.aicook.recipe.RecipeDtos.Detail;
import com.example.aicook.recipe.RecipeDtos.DetailIngredient;
import com.example.aicook.recipe.RecipeDtos.DetailStep;
import com.example.aicook.recipe.RecipeDtos.MissingTool;
import com.example.aicook.recipe.RecipeDtos.RecipeBadge;
import com.example.aicook.recipe.RecipeDtos.SearchResult;
import com.example.aicook.recipe.RecipeDtos.ToolFit;
import com.example.aicook.recipe.RecipeDtos.ToolNeed;
import com.example.aicook.reference.ReferenceData;
import com.example.aicook.reference.Rules.AllergenRef;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecipeService {

    private static final int SUGGESTION_COUNT = 6;

    private final RecipeRepository recipes;
    private final ReferenceData reference;

    public RecipeService(RecipeRepository recipes, ReferenceData reference) {
        this.recipes = recipes;
        this.reference = reference;
    }

    public List<SearchResult> search(String query) {
        return search(query, null);
    }

    /**
     * Stored recipes whose name contains the query. An empty list means "not found".
     * With the user's tools, recipes they can make come first; the order is otherwise kept.
     *
     * @param ownedTools the user's tool codes, or null when they were not sent
     */
    public List<SearchResult> search(String query, List<String> ownedTools) {
        String needle = RecipeRepository.normalize(query);
        if (needle.isEmpty()) {
            throw ApiException.badRequest("EMPTY_QUERY", "검색어를 입력해 주세요.");
        }
        Set<String> owned = knownTools(ownedTools);
        List<SearchResult> results = recipes.searchByName(needle).stream()
                .map(recipe -> toSearchResult(recipe, owned))
                .toList();
        if (owned == null) {
            return results;
        }
        return results.stream()
                .sorted(Comparator.comparing((SearchResult result) -> !result.toolFit().ok()))
                .toList();
    }

    public List<SearchResult> suggestions() {
        return suggestions(null);
    }

    /** Recipes already stored, for the home screen: they open at once, without a web import. */
    public List<SearchResult> suggestions(List<String> ownedTools) {
        Set<String> owned = knownTools(ownedTools);
        return recipes.findStored(SUGGESTION_COUNT).stream().map(recipe -> toSearchResult(recipe, owned)).toList();
    }

    public Detail detail(String id) {
        return detail(id, null);
    }

    public Detail detail(String id, List<String> ownedTools) {
        Recipe recipe = find(id);
        return new Detail(recipe.id(), recipe.name(), badgeOf(recipe), recipe.sourceUrl(), recipe.sourceDomain(),
                recipe.baseServings(), recipe.totalMinutes(),
                ToolNeeds.of(recipe, reference).stream()
                        .map(need -> new ToolNeed(need.id(), need.label(), need.anyOf()))
                        .toList(),
                recipe.ingredients().stream().map(this::toDetailIngredient).toList(),
                recipe.steps().stream().map(step -> new DetailStep(step.order(), step.action())).toList(),
                toolFit(recipe, knownTools(ownedTools)));
    }

    /** Tool codes the server knows; null when the request sent no tools at all. */
    private Set<String> knownTools(List<String> ownedTools) {
        if (ownedTools == null) {
            return null;
        }
        return ownedTools.stream()
                .filter(code -> code != null && reference.tool(code).isPresent())
                .collect(Collectors.toSet());
    }

    /** Decided by reference data and server code only; the AI is never asked. */
    private ToolFit toolFit(Recipe recipe, Set<String> owned) {
        if (owned == null) {
            return null;
        }
        List<MissingTool> missing = ToolNeeds.missing(recipe, reference, owned).stream()
                .map(need -> new MissingTool(need.id(), nameWithSubstitutes(recipe, need)))
                .toList();
        return new ToolFit(missing.isEmpty(), missing);
    }

    /** "뚝배기 또는 냄비": a tool a verified rule accepts instead is named too, since owning it is enough. */
    private String nameWithSubstitutes(Recipe recipe, ToolNeeds.Need need) {
        List<String> substitutes = need.anyOf().stream()
                .flatMap(toolId -> reference.toolRulesFor(recipe.id(), toolId).stream())
                .map(rule -> reference.tool(rule.substituteToolId()).map(tool -> tool.name()).orElse(null))
                .filter(name -> name != null && !need.label().contains(name))
                .distinct()
                .toList();
        return substitutes.isEmpty() ? need.label() : need.label() + " 또는 " + String.join(" 또는 ", substitutes);
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

    private SearchResult toSearchResult(Recipe recipe, Set<String> owned) {
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
                allergenCodes, checkNeeded, toolFit(recipe, owned));
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
