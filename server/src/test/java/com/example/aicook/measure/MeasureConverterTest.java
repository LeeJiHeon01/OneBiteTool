package com.example.aicook.measure;

import com.example.aicook.measure.MeasureResult.QuantityStatus;
import com.example.aicook.reference.Rules.WeightReference;
import com.example.aicook.reference.Sensitivity;
import com.example.aicook.reference.Tools.ContentType;
import com.example.aicook.reference.Tools.Cup;
import com.example.aicook.reference.Tools.FillLevel;
import com.example.aicook.reference.Tools.Variant;
import com.example.aicook.reference.Units;
import com.example.aicook.reference.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Measurement rules (spec 4.2, 4.7, 13) with fixed tool volumes: rice spoon 10 mL, teaspoon 4 mL,
 * small paper cup 193 mL, water bottle 500 mL, rice bowl 300 mL.
 */
class MeasureConverterTest {

    private static final Variant RICE_SPOON = spoon("spoon_rice", "밥숟가락", 10, true);
    private static final Variant TEASPOON = spoon("teaspoon", "티스푼", 4, false);
    private static final Variant PAPER_CUP = tool("paper_cup_small", "작은 종이컵", 193.0, new Cup(70, 50, 78, 8), List.of());
    private static final Variant WATER_BOTTLE = tool("water_bottle_500", "생수병", 500.0, null, List.of());
    private static final Variant RICE_BOWL = tool("rice_bowl", "밥공기", 300.0, null, List.of());
    private static final Variant KNIFE = tool("knife", "칼", null, null, List.of());
    private static final Variant MEASURING_SPOON = standard("measuring_spoon", "계량스푼");
    private static final Variant MEASURING_CUP = standard("measuring_cup", "계량컵");
    private static final List<WeightReference> KIMCHI_IN_BOWL = List.of(new WeightReference("kimchi", "rice_bowl", 200));

    private final Units units = Units.load();
    private final AmountParser parser = new AmountParser(units.vagueWords());
    private final MeasureConverter converter = new MeasureConverter(units);

    /** The rice spoon has thirds and a half; the teaspoon only has a half (as in the fill_level table). */
    private static Variant spoon(String id, String name, double volumeMl, boolean withThirds) {
        List<FillLevel> levels = new ArrayList<>();
        for (ContentType type : ContentType.values()) {
            String prefix = type.name().toLowerCase();
            levels.add(level(prefix + ".full", type, 1.0, false));
            levels.add(level(prefix + ".1_2", type, 0.5, false));
            if (withThirds) {
                levels.add(level(prefix + ".2_3", type, 2.0 / 3, false));
                levels.add(level(prefix + ".1_3", type, 1.0 / 3, false));
            }
        }
        levels.add(level("powder.heaped", ContentType.POWDER, 1.0, true));
        return tool(id, name, volumeMl, null, levels);
    }

    private static FillLevel level(String key, ContentType type, double fraction, boolean heaped) {
        return new FillLevel(key, type, fraction, heaped, "fill." + key, ValueSource.ASSUMED);
    }

    private static Variant tool(String id, String name, Double volumeMl, Cup cup, List<FillLevel> levels) {
        return new Variant(id, name, null, "tool." + id, null, volumeMl, true, false, true, ValueSource.ASSUMED, null,
                cup, levels);
    }

    private static Variant standard(String id, String name) {
        return new Variant(id, name, null, "tool." + id, null, null, false, true, true, ValueSource.PUBLISHED, null,
                null, List.of());
    }

    private MeasureResult convert(String rawText, Sensitivity sensitivity, IngredientForm form, Variant... tools) {
        return converter.convert(new MeasureConverter.Request(parser.parse(rawText), sensitivity, form, true,
                Arrays.asList(tools), List.of()));
    }

    @Test
    void tsAndTsUnitsAreSpoonsAndCaseDecidesWhichOne() {
        assertThat(convert("1Ts", Sensitivity.NORMAL, IngredientForm.LIQUID, RICE_SPOON, TEASPOON).text())
                .isEqualTo(convert("1큰술", Sensitivity.NORMAL, IngredientForm.LIQUID, RICE_SPOON, TEASPOON).text());
        assertThat(convert("1ts", Sensitivity.NORMAL, IngredientForm.LIQUID, RICE_SPOON, TEASPOON).text())
                .isEqualTo(convert("1작은술", Sensitivity.NORMAL, IngredientForm.LIQUID, RICE_SPOON, TEASPOON).text());
        assertThat(units.mlByUnit().get("Ts")).isEqualTo(15.0);
        assertThat(units.mlByUnit().get("ts")).isEqualTo(5.0);
        // Unknown spellings are not guessed.
        assertThat(units.mlByUnit()).doesNotContainKey("TS");
    }

