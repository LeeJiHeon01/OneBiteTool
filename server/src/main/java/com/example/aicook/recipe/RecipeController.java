package com.example.aicook.recipe;

import com.example.aicook.recipe.RecipeDtos.Detail;
import com.example.aicook.recipe.RecipeDtos.SearchResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Allergy data is never accepted here: query strings end up in access logs.
@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    public List<SearchResult> search(@RequestParam("q") String query) {
        return recipeService.search(query);
    }

    @GetMapping("/suggestions")
    public List<SearchResult> suggestions() {
        return recipeService.suggestions();
    }

    @GetMapping("/{id}")
    public Detail detail(@PathVariable("id") String id) {
        return recipeService.detail(id);
    }
}
