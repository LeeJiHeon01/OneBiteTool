package com.example.aicook.recipeimport;

import com.example.aicook.recipeimport.RecipeImportService.Reextracted;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Development-only maintenance: extract a stored web recipe again after the extraction prompt
 * changed. It rewrites stored data and costs an AI call, so it does not exist unless
 * app.maintenance-enabled is true (APP_MAINTENANCE_ENABLED in server/.env).
 */
@RestController
@RequestMapping("/api/maintenance")
@ConditionalOnProperty(name = "app.maintenance-enabled", havingValue = "true")
public class RecipeMaintenanceController {

    private final RecipeImportService importService;

    public RecipeMaintenanceController(RecipeImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/recipes/{id}/reextract")
    public Reextracted reextract(@PathVariable("id") String id) {
        return importService.reextract(id);
    }
}
