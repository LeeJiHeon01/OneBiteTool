package com.example.aicook.measure;

import com.example.aicook.common.Korean;
import com.example.aicook.measure.MeasureResult.QuantityStatus;
import com.example.aicook.measure.ParsedAmount.Amount;
import com.example.aicook.reference.Rules.WeightReference;
import com.example.aicook.reference.Sensitivity;
import com.example.aicook.reference.Tools.ContentType;
import com.example.aicook.reference.Tools.FillLevel;
import com.example.aicook.reference.Tools.Variant;
import com.example.aicook.reference.Units;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Stream;

/**
 * Turns a source amount into an amount of a tool the user owns ("밥숟가락으로 1번 반").
 * All arithmetic happens here; the AI never computes amounts.
 *
 * <ul>
 * <li>A user with a measuring spoon or cup sees the units it covers exactly as written.</li>
 * <li>Spoon units pick their own spoon first (큰술: rice spoon, 작은술: teaspoon) and fall back
 *     to the other spoon with a fraction ("밥숟가락 반").</li>
 * <li>Plain volumes (ml, 컵) of liquids use the owned cup, bottle or bowl that needs the fewest
 *     pours, then the tidiest amount; solids are never measured by the volume of a container.</li>
 * <li>Weights are shown as written. Only for beginners they are expressed with a tool, and only
 *     when a per-ingredient weight reference exists; grams are never treated as millilitres.</li>
 * </ul>
 */
public class MeasureConverter {

    /** Above this many fills the next tool is tried when one fits. */
    private static final double MAX_COMFORTABLE_COUNT = 5;
    /** Plain volumes below this are spooned rather than poured into a cup. */
    private static final double SMALL_VOLUME_ML = 30;
    private static final double EPSILON = 1e-9;
    private static final String TASTE_AS_YOU_GO = "맛보며 조금씩";
    /** Marks of a cup with known dimensions; their height is computed from the cup's shape. */
    private static final List<Part> CUP_PARTS =
            List.of(new Part(1.0 / 3, null), new Part(0.5, null), new Part(2.0 / 3, null));
    /** Bowls and bottles have curved sides, so no marks are computed: only "full" and "about half". */
    private static final List<Part> CONTAINER_PARTS = List.of(new Part(0.5, null));

    private final Units units;

    public MeasureConverter(Units units) {
        this.units = units;
    }

    /**
     * @param form             physical form of the ingredient
     * @param weightAsTool     true for beginners: a weight is expressed with a tool, grams in brackets
     * @param ownedTools       every tool the user owns
     * @param weightReferences "this ingredient weighs N grams in tool X" entries for the ingredient
     */
    public record Request(ParsedAmount amount, Sensitivity sensitivity, IngredientForm form, boolean weightAsTool,
                          List<Variant> ownedTools, List<WeightReference> weightReferences) {
    }

    public MeasureResult convert(Request request) {
        ParsedAmount amount = request.amount();
        boolean seasoning = request.sensitivity() == Sensitivity.HIGH;
        if (amount.vague()) {
            String text = seasoning ? TASTE_AS_YOU_GO : amount.rawText();
            return MeasureResult.unconverted(text, seasoning, QuantityStatus.NEEDS_CHECK);
        }
        if (amount.primary() == null) {
            return MeasureResult.unconverted(amount.rawText(), seasoning, QuantityStatus.NEEDS_CHECK);
        }
        if (units.isWeightUnit(amount.primary().unit())) {
            return convertWeight(request, seasoning);
        }
        Amount volume = volumeAmount(amount);
        if (volume == null) {
            // counts ("1개", "1/4대") and units the server does not know stay as written
            return MeasureResult.unconverted(amount.rawText(), false, QuantityStatus.ORIGINAL);
        }
        return convertVolume(request, volume, seasoning, "");
    }

    /** Volume of the amount in mL; the parenthesised value wins when it is a volume. */
    public OptionalDouble volumeMl(ParsedAmount amount) {
        Amount volume = volumeAmount(amount);
        return volume == null ? OptionalDouble.empty() : OptionalDouble.of(toMl(volume));
    }

    private Amount volumeAmount(ParsedAmount amount) {
        if (isVolume(amount.alternative())) {
            return amount.alternative();
        }
        return isVolume(amount.primary()) ? amount.primary() : null;
    }

    private boolean isVolume(Amount amount) {
        return amount != null && amount.unit() != null && units.mlByUnit().containsKey(amount.unit());
    }

    private double toMl(Amount amount) {
        return amount.quantity() * units.mlByUnit().get(amount.unit());
    }

    // ---- weight -------------------------------------------------------------------------

