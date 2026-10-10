package com.example.aicook.seed;

import com.example.aicook.reference.Rules.IngredientRuleType;
import com.example.aicook.reference.Rules.Placement;
import com.example.aicook.reference.Rules.TriggerType;
import com.example.aicook.reference.Sensitivity;
import com.example.aicook.reference.ValueSource;

import java.util.List;
import java.util.Map;

/** Shapes of the JSON files under data/seed and data/fixtures. */
final class SeedFiles {

    private SeedFiles() {
    }

    // tools.json: measured values only; names and categories come from V2__seed_catalog.sql
    record Tools(List<Tool> tools) {
    }

    /** standardMeasure marks a measuring spoon or cup; such tools have no volume of their own. */
    record Tool(String code, Boolean standardMeasure, Double representativeVolumeMl, ValueSource valueSource,
                String sourceRef, CupSize cup) {
    }

    record CupSize(double topDiameterMm, double bottomDiameterMm, double heightMm, double fillLineFromTopMm) {
    }

    // ingredients.json
    record Ingredients(List<Ingredient> ingredients) {
    }

    record Ingredient(String code, String name, String category, String form, Sensitivity sensitivity,
                      boolean garnish, List<String> aliases) {
    }

    // allergen-mapping.json
    record AllergenMappings(List<AllergenMapping> mappings) {
    }

    /** reviewLevel is "AI_RESEARCHED" or absent; HUMAN_VERIFIED comes from verifiedBy only. */
    record AllergenMapping(String ingredient, List<AllergenLink> allergens, boolean productVaries,
                           String reviewLevel, String verifiedBy, String verifiedAt) {
    }

    record AllergenLink(String code, boolean hidden) {
    }

    // safety-rules.json
    record SafetyRules(List<SafetyRule> rules) {
    }

    record SafetyRule(String code, TriggerType triggerType, String triggerValue, String message,
                      String detailForLow, Placement placement, boolean requiresHeat, String verifiedBy,
                      String verifiedAt) {
    }

    // adaptation-rules.json
    record AdaptationRules(List<IngredientRule> ingredientRules, List<ToolRule> toolRules) {
    }

    /** recipeRef is the fixture ref the rule is limited to, or null for every recipe. */
    record IngredientRule(IngredientRuleType type, String ingredient, String substitute, String impact,
                          String recipeRef, int priority, String verifiedBy, String verifiedAt) {
    }

    record ToolRule(String requiredTool, String substituteTool, String conditionNote, String recipeRef,
                    String extraSafetyNote, String verifiedBy, String verifiedAt) {
    }

    // weight-references.json
    record WeightReferences(List<WeightRef> references) {
    }

    /** The ingredient weighs {@code grams} when {@code tool} is filled once. */
    record WeightRef(String ingredient, String tool, String unitLabel, double grams, String sizeNote,
                     ValueSource valueSource, String sourceRef) {
    }

    // assets/source/images/manifest.json (on the classpath as images/manifest.json)
    record ImageManifest(Map<String, ImageGroup> groups, List<ImageEntry> images) {
    }

    /** outDir and size are null for groups that have no prepared pictures yet. */
    record ImageGroup(String name, String outDir, Integer size) {
    }

    /** status is "ready" or "missing"; source and out are null for missing pictures. */
    record ImageEntry(String key, String group, String source, String out, String status) {
    }

    // fixtures/recipes/*.json
    record FixtureRecipe(String ref, String name, double baseServings, Integer totalMinutes,
                         String verificationStatus, List<FixtureIngredient> ingredients, List<FixtureStep> steps) {
    }

    /** ingredient is a dictionary code or null; amount is the text as written ("1큰술"). */
    record FixtureIngredient(String key, String ingredient, String name, String amount, boolean required) {
    }

    /** durationMaxSeconds is the long end of a time range ("3~4분"); left out when the time is not a range. */
    record FixtureStep(String action, String actionKey, List<String> ingredients, List<String> tools,
                       Integer durationSeconds, Integer durationMaxSeconds, String timerStartCondition,
                       String heatLevel, String heatRaw, String deviceSettingRaw, String completionCheck) {
    }
}
