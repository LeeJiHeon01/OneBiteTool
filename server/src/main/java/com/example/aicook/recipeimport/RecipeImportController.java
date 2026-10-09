package com.example.aicook.recipeimport;

import com.example.aicook.recipeimport.RecipeImportService.ImportJob;
import com.example.aicook.recipeimport.RecipeImportService.ImportRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Takes the dish name and tools only; allergy data is never part of an import (spec 8.3.1).
@RestController
@RequestMapping("/api/recipe-imports")
public class RecipeImportController {

    private final RecipeImportService importService;

    public RecipeImportController(RecipeImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    public ImportJob start(@RequestBody ImportRequest request) {
        return importService.start(request);
    }

    @GetMapping("/{id}")
    public ImportJob find(@PathVariable("id") String id) {
        return importService.find(id);
    }
}
