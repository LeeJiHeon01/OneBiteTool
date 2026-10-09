package com.example.aicook;

import com.example.aicook.recipe.RecipeDtos.DetailIngredient;
import com.example.aicook.recipe.RecipeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of the 1st-phase endpoints (spec 8.2, 8.3.1) against the migrated test database. */
// The API key is cleared so tests never call the AI API.
@SpringBootTest(properties = {"spring.datasource.url=${SPRING_DATASOURCE_TEST_URL}", "openai.api-key="})
@AutoConfigureMockMvc
class ApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RecipeService recipes;
    @Autowired
    private JdbcClient jdbc;

    private String recipeId;
    private String greenOnionId;

    @BeforeEach
    void findFixture() {
        recipeId = recipes.search("김치볶음밥").get(0).id();
        greenOnionId = recipes.detail(recipeId).ingredients().stream()
                .filter(ingredient -> ingredient.name().equals("대파"))
                .map(DetailIngredient::id)
                .findFirst().orElseThrow();
    }

    @Test
    void migrationsAndSeedSyncFilledTheReferenceTables() {
        assertThat(count("tool_variant")).isEqualTo(28);
        assertThat(jdbc.sql("SELECT code FROM tool_variant WHERE is_standard_measure ORDER BY code")
                .query(String.class).list()).containsExactly("measuring_cup", "measuring_spoon");
        assertThat(count("weight_reference")).isEqualTo(3);
        assertThat(count("allergen")).isEqualTo(19);
        assertThat(count("fill_level")).isEqualTo(14);
        assertThat(count("safety_rule")).isEqualTo(12);
        assertThat(count("adaptation_rule")).isEqualTo(4);
        assertThat(count("tool_substitution_rule")).isEqualTo(1);
        assertThat(count("recipe")).isEqualTo(1);
        assertThat(count("recipe_step")).isEqualTo(8);
    }

    @Test
    void imageTablesFollowTheManifest() {
        assertThat(count("image_group")).isEqualTo(8);
        assertThat(count("image_file")).isEqualTo(71);
        assertThat(jdbc.sql("SELECT count(*) FROM image_file WHERE status = 'READY'").query(Long.class).single())
                .isEqualTo(46);
        assertThat(jdbc.sql("SELECT file_path FROM image_file WHERE image_key = 'tool.teaspoon'")
                .query(String.class).single()).isEqualTo("tools/teaspoon.png");
    }

    @Test
    void everyTableHasAuditColumnsAndEveryColumnHasAComment() {
        // tables missing any of created_at/created_by/updated_at/updated_by
        assertThat(jdbc.sql("""
                        SELECT t.table_name FROM information_schema.tables t
                        WHERE t.table_schema = 'public' AND t.table_name <> 'flyway_schema_history'
                          AND (SELECT count(*) FROM information_schema.columns c
                               WHERE c.table_schema = 'public' AND c.table_name = t.table_name
                                 AND c.column_name IN ('created_at', 'created_by', 'updated_at', 'updated_by')) < 4
                        """)
                .query(String.class).list()).isEmpty();
        // columns without a description
        assertThat(jdbc.sql("""
                        SELECT t.relname || '.' || a.attname
                        FROM pg_attribute a
                        JOIN pg_class t ON t.oid = a.attrelid
                        JOIN pg_namespace n ON n.oid = t.relnamespace
                        WHERE n.nspname = 'public' AND t.relkind = 'r' AND t.relname <> 'flyway_schema_history'
                          AND a.attnum > 0 AND NOT a.attisdropped
                          AND col_description(t.oid, a.attnum) IS NULL
                        """)
                .query(String.class).list()).isEmpty();
    }

    @Test
    void idsUseTheTablePrefixAndAreTwentyCharactersLong() {
        assertThat(recipeId).matches("RECIPE_[0-9]{13}");
        assertThat(greenOnionId).matches("RINGR_[0-9]{14}");
    }

    // table names are fixed strings from this test
    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    @Test
    void toolsListVolumesCupSizesAndFillLevels() throws Exception {
        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].id").value("measure"))
                .andExpect(jsonPath("$[0].variants", hasSize(12)))
                .andExpect(jsonPath("$[0].variants[?(@.id == 'spoon_rice')].representativeVolumeMl").value(10.0))
                .andExpect(jsonPath("$[0].variants[?(@.id == 'spoon_rice')].valueSource").value("ASSUMED"))
                .andExpect(jsonPath("$[0].variants[?(@.id == 'paper_cup_small')].cup.topDiameterMm").value(70.0))
                .andExpect(jsonPath("$[0].variants[?(@.id == 'measuring_spoon')].isStandardMeasure").value(true))
                .andExpect(jsonPath("$[0].variants[?(@.id == 'soju_glass')].isBasic").value(false))
                .andExpect(jsonPath("$[2].variants[?(@.id == 'oven')].heatSourceType").value("OVEN"))
                .andExpect(jsonPath("$[2].variants[0].heatSourceType").value("GAS"));
    }

    @Test
    void searchReturnsAllergenCodesIncludingHiddenOnes() throws Exception {
        mockMvc.perform(get("/api/recipes").param("q", "김치 볶음밥"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(recipeId))
                .andExpect(jsonPath("$[0].recipeBadge").value("IMPORTED"))
                .andExpect(jsonPath("$[0].allergenCodes", containsInAnyOrder("egg", "shrimp", "soybean", "wheat")))
                // Every ingredient of the fixture has a reviewed (AI_RESEARCHED) mapping.
                .andExpect(jsonPath("$[0].allergyCheckNeeded").value(false));
    }

    @Test
    void searchWithoutAMatchReturnsAnEmptyList() throws Exception {
        mockMvc.perform(get("/api/recipes").param("q", "없는요리"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void blankQueryIsRejected() throws Exception {
        mockMvc.perform(get("/api/recipes").param("q", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_QUERY"));
    }

    @Test
    void detailListsIngredientsToolsAndSteps() throws Exception {
        mockMvc.perform(get("/api/recipes/" + recipeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredients", hasSize(8)))
                .andExpect(jsonPath("$.ingredients[2].name").value("간장"))
                .andExpect(jsonPath("$.ingredients[2].rawText").value("1큰술"))
                .andExpect(jsonPath("$.ingredients[2].allergens[0].hidden").value(true))
                .andExpect(jsonPath("$.requiredTools[*].id", containsInAnyOrder("knife", "frying_pan", "stove")))
                .andExpect(jsonPath("$.steps", hasSize(8)));
    }

    @Test
    void unknownRecipeIsNotFound() throws Exception {
        mockMvc.perform(get("/api/recipes/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"));
        mockMvc.perform(get("/api/recipes/not-a-number"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adaptationReturnsACookablePlan() throws Exception {
        String body = """
                {
                  "recipeId": "%s",
                  "servings": 1,
                  "missingIngredientIds": ["%s"],
                  "profile": {
                    "skillLevel": "LOW",
                    "toolVariantIds": ["spoon_rice", "teaspoon", "paper_cup_small", "knife", "gas_stove", "frying_pan"],
                    "allergenCodes": ["egg"]
                  }
                }
                """.formatted(recipeId, greenOnionId);

        mockMvc.perform(post("/api/recipe-adaptations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COOKABLE"))
                .andExpect(jsonPath("$.changes[0].type").value("OMIT"))
                .andExpect(jsonPath("$.allergyWarnings[0].allergenCode").value("egg"))
                .andExpect(jsonPath("$.ingredients", hasSize(7)))
                .andExpect(jsonPath("$.steps", hasSize(7)))
                .andExpect(jsonPath("$.steps[2].durationSeconds").value(180))
                .andExpect(jsonPath("$.steps[2].heatVisualKey").value("heat.gas.MEDIUM"));
    }

    @Test
    void malformedBodyIsRejectedWithoutEchoingIt() throws Exception {
        String body = "{\"recipeId\": \"1\", \"profile\": {\"skillLevel\": \"EXPERT\"}}";

        mockMvc.perform(post("/api/recipe-adaptations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BODY"));
    }
}
