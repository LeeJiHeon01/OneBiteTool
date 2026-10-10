package com.example.aicook.reference;

import com.example.aicook.reference.Rules.AllergenRef;
import com.example.aicook.reference.Rules.ReviewLevel;
import com.example.aicook.reference.Rules.Ingredient;
import com.example.aicook.reference.Rules.IngredientRule;
import com.example.aicook.reference.Rules.IngredientRuleType;
import com.example.aicook.reference.Rules.Placement;
import com.example.aicook.reference.Rules.SafetyRule;
import com.example.aicook.reference.Rules.ToolRule;
import com.example.aicook.reference.Rules.TriggerType;
import com.example.aicook.reference.Rules.WeightReference;
import com.example.aicook.reference.Tools.Category;
import com.example.aicook.reference.Tools.ContentType;
import com.example.aicook.reference.Tools.Cup;
import com.example.aicook.reference.Tools.FillLevel;
import com.example.aicook.reference.Tools.Variant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads the reference tables into {@link ReferenceData}. */
@Repository
public class ReferenceRepository {

    private final JdbcClient jdbc;

    public ReferenceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public ReferenceData load(Units units) {
        return new ReferenceData(loadToolCategories(), units, loadIngredients(), loadAllergenCodes(),
                loadAllergenMappings(), loadSafetyRules(), loadIngredientRules(), loadToolRules(),
                loadWeightReferences());
    }

    private record CategoryRow(String code, String name) {
    }

    private record VariantRow(String categoryCode, Variant variant) {
    }

    private record FillLevelRow(String toolCode, FillLevel level) {
    }

    private List<Category> loadToolCategories() {
        Map<String, List<FillLevel>> levelsByTool = jdbc.sql("""
                        SELECT t.code AS tool_code, f.code, f.content_type, f.fraction, f.is_heaped, f.image_key,
                               f.value_source
                        FROM fill_level f JOIN tool_variant t ON t.id = f.tool_variant_id
                        ORDER BY f.id
                        """)
                .query((rs, row) -> new FillLevelRow(rs.getString("tool_code"), new FillLevel(rs.getString("code"),
                        ContentType.valueOf(rs.getString("content_type")), rs.getDouble("fraction"),
                        rs.getBoolean("is_heaped"), rs.getString("image_key"),
                        ValueSource.valueOf(rs.getString("value_source")))))
                .list().stream()
                .collect(Collectors.groupingBy(FillLevelRow::toolCode,
                        Collectors.mapping(FillLevelRow::level, Collectors.toList())));

        Map<String, List<Variant>> variantsByCategory = jdbc.sql("""
                        SELECT c.code AS category_code, v.*
                        FROM tool_variant v JOIN tool_category c ON c.id = v.category_id
                        ORDER BY v.sort_order, v.id
                        """)
                .query((rs, row) -> new VariantRow(rs.getString("category_code"),
                        toVariant(rs, levelsByTool.getOrDefault(rs.getString("code"), List.of()))))
                .list().stream()
                .collect(Collectors.groupingBy(VariantRow::categoryCode,
                        Collectors.mapping(VariantRow::variant, Collectors.toList())));

        return jdbc.sql("SELECT code, name_ko FROM tool_category ORDER BY sort_order, id")
                .query((rs, row) -> new CategoryRow(rs.getString("code"), rs.getString("name_ko")))
                .list().stream()
                .map(category -> new Category(category.code(), category.name(),
                        variantsByCategory.getOrDefault(category.code(), List.of())))
                .toList();
    }

    private Variant toVariant(ResultSet rs, List<FillLevel> levels) throws SQLException {
        BigDecimal volume = rs.getBigDecimal("representative_volume_ml");
        String valueSource = rs.getString("value_source");
        return new Variant(rs.getString("code"), rs.getString("name_ko"), rs.getString("sub_label_ko"),
                rs.getString("icon_key"), rs.getString("heat_source_type"),
                volume == null ? null : volume.doubleValue(), rs.getBoolean("is_approximate"),
                rs.getBoolean("is_standard_measure"), rs.getBoolean("is_basic"),
                valueSource == null ? null : ValueSource.valueOf(valueSource), rs.getString("source_ref"),
                toCup(rs), levels);
    }

    private Cup toCup(ResultSet rs) throws SQLException {
        BigDecimal top = rs.getBigDecimal("top_diameter_mm");
        if (top == null) {
            return null;
        }
        return new Cup(top.doubleValue(), rs.getDouble("bottom_diameter_mm"), rs.getDouble("height_mm"),
                rs.getDouble("fill_line_from_top_mm"));
    }