    private MeasureResult convertWeight(Request request, boolean seasoning) {
        ParsedAmount amount = request.amount();
        if (!request.weightAsTool()) {
            return MeasureResult.unconverted(amount.rawText(), false, QuantityStatus.ORIGINAL);
        }
        String gramsNote = " (" + amount.rawText() + ")";
        // "2g(1/3작은술)": the source already gives a spoon amount for the weight
        if (isVolume(amount.alternative())) {
            return convertVolume(request, amount.alternative(), seasoning, gramsNote);
        }
        double grams = amount.primary().quantity() * units.gramsPerUnit(amount.primary().unit());
        ContentType contentType = contentTypeOf(request.form());
        for (WeightReference reference : request.weightReferences()) {
            Optional<Variant> tool = ownedMeasuringTool(request, reference.toolId());
            if (tool.isEmpty()) {
                continue;
            }
            Optional<Expression> expression = express(grams / reference.grams(), request.sensitivity(), tool.get(), contentType);
            if (expression.isPresent()) {
                return expression.get().toResult(seasoning, gramsNote);
            }
        }
        // no reference for this ingredient: grams cannot become a tool amount
        return MeasureResult.unconverted(amount.rawText(), seasoning, QuantityStatus.NEEDS_CHECK);
    }

    // ---- volume -------------------------------------------------------------------------

    private MeasureResult convertVolume(Request request, Amount volume, boolean seasoning, String note) {
        String rawText = request.amount().rawText();
        // With the standard measuring tool for this unit nothing is converted, so it is not approximate.
        if (ownsStandardToolFor(request, volume.unit())) {
            return MeasureResult.unconverted(rawText, false, QuantityStatus.ORIGINAL);
        }
        List<String> spoonOrder = units.toolsFor(volume.unit());
        boolean spoonUnit = spoonOrder != null && !spoonOrder.isEmpty();
        // A cup or bowl only measures what can be poured; paste, powder and solids keep the source amount.
        if (!spoonUnit && request.form() != IngredientForm.LIQUID) {
            return MeasureResult.unconverted(rawText, false, QuantityStatus.ORIGINAL);
        }
        double ml = toMl(volume);
        ContentType contentType = contentTypeOf(request.form());
        if (spoonUnit) {
            return spooned(spoonOrder.stream().flatMap(id -> ownedMeasuringTool(request, id).stream()).toList(),
                    ml, request.sensitivity(), contentType, units.normalToleranceRatio())
                    .map(found -> found.toResult(seasoning, note))
                    .orElseGet(() -> MeasureResult.unconverted(rawText, seasoning, QuantityStatus.NEEDS_CHECK));
        }
        Optional<Expression> expression = plainVolume(request, ml, contentType);
        if (expression.isPresent()) {
            return expression.get().toResult(seasoning, note);
        }
        // An ordinary liquid with no amount inside the tolerance: the closest amount, marked for checking.
        // An ingredient whose proportion matters is never approximated beyond its tolerance.
        Optional<Expression> closest = request.sensitivity() == Sensitivity.NORMAL
                ? closestPoured(request, ml, contentType) : Optional.empty();
        return closest.map(found -> found.toResult(false, note, QuantityStatus.NEEDS_CHECK))
                .orElseGet(() -> MeasureResult.unconverted(rawText, seasoning, QuantityStatus.NEEDS_CHECK));
    }

    /**
     * A plain volume goes into the owned cup, bottle or bowl that needs the fewest pours. A small
     * volume is spooned when that takes few spoons, and spoons are the last resort otherwise.
     */
    private Optional<Expression> plainVolume(Request request, double ml, ContentType contentType) {
        List<Variant> measuring = request.ownedTools().stream().filter(Variant::isMeasuringTool).toList();
        List<Variant> spoons = measuring.stream()
                .filter(MeasureConverter::isSpoon)
                .sorted(Comparator.comparingDouble(Variant::representativeVolumeMl).reversed())
                .toList();
        Optional<Expression> spoonAmount = spooned(spoons, ml, request.sensitivity(), contentType,
                units.liquidToleranceRatio());
        if (ml < SMALL_VOLUME_ML && spoonAmount.filter(Expression::isComfortable).isPresent()) {
            return spoonAmount;
        }
        Optional<Expression> poured = poured(pourableInOrder(measuring), ml, request.sensitivity(), contentType);
        return poured.isPresent() ? poured : spoonAmount;
    }

    /** The amount closest to the source in any owned cup, bottle or bowl, whatever its error. */
    private Optional<Expression> closestPoured(Request request, double ml, ContentType contentType) {
        List<Variant> measuring = request.ownedTools().stream().filter(Variant::isMeasuringTool).toList();
        return pourableInOrder(measuring).stream()
                .flatMap(tool -> express(ml / tool.representativeVolumeMl(), Sensitivity.NORMAL, tool, contentType,
                        Double.MAX_VALUE).stream())
                .min(Comparator.comparingDouble(Expression::error).thenComparingInt(Expression::pours));
    }

