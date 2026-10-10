package com.example.aicook.recipeimport;

import com.example.aicook.adaptation.AdaptationRequest;
import com.example.aicook.adaptation.AdaptationRequest.Profile;
import com.example.aicook.adaptation.AdaptationResponse;
import com.example.aicook.adaptation.AdaptationResponse.AdaptedIngredient;
import com.example.aicook.adaptation.AdaptationResponse.AdaptedStep;
import com.example.aicook.adaptation.AdaptationResponse.SafetyNote;
import com.example.aicook.adaptation.AdaptationService;
import com.example.aicook.measure.MeasureResult.QuantityStatus;
import com.example.aicook.recipe.Recipe.SkillLevel;
import com.example.aicook.reference.ReferenceData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quality fixes of 2026-10-10: joined steps, liquids measured by cup, ingredients outside the
 * dictionary, and safety notes that need a heating step.
 */
// The API key is cleared so tests never call the AI API.
@SpringBootTest(properties = {"spring.datasource.url=${SPRING_DATASOURCE_TEST_URL}", "openai.api-key="})
class QualityFixesTest {

    private static final String DOMAIN = "quality.example.com";
    private static final String PORK_NOTE = "돼지고기는 속까지 완전히 익혀 주세요.";

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ImportedRecipeWriter writer;
    @Autowired
    private AdaptationService adaptations;
    @Autowired
    private ReferenceData reference;

