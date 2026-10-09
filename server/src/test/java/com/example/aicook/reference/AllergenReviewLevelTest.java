package com.example.aicook.reference;

import com.example.aicook.reference.Rules.AllergenRef;
import com.example.aicook.reference.Rules.ReviewLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Review levels of the allergen mapping (spec "매핑 검토 등급", CLAUDE.md rule 13-1) as synced
 * from data/seed into the test database.
 */
// The API key is cleared so tests never call the AI API.
@SpringBootTest(properties = {"spring.datasource.url=${SPRING_DATASOURCE_TEST_URL}", "openai.api-key="})
@AutoConfigureMockMvc
class AllergenReviewLevelTest {

    @Autowired
    private ReferenceData reference;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MockMvc mockMvc;

    @Test
    void researchedIngredientCountsAsReviewedEvenWithNoAllergens() {
        assertThat(reference.allergenReviewLevel("onion")).isEqualTo(ReviewLevel.AI_RESEARCHED);
        assertThat(reference.allergensOf("onion")).hasValue(java.util.List.of());
        assertThat(reference.isAllergenReviewed("onion")).isTrue();
    }

    @Test
    void soySauceWarnsAboutSoybeanAndWheatAsHiddenAndProductDependent() {
        assertThat(reference.allergensOf("soy_sauce").orElseThrow())
                .containsExactlyInAnyOrder(new AllergenRef("soybean", true), new AllergenRef("wheat", true));
        assertThat(reference.allergenProductVaries("soy_sauce")).isTrue();
        assertThat(reference.allergenProductVaries("egg")).isFalse();
    }

    @Test
    void ingredientOutsideTheDictionaryOrWithADraftMappingStillNeedsChecking() {
        assertThat(reference.isAllergenReviewed(null)).isFalse();
        assertThat(reference.isAllergenReviewed("no_such_ingredient")).isFalse();
        // In the dictionary, but the research file does not cover it.
        assertThat(reference.ingredient("ginger_powder")).isPresent();
        assertThat(reference.isAllergenReviewed("ginger_powder")).isFalse();
    }

    @Test
    void draftedAllergenIsKeptWhenTheResearchFoundNone() {
        // Conservative union: the earlier draft had soybean for cooking oil, the research had none.
        assertThat(reference.allergensOf("cooking_oil").orElseThrow()).contains(new AllergenRef("soybean", true));
    }

    @Test
    void noMappingIsMarkedAsVerifiedByAPerson() {
        assertThat(jdbc.sql("SELECT count(*) FROM ingredient WHERE allergen_review_level = 'HUMAN_VERIFIED'")
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM ingredient WHERE allergen_verified_by IS NOT NULL")
                .query(Integer.class).single()).isZero();
    }

    @Test
    void greenOnionIsFoundUnderItsShortName() {
        assertThat(jdbc.sql("""
                        SELECT i.code FROM ingredient_alias a JOIN ingredient i ON i.id = a.ingredient_id
                        WHERE a.alias = '파'
                        """)
                .query(String.class).single()).isEqualTo("green_onion");
    }

    @Test
    void recipeDetailCarriesReviewLevelAndProductFlagPerIngredient() throws Exception {
        String recipeId = jdbc.sql("SELECT id FROM recipe WHERE source = 'FIXTURE' ORDER BY id LIMIT 1")
                .query(String.class).single();

        mockMvc.perform(get("/api/recipes/" + recipeId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredients[?(@.name == '간장')].allergenReviewLevel").value("AI_RESEARCHED"))
                .andExpect(jsonPath("$.ingredients[?(@.name == '간장')].allergenProductVaries").value(true))
                .andExpect(jsonPath("$.ingredients[?(@.name == '간장')].allergyCheckNeeded").value(false))
                .andExpect(jsonPath("$.ingredients[?(@.name == '간장')].allergens[*].code",
                        containsInAnyOrder("soybean", "wheat")))
                .andExpect(jsonPath("$.ingredients[?(@.name == '김치')].allergens[*].code",
                        containsInAnyOrder("shrimp")));
    }
}
