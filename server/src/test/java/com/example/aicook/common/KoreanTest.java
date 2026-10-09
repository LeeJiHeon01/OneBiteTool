package com.example.aicook.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanTest {

    @Test
    void picksTheParticleForTheLastSyllable() {
        assertThat(Korean.topic("간장")).isEqualTo("간장은");
        assertThat(Korean.topic("대파")).isEqualTo("대파는");
        assertThat(Korean.object("가위")).isEqualTo("가위를");
        assertThat(Korean.object("칼")).isEqualTo("칼을");
    }

    @Test
    void usesRoAfterRieulOrNoFinalConsonant() {
        assertThat(Korean.with("밥숟가락")).isEqualTo("밥숟가락으로");
        assertThat(Korean.with("작은 종이컵")).isEqualTo("작은 종이컵으로");
        assertThat(Korean.with("칼")).isEqualTo("칼로");
        assertThat(Korean.with("가위")).isEqualTo("가위로");
        assertThat(Korean.with("들기름")).isEqualTo("들기름으로");
    }
}
