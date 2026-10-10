package com.example.aicook.recipeimport;

import com.example.aicook.adaptation.AdaptationRequest;
import com.example.aicook.adaptation.AdaptationRequest.Profile;
import com.example.aicook.adaptation.AdaptationResponse;
import com.example.aicook.adaptation.AdaptationResponse.AdaptedStep;
import com.example.aicook.adaptation.AdaptationService;
import com.example.aicook.recipe.Recipe;
import com.example.aicook.recipe.Recipe.SkillLevel;
import com.example.aicook.recipe.RecipeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A time the source gives as a range ("2~3분") is stored with both ends and reaches the adapted
 * step, so the user can pick one. The long end is read from the quoted phrase only; a middle
 * value is never made up.
 */
// The API key is cleared so tests never call the AI API.
@SpringBootTest(properties = {"spring.datasource.url=${SPRING_DATASOURCE_TEST_URL}", "openai.api-key="})
class RangeTimeTest {

    private static final String DOMAIN = "range-time.example.com";

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ImportedRecipeWriter writer;
    @Autowired
    private RecipeService recipes;
    @Autowired
    private AdaptationService adaptations;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM recipe WHERE source_domain = :domain").param("domain", DOMAIN).update();
    }

    @Test
    void longEndIsReadFromTheQuotedRange() {
        assertThat(ExtractValidator.durationMaxSeconds(timed(120, "2~3분 볶는다"))).isEqualTo(180);
        assertThat(ExtractValidator.durationMaxSeconds(timed(600, "10 - 15분간 끓인다"))).isEqualTo(900);
        assertThat(ExtractValidator.durationMaxSeconds(timed(30, "30~40초"))).isEqualTo(40);
        assertThat(ExtractValidator.durationMaxSeconds(timed(3600, "1~2시간 재운다"))).isEqualTo(7200);
    }

    @Test
    void nothingIsGuessedWhenTheQuoteIsNotAPlainRange() {
        // No range at all.
        assertThat(ExtractValidator.durationMaxSeconds(timed(180, "3분 볶는다"))).isNull();
        // The stated time is not the short end of the range.
        assertThat(ExtractValidator.durationMaxSeconds(timed(180, "2~3분 볶는다"))).isNull();
        // Not increasing.
        assertThat(ExtractValidator.durationMaxSeconds(timed(180, "3~3분"))).isNull();
        // Two ranges in one phrase.
        assertThat(ExtractValidator.durationMaxSeconds(timed(120, "2~3분 볶고 5~6분 끓인다"))).isNull();
        assertThat(ExtractValidator.durationMaxSeconds(timed(null, null))).isNull();
    }

    @Test
    void bothEndsAreStoredAndReachTheAdaptedStep() {
        String recipeId = save("범위볶음", timed(120, "2~3분 볶는다"), timed(300, "5분 끓인다"));

        List<Recipe.Step> stored = recipes.find(recipeId).steps();
        assertThat(stored.get(0).durationSeconds()).isEqualTo(120);
        assertThat(stored.get(0).durationMaxSeconds()).isEqualTo(180);
        assertThat(stored.get(1).durationSeconds()).isEqualTo(300);
        assertThat(stored.get(1).durationMaxSeconds()).isNull();

        Profile profile = new Profile(SkillLevel.LOW, List.of("frying_pan", "gas_stove", "paper_cup_small"), null,
                List.of());
        AdaptationResponse response = adaptations.adapt(
                new AdaptationRequest(recipeId, 1, List.of(), List.of(), profile));
        List<AdaptedStep> steps = response.steps();
        assertThat(steps.get(0).durationSeconds()).isEqualTo(120);
        assertThat(steps.get(0).durationMaxSeconds()).isEqualTo(180);
        assertThat(steps.get(1).durationMaxSeconds()).isNull();
    }

    private static Extract.Step timed(Integer seconds, String evidence) {
        return new Extract.Step("물을 넣고 익혀요", "action.stir_fry", List.of("물"), List.of(), seconds, evidence,
                seconds == null ? null : "넣은 뒤", null, null, null, null, "익힌다");
    }

    private String save(String name, Extract.Step... steps) {
        Extract.Ingredient water = new Extract.Ingredient("물", "200ml", null, "물 200ml");
        Extract.Page page = new Extract.Page(true, name, null, null, null, List.of(water), List.of(steps));
        return writer.save(name, "https://" + DOMAIN + "/" + name.hashCode(), null, null, page);
    }
}
