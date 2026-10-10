package com.example.aicook.recipeimport;

import com.example.aicook.adaptation.AdaptationRequest;
import com.example.aicook.adaptation.AdaptationRequest.Profile;
import com.example.aicook.adaptation.AdaptationResponse;
import com.example.aicook.adaptation.AdaptationResponse.ItemKind;
import com.example.aicook.adaptation.AdaptationResponse.RequiredItem;
import com.example.aicook.adaptation.AdaptationService;
import com.example.aicook.recipe.RecipeDtos.MissingTool;
import com.example.aicook.recipe.RecipeDtos.SearchResult;
import com.example.aicook.recipe.RecipeDtos.ToolFit;
import com.example.aicook.recipe.RecipeService;
import com.example.aicook.recipe.Recipe.SkillLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Can I make this with my tools?" on search results and the recipe detail: decided by the
 * same rules as the adaptation, without the AI, from tool codes sent as a query parameter.
 */
// The API key is cleared so tests never call the AI API.
@SpringBootTest(properties = {"spring.datasource.url=${SPRING_DATASOURCE_TEST_URL}", "openai.api-key="})
@AutoConfigureMockMvc
class ToolFitTest {

    private static final String DOMAIN = "tool-fit.example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private RecipeService recipes;
    @Autowired
    private ImportedRecipeWriter writer;
    @Autowired
    private AdaptationService adaptations;

    private String friedRiceId;

    @BeforeEach
    void setUp() {
        cleanUp();
        friedRiceId = recipes.search("김치볶음밥").get(0).id();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM recipe WHERE source_domain = :domain").param("domain", DOMAIN).update();
    }

    @Test
    void recipeFitsWhenEveryNeededToolIsOwned() {
        ToolFit fit = recipes.detail(friedRiceId, List.of("knife", "frying_pan", "gas_stove")).toolFit();

        assertThat(fit.ok()).isTrue();
        assertThat(fit.missingTools()).isEmpty();
    }

    @Test
    void missingToolIsNamed() {
        ToolFit fit = recipes.detail(friedRiceId, List.of("knife", "gas_stove")).toolFit();

        assertThat(fit.ok()).isFalse();
        assertThat(fit.missingTools()).containsExactly(new MissingTool("frying_pan", "프라이팬"));
    }

    @Test
    void missingToolNamesTheToolAVerifiedRuleAcceptsInstead() {
        // Scissors may replace the knife in this recipe, so either one is enough.
        ToolFit fit = recipes.detail(friedRiceId, List.of("frying_pan", "gas_stove")).toolFit();

        assertThat(fit.missingTools()).containsExactly(new MissingTool("knife", "칼 또는 가위"));
    }

    @Test
    void verifiedSubstitutionRuleCountsAsOwningTheTool() {
        // The fixture has a knife -> scissors rule.
        ToolFit fit = recipes.detail(friedRiceId, List.of("scissors", "frying_pan", "induction")).toolFit();

        assertThat(fit.ok()).isTrue();
    }

    @Test
    void stewNeedsAPotOrPanAndAStove() {
        String stewId = boiledRecipe("도구찌개");

        ToolFit withoutPot = recipes.detail(stewId, List.of("gas_stove", "spoon_rice")).toolFit();
        ToolFit withPot = recipes.detail(stewId, List.of("gas_stove", "pot")).toolFit();
        ToolFit withoutStove = recipes.detail(stewId, List.of("pot")).toolFit();

        assertThat(withoutPot.ok()).isFalse();
        assertThat(withoutPot.missingTools()).extracting(MissingTool::id).containsExactly("cooking_vessel");
        assertThat(withPot.ok()).isTrue();
        assertThat(withoutStove.missingTools()).extracting(MissingTool::id).containsExactly("stove");
    }