    @Test
    void unitsAreShownInKoreanWithoutChangingTheNumber() {
        assertThat(units.readable("1Ts", "Ts")).isEqualTo("1큰술");
        assertThat(units.readable("1/2T", "T")).isEqualTo("1/2큰술");
        assertThat(units.readable("2ts", "ts")).isEqualTo("2작은술");
        assertThat(units.readable("1C", "C")).isEqualTo("1컵");
        assertThat(units.readable("2큰술", "큰술")).isEqualTo("2큰술");
        assertThat(units.readable("200g", "g")).isEqualTo("200g");
        assertThat(units.readable("1개", "개")).isEqualTo("1개");
        assertThat(units.readable("약간", null)).isEqualTo("약간");
    }

    @Test
    void ratioIngredientMustStayWithinTenPercentWhereAnOrdinaryOneMayBeTwentyFivePercentOff() {
        // 0.28큰술 = 4.2 mL = 0.42 rice spoons; the nearest picture is a half spoon, 19% more.
        MeasureResult ordinary = convert("0.28큰술", Sensitivity.NORMAL, IngredientForm.POWDER, RICE_SPOON);
        MeasureResult flour = convert("0.28큰술", Sensitivity.RATIO, IngredientForm.POWDER, RICE_SPOON);

        assertThat(ordinary.status()).isEqualTo(QuantityStatus.CONVERTED_APPROX);
        assertThat(flour.status()).isEqualTo(QuantityStatus.NEEDS_CHECK);
        assertThat(flour.text()).isEqualTo("0.28큰술");
        assertThat(flour.tasteAdjust()).isFalse();
    }

    @Test
    void ratioIngredientIsConvertedWhenTheAmountFitsAndIsNeverMarkedTasteAndAdd() {
        MeasureResult flour = convert("1큰술", Sensitivity.RATIO, IngredientForm.POWDER, RICE_SPOON);

        assertThat(flour.status()).isEqualTo(QuantityStatus.CONVERTED_APPROX);
        assertThat(flour.text()).isEqualTo("밥숟가락으로 1번 반");
        assertThat(flour.tasteAdjust()).isFalse();
    }

    private MeasureResult convertWeight(String rawText, boolean beginner, List<WeightReference> references,
                                        Variant... tools) {
        return converter.convert(new MeasureConverter.Request(parser.parse(rawText), Sensitivity.NORMAL,
                IngredientForm.SOLID, beginner, Arrays.asList(tools), references));
    }

    // ---- standard measuring tools ---------------------------------------------------------

    @Test
    void measuringSpoonKeepsSpoonUnitsAsWritten() {
        MeasureResult result = convert("1큰술", Sensitivity.HIGH, IngredientForm.LIQUID, MEASURING_SPOON, RICE_SPOON);

        assertThat(result.text()).isEqualTo("1큰술");
        // not converted, so it is not an approximation
        assertThat(result.status()).isEqualTo(QuantityStatus.ORIGINAL);
        assertThat(result.toolVariantId()).isNull();
    }

    @Test
    void measuringSpoonAloneDoesNotCoverCupUnits() {
        MeasureResult result = convert("1컵", Sensitivity.NORMAL, IngredientForm.LIQUID, MEASURING_SPOON, PAPER_CUP);

        assertThat(result.text()).isEqualTo("작은 종이컵 1컵");
        assertThat(result.status()).isEqualTo(QuantityStatus.CONVERTED_APPROX);
    }

    @Test
    void measuringCupKeepsCupAndMillilitreUnitsAsWritten() {
        MeasureResult millilitres = convert("400ml", Sensitivity.NORMAL, IngredientForm.LIQUID, MEASURING_CUP, PAPER_CUP);
        MeasureResult spoonUnit = convert("1큰술", Sensitivity.NORMAL, IngredientForm.LIQUID, MEASURING_CUP, RICE_SPOON);

        assertThat(millilitres.text()).isEqualTo("400ml");
        assertThat(millilitres.status()).isEqualTo(QuantityStatus.ORIGINAL);
        assertThat(spoonUnit.text()).isEqualTo("밥숟가락으로 1번 반");
    }