    /** The first spoon that needs few fills; when none does, the first spoon that can express the amount. */
    private Optional<Expression> spooned(List<Variant> spoonsInOrder, double ml, Sensitivity sensitivity,
                                         ContentType contentType, double normalTolerance) {
        List<Expression> expressions = spoonsInOrder.stream()
                .flatMap(spoon -> express(ml / spoon.representativeVolumeMl(), sensitivity, spoon, contentType,
                        normalTolerance).stream())
                .toList();
        return expressions.stream().filter(Expression::isComfortable).findFirst()
                .or(() -> expressions.stream().findFirst());
    }

    /**
     * Fewest pours first, then the tidiest amount (whole, then a half, then thirds), then the
     * usual order of the tools, then the smallest error. Only amounts inside the liquid tolerance
     * compete (10% for ordinary liquids too: a quarter more water makes a soup bland); seasoning
     * is only ever rounded down.
     */
    private Optional<Expression> poured(List<Variant> toolsInOrder, double ml, Sensitivity sensitivity,
                                        ContentType contentType) {
        return toolsInOrder.stream()
                .flatMap(tool -> express(ml / tool.representativeVolumeMl(), sensitivity, tool, contentType,
                        units.liquidToleranceRatio()).stream())
                .min(Comparator.comparingInt(Expression::pours)
                        .thenComparingInt(Expression::tidiness)
                        .thenComparingInt((Expression expression) -> toolsInOrder.indexOf(expression.tool()))
                        .thenComparingDouble(Expression::error));
    }

    private boolean ownsStandardToolFor(Request request, String unit) {
        Optional<String> standardTool = units.standardToolFor(unit);
        return standardTool.isPresent() && request.ownedTools().stream()
                .anyMatch(tool -> tool.isStandardMeasure() && tool.id().equals(standardTool.get()));
    }

    /** Cups, then the bottle and bowls in the seed order: the usual order when nothing else decides. */
    private List<Variant> pourableInOrder(List<Variant> measuring) {
        List<Variant> tools = new ArrayList<>(measuring.stream().filter(MeasureConverter::isCup).toList());
        measuring.stream()
                .filter(tool -> !isSpoon(tool) && !isCup(tool))
                .sorted(Comparator.comparingInt(this::containerOrder))
                .forEach(tools::add);
        return tools;
    }