    private final List<Extract.Ingredient> ingredients = new ArrayList<>();
    private final List<Extract.Step> steps = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM recipe WHERE source_domain = :domain").param("domain", DOMAIN).update();
    }

    @Test
    void sameActionInARowIsShownAsOneStepWithEveryIngredient() {
        ingredient("간장", "1큰술");
        ingredient("참치액", "1큰술");
        ingredient("식초", "1큰술");
        ingredient("두부", "1모");
        step("두부를 크게 썰어요", "action.cut", "두부");
        step("간장을 넣어요", "action.add", "간장");
        step("참치액을 넣어요", "action.add", "참치액");
        step("식초를 넣어요", "action.add", "식초");

        AdaptationResponse response = adapt("두부양념", "spoon_rice", "knife");

        assertThat(response.steps()).extracting(AdaptedStep::action)
                .containsExactly("두부를 크게 썰어요", "간장, 참치액, 식초를 넣어요");
        AdaptedStep joined = response.steps().get(1);
        assertThat(joined.order()).isEqualTo(2);
        assertThat(joined.mergedFromStepIds()).hasSize(3);
        assertThat(joined.ingredientIds()).hasSize(3);
        assertThat(response.steps().get(0).mergedFromStepIds()).isEmpty();
    }

    @Test
    void joinedStepKeepsTheSafetyNotesOfEveryStepItCameFrom() {
        ingredient("양파", "1개");
        ingredient("당근", "1개");
        step("양파를 썰어요", "action.cut", "양파");
        step("당근을 썰어요", "action.cut", "당근");

        AdaptedStep joined = adapt("채소손질", "knife").steps().get(0);

        assertThat(joined.action()).isEqualTo("양파, 당근을 썰어요");
        assertThat(joined.safetyNotes()).extracting(SafetyNote::message)
                .containsExactly("칼을 쓸 때는 손가락을 조심하세요.");
    }

    @Test
    void stockIsALiquidMeasuredByBottleOrCup() {
        ingredient("멸치육수", "500ml");
        ingredient("고추장", "2큰술");
        step("멸치육수와 고추장을 넣어요", "action.add", "멸치육수", "고추장");

        AdaptationResponse response = adapt("국물", "spoon_rice", "paper_cup_small", "water_bottle_500");

        AdaptedIngredient stock = row(response, "멸치육수");
        // Cups come before the bottle (spec 4.7); either way it is a poured amount, not the source text.
        assertThat(stock.display().toolVariantId()).isIn("paper_cup_small", "water_bottle_500");
        assertThat(stock.display().text()).isNotEqualTo("500ml");
        assertThat(stock.quantityStatus()).isEqualTo(QuantityStatus.CONVERTED_APPROX);
        assertThat(reference.isAllergenReviewed("broth")).isTrue();
        // A paste is spooned, never poured into a cup.
        assertThat(row(response, "고추장").display().toolVariantId()).isEqualTo("spoon_rice");
    }

    @Test
    void pasteWrittenInCupsIsNotConvertedToTheUsersCup() {
        ingredient("고추장", "1컵");
        step("고추장을 넣어요", "action.add", "고추장");

        AdaptedIngredient paste = row(adapt("양념장", "spoon_rice", "paper_cup_small"), "고추장");

        assertThat(paste.display().text()).isEqualTo("1컵");
        assertThat(paste.display().toolVariantId()).isNull();
    }

    @Test
    void ingredientOutsideTheDictionaryIsMarkedNeedsCheckingNotTasteAndAdd() {
        ingredient("처음보는양념", "1큰술");
        ingredient("모르는재료", "약간");
        ingredient("간장", "1큰술");
        step("재료를 모두 넣어요", null, "처음보는양념", "모르는재료", "간장");

        AdaptationResponse response = adapt("실험", "spoon_rice");

        AdaptedIngredient unknown = row(response, "처음보는양념");
        // Still never rounded up, but not presented as seasoning.
        assertThat(unknown.display().text()).isEqualTo("밥숟가락으로 1번 반");
        assertThat(unknown.display().tasteAdjust()).isFalse();
        assertThat(unknown.quantityStatus()).isEqualTo(QuantityStatus.NEEDS_CHECK);
        assertThat(row(response, "모르는재료").display().text()).isEqualTo("약간");
        assertThat(row(response, "모르는재료").display().tasteAdjust()).isFalse();
        assertThat(row(response, "간장").display().tasteAdjust()).isTrue();
    }

    @Test
    void porkNoteIsShownOnHeatingStepsOnly() {
        ingredient("돼지고기", "300g");
        ingredient("양파", "1개");
        step("돼지고기를 한입 크기로 썰어요", "action.cut", "돼지고기");
        step("돼지고기에 양파를 섞어요", "action.mix", "돼지고기", "양파");
        step("돼지고기를 볶아요", "action.stir_fry", "돼지고기");

        AdaptationResponse response = adapt("돼지볶음", "knife", "frying_pan", "gas_stove");

        assertThat(notes(response, 0)).doesNotContain(PORK_NOTE);
        assertThat(notes(response, 1)).doesNotContain(PORK_NOTE);
        assertThat(notes(response, 2)).contains(PORK_NOTE);
        // Still part of the summary shown before cooking.
        assertThat(response.safetySummary()).contains(PORK_NOTE);
    }

    private void ingredient(String name, String amount) {
        ingredients.add(new Extract.Ingredient(name, amount, null, name + " " + amount));
    }

    private void step(String action, String actionKey, String... ingredientNames) {
        steps.add(new Extract.Step(action, actionKey, List.of(ingredientNames), List.of(), null, null, null, null,
                null, null, null, action));
    }

    private AdaptationResponse adapt(String name, String... toolIds) {
        Extract.Page page = new Extract.Page(true, name, null, null, null, List.copyOf(ingredients),
                List.copyOf(steps));
        String recipeId = writer.save(name, "https://" + DOMAIN + "/" + name.hashCode(), null, null, page);
        Profile profile = new Profile(SkillLevel.LOW, List.of(toolIds), null, List.of());
        AdaptationResponse response = adaptations.adapt(
                new AdaptationRequest(recipeId, 1, List.of(), List.of(), profile));
        assertThat(response.status()).as(response.requiredItems().toString())
                .isEqualTo(AdaptationResponse.Status.COOKABLE);
        return response;
    }

    private static AdaptedIngredient row(AdaptationResponse response, String name) {
        return response.ingredients().stream()
                .filter(ingredient -> ingredient.name().equals(name)).findFirst().orElseThrow();
    }

    private static List<String> notes(AdaptationResponse response, int stepIndex) {
        return response.steps().get(stepIndex).safetyNotes().stream().map(SafetyNote::message).toList();
    }
}
