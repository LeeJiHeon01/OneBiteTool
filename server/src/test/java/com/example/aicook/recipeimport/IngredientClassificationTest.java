package com.example.aicook.recipeimport;

import com.example.aicook.adaptation.AdaptationRequest;
import com.example.aicook.adaptation.AdaptationRequest.Profile;
import com.example.aicook.adaptation.AdaptationResponse;
import com.example.aicook.adaptation.AdaptationResponse.AdaptedIngredient;
import com.example.aicook.adaptation.AdaptationService;
import com.example.aicook.measure.MeasureResult.QuantityStatus;
import com.example.aicook.recipe.Recipe.SkillLevel;
import com.example.aicook.reference.ReferenceData;
import com.example.aicook.reference.Sensitivity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ingredient category, form and sensitivity from data/seed (2026-10-09 classification), and how
 * they reach an adapted recipe: which ingredients get "taste and add", and the ratio tolerance.
 */
// The API key is cleared so tests never call the AI API.
@SpringBootTest(properties = {"spring.datasource.url=${SPRING_DATASOURCE_TEST_URL}", "openai.api-key="})
class IngredientClassificationTest {

    private static final String DOMAIN = "classification.example.com";

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ReferenceData reference;
    @Autowired
    private ImportedRecipeWriter writer;
    @Autowired
    private AdaptationService adaptations;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM recipe WHERE source_domain = :domain").param("domain", DOMAIN).update();
    }

    @Test
    void everyIngredientHasCategoryFormAndSensitivity() {
        assertThat(jdbc.sql("SELECT count(*) FROM ingredient WHERE category IS NULL OR form IS NULL")
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM ingredient WHERE default_sensitivity = 'RATIO'")
                .query(Integer.class).single()).isEqualTo(5);
    }

    @Test
    void fishSauceAndSesameSaltAreTheirOwnSeasoningIngredients() {
        assertThat(reference.sensitivityOf("fish_sauce")).isEqualTo(Sensitivity.HIGH);
        assertThat(reference.sensitivityOf("sesame_salt")).isEqualTo(Sensitivity.HIGH);
        assertThat(reference.isAllergenReviewed("fish_sauce")).isTrue();
        assertThat(reference.isAllergenReviewed("sesame_salt")).isTrue();
        assertThat(ownerOf("멸치액젓")).isEqualTo("fish_sauce");
        assertThat(ownerOf("피시소스")).isEqualTo("fish_sauce");
        // No longer an alias of sesame or anchovy.
        assertThat(jdbc.sql("SELECT count(*) FROM ingredient_alias WHERE alias = '깨소금'")
                .query(Integer.class).single()).isZero();
        assertThat(reference.sensitivityOf("anchovy")).isEqualTo(Sensitivity.NORMAL);
        assertThat(reference.sensitivityOf("sesame")).isEqualTo(Sensitivity.NORMAL);
    }

    @Test
    void cookingAlcoholIsNotSeasoningButOysterAndSoySauceAre() {
        AdaptationResponse response = adapt(recipe("양념",
                ingredient("청주", "1큰술"), ingredient("맛술", "1큰술"), ingredient("와인", "1큰술"),
                ingredient("굴소스", "1큰술"), ingredient("간장", "1큰술")), "spoon_rice");

        assertThat(tasteAdjust(response, "청주")).isFalse();
        assertThat(tasteAdjust(response, "맛술")).isFalse();
        assertThat(tasteAdjust(response, "와인")).isFalse();
        assertThat(tasteAdjust(response, "굴소스")).isTrue();
        assertThat(tasteAdjust(response, "간장")).isTrue();
    }

    @Test
    void flourOutsideTheTenPercentToleranceNeedsChecking() {
        // 0.28큰술 is 0.42 rice spoons; the nearest picture (a half) is 19% more.
        AdaptationResponse response = adapt(recipe("반죽",
                ingredient("밀가루", "0.28큰술"), ingredient("참깨", "0.28큰술")), "spoon_rice");

        assertThat(row(response, "밀가루").sensitivity()).isEqualTo(Sensitivity.RATIO);
        assertThat(row(response, "밀가루").quantityStatus()).isEqualTo(QuantityStatus.NEEDS_CHECK);
        assertThat(row(response, "밀가루").display().text()).isEqualTo("0.28큰술");
        // An ordinary ingredient of the same amount is within its 25% tolerance.
        assertThat(row(response, "참깨").quantityStatus()).isEqualTo(QuantityStatus.CONVERTED_APPROX);
    }

    @Test
    void waterMixedWithARatioIngredientIsHeldToTheRatioTolerance() {
        double cupMl = reference.tool("paper_cup_small").orElseThrow().representativeVolumeMl();
        // 1.19 cups: the nearest mark (1 and 1/3) is 12% more: fine for ordinary water, not for rice water.
        String water = Math.round(cupMl * 1.19) + "ml";

        AdaptationResponse withRice = adapt(recipe("밥", ingredient("쌀", "1컵"), ingredient("물", water)),
                "paper_cup_small");
        AdaptationResponse alone = adapt(recipe("물", ingredient("물", water)), "paper_cup_small");

        assertThat(row(withRice, "물").sensitivity()).isEqualTo(Sensitivity.RATIO);
        assertThat(row(withRice, "물").quantityStatus()).isEqualTo(QuantityStatus.NEEDS_CHECK);
        assertThat(row(alone, "물").sensitivity()).isEqualTo(Sensitivity.NORMAL);
        assertThat(row(alone, "물").quantityStatus()).isEqualTo(QuantityStatus.CONVERTED_APPROX);
    }

    @Test
    void adaptedIngredientTellsWhenItsAllergensDifferByProduct() {
        AdaptationResponse response = adapt(recipe("양념", ingredient("간장", "1큰술"), ingredient("양파", "1개")),
                "spoon_rice");

        assertThat(row(response, "간장").allergenProductVaries()).isTrue();
        assertThat(row(response, "양파").allergenProductVaries()).isFalse();
    }

    private String ownerOf(String alias) {
        return jdbc.sql("""
                        SELECT i.code FROM ingredient_alias a JOIN ingredient i ON i.id = a.ingredient_id
                        WHERE a.alias = :alias
                        """)
                .param("alias", alias).query(String.class).single();
    }

    private static Extract.Ingredient ingredient(String name, String amount) {
        return new Extract.Ingredient(name, amount, null, name + " " + amount);
    }

    /** A stored recipe with one step that uses every ingredient. */
    private String recipe(String name, Extract.Ingredient... ingredients) {
        List<String> names = List.of(ingredients).stream().map(Extract.Ingredient::name).toList();
        Extract.Step step = new Extract.Step("재료를 모두 넣어요", null, names, List.of(), null, null, null, null,
                null, null, null, "재료를 넣는다");
        Extract.Page page = new Extract.Page(true, name, null, null, null, List.of(ingredients), List.of(step));
        return writer.save(name, "https://" + DOMAIN + "/" + name.hashCode() + "/" + names.hashCode(), null, null,
                page);
    }

    private AdaptationResponse adapt(String recipeId, String... toolIds) {
        Profile profile = new Profile(SkillLevel.LOW, List.of(toolIds), null, List.of());
        AdaptationResponse response = adaptations.adapt(
                new AdaptationRequest(recipeId, 1, List.of(), List.of(), profile));
        assertThat(response.status()).isEqualTo(AdaptationResponse.Status.COOKABLE);
        return response;
    }

    private static AdaptedIngredient row(AdaptationResponse response, String name) {
        return response.ingredients().stream()
                .filter(ingredient -> ingredient.name().equals(name)).findFirst().orElseThrow();
    }

    private static boolean tasteAdjust(AdaptationResponse response, String name) {
        return row(response, name).display().tasteAdjust();
    }
}