    // ---- spoons ---------------------------------------------------------------------------

    @Test
    void tablespoonUsesTheRiceSpoonEvenWhenATeaspoonIsOwned() {
        MeasureResult result = convert("1큰술", Sensitivity.NORMAL, IngredientForm.LIQUID, TEASPOON, RICE_SPOON);

        // 15 mL in a 10 mL spoon
        assertThat(result.toolVariantId()).isEqualTo("spoon_rice");
        assertThat(result.text()).isEqualTo("밥숟가락으로 1번 반");
        assertThat(result.count()).isEqualTo(1.5);
        assertThat(result.fillLevelKey()).isEqualTo("liquid.1_2");
        assertThat(result.status()).isEqualTo(QuantityStatus.CONVERTED_APPROX);
    }

    @Test
    void teaspoonUnitUsesTheTeaspoon() {
        MeasureResult result = convert("1작은술", Sensitivity.NORMAL, IngredientForm.LIQUID, RICE_SPOON, TEASPOON);

        assertThat(result.text()).isEqualTo("티스푼으로 1번");
        assertThat(result.fillLevelKey()).isEqualTo("liquid.full");
    }

    @Test
    void withoutATeaspoonTheRiceSpoonIsFilledHalfway() {
        MeasureResult result = convert("1작은술", Sensitivity.NORMAL, IngredientForm.LIQUID, RICE_SPOON);

        assertThat(result.text()).isEqualTo("밥숟가락 반");
        assertThat(result.count()).isEqualTo(0.5);
        assertThat(result.fillLevelKey()).isEqualTo("liquid.1_2");
    }

    @Test
    void withoutARiceSpoonTheTeaspoonIsUsedForATablespoon() {
        MeasureResult result = convert("1큰술", Sensitivity.NORMAL, IngredientForm.LIQUID, TEASPOON);

        assertThat(result.text()).isEqualTo("티스푼으로 3번 반");
    }

    @Test
    void powderUsesThePowderPicturesAndNeverTheHeapedOne() {
        MeasureResult result = convert("1큰술", Sensitivity.NORMAL, IngredientForm.POWDER, RICE_SPOON);

        assertThat(result.fillLevelKey()).isEqualTo("powder.1_2");
        assertThat(result.text()).isEqualTo("밥숟가락으로 1번 반");
    }

    @Test
    void seasoningIsRoundedDownAndAsksToTaste() {
        // 13 mL is 1.3 rice spoons; one and a third would already be more than the source
        MeasureResult result = convert("13ml", Sensitivity.HIGH, IngredientForm.LIQUID, RICE_SPOON);

        assertThat(result.text()).isEqualTo("밥숟가락으로 1번");
        assertThat(result.tasteAdjust()).isTrue();
    }

    @Test
    void seasoningNeverExceedsTheSourceAmount() {
        // 1.9 rice spoons: a normal ingredient would become 2
        MeasureResult seasoning = convert("19ml", Sensitivity.HIGH, IngredientForm.LIQUID, RICE_SPOON);
        MeasureResult normal = convert("19ml", Sensitivity.NORMAL, IngredientForm.LIQUID, RICE_SPOON);

        assertThat(seasoning.text()).isEqualTo("밥숟가락으로 1번과 2/3");
        assertThat(seasoning.count()).isLessThanOrEqualTo(1.9);
        assertThat(normal.text()).isEqualTo("밥숟가락으로 2번");
        assertThat(normal.tasteAdjust()).isFalse();
    }

    @Test
    void amountTooSmallForAnyPictureIsNotForcedIntoATool() {
        // 1 mL is a tenth of a rice spoon; the smallest picture is a third
        MeasureResult result = convert("1ml", Sensitivity.NORMAL, IngredientForm.LIQUID, RICE_SPOON);

        assertThat(result.status()).isEqualTo(QuantityStatus.NEEDS_CHECK);
        assertThat(result.text()).isEqualTo("1ml");
    }

    // ---- cups, bottle and bowls -----------------------------------------------------------

