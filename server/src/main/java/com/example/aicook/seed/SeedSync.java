package com.example.aicook.seed;

import com.example.aicook.common.Json;
import com.example.aicook.seed.SeedFiles.AllergenMapping;
import com.example.aicook.seed.SeedFiles.CupSize;
import com.example.aicook.seed.SeedFiles.ImageEntry;
import com.example.aicook.seed.SeedFiles.ImageGroup;
import com.example.aicook.seed.SeedFiles.ImageManifest;
import com.example.aicook.seed.SeedFiles.Ingredient;
import com.example.aicook.seed.SeedFiles.IngredientRule;
import com.example.aicook.seed.SeedFiles.SafetyRule;
import com.example.aicook.seed.SeedFiles.Tool;
import com.example.aicook.seed.SeedFiles.ToolRule;
import com.example.aicook.seed.SeedFiles.WeightRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copies the human-maintained files under data/seed into the reference tables at startup.
 * The files are the source of truth: rule and mapping tables are rewritten on every start,
 * and startup fails on any id that does not exist.
 */
@Component
public class SeedSync {

    private static final Logger log = LoggerFactory.getLogger(SeedSync.class);
    /** verifiedBy value of a rule that is used in development but not yet checked by a person. */
    private static final String DRAFT_MARK = "DRAFT_UNVERIFIED";
    /** A cup's stated volume must match the volume computed from its dimensions this closely. */
    private static final double CUP_VOLUME_TOLERANCE = 0.05;

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final FixtureImporter fixtureImporter;

