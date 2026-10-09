package com.example.aicook.reference;

import com.example.aicook.common.Json;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How source units are read (data/seed/units.json): standard spoon and cup sizes, weight
 * units and words that mean "no exact amount". Unrelated to the volume of the user's own tools.
 *
 * @param volumeTools containers to try after cups for a plain volume, in order
 *                    (the water bottle before bowls)
 */
public record Units(List<VolumeUnit> volumeUnits, List<String> weightUnits, List<String> vagueWords,
                    List<String> volumeTools, double normalToleranceRatio,
                    double ratioToleranceRatio) {

    private static final Set<String> KILOGRAM_UNITS = Set.of("kg", "킬로그램");

    /**
     * @param standardTool measuring spoon or cup that shows this unit as written when owned
     * @param tools        spoons to try in order for this unit (큰술: rice spoon then teaspoon);
     *                     null for plain volumes, which are matched to a tool by size
     */
    public record VolumeUnit(List<String> labels, double ml, String standardTool, List<String> tools) {
    }

    public static Units load() {
        return Json.readResource("seed/units.json", Units.class);
    }

    /** Unit label -> mL per unit. Labels are case-sensitive ("T" is a tablespoon, "t" a teaspoon). */
    public Map<String, Double> mlByUnit() {
        return volumeUnits.stream()
                .flatMap(unit -> unit.labels().stream().map(label -> Map.entry(label, unit.ml())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Spoon ids to try in order for a spoon unit; empty for plain volumes and unknown units. */
    public List<String> toolsFor(String unit) {
        return find(unit).map(VolumeUnit::tools).orElse(List.of());
    }

    /** Standard measuring tool for a unit (계량스푼 for 큰술, 계량컵 for 컵 and mL). */
    public Optional<String> standardToolFor(String unit) {
        return find(unit).map(VolumeUnit::standardTool);
    }

    private Optional<VolumeUnit> find(String unit) {
        return volumeUnits.stream().filter(volumeUnit -> volumeUnit.labels().contains(unit)).findFirst();
    }

    public boolean isWeightUnit(String unit) {
        return unit != null && weightUnits.contains(unit);
    }

    public double gramsPerUnit(String unit) {
        return KILOGRAM_UNITS.contains(unit) ? 1000 : 1;
    }
}
