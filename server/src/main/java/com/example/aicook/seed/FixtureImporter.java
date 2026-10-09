package com.example.aicook.seed;

import com.example.aicook.common.Json;
import com.example.aicook.measure.AmountParser;
import com.example.aicook.measure.ParsedAmount;
import com.example.aicook.measure.ParsedAmount.Amount;
import com.example.aicook.recipe.Recipe.UnitKind;
import com.example.aicook.recipe.RecipeRepository;
import com.example.aicook.reference.Units;
import com.example.aicook.seed.SeedFiles.FixtureIngredient;
import com.example.aicook.seed.SeedFiles.FixtureRecipe;
import com.example.aicook.seed.SeedFiles.FixtureStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores the fixed recipes from data/fixtures/recipes (source FIXTURE) for development, tests
 * and demos. A fixture that is already stored is left untouched so its ids stay stable;
 * delete its recipe row to import an edited file again.
 */
@Component
public class FixtureImporter {

    private static final Logger log = LoggerFactory.getLogger(FixtureImporter.class);
    private static final String LOCATION = "classpath*:fixtures/recipes/*.json";

    private final JdbcClient jdbc;
    private final AmountParser amountParser;
    private final Units units;

    public FixtureImporter(JdbcClient jdbc, AmountParser amountParser, Units units) {
        this.jdbc = jdbc;
        this.amountParser = amountParser;
        this.units = units;
    }

    void importAll() {
        for (FixtureRecipe fixture : readFixtures()) {
            if (findRecipeId(fixture.ref()).isPresent()) {
                continue;
            }
            String recipeId = insertRecipe(fixture);
            Map<String, String> ingredientIds = insertIngredients(recipeId, fixture);
            insertSteps(recipeId, fixture, ingredientIds);
            log.info("Imported fixture recipe {}", fixture.ref());
        }
    }

    Optional<String> findRecipeId(String ref) {
        return jdbc.sql("SELECT id FROM recipe WHERE source = 'FIXTURE' AND source_ref = :ref")
                .param("ref", ref)
                .query(String.class).optional();
    }

    private List<FixtureRecipe> readFixtures() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
            return Arrays.stream(resources)
                    .sorted(Comparator.comparing(Resource::getFilename))
                    .map(FixtureImporter::read)
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static FixtureRecipe read(Resource resource) {
        try (InputStream stream = resource.getInputStream()) {
            return Json.read(stream, FixtureRecipe.class);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private String insertRecipe(FixtureRecipe fixture) {
        return jdbc.sql("""
                        INSERT INTO recipe (name, normalized_name, source, source_ref, base_servings, total_minutes,
                                            verification_status)
                        VALUES (:name, :normalizedName, 'FIXTURE', :ref, :servings, :minutes, :status)
                        RETURNING id
                        """)
                .param("name", fixture.name())
                .param("normalizedName", RecipeRepository.normalize(fixture.name()))
                .param("ref", fixture.ref())
                .param("servings", fixture.baseServings())
                .param("minutes", fixture.totalMinutes())
                .param("status", fixture.verificationStatus())
                .query(String.class).single();
    }

    /** @return fixture ingredient key -> recipe_ingredient id */
    private Map<String, String> insertIngredients(String recipeId, FixtureRecipe fixture) {
        Map<String, String> ids = new HashMap<>();
        int order = 0;
        for (FixtureIngredient ingredient : fixture.ingredients()) {
            ParsedAmount amount = amountParser.parse(ingredient.amount());
            Amount primary = amount.primary();
            Amount alternative = amount.alternative();
            String id = jdbc.sql("""
                            INSERT INTO recipe_ingredient (recipe_id, ingredient_id, name_ko, quantity, unit,
                                                           alt_quantity, alt_unit, unit_kind, raw_text, is_required,
                                                           sort_order)
                            VALUES (:recipeId, :ingredientId, :name, :quantity, :unit, :altQuantity, :altUnit,
                                    :unitKind, :rawText, :required, :sortOrder)
                            RETURNING id
                            """)
                    .param("recipeId", recipeId)
                    .param("ingredientId", dictionaryId(fixture, ingredient))
                    .param("name", ingredient.name())
                    .param("quantity", primary == null ? null : primary.quantity())
                    .param("unit", primary == null ? null : primary.unit())
                    .param("altQuantity", alternative == null ? null : alternative.quantity())
                    .param("altUnit", alternative == null ? null : alternative.unit())
                    .param("unitKind", unitKindOf(amount).name())
                    // The source text is kept as written, name included.
                    .param("rawText", ingredient.name() + " " + ingredient.amount())
                    .param("required", ingredient.required())
                    .param("sortOrder", order++)
                    .query(String.class).single();
            ids.put(ingredient.key(), id);
        }
        return ids;
    }

    private String dictionaryId(FixtureRecipe fixture, FixtureIngredient ingredient) {
        if (ingredient.ingredient() == null) {
            return null;
        }
        return jdbc.sql("SELECT id FROM ingredient WHERE code = :code")
                .param("code", ingredient.ingredient())
                .query(String.class).optional()
                .orElseThrow(() -> new IllegalStateException(
                        "Fixture " + fixture.ref() + ": unknown ingredient code " + ingredient.ingredient()));
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

    private void insertSteps(String recipeId, FixtureRecipe fixture, Map<String, String> ingredientIds) {
        int order = 1;
        for (FixtureStep step : fixture.steps()) {
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
                    .param("action", step.action())
                    .param("actionKey", step.actionKey())
                    .param("duration", step.durationSeconds())
                    .param("timerStart", step.timerStartCondition())
                    .param("heatLevel", step.heatLevel())
                    .param("heatRaw", step.heatRaw())
                    .param("deviceSettingRaw", step.deviceSettingRaw())
                    .param("completionCheck", step.completionCheck())
                    .query(String.class).single();
            for (String key : step.ingredients()) {
                String ingredientId = ingredientIds.get(key);
                if (ingredientId == null) {
                    throw new IllegalStateException("Fixture " + fixture.ref() + ": unknown ingredient key " + key);
                }
                jdbc.sql("INSERT INTO recipe_step_ingredient (step_id, recipe_ingredient_id) VALUES (:stepId, :id)")
                        .param("stepId", stepId).param("id", ingredientId).update();
            }
            for (String toolCode : step.tools()) {
                int inserted = jdbc.sql("""
                                INSERT INTO recipe_step_tool (step_id, tool_variant_id)
                                SELECT :stepId, id FROM tool_variant WHERE code = :code
                                """)
                        .param("stepId", stepId).param("code", toolCode).update();
                if (inserted != 1) {
                    throw new IllegalStateException("Fixture " + fixture.ref() + ": unknown tool code " + toolCode);
                }
            }
        }
    }
}