    public SeedSync(JdbcClient jdbc, TransactionTemplate transaction, FixtureImporter fixtureImporter) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.fixtureImporter = fixtureImporter;
    }

    public void run() {
        transaction.executeWithoutResult(status -> {
            syncImages(Json.readResource("images/manifest.json", SeedFiles.ImageManifest.class));
            syncTools(Json.readResource("seed/tools.json", SeedFiles.Tools.class).tools());
            syncIngredients(Json.readResource("seed/ingredients.json", SeedFiles.Ingredients.class).ingredients());
            syncAllergenMappings(
                    Json.readResource("seed/allergen-mapping.json", SeedFiles.AllergenMappings.class).mappings());
            syncWeightReferences(
                    Json.readResource("seed/weight-references.json", SeedFiles.WeightReferences.class).references());
            syncSafetyRules(Json.readResource("seed/safety-rules.json", SeedFiles.SafetyRules.class).rules());
            // Rules can point at a fixture recipe, so recipes go in before the rules.
            fixtureImporter.importAll();
            SeedFiles.AdaptationRules rules =
                    Json.readResource("seed/adaptation-rules.json", SeedFiles.AdaptationRules.class);
            syncIngredientRules(rules.ingredientRules());
            syncToolRules(rules.toolRules());
            linkUnmatchedRecipeIngredients();
            requireKnownImageKeys();
        });
        log.info("Seed data synced into the reference tables");
    }

    /** Image groups and files follow the manifest; a picture without artwork is stored as MISSING. */
    private void syncImages(ImageManifest manifest) {
        Map<String, String> groupIds = new HashMap<>();
        int groupOrder = 0;
        for (Map.Entry<String, ImageGroup> group : manifest.groups().entrySet()) {
            groupIds.put(group.getKey(), jdbc.sql("""
                            INSERT INTO image_group (code, name_ko, output_dir, target_size_px, sort_order)
                            VALUES (:code, :name, :outputDir, :size, :sortOrder)
                            ON CONFLICT (code) DO UPDATE
                            SET name_ko = EXCLUDED.name_ko, output_dir = EXCLUDED.output_dir,
                                target_size_px = EXCLUDED.target_size_px, sort_order = EXCLUDED.sort_order
                            RETURNING id
                            """)
                    .param("code", group.getKey())
                    .param("name", group.getValue().name())
                    .param("outputDir", group.getValue().outDir())
                    .param("size", group.getValue().size())
                    .param("sortOrder", groupOrder++)
                    .query(String.class).single());
        }
        int fileOrder = 0;
        for (ImageEntry image : manifest.images()) {
            ImageGroup group = manifest.groups().get(image.group());
            require(group != null, "manifest.json: unknown group " + image.group() + " for " + image.key());
            boolean ready = "ready".equals(image.status());
            String storedName = ready ? image.out() : null;
            jdbc.sql("""
                            INSERT INTO image_file (image_group_id, image_key, status, original_file_name,
                                                    stored_file_name, file_path, file_ext, width_px, height_px,
                                                    sort_order)
                            VALUES (:groupId, :key, :status, :originalName, :storedName, :path, :ext, :size, :size,
                                    :sortOrder)
                            ON CONFLICT (image_key) DO UPDATE
                            SET image_group_id = EXCLUDED.image_group_id, status = EXCLUDED.status,
                                original_file_name = EXCLUDED.original_file_name,
                                stored_file_name = EXCLUDED.stored_file_name, file_path = EXCLUDED.file_path,
                                file_ext = EXCLUDED.file_ext, width_px = EXCLUDED.width_px,
                                height_px = EXCLUDED.height_px, sort_order = EXCLUDED.sort_order
                            """)
                    .param("groupId", groupIds.get(image.group()))
                    .param("key", image.key())
                    .param("status", ready ? "READY" : "MISSING")
                    .param("originalName", ready ? image.source() : null)
                    .param("storedName", storedName)
                    .param("path", ready ? group.outDir() + "/" + storedName : null)
                    .param("ext", ready ? storedName.substring(storedName.lastIndexOf('.') + 1) : null)
                    .param("size", ready ? group.size() : null)
                    .param("sortOrder", fileOrder++)
                    .update();
        }
    }

    /** Every picture key used by tools, fill levels and recipe steps must be a known image. */
    private void requireKnownImageKeys() {
        List<String> unknown = jdbc.sql("""
                        SELECT used.image_key FROM (
                            SELECT icon_key AS image_key FROM tool_variant
                            UNION SELECT image_key FROM fill_level
                            UNION SELECT action_key FROM recipe_step WHERE action_key IS NOT NULL
                        ) used
                        WHERE NOT EXISTS (SELECT 1 FROM image_file f WHERE f.image_key = used.image_key)
                        ORDER BY 1
                        """)
                .query(String.class).list();
        require(unknown.isEmpty(), "picture keys missing from manifest.json: " + unknown);
    }

    private void syncTools(List<Tool> tools) {
        for (Tool tool : tools) {
            CupSize cup = tool.cup();
            if (cup != null) {
                requireCupVolume(tool, cup);
            }
            int updated = jdbc.sql("""
                            UPDATE tool_variant
                            SET representative_volume_ml = :volume, value_source = :valueSource,
                                source_ref = :sourceRef, is_approximate = TRUE,
                                is_standard_measure = :standardMeasure,
                                top_diameter_mm = :top, bottom_diameter_mm = :bottom, height_mm = :height,
                                fill_line_from_top_mm = :fillLine
                            WHERE code = :code
                            """)
                    .param("volume", tool.representativeVolumeMl())
                    .param("valueSource", tool.valueSource().name())
                    .param("sourceRef", tool.sourceRef())
                    .param("standardMeasure", Boolean.TRUE.equals(tool.standardMeasure()))
                    .param("top", cup == null ? null : cup.topDiameterMm())
                    .param("bottom", cup == null ? null : cup.bottomDiameterMm())
                    .param("height", cup == null ? null : cup.heightMm())
                    .param("fillLine", cup == null ? null : cup.fillLineFromTopMm())
                    .param("code", tool.code())
                    .update();
            require(updated == 1, "tools.json: unknown tool code " + tool.code());
        }
    }

    /** Volume of the cup (a cone frustum) up to its fill line, in mL. */
    static double cupVolumeMl(CupSize cup) {
        double liquidHeight = cup.heightMm() - cup.fillLineFromTopMm();
        double bottomRadius = cup.bottomDiameterMm() / 2;
        double topRadius = cup.topDiameterMm() / 2;
        double surfaceRadius = bottomRadius + (topRadius - bottomRadius) * (liquidHeight / cup.heightMm());
        double cubicMm = Math.PI * liquidHeight / 3
                * (surfaceRadius * surfaceRadius + surfaceRadius * bottomRadius + bottomRadius * bottomRadius);
        return cubicMm / 1000;
    }

    private void requireCupVolume(Tool tool, CupSize cup) {
        double computed = cupVolumeMl(cup);
        double difference = Math.abs(computed - tool.representativeVolumeMl()) / computed;
        require(difference <= CUP_VOLUME_TOLERANCE, "tools.json: " + tool.code() + " volume "
                + tool.representativeVolumeMl() + " mL does not match its dimensions (" + Math.round(computed) + " mL)");
    }

    /**
     * Stored recipe ingredients that had no dictionary entry are matched again, so a recipe
     * imported earlier gains allergens and safety notes when the dictionary grows. Exact name
     * or alias only; an existing link is never changed.
     */
    private void linkUnmatchedRecipeIngredients() {
        int linked = jdbc.sql("""
                        UPDATE recipe_ingredient ri
                        SET ingredient_id = known.id
                        FROM (SELECT id, name_ko AS name FROM ingredient
                              UNION
                              SELECT ingredient_id, alias FROM ingredient_alias) known
                        WHERE ri.ingredient_id IS NULL AND ri.name_ko = known.name
                        """)
                .update();
        if (linked > 0) {
            log.info("Linked {} stored recipe ingredients to the dictionary", linked);
        }
    }

    private void syncIngredients(List<Ingredient> ingredients) {
        jdbc.sql("DELETE FROM ingredient_alias").update();
        for (Ingredient ingredient : ingredients) {
            String id = jdbc.sql("""
                            INSERT INTO ingredient (code, name_ko, category, form, default_sensitivity, is_garnish)
                            VALUES (:code, :name, :category, :form, :sensitivity, :garnish)
                            ON CONFLICT (code) DO UPDATE
                            SET name_ko = EXCLUDED.name_ko, category = EXCLUDED.category, form = EXCLUDED.form,
                                default_sensitivity = EXCLUDED.default_sensitivity, is_garnish = EXCLUDED.is_garnish
                            RETURNING id
                            """)
                    .param("code", ingredient.code())
                    .param("name", ingredient.name())
                    .param("category", ingredient.category())
                    .param("form", ingredient.form())
                    .param("sensitivity", ingredient.sensitivity().name())
                    .param("garnish", ingredient.garnish())
                    .query(String.class).single();
            for (String alias : ingredient.aliases()) {
                jdbc.sql("INSERT INTO ingredient_alias (ingredient_id, alias) VALUES (:id, :alias)")
                        .param("id", id).param("alias", alias).update();
            }
        }
    }

    private void syncAllergenMappings(List<AllergenMapping> mappings) {
        jdbc.sql("DELETE FROM ingredient_allergen").update();
        jdbc.sql("""
                        UPDATE ingredient
                        SET allergen_verified_by = NULL, allergen_verified_at = NULL,
                            allergen_review_level = NULL, allergen_product_varies = FALSE
                        """)
                .update();
        for (AllergenMapping mapping : mappings) {
            String ingredientId = ingredientId(mapping.ingredient(), "allergen-mapping.json");
            boolean verified = isVerifiedByPerson(mapping.verifiedBy());
            String reviewLevel = reviewLevelOf(mapping);
            jdbc.sql("""
                            UPDATE ingredient
                            SET allergen_review_level = :level, allergen_product_varies = :productVaries,
                                allergen_verified_by = :verifiedBy, allergen_verified_at = :verifiedAt
                            WHERE id = :id
                            """)
                    .param("level", reviewLevel)
                    .param("productVaries", mapping.productVaries())
                    .param("verifiedBy", verified ? mapping.verifiedBy() : null)
                    .param("verifiedAt", verified ? toTime(mapping.verifiedAt()) : null)
                    .param("id", ingredientId)
                    .update();
            mapping.allergens().forEach(allergen -> jdbc.sql("""
                            INSERT INTO ingredient_allergen (ingredient_id, allergen_id, is_hidden, review_level,
                                                             verified_by, verified_at)
                            VALUES (:ingredientId, :allergenId, :hidden, :level, :verifiedBy, :verifiedAt)
                            """)
                    .param("ingredientId", ingredientId)
                    .param("allergenId", idOf("allergen", allergen.code(), "allergen-mapping.json"))
                    .param("hidden", allergen.hidden())
                    .param("level", reviewLevel)
                    .param("verifiedBy", mapping.verifiedBy())
                    .param("verifiedAt", toTime(mapping.verifiedAt()))
                    .update());
        }
    }

    private void syncWeightReferences(List<WeightRef> references) {
        jdbc.sql("DELETE FROM weight_reference").update();
        for (WeightRef reference : references) {
            jdbc.sql("""
                            INSERT INTO weight_reference (ingredient_id, tool_variant_id, unit_label_ko, approx_grams,
                                                          size_note_ko, value_source, source_ref)
                            VALUES (:ingredientId, :toolId, :unitLabel, :grams, :sizeNote, :valueSource, :sourceRef)
                            """)
                    .param("ingredientId", ingredientId(reference.ingredient(), "weight-references.json"))
                    .param("toolId", idOf("tool_variant", reference.tool(), "weight-references.json"))
                    .param("unitLabel", reference.unitLabel())
                    .param("grams", reference.grams())
                    .param("sizeNote", reference.sizeNote())
                    .param("valueSource", reference.valueSource().name())
                    .param("sourceRef", reference.sourceRef())
                    .update();
        }
    }

    private void syncSafetyRules(List<SafetyRule> rules) {
        jdbc.sql("DELETE FROM safety_rule").update();
        for (SafetyRule rule : rules) {
            if (isUnsigned(rule.verifiedBy(), "safety rule " + rule.code())) {
                continue;
            }
            jdbc.sql("""
                            INSERT INTO safety_rule (code, trigger_type, trigger_value, message_ko, detail_low_ko,
                                                     placement, verified_by, verified_at)
                            VALUES (:code, :triggerType, :triggerValue, :message, :detail, :placement,
                                    :verifiedBy, :verifiedAt)
                            """)
                    .param("code", rule.code())
                    .param("triggerType", rule.triggerType().name())
                    .param("triggerValue", rule.triggerValue())
                    .param("message", rule.message())
                    .param("detail", rule.detailForLow())
                    .param("placement", rule.placement().name())
                    .param("verifiedBy", rule.verifiedBy())
                    .param("verifiedAt", toTime(rule.verifiedAt()))
                    .update();
        }
    }

    private void syncIngredientRules(List<IngredientRule> rules) {
        jdbc.sql("DELETE FROM adaptation_rule").update();
        for (IngredientRule rule : rules) {
            if (isUnsigned(rule.verifiedBy(), "adaptation rule for " + rule.ingredient())) {
                continue;
            }
            jdbc.sql("""
                            INSERT INTO adaptation_rule (type, ingredient_id, substitute_ingredient_id, impact_note_ko,
                                                         applies_to_recipe_id, priority, verified_by, verified_at)
                            VALUES (:type, :ingredientId, :substituteId, :impact, :recipeId, :priority,
                                    :verifiedBy, :verifiedAt)
                            """)
                    .param("type", rule.type().name())
                    .param("ingredientId", ingredientId(rule.ingredient(), "adaptation-rules.json"))
                    .param("substituteId",
                            rule.substitute() == null ? null : ingredientId(rule.substitute(), "adaptation-rules.json"))
                    .param("impact", rule.impact())
                    .param("recipeId", fixtureRecipeId(rule.recipeRef()))
                    .param("priority", rule.priority())
                    .param("verifiedBy", rule.verifiedBy())
                    .param("verifiedAt", toTime(rule.verifiedAt()))
                    .update();
        }
    }

    private void syncToolRules(List<ToolRule> rules) {
        jdbc.sql("DELETE FROM tool_substitution_rule").update();
        for (ToolRule rule : rules) {
            if (isUnsigned(rule.verifiedBy(), "tool rule for " + rule.requiredTool())) {
                continue;
            }
            jdbc.sql("""
                            INSERT INTO tool_substitution_rule (required_tool_id, substitute_tool_id, condition_note_ko,
                                                                applies_to_recipe_id, extra_safety_note_ko,
                                                                verified_by, verified_at)
                            VALUES (:requiredId, :substituteId, :condition, :recipeId, :extraSafety,
                                    :verifiedBy, :verifiedAt)
                            """)
                    .param("requiredId", idOf("tool_variant", rule.requiredTool(), "adaptation-rules.json"))
                    .param("substituteId", idOf("tool_variant", rule.substituteTool(), "adaptation-rules.json"))
                    .param("condition", rule.conditionNote())
                    .param("recipeId", fixtureRecipeId(rule.recipeRef()))
                    .param("extraSafety", rule.extraSafetyNote())
                    .param("verifiedBy", rule.verifiedBy())
                    .param("verifiedAt", toTime(rule.verifiedAt()))
                    .update();
        }
    }

    /**
     * HUMAN_VERIFIED needs the name of the person who checked the mapping; the server never
     * takes that level from the file alone. AI_RESEARCHED is a mapping researched from public
     * sources. Anything else is a draft: enough to warn about an allergen, not to count as checked.
     */
    private static String reviewLevelOf(AllergenMapping mapping) {
        if (isVerifiedByPerson(mapping.verifiedBy())) {
            return "HUMAN_VERIFIED";
        }
        return "AI_RESEARCHED".equals(mapping.reviewLevel()) ? "AI_RESEARCHED" : null;
    }

    private static boolean isVerifiedByPerson(String verifiedBy) {
        return verifiedBy != null && !verifiedBy.isBlank() && !DRAFT_MARK.equals(verifiedBy);
    }

    /** The rule tables require a verifier; entries without one are left out and reported. */
    private boolean isUnsigned(String verifiedBy, String what) {
        if (verifiedBy == null || verifiedBy.isBlank()) {
            log.warn("Skipped {}: verifiedBy is missing", what);
            return true;
        }
        return false;
    }

    private String ingredientId(String code, String file) {
        return idOf("ingredient", code, file);
    }

    // table is one of a fixed set of names passed by this class, never user input
    private String idOf(String table, String code, String file) {
        return jdbc.sql("SELECT id FROM " + table + " WHERE code = :code")
                .param("code", code)
                .query(String.class).optional()
                .orElseThrow(() -> new IllegalStateException(file + ": unknown " + table + " code " + code));
    }

    private String fixtureRecipeId(String recipeRef) {
        if (recipeRef == null) {
            return null;
        }
        return fixtureImporter.findRecipeId(recipeRef)
                .orElseThrow(() -> new IllegalStateException("adaptation-rules.json: unknown recipeRef " + recipeRef));
    }

    private static OffsetDateTime toTime(String text) {
        return text == null ? null : OffsetDateTime.parse(text);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Invalid seed data - " + message);
        }
    }
}
