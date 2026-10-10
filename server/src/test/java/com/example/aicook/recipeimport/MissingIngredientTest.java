package com.example.aicook.recipeimport;

import com.example.aicook.adaptation.AdaptationRequest;
import com.example.aicook.adaptation.AdaptationRequest.Profile;
import com.example.aicook.adaptation.AdaptationRequest.SubstituteAnswer;
import com.example.aicook.adaptation.AdaptationResponse;
import com.example.aicook.adaptation.AdaptationResponse.Change;
import com.example.aicook.adaptation.AdaptationResponse.ChangeType;
import com.example.aicook.adaptation.AdaptationResponse.RequiredItem;
import com.example.aicook.adaptation.AdaptationResponse.Status;
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
 * A missing ingredient that has no verified rule (spec 2.2): the ingredient a dish is built
 * around stops the recipe, anything else is left out and reported as an unverified change.
 */
// The API key is cleared so tests never call the AI API.
@SpringBootTest(properties = {"spring.datasource.url=${SPRING_DATASOURCE_TEST_URL}", "openai.api-key="})
class MissingIngredientTest {

    private static final String DOMAIN = "missing.example.com";

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
    void sideIngredientsWithoutARuleAreLeftOutAndReportedAsUnverified() {
        String recipeId = recipe("제육볶음", "돼지고기 앞다리살", "굴소스", "당근", "후추", "양파");

        AdaptationResponse response = adapt(recipeId, "굴소스", "당근", "후추");

        assertThat(response.status()).isEqualTo(Status.COOKABLE);
        assertThat(response.changes()).extracting(Change::type)
                .containsExactly(ChangeType.OMIT_BY_USER, ChangeType.OMIT_BY_USER, ChangeType.OMIT_BY_USER);
        assertThat(response.changes().get(0).description()).isEqualTo("굴소스 없이 만들어요.");
        assertThat(response.changes().get(0).impact()).contains("확인된 규칙이 없어요");
        assertThat(response.ingredients()).extracting(row -> row.name())
                .containsExactly("돼지고기 앞다리살", "양파");
    }

    @Test
    void ingredientNamedByAnAliasInTheDishNameIsRequired() {
        // "제육" is an alias of pork.
        String recipeId = recipe("제육볶음", "돼지고기 앞다리살", "꿀", "양파");

        AdaptationResponse response = adapt(recipeId, "돼지고기 앞다리살");

        assertThat(response.status()).isEqualTo(Status.NOT_COOKABLE);
        assertThat(response.requiredItems()).extracting(RequiredItem::name).containsExactly("돼지고기 앞다리살");
        assertThat(response.requiredItems().get(0).reason()).isEqualTo("요리 이름에 들어간 재료라 꼭 필요해요");
    }

    @Test
    void sauceNamedInTheDishNameIsRequiredAndSoIsTheUnnamedMainIngredient() {
        String recipeId = recipe("고추장불고기", "돼지고기", "고추장", "설탕", "양파");

        assertThat(adapt(recipeId, "고추장").requiredItems()).extracting(RequiredItem::name)
                .containsExactly("고추장");
        // No main-category ingredient is in the name, so the meat is taken as the centre of the dish.
        assertThat(adapt(recipeId, "돼지고기").requiredItems()).extracting(RequiredItem::reason)
                .containsExactly("이 요리의 중심 재료라 꼭 필요해요");
        assertThat(adapt(recipeId, "설탕", "양파").status()).isEqualTo(Status.COOKABLE);
    }

    @Test
    void otherMainCategoryIngredientsAreOptionalOnceTheDishNameNamesOne() {
        String recipeId = recipe("계란말이", "계란", "게맛살", "당근");

        assertThat(adapt(recipeId, "게맛살", "당근").status()).isEqualTo(Status.COOKABLE);
        assertThat(adapt(recipeId, "계란").status()).isEqualTo(Status.NOT_COOKABLE);
    }

    @Test
    void shortNameInsideAnotherIngredientNameDoesNotCount() {
        // "김치전" contains "김", but that is the 김 of 김치.
        String recipeId = recipe("김치전", "김치", "김", "부침가루");

        assertThat(adapt(recipeId, "김").status()).isEqualTo(Status.COOKABLE);
        assertThat(adapt(recipeId, "김치").status()).isEqualTo(Status.NOT_COOKABLE);
    }

    @Test
    void everyMainCategoryIngredientIsRequiredWhenTheNameNamesNone() {
        String recipeId = recipe("오므라이스", "계란", "밥", "양파", "케첩");

        assertThat(adapt(recipeId, "계란").status()).isEqualTo(Status.NOT_COOKABLE);
        assertThat(adapt(recipeId, "밥").status()).isEqualTo(Status.NOT_COOKABLE);
        assertThat(adapt(recipeId, "양파", "케첩").status()).isEqualTo(Status.COOKABLE);
    }