    @Test
    void fitAgreesWithWhatTheAdaptationRequires() {
        String stewId = boiledRecipe("도구찌개");
        List<List<String>> toolSets = List.of(
                List.of("knife", "frying_pan", "gas_stove"), List.of("scissors", "frying_pan", "induction"),
                List.of("knife"), List.of("pot"), List.of("gas_stove"), List.of("spoon_rice"));

        for (String recipeId : List.of(friedRiceId, stewId)) {
            for (List<String> tools : toolSets) {
                AdaptationResponse response = adaptations.adapt(new AdaptationRequest(recipeId, 1, List.of(),
                        List.of(), new Profile(SkillLevel.LOW, tools, null, List.of())));
                List<String> required = response.requiredItems().stream()
                        .filter(item -> item.kind() == ItemKind.TOOL).map(RequiredItem::id).toList();

                assertThat(recipes.detail(recipeId, tools).toolFit().missingTools())
                        .extracting(MissingTool::id).containsExactlyElementsOf(required);
            }
        }
    }

    @Test
    void searchPutsRecipesTheUserCanMakeFirst() {
        // Stored in this order; only the second one can be made without a stove.
        boiledRecipe("정렬요리 끓임");
        mixedRecipe("정렬요리 무침");

        List<SearchResult> plain = recipes.search("정렬요리");
        List<SearchResult> withTools = recipes.search("정렬요리", List.of("spoon_rice"));
        List<SearchResult> withEverything = recipes.search("정렬요리", List.of("pot", "gas_stove"));

        assertThat(plain).extracting(SearchResult::name).containsExactly("정렬요리 끓임", "정렬요리 무침");
        assertThat(plain).extracting(SearchResult::toolFit).containsOnlyNulls();
        assertThat(withTools).extracting(SearchResult::name).containsExactly("정렬요리 무침", "정렬요리 끓임");
        assertThat(withTools.get(1).toolFit().ok()).isFalse();
        // Nothing to reorder when both can be made.
        assertThat(withEverything).extracting(SearchResult::name).containsExactly("정렬요리 끓임", "정렬요리 무침");
    }

    @Test
    void toolsArriveAsAQueryParameterAndUnknownCodesAreIgnored() throws Exception {
        mockMvc.perform(get("/api/recipes/" + friedRiceId).param("tools", "knife,gas_stove,no_such_tool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolFit.ok").value(false))
                .andExpect(jsonPath("$.toolFit.missingTools", hasSize(1)))
                .andExpect(jsonPath("$.toolFit.missingTools[0].id").value("frying_pan"))
                .andExpect(jsonPath("$.toolFit.missingTools[0].name").value("프라이팬"));
        mockMvc.perform(get("/api/recipes").param("q", "김치볶음밥").param("tools", "knife,frying_pan,gas_stove"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toolFit.ok").value(true));
        mockMvc.perform(get("/api/recipes/suggestions").param("tools", "knife"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toolFit.ok").value(false));
    }

    @Test
    void withoutToolsNoFitIsReturned() throws Exception {
        mockMvc.perform(get("/api/recipes/" + friedRiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolFit").value(nullValue()));
        mockMvc.perform(get("/api/recipes").param("q", "김치볶음밥"))
                .andExpect(jsonPath("$[0].toolFit").value(nullValue()));
    }

    @Test
    void similarNamesAreFoundAmongStoredRecipesOnly() throws Exception {
        boiledRecipe("비슷한찌개 하나");
        mixedRecipe("전혀다른무침");

        assertThat(recipes.similar("비슷한찌개 둘", null)).extracting(SearchResult::name)
                .containsExactly("비슷한찌개 하나");
        assertThat(recipes.similar("쿠키", null)).isEmpty();
        mockMvc.perform(get("/api/recipes/similar").param("q", "비슷한찌개").param("tools", "pot,gas_stove"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("비슷한찌개 하나"))
                .andExpect(jsonPath("$[0].toolFit.ok").value(true));
    }

    /** A recipe with one boiling step that names no tool. */
    private String boiledRecipe(String name) {
        return save(name, "action.boil", "물을 끓여요");
    }

    /** A recipe with one step that needs no tool at all. */
    private String mixedRecipe(String name) {
        return save(name, null, "재료를 섞어요");
    }

    private String save(String name, String actionKey, String action) {
        Extract.Ingredient water = new Extract.Ingredient("물", "500ml", null, "물 500ml");
        Extract.Step step = new Extract.Step(action, actionKey, List.of("물"), List.of(), null, null, null, null,
                null, null, null, action);
        Extract.Page page = new Extract.Page(true, name, null, null, null, List.of(water), List.of(step));
        return writer.save(name, "https://" + DOMAIN + "/" + name.hashCode(), null, null, page);
    }
}
