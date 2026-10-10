package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationRequest.Profile;
import com.example.aicook.adaptation.AdaptationRequest.SubstituteAnswer;
import com.example.aicook.adaptation.AdaptationResponse.AdaptedIngredient;
import com.example.aicook.adaptation.AdaptationResponse.AdaptedStep;
import com.example.aicook.adaptation.AdaptationResponse.AllergyWarning;
import com.example.aicook.adaptation.AdaptationResponse.ChangeType;
import com.example.aicook.adaptation.AdaptationResponse.ItemKind;
import com.example.aicook.adaptation.AdaptationResponse.Status;
import com.example.aicook.common.ApiException;
import com.example.aicook.measure.MeasureResult.QuantityStatus;
import com.example.aicook.recipe.Recipe.HeatLevel;
import com.example.aicook.recipe.Recipe.SkillLevel;
import com.example.aicook.recipe.RecipeDtos.DetailIngredient;
import com.example.aicook.recipe.RecipeService;
import com.example.aicook.reference.ReferenceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adaptation rules from spec 13 (도구, 안전·알레르기, 재구성) against the kimchi fried rice fixture
 * stored in the test database. Steps: 1 김치 썰기, 2 대파 썰기, 3 팬 달구기, 4 볶기(3분), 5 밥·간장(2분),
 * 6 계란, 7 참기름, 8 깨.
 */
// The API key is cleared so tests never call the AI API.
@SpringBootTest(properties = {"spring.datasource.url=${SPRING_DATASOURCE_TEST_URL}", "openai.api-key="})
class AdaptationServiceTest {

    private static final List<String> FULL_KITCHEN =
            List.of("spoon_rice", "teaspoon", "paper_cup_small", "rice_bowl", "knife", "gas_stove", "frying_pan");

    @Autowired
    private AdaptationService service;
    @Autowired
    private RecipeService recipes;
    @Autowired
    private ReferenceData reference;

    private String recipeId;
    /** ingredient name -> recipe ingredient id */
    private Map<String, String> ids;

    @BeforeEach
    void findFixture() {
        recipeId = recipes.search("김치볶음밥").get(0).id();
        ids = recipes.detail(recipeId).ingredients().stream()
                .collect(Collectors.toMap(DetailIngredient::name, DetailIngredient::id));
    }

    private static Profile profile(SkillLevel skill, List<String> tools, List<String> allergens) {
        return new Profile(skill, tools, null, allergens);
    }

    private AdaptationResponse adapt(Profile profile, List<String> missingNames, SubstituteAnswer... answers) {
        List<String> missing = missingNames.stream().map(ids::get).toList();
        return service.adapt(new AdaptationRequest(recipeId, 1, missing, Arrays.asList(answers), profile));
    }

    private AdaptationResponse adaptDefault(String... missingNames) {
        return adapt(profile(SkillLevel.LOW, FULL_KITCHEN, List.of()), List.of(missingNames));
    }

    private SubstituteAnswer perillaOil(boolean hasIt) {
        return new SubstituteAnswer(ids.get("참기름"), "perilla_oil", hasIt);
    }

    private AdaptedIngredient ingredient(AdaptationResponse response, String originalName) {
        String id = ids.get(originalName);
        return response.ingredients().stream().filter(item -> item.ingredientId().equals(id)).findFirst().orElseThrow();
    }

    private static AdaptedStep stepWith(AdaptationResponse response, String actionPart) {
        return response.steps().stream().filter(step -> step.action().contains(actionPart)).findFirst().orElseThrow();
    }

