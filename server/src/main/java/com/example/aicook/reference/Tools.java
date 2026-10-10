package com.example.aicook.reference;

import java.util.List;

/** Tool catalog as stored in tool_category, tool_variant and fill_level. Ids are the table codes. */
public final class Tools {

    private Tools() {
    }

    public record Category(String id, String name, List<Variant> variants) {
    }

    /**
     * One selectable tool.
     *
     * @param heatSourceType           GAS, INDUCTION, ... for heating appliances, otherwise null
     * @param representativeVolumeMl   volume when filled to the "full" line; null for non-measuring tools
     * @param cup                      dimensions used to compute cup marks; null for spoons
     * @param isStandardMeasure        measuring spoon or cup: units it covers are shown as written
     * @param isBasic                  false = listed under "more" on the tool selection screen
     * @param fillLevels               spoon amount pictures; empty for cups (their marks are computed)
     */
    public record Variant(String id, String name, String subLabel, String iconKey, String heatSourceType,
                          Double representativeVolumeMl, boolean isApproximate, boolean isStandardMeasure,
                          boolean isBasic, ValueSource valueSource,
                          String sourceRef, Cup cup, List<FillLevel> fillLevels) {

        public boolean isMeasuringTool() {
            return representativeVolumeMl != null;
        }

        /** The same tool holding the volume its owner measured instead of the representative one. */
        public Variant withVolume(double volumeMl) {
            return new Variant(id, name, subLabel, iconKey, heatSourceType, volumeMl, isApproximate,
                    isStandardMeasure, isBasic, valueSource, sourceRef, cup, fillLevels);
        }
    }

    public record Cup(double topDiameterMm, double bottomDiameterMm, double heightMm, double fillLineFromTopMm) {
    }

    /** fraction is the share of the representative volume; heaped amounts have no reliable volume. */
    public record FillLevel(String key, ContentType contentType, double fraction, boolean heaped, String imageKey,
                            ValueSource valueSource) {
    }

    public enum ContentType {
        POWDER,
        LIQUID
    }
}