    private int containerOrder(Variant tool) {
        int index = units.volumeTools() == null ? -1 : units.volumeTools().indexOf(tool.id());
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static boolean isSpoon(Variant tool) {
        return !tool.fillLevels().isEmpty();
    }

    private static boolean isCup(Variant tool) {
        return tool.cup() != null;
    }

    private static ContentType contentTypeOf(IngredientForm form) {
        return form == IngredientForm.POWDER ? ContentType.POWDER : ContentType.LIQUID;
    }

    private Optional<Variant> ownedMeasuringTool(Request request, String toolId) {
        return request.ownedTools().stream()
                .filter(tool -> tool.id().equals(toolId) && tool.isMeasuringTool())
                .findFirst();
    }

    // ---- expressing a number of fills ------------------------------------------------------

    /** A part of one fill: its share of the tool and the spoon picture that shows it. */
    private record Part(double fraction, String key) {
    }

    /**
     * Expresses {@code fills} as whole fills plus at most one fraction the tool supports.
     * Seasoning is rounded down so the guided amount never exceeds the source; other
     * ingredients go to the nearest amount and must stay within the tolerance of their
     * sensitivity (tighter for ingredients whose proportion matters).
     */
    private Optional<Expression> express(double fills, Sensitivity sensitivity, Variant tool,
                                         ContentType contentType) {
        return express(fills, sensitivity, tool, contentType, units.normalToleranceRatio());
    }

    /** @param normalTolerance tolerance of an ordinary ingredient here; liquids poured into a tool use a tighter one */
    private Optional<Expression> express(double fills, Sensitivity sensitivity, Variant tool,
                                         ContentType contentType, double normalTolerance) {
        boolean seasoning = sensitivity == Sensitivity.HIGH;
        if (fills <= EPSILON) {
            return Optional.empty();
        }
        List<Part> parts;
        String fullKey = null;
        if (isSpoon(tool)) {
            // Only pictures that exist for this spoon and content; heaped amounts have no known volume.
            List<FillLevel> levels = tool.fillLevels().stream()
                    .filter(level -> level.contentType() == contentType && !level.heaped())
                    .toList();
            Optional<FillLevel> full = levels.stream().filter(level -> level.fraction() > 1 - EPSILON).findFirst();
            if (full.isEmpty()) {
                return Optional.empty();
            }
            fullKey = full.get().key();
            parts = levels.stream()
                    .filter(level -> level.fraction() < 1 - EPSILON)
                    .map(level -> new Part(level.fraction(), level.key()))
                    .toList();
        } else {
            parts = isCup(tool) ? CUP_PARTS : CONTAINER_PARTS;
        }
        String wholeKey = fullKey;
        String counter = units.counterOf(tool.id()).orElse(null);
        int base = (int) Math.floor(fills + EPSILON);
        Stream<Expression> candidates = Stream.of(base - 1, base, base + 1)
                .filter(whole -> whole >= 0)
                .flatMap(whole -> Stream.concat(
                        Stream.of(new Expression(tool, whole, 0, wholeKey, 0, counter)),
                        parts.stream().map(part ->
                                new Expression(tool, whole, part.fraction(), part.key(), 0, counter))))
                .filter(candidate -> candidate.total() > EPSILON)
                .map(candidate -> candidate.withError(Math.abs(candidate.total() - fills) / fills));
        if (seasoning) {
            return candidates
                    .filter(candidate -> candidate.total() <= fills + EPSILON)
                    .max(Comparator.comparingDouble(Expression::total));
        }
        double tolerance = sensitivity == Sensitivity.RATIO ? units.ratioToleranceRatio() : normalTolerance;
        return candidates
                .min(Comparator.comparingDouble(Expression::error).thenComparingDouble(Expression::total))
                .filter(candidate -> candidate.error() <= tolerance + EPSILON);
    }

    /** counter: the word a container is counted with ("병"), null for tools counted in fills. */
    private record Expression(Variant tool, int whole, double fraction, String key, double error, String counter) {

        double total() {
            return whole + fraction;
        }

        boolean isComfortable() {
            return total() <= MAX_COMFORTABLE_COUNT + EPSILON;
        }

        /** How many times the tool is filled and poured: one and a half cups are two pours. */
        int pours() {
            return (int) Math.ceil(total() - EPSILON);
        }

        /** 0 for a whole number of fills, 1 with a half, 2 with a third or two thirds. */
        int tidiness() {
            if (!hasFraction()) {
                return 0;
            }
            return isHalf() ? 1 : 2;
        }

        boolean hasFraction() {
            return fraction > EPSILON;
        }

        Expression withError(double newError) {
            return new Expression(tool, whole, fraction, key, newError, counter);
        }

        MeasureResult toResult(boolean tasteAdjust, String note) {
            return toResult(tasteAdjust, note, QuantityStatus.CONVERTED_APPROX);
        }

        MeasureResult toResult(boolean tasteAdjust, String note, QuantityStatus status) {
            return new MeasureResult(tool.id(), key, total(), text() + note, tasteAdjust, status);
        }

        private String text() {
            if (isSpoon(tool)) {
                return spoonText();
            }
            if (isCup(tool)) {
                return cupText();
            }
            return counter != null ? countedText() : containerText();
        }

        /** A bottle is counted itself: "생수병 1병", "생수병 1병 반", "생수병 반 병". */
        private String countedText() {
            if (whole == 0) {
                return tool.name() + " 반 " + counter;
            }
            return tool.name() + " " + whole + counter + (hasFraction() ? " 반" : "");
        }

        /** "밥숟가락으로 1번 반", "밥숟가락 반", "밥숟가락으로 1번과 2/3". */
        private String spoonText() {
            if (whole == 0) {
                return tool.name() + " " + fractionText();
            }
            String text = Korean.with(tool.name()) + " " + whole + "번";
            if (!hasFraction()) {
                return text;
            }
            return text + (isHalf() ? " 반" : "과 " + fractionText());
        }

        /** "종이컵 2컵", "종이컵 반 컵", "종이컵 1컵과 1/3". */
        private String cupText() {
            if (whole == 0) {
                return tool.name() + " " + (isHalf() ? "반 컵" : fractionText() + "컵");
            }
            String text = tool.name() + " " + whole + "컵";
            if (!hasFraction()) {
                return text;
            }
            return text + (isHalf() ? " 반" : "과 " + fractionText());
        }

        /** Bowls and bottles are described in words only: "밥공기로 가득 1번", "밥공기 반 정도". */
        private String containerText() {
            if (whole == 0) {
                return tool.name() + " 반 정도";
            }
            return Korean.with(tool.name()) + " 가득 " + whole + "번" + (hasFraction() ? " 반" : "");
        }

        private boolean isHalf() {
            return Math.abs(fraction - 0.5) < 1e-3;
        }

        private String fractionText() {
            if (isHalf()) {
                return "반";
            }
            if (Math.abs(fraction - 1.0 / 3) < 1e-3) {
                return "1/3";
            }
            if (Math.abs(fraction - 2.0 / 3) < 1e-3) {
                return "2/3";
            }
            if (Math.abs(fraction - 0.25) < 1e-3) {
                return "1/4";
            }
            return Math.abs(fraction - 0.75) < 1e-3 ? "3/4" : Math.round(fraction * 100) + "%";
        }
    }
}
