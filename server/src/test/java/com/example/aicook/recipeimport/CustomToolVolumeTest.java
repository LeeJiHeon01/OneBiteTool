package com.example.aicook.recipeimport;

import com.example.aicook.adaptation.AdaptationRequest;
import com.example.aicook.adaptation.AdaptationRequest.CustomToolVolume;
import com.example.aicook.adaptation.AdaptationRequest.Profile;
import com.example.aicook.adaptation.AdaptationResponse;
import com.example.aicook.adaptation.AdaptationResponse.AdaptedIngredient;
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

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tool volumes the user measured against a water bottle (spec 4.8): the server uses an accepted
 * value instead of the representative volume for that request and marks the amount as
 * user-measured; anything else is ignored and the representative volume stays.
 */
// The API key is cleared so tests never call the AI API.
@SpringBootTest(properties = {"spring.datasource.url=${SPRING_DATASOURCE_TEST_URL}", "openai.api-key="})
class CustomToolVolumeTest {

    private static final String DOMAIN = "custom-volume.example.com";

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
    void measuredBowlIsUsedInsteadOfTheRepresentativeVolume() {
        String recipeId = recipe("물", ingredient("물", "500ml"));

        AdaptedIngredient representative = row(adapt(recipeId, List.of("soup_bowl")), "물");
        AdaptedIngredient measured = row(adapt(recipeId, List.of("soup_bowl"), volume("soup_bowl", 250)), "물");

        assertThat(representative.display().text()).isEqualTo("국그릇으로 가득 1번");
        assertThat(representative.display().userMeasured()).isFalse();
        assertThat(measured.display().text()).isEqualTo("국그릇으로 가득 2번");
        assertThat(measured.display().toolVariantId()).isEqualTo("soup_bowl");
        assertThat(measured.display().count()).isEqualTo(2.0);
        assertThat(measured.display().userMeasured()).isTrue();
        // The source amount is still returned as written.
        assertThat(measured.original().rawText()).isEqualTo("500ml");
    }

    @Test
    void ignoredToolsAreReportedBack() {
        String recipeId = recipe("물", ingredient("물", "500ml"));

        AdaptationResponse accepted = adapt(recipeId, List.of("soup_bowl"), volume("soup_bowl", 250));
        AdaptationResponse ignored = adapt(recipeId, List.of("soup_bowl", "spoon_rice"),
                volume("soup_bowl", 5000), volume("spoon_rice", 20), volume("rice_bowl", 250),
                volume("no_such_tool", 250), new CustomToolVolume(null, 250.0), null);

        assertThat(accepted.ignoredCustomTools()).isEmpty();
        // Out of range, not measurable this way, not owned; an unknown code is not echoed back.
        assertThat(ignored.ignoredCustomTools()).containsExactly("rice_bowl", "soup_bowl", "spoon_rice");
        assertThat(row(ignored, "물").display().text()).isEqualTo("국그릇으로 가득 1번");
        assertThat(row(ignored, "물").display().userMeasured()).isFalse();
        assertThat(row(ignored, "물").quantityStatus()).isEqualTo(QuantityStatus.CONVERTED_APPROX);
    }

    @Test
    void volumeOutsideTheRangeIsIgnored() {
        String recipeId = recipe("물", ingredient("물", "500ml"));

        for (double outside : new double[]{19, 1001, -5, 0}) {
            AdaptedIngredient water = row(adapt(recipeId, List.of("soup_bowl"), volume("soup_bowl", outside)), "물");

            assertThat(water.display().text()).isEqualTo("국그릇으로 가득 1번");
            assertThat(water.display().userMeasured()).isFalse();
        }
        // The limits themselves are accepted.
        assertThat(row(adapt(recipeId, List.of("soup_bowl"), volume("soup_bowl", 1000)), "물")
                .display().userMeasured()).isTrue();
    }