    private List<Ingredient> loadIngredients() {
        Map<String, List<String>> aliases = jdbc.sql("""
                        SELECT i.code, a.alias FROM ingredient_alias a JOIN ingredient i ON i.id = a.ingredient_id
                        ORDER BY a.id
                        """)
                .query((rs, row) -> Map.entry(rs.getString("code"), rs.getString("alias")))
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return jdbc.sql("""
                        SELECT code, name_ko, category, form, default_sensitivity, is_garnish,
                               allergen_review_level, allergen_product_varies
                        FROM ingredient ORDER BY id
                        """)
                .query((rs, row) -> new Ingredient(rs.getString("code"), rs.getString("name_ko"),
                        rs.getString("category"), rs.getString("form"),
                        Sensitivity.valueOf(rs.getString("default_sensitivity")),
                        rs.getBoolean("is_garnish"), reviewLevel(rs.getString("allergen_review_level")),
                        rs.getBoolean("allergen_product_varies"),
                        aliases.getOrDefault(rs.getString("code"), List.of())))
                .list();
    }

    private static ReviewLevel reviewLevel(String value) {
        return value == null ? null : ReviewLevel.valueOf(value);
    }

    private Set<String> loadAllergenCodes() {
        return new HashSet<>(jdbc.sql("SELECT code FROM allergen").query(String.class).list());
    }

    private record MappingRow(String ingredientCode, AllergenRef allergen) {
    }

    private Map<String, List<AllergenRef>> loadAllergenMappings() {
        return jdbc.sql("""
                        SELECT i.code AS ingredient_code, a.code AS allergen_code, ia.is_hidden
                        FROM ingredient_allergen ia
                        JOIN ingredient i ON i.id = ia.ingredient_id
                        JOIN allergen a ON a.id = ia.allergen_id
                        ORDER BY i.id, a.sort_order
                        """)
                .query((rs, row) -> new MappingRow(rs.getString("ingredient_code"),
                        new AllergenRef(rs.getString("allergen_code"), rs.getBoolean("is_hidden"))))
                .list().stream()
                .collect(Collectors.groupingBy(MappingRow::ingredientCode,
                        Collectors.mapping(MappingRow::allergen, Collectors.toList())));
    }

    private List<SafetyRule> loadSafetyRules() {
        return jdbc.sql("""
                        SELECT code, trigger_type, trigger_value, message_ko, detail_low_ko, placement,
                               requires_heat
                        FROM safety_rule ORDER BY id
                        """)
                .query((rs, row) -> new SafetyRule(rs.getString("code"),
                        TriggerType.valueOf(rs.getString("trigger_type")), rs.getString("trigger_value"),
                        rs.getString("message_ko"), rs.getString("detail_low_ko"),
                        Placement.valueOf(rs.getString("placement")), rs.getBoolean("requires_heat")))
                .list();
    }

    private List<IngredientRule> loadIngredientRules() {
        return jdbc.sql("""
                        SELECT r.type, i.code AS ingredient_code, s.code AS substitute_code, r.impact_note_ko,
                               r.applies_to_recipe_id, r.priority
                        FROM adaptation_rule r
                        JOIN ingredient i ON i.id = r.ingredient_id
                        LEFT JOIN ingredient s ON s.id = r.substitute_ingredient_id
                        ORDER BY r.priority, r.id
                        """)
                .query((rs, row) -> new IngredientRule(IngredientRuleType.valueOf(rs.getString("type")),
                        rs.getString("ingredient_code"), rs.getString("substitute_code"),
                        rs.getString("impact_note_ko"), rs.getString("applies_to_recipe_id"),
                        rs.getInt("priority")))
                .list();
    }

    private List<WeightReference> loadWeightReferences() {
        return jdbc.sql("""
                        SELECT i.code AS ingredient_code, t.code AS tool_code, w.approx_grams
                        FROM weight_reference w
                        JOIN ingredient i ON i.id = w.ingredient_id
                        JOIN tool_variant t ON t.id = w.tool_variant_id
                        ORDER BY w.id
                        """)
                .query((rs, row) -> new WeightReference(rs.getString("ingredient_code"), rs.getString("tool_code"),
                        rs.getDouble("approx_grams")))
                .list();
    }

    private List<ToolRule> loadToolRules() {
        return jdbc.sql("""
                        SELECT req.code AS required_code, sub.code AS substitute_code, r.condition_note_ko,
                               r.applies_to_recipe_id, r.extra_safety_note_ko
                        FROM tool_substitution_rule r
                        JOIN tool_variant req ON req.id = r.required_tool_id
                        JOIN tool_variant sub ON sub.id = r.substitute_tool_id
                        ORDER BY r.id
                        """)
                .query((rs, row) -> new ToolRule(rs.getString("required_code"), rs.getString("substitute_code"),
                        rs.getString("condition_note_ko"), rs.getString("applies_to_recipe_id"),
                        rs.getString("extra_safety_note_ko")))
                .list();
    }
}
