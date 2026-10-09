package com.example.aicook.recipe;

import com.example.aicook.recipe.Recipe.HeatLevel;
import com.example.aicook.recipe.Recipe.Source;
import com.example.aicook.recipe.Recipe.UnitKind;
import com.example.aicook.recipe.Recipe.VerificationStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Reads recipes with their ingredients and steps. */
@Repository
public class RecipeRepository {

    private final JdbcClient jdbc;

    public RecipeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Search key stored in recipe.normalized_name: no whitespace, lower case. */
    public static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    public Optional<Recipe> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT * FROM recipe WHERE id = :id")
                .param("id", id)
                .query((rs, row) -> toRecipe(rs))
                .optional();
    }

    /** Recipes whose normalized name contains the normalized query. */
    public List<Recipe> searchByName(String normalizedQuery) {
        return jdbc.sql("SELECT * FROM recipe WHERE position(:query in normalized_name) > 0 ORDER BY id")
                .param("query", normalizedQuery)
                .query((rs, row) -> toRecipe(rs))
                .list();
    }

    private Recipe toRecipe(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        return new Recipe(id, rs.getString("name"), Source.valueOf(rs.getString("source")),
                rs.getString("source_url"), rs.getString("source_domain"),
                VerificationStatus.valueOf(rs.getString("verification_status")),
                rs.getBigDecimal("base_servings").doubleValue(), (Integer) rs.getObject("total_minutes"),
                loadIngredients(id), loadSteps(id));
    }

    private List<Recipe.Ingredient> loadIngredients(String recipeId) {
        return jdbc.sql("""
                        SELECT ri.*, i.code AS dictionary_code
                        FROM recipe_ingredient ri LEFT JOIN ingredient i ON i.id = ri.ingredient_id
                        WHERE ri.recipe_id = :recipeId
                        ORDER BY ri.sort_order, ri.id
                        """)
                .param("recipeId", recipeId)
                .query((rs, row) -> new Recipe.Ingredient(rs.getString("id"), rs.getString("dictionary_code"),
                        rs.getString("name_ko"), rs.getString("raw_text"), toDouble(rs.getBigDecimal("quantity")),
                        rs.getString("unit"), toDouble(rs.getBigDecimal("alt_quantity")), rs.getString("alt_unit"),
                        UnitKind.valueOf(rs.getString("unit_kind")), rs.getBoolean("is_required")))
                .list();
    }

    private record Link(String stepId, String targetId) {
    }

    private List<Recipe.Step> loadSteps(String recipeId) {
        Map<String, List<String>> ingredientIds = loadLinks("""
                SELECT si.step_id, si.recipe_ingredient_id AS target_id
                FROM recipe_step_ingredient si
                JOIN recipe_step s ON s.id = si.step_id
                JOIN recipe_ingredient ri ON ri.id = si.recipe_ingredient_id
                WHERE s.recipe_id = :recipeId
                ORDER BY ri.sort_order, ri.id
                """, recipeId);
        Map<String, List<String>> toolIds = loadLinks("""
                SELECT st.step_id, t.code AS target_id
                FROM recipe_step_tool st
                JOIN recipe_step s ON s.id = st.step_id
                JOIN tool_variant t ON t.id = st.tool_variant_id
                WHERE s.recipe_id = :recipeId
                ORDER BY t.sort_order, t.id
                """, recipeId);
        return jdbc.sql("SELECT * FROM recipe_step WHERE recipe_id = :recipeId ORDER BY step_order")
                .param("recipeId", recipeId)
                .query((rs, row) -> {
                    String stepId = rs.getString("id");
                    String heat = rs.getString("heat_level");
                    return new Recipe.Step(stepId, rs.getInt("step_order"), rs.getString("action_ko"),
                            rs.getString("action_key"), (Integer) rs.getObject("duration_seconds"),
                            rs.getString("timer_start_condition"), heat == null ? null : HeatLevel.valueOf(heat),
                            rs.getString("heat_raw"), rs.getString("device_setting_raw"),
                            rs.getString("completion_check"),
                            ingredientIds.getOrDefault(stepId, List.of()), toolIds.getOrDefault(stepId, List.of()));
                })
                .list();
    }

    private Map<String, List<String>> loadLinks(String sql, String recipeId) {
        return jdbc.sql(sql)
                .param("recipeId", recipeId)
                .query((rs, row) -> new Link(rs.getString("step_id"), rs.getString("target_id")))
                .list().stream()
                .collect(Collectors.groupingBy(Link::stepId, Collectors.mapping(Link::targetId, Collectors.toList())));
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