    @Test
    void liquidVolumeBecomesCups() {
        MeasureResult result = convert("400ml", Sensitivity.NORMAL, IngredientForm.LIQUID,
                RICE_SPOON, RICE_BOWL, WATER_BOTTLE, PAPER_CUP);

        assertThat(result.toolVariantId()).isEqualTo("paper_cup_small");
        assertThat(result.text()).isEqualTo("작은 종이컵 2컵");
        assertThat(result.count()).isEqualTo(2.0);
        assertThat(result.fillLevelKey()).isNull();
    }

    @Test
    void halfACupIsAMarkComputedForTheCup() {
        MeasureResult result = convert("100ml", Sensitivity.NORMAL, IngredientForm.LIQUID, PAPER_CUP);

        assertThat(result.text()).isEqualTo("작은 종이컵 반 컵");
    }

    @Test
    void bottleIsPreferredOverABowlForLiquids() {
        MeasureResult result = convert("500ml", Sensitivity.NORMAL, IngredientForm.LIQUID, RICE_BOWL, WATER_BOTTLE);

        assertThat(result.text()).isEqualTo("생수병 1병");
    }

    @Test
    void theToolThatNeedsTheFewestPoursIsChosen() {
        // The paper cup would take two and two thirds cups: three pours against one bottle.
        MeasureResult stock = convert("500ml", Sensitivity.NORMAL, IngredientForm.LIQUID,
                RICE_SPOON, PAPER_CUP, WATER_BOTTLE);
        MeasureResult withoutBottle = convert("500ml", Sensitivity.NORMAL, IngredientForm.LIQUID,
                RICE_SPOON, PAPER_CUP);

        assertThat(stock.toolVariantId()).isEqualTo("water_bottle_500");
        assertThat(stock.text()).isEqualTo("생수병 1병");
        assertThat(stock.count()).isEqualTo(1.0);
        assertThat(withoutBottle.text()).isEqualTo("작은 종이컵 2컵과 2/3");
    }

    @Test
    void withTheSamePoursTheTidierAmountWins() {
        Variant bigCup = tool("glass_cup", "유리컵", 750.0, new Cup(90, 70, 150, 10), List.of());
        Variant soupBowl = tool("soup_bowl", "국그릇", 500.0, null, List.of());

        // One pour either way: a whole bottle is tidier than two thirds of the cup.
        MeasureResult wholeOverThirds = convert("500ml", Sensitivity.NORMAL, IngredientForm.LIQUID,
                bigCup, WATER_BOTTLE);
        // Both are one whole fill: the usual order (bottle before bowls) decides.
        MeasureResult usualOrder = convert("500ml", Sensitivity.NORMAL, IngredientForm.LIQUID,
                soupBowl, WATER_BOTTLE);
        // A half is tidier than a third: 250 mL is half a bottle or a third of the big cup.
        MeasureResult halfOverThird = convert("250ml", Sensitivity.NORMAL, IngredientForm.LIQUID,
                bigCup, WATER_BOTTLE);

        assertThat(wholeOverThirds.text()).isEqualTo("생수병 1병");
        assertThat(usualOrder.text()).isEqualTo("생수병 1병");
        assertThat(halfOverThird.text()).isEqualTo("생수병 반 병");
    }

    @Test
    void aRoughAmountDoesNotWinJustBecauseItIsFewerPours() {
        // One bottle would be 25% too much; two cups are 3.5% off.
        MeasureResult result = convert("400ml", Sensitivity.NORMAL, IngredientForm.LIQUID, PAPER_CUP, WATER_BOTTLE);

        assertThat(result.text()).isEqualTo("작은 종이컵 2컵");
    }

    @Test
    void measuredVolumeIsUsedWhenChoosingTheTool() {
        Variant soupBowl = tool("soup_bowl", "국그릇", 500.0, null, List.of());

        MeasureResult representative = convert("1000ml", Sensitivity.NORMAL, IngredientForm.LIQUID,
                PAPER_CUP, soupBowl);
        // The user's bowl holds 250 mL: four bowls now, against five cups.
        MeasureResult measured = convert("1000ml", Sensitivity.NORMAL, IngredientForm.LIQUID,
                PAPER_CUP, soupBowl.withVolume(250));
        // Measured at 1000 mL it is a single pour.
        MeasureResult bigBowl = convert("1000ml", Sensitivity.NORMAL, IngredientForm.LIQUID,
                PAPER_CUP, soupBowl.withVolume(1000));

        assertThat(representative.text()).isEqualTo("국그릇으로 가득 2번");
        assertThat(measured.text()).isEqualTo("국그릇으로 가득 4번");
        assertThat(bigBowl.text()).isEqualTo("국그릇으로 가득 1번");
    }