    @Test
    void spoonsStandardToolsAndUnownedToolsCannotBeMeasured() {
        String recipeId = recipe("양념물", ingredient("물", "500ml"), ingredient("식용유", "1큰술"));

        AdaptationResponse plain = adapt(recipeId, List.of("soup_bowl", "spoon_rice"));
        AdaptationResponse response = adapt(recipeId, List.of("soup_bowl", "spoon_rice"),
                volume("spoon_rice", 20), volume("water_bottle_500", 400), volume("rice_bowl", 250),
                new CustomToolVolume("soup_bowl", null), new CustomToolVolume(null, 250.0), null);

        assertThat(row(response, "식용유").display()).isEqualTo(row(plain, "식용유").display());
        assertThat(row(response, "물").display()).isEqualTo(row(plain, "물").display());
        assertThat(row(response, "물").display().userMeasured()).isFalse();
    }

    @Test
    void seasoningIsStillRoundedDownWithAMeasuredCup() {
        // 250 mL in a 200 mL cup is 1.25 cups: seasoning goes down to 1 cup, never up to 1 and 1/3.
        String recipeId = recipe("간장물", ingredient("간장", "250ml"));

        AdaptedIngredient soySauce =
                row(adapt(recipeId, List.of("paper_cup_small"), volume("paper_cup_small", 200)), "간장");

        assertThat(soySauce.display().count()).isEqualTo(1.0);
        assertThat(soySauce.display().tasteAdjust()).isTrue();
        assertThat(soySauce.display().userMeasured()).isTrue();
    }

    @Test
    void toleranceStillAppliesWithAMeasuredCup() {
        // 1.19 cups of a 200 mL cup: the nearest mark is 12% off, too far for rice water.
        String recipeId = recipe("밥", ingredient("쌀", "1컵"), ingredient("물", "238ml"));

        AdaptedIngredient water =
                row(adapt(recipeId, List.of("paper_cup_small"), volume("paper_cup_small", 200)), "물");

        assertThat(water.quantityStatus()).isEqualTo(QuantityStatus.NEEDS_CHECK);
        assertThat(water.display().userMeasured()).isFalse();
    }

    @Test
    void weightReferenceIsScaledButStaysApproximate() {
        double bowlMl = reference.tool("rice_bowl").orElseThrow().representativeVolumeMl();
        double gramsPerBowl = reference.weightReferencesOf("kimchi").stream()
                .filter(weight -> weight.toolId().equals("rice_bowl")).findFirst().orElseThrow().grams();
        String recipeId = recipe("김치", ingredient("김치", Math.round(gramsPerBowl) + "g"));

        AdaptedIngredient representative = row(adapt(recipeId, List.of("rice_bowl")), "김치");
        // A bowl half the representative size holds half the grams, so the same weight is two bowls.
        AdaptedIngredient measured = row(adapt(recipeId, List.of("rice_bowl"), volume("rice_bowl", bowlMl / 2)), "김치");

        assertThat(representative.display().count()).isEqualTo(1.0);
        assertThat(measured.display().count()).isEqualTo(2.0);
        assertThat(measured.quantityStatus()).isEqualTo(QuantityStatus.CONVERTED_APPROX);
        // Grams per bowl is itself an assumed value, so this is not shown as user-measured.
        assertThat(measured.display().userMeasured()).isFalse();
    }

    @Test
    void measuredVolumesAreNotStored() {
        String recipeId = recipe("물", ingredient("물", "500ml"));
        adapt(recipeId, List.of("soup_bowl"), volume("soup_bowl", 250));

        // The next request without the value is back on the representative volume.
        assertThat(row(adapt(recipeId, List.of("soup_bowl")), "물").display().text()).isEqualTo("국그릇으로 가득 1번");
        assertThat(reference.tool("soup_bowl").orElseThrow().representativeVolumeMl()).isEqualTo(500.0);
    }

    private static CustomToolVolume volume(String toolCode, double volumeMl) {
        return new CustomToolVolume(toolCode, volumeMl);
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

    private AdaptationResponse adapt(String recipeId, List<String> toolIds, CustomToolVolume... volumes) {
        Profile profile = new Profile(SkillLevel.LOW, toolIds, null, List.of(), Arrays.asList(volumes));
        AdaptationResponse response = adaptations.adapt(
                new AdaptationRequest(recipeId, 1, List.of(), List.of(), profile));
        assertThat(response.status()).isEqualTo(AdaptationResponse.Status.COOKABLE);
        return response;
    }

    private static AdaptedIngredient row(AdaptationResponse response, String name) {
        return response.ingredients().stream()
                .filter(ingredient -> ingredient.name().equals(name)).findFirst().orElseThrow();
    }
}
