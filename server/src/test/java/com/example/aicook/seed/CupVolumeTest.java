package com.example.aicook.seed;

import com.example.aicook.seed.SeedFiles.CupSize;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Cup volume is computed from the cup's dimensions, never from "half the height". */
class CupVolumeTest {

    @Test
    void taperedCupUsesTheFrustumVolumeUpToTheFillLine() {
        // small paper cup in data/seed/tools.json
        assertThat(SeedSync.cupVolumeMl(new CupSize(70, 50, 78, 8))).isCloseTo(193, within(1.0));
    }

    @Test
    void straightCupIsACylinder() {
        assertThat(SeedSync.cupVolumeMl(new CupSize(70, 70, 95, 15))).isCloseTo(307.9, within(0.1));
    }

    @Test
    void halfTheHeightOfATaperedCupHoldsLessThanHalfTheVolume() {
        double full = SeedSync.cupVolumeMl(new CupSize(70, 50, 78, 0));
        double half = SeedSync.cupVolumeMl(new CupSize(70, 50, 78, 39));

        assertThat(half).isLessThan(full / 2);
    }
}