    @Test
    void cookableWithEverythingAtHand() {
        AdaptationResponse response = adaptDefault();

        assertThat(response.status()).isEqualTo(Status.COOKABLE);
        assertThat(response.changes()).isEmpty();
        assertThat(response.requiredItems()).isEmpty();
        assertThat(response.ingredients()).hasSize(8);
        assertThat(response.steps()).extracting(AdaptedStep::order).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void amountsAreConvertedAndTheOriginalIsKept() {
        AdaptationResponse response = adaptDefault();

        AdaptedIngredient soySauce = ingredient(response, "간장");
        assertThat(soySauce.original().rawText()).isEqualTo("1큰술");
        assertThat(soySauce.original().quantity()).isEqualTo(1.0);
        assertThat(soySauce.original().unit()).isEqualTo("큰술");
        assertThat(soySauce.quantityStatus()).isEqualTo(QuantityStatus.CONVERTED_APPROX);
        // a tablespoon (15 mL) goes into the rice spoon (10 mL); seasoning always asks to taste
        assertThat(soySauce.display().text()).isEqualTo("밥숟가락으로 1번 반");
        assertThat(soySauce.display().count()).isEqualTo(1.5);
        assertThat(soySauce.display().fillLevelKey()).isEqualTo("liquid.1_2");
        assertThat(soySauce.display().tasteAdjust()).isTrue();

        assertThat(ingredient(response, "식용유").display().text()).isEqualTo("밥숟가락으로 3번");
        assertThat(ingredient(response, "참기름").display().text()).isEqualTo("티스푼으로 1번");
        assertThat(ingredient(response, "깨").display().fillLevelKey()).isEqualTo("powder.full");
        assertThat(ingredient(response, "계란").quantityStatus()).isEqualTo(QuantityStatus.ORIGINAL);
        assertThat(ingredient(response, "대파").display().text()).isEqualTo("1/4대");
        assertThat(ingredient(response, "대파").quantityStatus()).isEqualTo(QuantityStatus.ORIGINAL);
    }

    @Test
    void beginnerGetsAWeightAsAToolAmountWithGramsInBrackets() {
        AdaptedIngredient kimchi = ingredient(adaptDefault(), "김치");

        assertThat(kimchi.original().rawText()).isEqualTo("200g");
        assertThat(kimchi.display().text()).isEqualTo("밥공기로 가득 1번 (200g)");
        assertThat(kimchi.display().toolVariantId()).isEqualTo("rice_bowl");
        assertThat(kimchi.quantityStatus()).isEqualTo(QuantityStatus.CONVERTED_APPROX);
    }

    @Test
    void experiencedCookSeesTheWeightAsWritten() {
        AdaptationResponse response = adapt(profile(SkillLevel.MID, FULL_KITCHEN, List.of()), List.of());

        AdaptedIngredient kimchi = ingredient(response, "김치");
        assertThat(kimchi.display().text()).isEqualTo("200g");
        assertThat(kimchi.quantityStatus()).isEqualTo(QuantityStatus.ORIGINAL);
        // spoon amounts do not depend on the skill level
        assertThat(ingredient(response, "간장").display().text()).isEqualTo("밥숟가락으로 1번 반");
    }

    @Test
    void measuringSpoonOwnerSeesSpoonUnitsAsWritten() {
        List<String> tools = List.of("measuring_spoon", "spoon_rice", "teaspoon", "knife", "gas_stove", "frying_pan");

        AdaptationResponse response = adapt(profile(SkillLevel.LOW, tools, List.of()), List.of());

        AdaptedIngredient soySauce = ingredient(response, "간장");
        assertThat(soySauce.display().text()).isEqualTo("1큰술");
        // shown as written, so there is no "approximate" status
        assertThat(soySauce.quantityStatus()).isEqualTo(QuantityStatus.ORIGINAL);
        assertThat(ingredient(response, "참기름").display().text()).isEqualTo("1작은술");
    }

    @Test
    void stepsWithoutADeviceSettingReturnNull() {
        assertThat(adaptDefault().steps()).allMatch(step -> step.deviceSettingRaw() == null);
    }

    @Test
    void amountsFollowTheToolsTheUserOwns() {
        List<String> tools = List.of("spoon_rice", "knife", "gas_stove", "frying_pan");

        AdaptationResponse response = adapt(profile(SkillLevel.LOW, tools, List.of()), List.of());

        // no teaspoon: a teaspoon unit becomes half a rice spoon
        assertThat(ingredient(response, "참기름").display().text()).isEqualTo("밥숟가락 반");
        // no rice bowl: grams cannot become a tool amount
        assertThat(ingredient(response, "김치").display().text()).isEqualTo("200g");
        assertThat(ingredient(response, "김치").quantityStatus()).isEqualTo(QuantityStatus.NEEDS_CHECK);
    }

    @Test
    void ingredientNamedInTheDishMakesItNotCookableWhenMissing() {
        AdaptationResponse response = adaptDefault("밥");

        assertThat(response.status()).isEqualTo(Status.NOT_COOKABLE);
        assertThat(response.requiredItems()).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(ItemKind.INGREDIENT);
            assertThat(item.name()).isEqualTo("밥");
        });
        assertThat(response.ingredients()).isEmpty();
        assertThat(response.steps()).isEmpty();
    }

    @Test
    void omittableIngredientIsRemovedAndReported() {
        AdaptationResponse response = adaptDefault("대파");

        assertThat(response.status()).isEqualTo(Status.COOKABLE);
        assertThat(response.changes()).singleElement().satisfies(change -> {
            assertThat(change.type()).isEqualTo(ChangeType.OMIT);
            assertThat(change.target()).isEqualTo(ids.get("대파"));
            assertThat(change.description()).isEqualTo("대파는 없어서 생략해요.");
            assertThat(change.impact()).isNotBlank();
        });
        assertThat(response.ingredients()).extracting(AdaptedIngredient::ingredientId).doesNotContain(ids.get("대파"));
        // The step that only cut the green onion is gone; the stir-fry step keeps the kimchi.
        assertThat(response.steps()).hasSize(7);
        assertThat(response.steps()).noneMatch(step -> step.action().contains("대파"));
        assertThat(stepWith(response, "볶으세요").ingredientIds()).containsExactly(ids.get("김치"));
    }

    @Test
    void garnishIsOmittedByTheGenericRule() {
        AdaptationResponse response = adaptDefault("깨");

        assertThat(response.changes()).singleElement()
                .satisfies(change -> assertThat(change.description()).isEqualTo("깨는 장식용이라 생략해요."));
        assertThat(response.steps()).hasSize(7);
    }

    @Test
    void substituteIsAskedBeforeItIsApplied() {
        AdaptationResponse response = adaptDefault("참기름");

        assertThat(response.status()).isEqualTo(Status.NEEDS_CONFIRMATION);
        assertThat(response.pendingQuestions()).singleElement().satisfies(question -> {
            assertThat(question.ingredientId()).isEqualTo(ids.get("참기름"));
            assertThat(question.substituteId()).isEqualTo("perilla_oil");
            assertThat(question.substituteName()).isEqualTo("들기름");
        });
        assertThat(response.steps()).isEmpty();
    }

    @Test
    void confirmedSubstituteReplacesTheIngredient() {
        AdaptationResponse response =
                adapt(profile(SkillLevel.LOW, FULL_KITCHEN, List.of()), List.of("참기름"), perillaOil(true));

        assertThat(response.status()).isEqualTo(Status.COOKABLE);
        assertThat(response.changes()).singleElement().satisfies(change -> {
            assertThat(change.type()).isEqualTo(ChangeType.SUBSTITUTE);
            assertThat(change.description()).isEqualTo("참기름은 들기름으로 바꿔요.");
        });
        AdaptedIngredient oil = ingredient(response, "참기름");
        assertThat(oil.name()).isEqualTo("들기름");
        assertThat(oil.original().rawText()).isEqualTo("1작은술");
        assertThat(stepWith(response, "한 바퀴").action()).isEqualTo("들기름을 한 바퀴 둘러요");
    }

    @Test
    void declinedSubstituteFallsBackToTheNextRule() {
        AdaptationResponse response =
                adapt(profile(SkillLevel.LOW, FULL_KITCHEN, List.of()), List.of("참기름"), perillaOil(false));

        assertThat(response.status()).isEqualTo(Status.COOKABLE);
        assertThat(response.changes()).singleElement()
                .satisfies(change -> assertThat(change.type()).isEqualTo(ChangeType.OMIT));
        assertThat(response.steps()).extracting(AdaptedStep::order).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(response.steps()).noneMatch(step -> step.action().contains("기름을 한 바퀴"));
    }

    @Test
    void scissorsReplaceAMissingKnifeThroughARule() {
        List<String> tools = List.of("spoon_rice", "scissors", "gas_stove", "frying_pan");

        AdaptationResponse response = adapt(profile(SkillLevel.MID, tools, List.of()), List.of());

        assertThat(response.status()).isEqualTo(Status.COOKABLE);
        assertThat(response.changes()).singleElement().satisfies(change -> {
            assertThat(change.type()).isEqualTo(ChangeType.TOOL_SUBSTITUTE);
            assertThat(change.target()).isEqualTo("knife");
            assertThat(change.description()).isEqualTo("칼 대신 가위를 써요.");
        });
        AdaptedStep cutting = response.steps().get(0);
        assertThat(cutting.toolIds()).containsExactly("scissors");
        assertThat(cutting.safetyNotes()).extracting(note -> note.message())
                .containsExactly("가위 날에 손이 닿지 않게 조심하세요.");
    }

    @Test
    void microwaveOnlyKitchenCannotCookAndListsTheStove() {
        List<String> tools = List.of("spoon_rice", "knife", "microwave", "frying_pan");

        AdaptationResponse response = adapt(profile(SkillLevel.LOW, tools, List.of()), List.of());

        assertThat(response.status()).isEqualTo(Status.NOT_COOKABLE);
        assertThat(response.requiredItems()).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(ItemKind.TOOL);
            assertThat(item.id()).isEqualTo("stove");
            assertThat(item.name()).isEqualTo("가스레인지 또는 인덕션");
        });
    }

    @Test
    void hiddenAllergenIsDetected() {
        AdaptationResponse response = adapt(profile(SkillLevel.LOW, FULL_KITCHEN, List.of("wheat", "egg")), List.of());

        assertThat(response.allergyWarnings()).containsExactlyInAnyOrder(
                new AllergyWarning("wheat", ids.get("간장"), true),
                new AllergyWarning("egg", ids.get("계란"), false));
        // The recipe is still returned unchanged: allergens are warned about, never swapped.
        assertThat(response.changes()).isEmpty();
        assertThat(stepWith(response, "간장").ingredientIds()).contains(ids.get("간장"));
        assertThat(stepWith(response, "계란").ingredientIds()).contains(ids.get("계란"));
    }

    @Test
    void noAllergiesMeansNoWarnings() {
        AdaptationResponse response = adaptDefault();

        assertThat(response.allergyWarnings()).isEmpty();
        assertThat(response.allergyCheckNeededIngredientIds()).isEmpty();
    }

    @Test
    void reviewedIngredientsNeedNoCheckingForSomeoneWithAnotherAllergy() {
        AdaptationResponse response = adapt(profile(SkillLevel.LOW, FULL_KITCHEN, List.of("peanut")), List.of());

        // Every fixture ingredient has an AI_RESEARCHED mapping and none contains peanut.
        assertThat(response.allergyWarnings()).isEmpty();
        assertThat(response.allergyCheckNeededIngredientIds()).isEmpty();
    }

    @Test
    void kimchiWarnsSomeoneWithAShrimpAllergy() {
        AdaptationResponse response = adapt(profile(SkillLevel.LOW, FULL_KITCHEN, List.of("shrimp")), List.of());

        assertThat(response.allergyWarnings())
                .containsExactly(new AllergyWarning("shrimp", ids.get("김치"), true));
    }

    @Test
    void ingredientOutsideTheDictionaryIsNeverTreatedAsSafe() {
        assertThat(reference.allergensOf(null)).isEmpty();
        assertThat(reference.allergensOf("not_in_dictionary")).isEmpty();
        // In the dictionary with no mapping rows: reviewed, no listed allergen.
        assertThat(reference.allergensOf("rice")).contains(List.of());
    }

    @Test
    void safetyNotesAreShownAtEverySkillLevel() {
        AdaptationResponse low = adapt(profile(SkillLevel.LOW, FULL_KITCHEN, List.of()), List.of());
        AdaptationResponse high = adapt(profile(SkillLevel.HIGH, FULL_KITCHEN, List.of()), List.of());

        AdaptedStep highStirFry = stepWith(high, "볶으세요");
        assertThat(highStirFry.safetyNotes()).extracting(note -> note.message())
                .containsExactly("뜨거운 기름이 튈 수 있어요.");
        assertThat(highStirFry.safetyNotes().get(0).detail()).isNull();
        assertThat(stepWith(low, "볶으세요").safetyNotes().get(0).detail()).isNotBlank();
        assertThat(high.safetySummary()).containsExactly("뜨거운 기름이 튈 수 있어요.");
    }

    @Test
    void skillLevelNeverChangesStepsTimeHeatOrChecks() {
        AdaptationResponse low = adapt(profile(SkillLevel.LOW, FULL_KITCHEN, List.of()), List.of());
        AdaptationResponse high = adapt(profile(SkillLevel.HIGH, FULL_KITCHEN, List.of()), List.of());

        assertThat(high.steps()).extracting(AdaptedStep::action)
                .isEqualTo(low.steps().stream().map(AdaptedStep::action).toList());
        assertThat(high.steps()).extracting(AdaptedStep::durationSeconds)
                .isEqualTo(low.steps().stream().map(AdaptedStep::durationSeconds).toList());
        assertThat(high.steps()).extracting(AdaptedStep::heatLevel)
                .isEqualTo(low.steps().stream().map(AdaptedStep::heatLevel).toList());
        assertThat(high.steps()).extracting(AdaptedStep::completionCheck)
                .isEqualTo(low.steps().stream().map(AdaptedStep::completionCheck).toList());
    }

    @Test
    void timersExistOnlyWhereTheRecipeHasThem() {
        AdaptationResponse response = adaptDefault();

        assertThat(response.steps()).extracting(AdaptedStep::durationSeconds)
                .containsExactly(null, null, null, 180, 120, null, null, null);
        assertThat(response.steps().get(3).timerStartCondition()).isEqualTo("볶기 시작할 때 눌러주세요");
    }

    @Test
    void heatPictureKeyMatchesTheHeatLevelAndStove() {
        List<String> tools = List.of("spoon_rice", "knife", "induction", "frying_pan");

        AdaptationResponse response = adapt(profile(SkillLevel.LOW, tools, List.of()), List.of());

        AdaptedStep mixing = stepWith(response, "간장");
        assertThat(mixing.heatLevel()).isEqualTo(HeatLevel.MEDIUM_LOW);
        assertThat(mixing.heatRaw()).isEqualTo("중약불");
        assertThat(mixing.heatVisualKey()).isEqualTo("heat.induction.MEDIUM_LOW");
        assertThat(response.steps().get(0).heatVisualKey()).isNull();
    }

    @Test
    void preferredStoveIsUsedWhenBothAreOwned() {
        List<String> tools = List.of("spoon_rice", "knife", "gas_stove", "induction", "frying_pan");
        Profile profile = new Profile(SkillLevel.LOW, tools, "induction", List.of());

        AdaptationResponse response = service.adapt(new AdaptationRequest(recipeId, 1, List.of(), List.of(), profile));

        assertThat(response.steps().get(2).heatVisualKey()).isEqualTo("heat.induction.MEDIUM");
    }

    @Test
    void unsupportedServingsAndUnknownIdsAreRejected() {
        Profile profile = profile(SkillLevel.LOW, FULL_KITCHEN, List.of());

        assertThatThrownBy(() -> service.adapt(new AdaptationRequest(recipeId, 2, List.of(), List.of(), profile)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.adapt(
                new AdaptationRequest(recipeId, 1, List.of("999999"), List.of(), profile)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> adapt(profile(SkillLevel.LOW, List.of("wok"), List.of()), List.of()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> adapt(profile(SkillLevel.LOW, FULL_KITCHEN, List.of("gluten")), List.of()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void allergyCodesAreKeptOutOfToString() {
        Profile profile = profile(SkillLevel.LOW, FULL_KITCHEN, List.of("shrimp"));
        AdaptationRequest request = new AdaptationRequest(recipeId, 1, List.of(), List.of(), profile);

        assertThat(request.toString()).doesNotContain("shrimp");
    }
}