    @Test
    void seasoningIsRoundedDownWhicheverToolIsChosen() {
        for (int ml = 100; ml <= 1200; ml += 50) {
            MeasureResult result = convert(ml + "ml", Sensitivity.HIGH, IngredientForm.LIQUID,
                    PAPER_CUP, WATER_BOTTLE, RICE_BOWL);
            double toolMl = switch (result.toolVariantId()) {
                case "paper_cup_small" -> 193;
                case "water_bottle_500" -> 500;
                default -> 300;
            };

            assertThat(result.count() * toolMl).isLessThanOrEqualTo(ml + 1e-6);
            assertThat(result.tasteAdjust()).isTrue();
        }
    }

    @Test
    void bowlIsDescribedOnlyAsFullOrAboutHalf() {
        MeasureResult oneAndAHalf = convert("450ml", Sensitivity.NORMAL, IngredientForm.LIQUID, RICE_SPOON, RICE_BOWL);
        MeasureResult half = convert("150ml", Sensitivity.NORMAL, IngredientForm.LIQUID, RICE_BOWL);

        assertThat(oneAndAHalf.text()).isEqualTo("밥공기로 가득 1번 반");
        assertThat(half.text()).isEqualTo("밥공기 반 정도");
        assertThat(half.fillLevelKey()).isNull();
    }

    @Test
    void solidIsNeverMeasuredByTheVolumeOfAContainer() {
        MeasureResult result = convert("1컵", Sensitivity.NORMAL, IngredientForm.SOLID, PAPER_CUP, RICE_BOWL);

        assertThat(result.status()).isEqualTo(QuantityStatus.ORIGINAL);
        assertThat(result.toolVariantId()).isNull();
        assertThat(result.text()).isEqualTo("1컵");
    }

    // ---- weight ---------------------------------------------------------------------------

    @Test
    void beginnerSeesAWeightAsAToolAmountWithGramsInBrackets() {
        MeasureResult full = convertWeight("200g", true, KIMCHI_IN_BOWL, RICE_SPOON, RICE_BOWL);
        MeasureResult half = convertWeight("100g", true, KIMCHI_IN_BOWL, RICE_SPOON, RICE_BOWL);

        // 200 g per bowl
        assertThat(full.text()).isEqualTo("밥공기로 가득 1번 (200g)");
        assertThat(full.toolVariantId()).isEqualTo("rice_bowl");
        assertThat(full.status()).isEqualTo(QuantityStatus.CONVERTED_APPROX);
        assertThat(half.text()).isEqualTo("밥공기 반 정도 (100g)");
    }

    @Test
    void weightBetweenHalfAndFullBowlIsNotGuessed() {
        // 150 g is three quarters of a bowl; a bowl has no mark for that
        MeasureResult result = convertWeight("150g", true, KIMCHI_IN_BOWL, RICE_SPOON, RICE_BOWL);

        assertThat(result.status()).isEqualTo(QuantityStatus.NEEDS_CHECK);
        assertThat(result.text()).isEqualTo("150g");
    }

    @Test
    void seasoningByWeightIsRoundedDownForAToolTheUserMeasured() {
        // The user's bowl holds 260 mL instead of 300 mL, so it takes about 173 g instead of 200 g.
        Variant measuredBowl = RICE_BOWL.withVolume(260);
        WeightReference scaled = KIMCHI_IN_BOWL.get(0).scaledBy(260.0 / 300);

        for (int grams = 100; grams <= 700; grams += 20) {
            MeasureResult result = converter.convert(new MeasureConverter.Request(parser.parse(grams + "g"),
                    Sensitivity.HIGH, IngredientForm.SOLID, true, List.of(RICE_SPOON, measuredBowl),
                    List.of(scaled)));

            assertThat(result.status()).isEqualTo(QuantityStatus.CONVERTED_APPROX);
            // The guided amount never weighs more than the source says.
            assertThat(result.count() * scaled.grams()).isLessThanOrEqualTo(grams + 1e-6);
            assertThat(result.tasteAdjust()).isTrue();
        }
        // 300 g is 1.73 of this bowl: down to one and a half, never up to two.
        MeasureResult threeHundred = converter.convert(new MeasureConverter.Request(parser.parse("300g"),
                Sensitivity.HIGH, IngredientForm.SOLID, true, List.of(measuredBowl), List.of(scaled)));
        assertThat(threeHundred.count()).isEqualTo(1.5);
    }

