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
// Tool codes are not sensitive and may be sent as "tools=pot,knife" to get toolFit back.
@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    public List<SearchResult> search(@RequestParam("q") String query,
                                     @RequestParam(name = "tools", required = false) List<String> tools) {
        return recipeService.search(query, tools);
    }

    @GetMapping("/suggestions")
    public List<SearchResult> suggestions(@RequestParam(name = "tools", required = false) List<String> tools) {
        return recipeService.suggestions(tools);
    }

    @GetMapping("/similar")
    public List<SearchResult> similar(@RequestParam("q") String query,
                                      @RequestParam(name = "tools", required = false) List<String> tools) {
        return recipeService.similar(query, tools);
    }

    @GetMapping("/{id}")
    public Detail detail(@PathVariable("id") String id,
                         @RequestParam(name = "tools", required = false) List<String> tools) {
        return recipeService.detail(id, tools);
    }
}
