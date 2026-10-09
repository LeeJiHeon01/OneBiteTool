package com.example.aicook.recipeimport;

import com.example.aicook.measure.AmountParser;
import com.example.aicook.measure.ParsedAmount;
import com.example.aicook.measure.ParsedAmount.Amount;
import com.example.aicook.recipe.Recipe.UnitKind;
import com.example.aicook.recipe.RecipeRepository;
import com.example.aicook.reference.Units;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores a checked extract as a recipe (source WEB_SEARCH, status IMPORTED). Only ingredient
 * names, amounts as written and the rewritten step sentences are stored; quotes from the page
 * and the page text itself are not.
 */
@Component
public class ImportedRecipeWriter {

    private final JdbcClient jdbc;
    private final AmountParser amountParser;
    private final Units units;

    public ImportedRecipeWriter(JdbcClient jdbc, AmountParser amountParser, Units units) {
        this.jdbc = jdbc;
        this.amountParser = amountParser;
        this.units = units;
    }

    /** A page is imported once; later requests reuse the stored recipe. */
    public Optional<String> findByUrl(String url) {
        return jdbc.sql("SELECT id FROM recipe WHERE source_url_hash = :hash")
                .param("hash", hash(url))
                .query(String.class).optional();
    }

    /**
     * @param name     recipe name shown in the app
     * @param servings servings stated by the page; null when the page does not say
     * @return the new recipe id
     */
    @Transactional
    public String save(String name, String url, Double servings, Integer totalMinutes, Extract.Page page) {
        String recipeId = insertRecipe(name, url, servings, totalMinutes);
        Map<String, List<String>> ingredientIds = insertIngredients(recipeId, page.ingredients());
        insertSteps(recipeId, page.steps(), ingredientIds);
        return recipeId;
    }

    private String insertRecipe(String name, String url, Double servings, Integer totalMinutes) {
        // TODO(spec): a page without a servings count is stored with the column default (1 serving).
        return jdbc.sql("""
                        INSERT INTO recipe (name, normalized_name, source, source_url, source_url_hash, source_domain,
                                            base_servings, total_minutes, verification_status)
                        VALUES (:name, :normalizedName, 'WEB_SEARCH', :url, :hash, :domain,
                                COALESCE(:servings, 1.0), :minutes, 'IMPORTED')
                        RETURNING id
                        """)
                .param("name", name)
                .param("normalizedName", RecipeRepository.normalize(name))
                .param("url", url)
                .param("hash", hash(url))
                .param("domain", domainOf(url))
                .param("servings", servings)
                .param("minutes", totalMinutes)
                .query(String.class).single();
    }

    /** @return ingredient name -> recipe_ingredient ids (a name can appear in several sections) */
    private Map<String, List<String>> insertIngredients(String recipeId, List<Extract.Ingredient> ingredients) {
        Map<String, List<String>> ids = new HashMap<>();
        int order = 0;
        for (Extract.Ingredient ingredient : ingredients) {
            ParsedAmount amount = amountParser.parse(ingredient.amountText());
            Amount primary = amount.primary();
            Amount alternative = amount.alternative();
            String amountText = ingredient.amountText() == null ? "" : " " + ingredient.amountText().strip();
            String id = jdbc.sql("""
                            INSERT INTO recipe_ingredient (recipe_id, ingredient_id, section_ko, name_ko, quantity,
                                                           unit, alt_quantity, alt_unit, unit_kind, raw_text,
                                                           is_required, sort_order)
                            VALUES (:recipeId, :ingredientId, :section, :name, :quantity, :unit, :altQuantity,
                                    :altUnit, :unitKind, :rawText, TRUE, :sortOrder)
                            RETURNING id
                            """)
                    .param("recipeId", recipeId)
                    // No dictionary match leaves the id empty: allergens are then "needs checking".
                    .param("ingredientId", dictionaryId(ingredient.name()).orElse(null))
                    .param("section", ingredient.section())
                    .param("name", ingredient.name().strip())
                    .param("quantity", primary == null ? null : primary.quantity())
                    .param("unit", primary == null ? null : primary.unit())
                    .param("altQuantity", alternative == null ? null : alternative.quantity())
                    .param("altUnit", alternative == null ? null : alternative.unit())
                    .param("unitKind", unitKindOf(amount).name())
                    .param("rawText", ingredient.name().strip() + amountText)
                    .param("sortOrder", order++)
                    .query(String.class).single();
            ids.computeIfAbsent(ingredient.name(), key -> new ArrayList<>()).add(id);
        }
        return ids;
    }

    /** Exact match on the dictionary name or one of its aliases; similar names are not guessed. */
    private Optional<String> dictionaryId(String name) {
        return jdbc.sql("""
                        SELECT id FROM ingredient WHERE name_ko = :name
                        UNION
                        SELECT ingredient_id FROM ingredient_alias WHERE alias = :name
                        LIMIT 1
                        """)
                .param("name", name.strip())
                .query(String.class).optional();
    }

    /** An amount that cannot be read as a number is stored as VAGUE, never with a guessed quantity. */
    private UnitKind unitKindOf(ParsedAmount amount) {
        if (amount.vague() || amount.primary() == null) {
            return UnitKind.VAGUE;
        }
        String unit = amount.primary().unit();
        if (unit != null && units.mlByUnit().containsKey(unit)) {
            return UnitKind.VOLUME;
        }
        return units.isWeightUnit(unit) ? UnitKind.WEIGHT : UnitKind.COUNT;
    }

    private void insertSteps(String recipeId, List<Extract.Step> steps, Map<String, List<String>> ingredientIds) {
        int order = 1;
        for (Extract.Step step : steps) {
            String stepId = jdbc.sql("""
                            INSERT INTO recipe_step (recipe_id, step_order, action_ko, action_key, duration_seconds,
                                                     timer_start_condition, heat_level, heat_raw, device_setting_raw,
                                                     completion_check)
                            VALUES (:recipeId, :order, :action, :actionKey, :duration, :timerStart, :heatLevel,
                                    :heatRaw, :deviceSettingRaw, :completionCheck)
                            RETURNING id
                            """)
                    .param("recipeId", recipeId)
                    .param("order", order++)
                    .param("action", step.action().strip())
                    .param("actionKey", step.actionKey())
                    .param("duration", step.durationSeconds())
                    // A start condition only makes sense for a step that has a time.
                    .param("timerStart", step.durationSeconds() == null ? null : step.timerStartCondition())
                    .param("heatLevel", step.heatLevel() == null ? null : step.heatLevel().name())
                    .param("heatRaw", step.heatLevel() == null ? null : step.heatRaw())
                    .param("deviceSettingRaw", step.deviceSettingRaw())
                    .param("completionCheck", step.completionCheck())
                    .query(String.class).single();
            step.ingredientNames().stream().distinct()
                    .flatMap(name -> ingredientIds.get(name).stream())
                    .forEach(id -> jdbc.sql(
                                    "INSERT INTO recipe_step_ingredient (step_id, recipe_ingredient_id) VALUES (:stepId, :id)")
                            .param("stepId", stepId).param("id", id).update());
            step.toolCodes().stream().distinct()
                    .forEach(code -> jdbc.sql("""
                                    INSERT INTO recipe_step_tool (step_id, tool_variant_id)
                                    SELECT :stepId, id FROM tool_variant WHERE code = :code
                                    """)
                            .param("stepId", stepId).param("code", code).update());
        }
    }

    private static String domainOf(String url) {
        String host = URI.create(url).getHost();
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static String hash(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