    @Test
    void experiencedCookSeesTheWeightAsWritten() {
        MeasureResult result = convertWeight("200g", false, KIMCHI_IN_BOWL, RICE_SPOON, RICE_BOWL);

        assertThat(result.text()).isEqualTo("200g");
        assertThat(result.status()).isEqualTo(QuantityStatus.ORIGINAL);
        assertThat(result.toolVariantId()).isNull();
    }

    @Test
    void weightWithoutAReferenceIsNeverTurnedIntoSpoonsOrCups() {
        MeasureResult noReference = convertWeight("100g", true, List.of(), RICE_SPOON, PAPER_CUP, RICE_BOWL);
        MeasureResult toolNotOwned = convertWeight("200g", true, KIMCHI_IN_BOWL, RICE_SPOON, PAPER_CUP);

        assertThat(noReference.status()).isEqualTo(QuantityStatus.NEEDS_CHECK);
        assertThat(noReference.text()).isEqualTo("100g");
        assertThat(toolNotOwned.status()).isEqualTo(QuantityStatus.NEEDS_CHECK);
        assertThat(toolNotOwned.toolVariantId()).isNull();
    }

    @Test
    void parenthesisedSpoonValueIsUsedForAWeight() {
        ParsedAmount amount = parser.parse("2g(1/3작은술)");
        MeasureResult result = converter.convert(new MeasureConverter.Request(amount, Sensitivity.HIGH,
                IngredientForm.POWDER, true, List.of(RICE_SPOON), List.of()));

        assertThat(converter.volumeMl(amount).getAsDouble()).isCloseTo(5.0 / 3, within(1e-9));
        // 1.67 mL is below a third of a rice spoon, so nothing is guessed
        assertThat(result.status()).isEqualTo(QuantityStatus.NEEDS_CHECK);
        assertThat(result.text()).isEqualTo("2g(1/3작은술)");
    }

    // ---- amounts that are not converted ---------------------------------------------------

    @Test
    void vagueSeasoningNeedsChecking() {
        MeasureResult result = convert("약간", Sensitivity.HIGH, IngredientForm.POWDER, RICE_SPOON);

        assertThat(result.status()).isEqualTo(QuantityStatus.NEEDS_CHECK);
        assertThat(result.count()).isNull();
        assertThat(result.text()).isEqualTo("맛보며 조금씩");
        assertThat(result.tasteAdjust()).isTrue();
    }

    @Test
    void vagueNormalIngredientKeepsTheSourceWording() {
        MeasureResult result = convert("약간", Sensitivity.NORMAL, IngredientForm.SOLID, RICE_SPOON);

        assertThat(result.status()).isEqualTo(QuantityStatus.NEEDS_CHECK);
        assertThat(result.text()).isEqualTo("약간");
    }

    @Test
    void countUnitsStayAsWritten() {
        MeasureResult egg = convert("1개", Sensitivity.NORMAL, IngredientForm.SOLID, RICE_SPOON);
        MeasureResult greenOnion = convert("1/4대", Sensitivity.NORMAL, IngredientForm.SOLID, RICE_SPOON);

        assertThat(egg.status()).isEqualTo(QuantityStatus.ORIGINAL);
        assertThat(egg.text()).isEqualTo("1개");
        assertThat(greenOnion.status()).isEqualTo(QuantityStatus.ORIGINAL);
        assertThat(greenOnion.text()).isEqualTo("1/4대");
    }

    @Test
    void withoutMeasuringToolsTheSourceAmountIsShown() {
        MeasureResult result = convert("1큰술", Sensitivity.NORMAL, IngredientForm.LIQUID, KNIFE);

        assertThat(result.status()).isEqualTo(QuantityStatus.NEEDS_CHECK);
        assertThat(result.text()).isEqualTo("1큰술");
    }
}