    @Test
    void missingOilAsksForAnotherOilAndRewritesTheRecipeWithIt() {
        String recipeId = recipe("제육볶음", "돼지고기 앞다리살", "식물성 오일", "양파");
        String oilId = idOf(recipeId, "식물성 오일");

        AdaptationResponse asked = adapt(recipeId, List.of(), List.of(), "식물성 오일");
        assertThat(asked.status()).isEqualTo(Status.NEEDS_CONFIRMATION);
        assertThat(asked.pendingQuestions()).singleElement()
                .satisfies(question -> assertThat(question.substituteName()).isEqualTo("올리브유"));

        AdaptationResponse swapped = adapt(recipeId, List.of(),
                List.of(new SubstituteAnswer(oilId, "olive_oil", true)), "식물성 오일");
        assertThat(swapped.status()).isEqualTo(Status.COOKABLE);
        assertThat(swapped.changes()).singleElement().satisfies(change -> {
            assertThat(change.type()).isEqualTo(ChangeType.SUBSTITUTE);
            assertThat(change.description()).isEqualTo("식물성 오일은 올리브유로 바꿔요.");
        });
        // Same amount, the substitute's own name.
        assertThat(swapped.ingredients()).filteredOn(row -> row.ingredientId().equals(oilId)).singleElement()
                .satisfies(row -> {
                    assertThat(row.name()).isEqualTo("올리브유");
                    assertThat(row.original().rawText()).isEqualTo("1큰술");
                });
    }

    @Test
    void decliningEverySubstituteLeavesTheIngredientOutAsUnverified() {
        String recipeId = recipe("제육볶음", "돼지고기 앞다리살", "식물성 오일", "양파");
        String oilId = idOf(recipeId, "식물성 오일");

        AdaptationResponse second = adapt(recipeId, List.of(),
                List.of(new SubstituteAnswer(oilId, "olive_oil", false)), "식물성 오일");
        assertThat(second.pendingQuestions()).singleElement()
                .satisfies(question -> assertThat(question.substituteName()).isEqualTo("콩기름"));

        AdaptationResponse none = adapt(recipeId, List.of(), List.of(
                new SubstituteAnswer(oilId, "olive_oil", false),
                new SubstituteAnswer(oilId, "soybean_oil", false)), "식물성 오일");
        assertThat(none.status()).isEqualTo(Status.COOKABLE);
        assertThat(none.changes()).extracting(Change::type).containsExactly(ChangeType.OMIT_BY_USER);
    }

    @Test
    void substituteWithAnAllergenTheUserAvoidsIsNeverOffered() {
        String recipeId = recipe("제육볶음", "돼지고기 앞다리살", "올리브유", "양파");
        String oilId = idOf(recipeId, "올리브유");

        // Cooking oil and soybean oil both carry soybean; neither may be suggested.
        AdaptationResponse response = adapt(recipeId, List.of("soybean"), List.of(), "올리브유");

        assertThat(response.pendingQuestions()).isEmpty();
        assertThat(response.changes()).extracting(Change::type).containsExactly(ChangeType.OMIT_BY_USER);
        // Without the allergy the first substitute is offered.
        assertThat(adapt(recipeId, List.of(), List.of(), "올리브유").pendingQuestions()).singleElement()
                .satisfies(question -> assertThat(question.ingredientId()).isEqualTo(oilId));
    }

    @Test
    void substituteAlreadyInTheRecipeIsNotOffered() {
        String recipeId = recipe("닭볶음", "닭고기", "꿀", "올리고당");

        AdaptationResponse response = adapt(recipeId, "꿀");

        assertThat(response.pendingQuestions()).isEmpty();
        assertThat(response.changes()).extracting(Change::type).containsExactly(ChangeType.OMIT_BY_USER);
    }

    /** A stored recipe with one step that uses every ingredient. */
    private String recipe(String name, String... ingredientNames) {
        List<Extract.Ingredient> ingredients = List.of(ingredientNames).stream()
                .map(ingredient -> new Extract.Ingredient(ingredient, "1큰술", null, ingredient + " 1큰술"))
                .toList();
        Extract.Step step = new Extract.Step("재료를 모두 넣어요", null, List.of(ingredientNames), List.of(), null,
                null, null, null, null, null, null, "재료를 넣는다");
        Extract.Page page = new Extract.Page(true, name, null, null, null, ingredients, List.of(step));
        return writer.save(name, "https://" + DOMAIN + "/" + name.hashCode(), null, null, page);
    }

    private String idOf(String recipeId, String ingredientName) {
        return recipes.find(recipeId).ingredients().stream()
                .filter(ingredient -> ingredient.name().equals(ingredientName))
                .map(Recipe.Ingredient::id).findFirst().orElseThrow();
    }

    private AdaptationResponse adapt(String recipeId, String... missingNames) {
        return adapt(recipeId, List.of(), List.of(), missingNames);
    }

    private AdaptationResponse adapt(String recipeId, List<String> allergenCodes, List<SubstituteAnswer> answers,
                                     String... missingNames) {
        Recipe recipe = recipes.find(recipeId);
        List<String> missing = recipe.ingredients().stream()
                .filter(ingredient -> List.of(missingNames).contains(ingredient.name()))
                .map(Recipe.Ingredient::id)
                .toList();
        assertThat(missing).hasSize(missingNames.length);
        Profile profile = new Profile(SkillLevel.LOW, List.of("spoon_rice"), null, allergenCodes);
        return adaptations.adapt(new AdaptationRequest(recipeId, 1, missing, answers, profile));
    }
}
