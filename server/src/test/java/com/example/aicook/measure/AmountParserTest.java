package com.example.aicook.measure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AmountParserTest {

    private final AmountParser parser = new AmountParser(List.of("약간", "적당량", "조금"));

    @Test
    void readsWholeNumberWithUnit() {
        ParsedAmount amount = parser.parse("1큰술");

        assertThat(amount.vague()).isFalse();
        assertThat(amount.primary().quantity()).isEqualTo(1.0);
        assertThat(amount.primary().unit()).isEqualTo("큰술");
        assertThat(amount.alternative()).isNull();
    }

    @Test
    void readsFractionsAndMixedNumbers() {
        assertThat(parser.parse("1/3작은술").primary().quantity()).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(parser.parse("1 1/2컵").primary().quantity()).isEqualTo(1.5);
        assertThat(parser.parse("1½큰술").primary().quantity()).isEqualTo(1.5);
        assertThat(parser.parse("0.5컵").primary().quantity()).isEqualTo(0.5);
    }

    @Test
    void readsParenthesisedAlternativeAndKeepsRawText() {
        ParsedAmount amount = parser.parse("2g(1/3작은술)");

        assertThat(amount.rawText()).isEqualTo("2g(1/3작은술)");
        assertThat(amount.primary().unit()).isEqualTo("g");
        assertThat(amount.primary().quantity()).isEqualTo(2.0);
        assertThat(amount.alternative().unit()).isEqualTo("작은술");
        assertThat(amount.alternative().quantity()).isCloseTo(1.0 / 3, within(1e-9));
    }

    @Test
    void vagueAmountGetsNoNumber() {
        ParsedAmount amount = parser.parse("약간");

        assertThat(amount.vague()).isTrue();
        assertThat(amount.primary()).isNull();
    }

    @Test
    void unreadableAmountIsNotGuessed() {
        ParsedAmount range = parser.parse("1~2큰술");
        ParsedAmount words = parser.parse("한 줌");

        assertThat(range.primary()).isNull();
        assertThat(range.vague()).isFalse();
        assertThat(words.primary()).isNull();
    }
}
