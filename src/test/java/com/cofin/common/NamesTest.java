package com.cofin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NamesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "WiFi        | wifi",
            "'  #WiFi '  | wifi",
            "##安靜      | 安靜",
            "'有  插座'  | 有 插座",
    })
    void tagIsNormalized(String raw, String expected) {
        assertThat(Names.tag(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "#", " ## "})
    void blankTagIsRejected(String raw) {
        assertThatThrownBy(() -> Names.tag(raw))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("不能是空的");
    }

    @Test
    void tagLengthCountsCharactersNotBytes() {
        assertThat(Names.tag("咖".repeat(20))).hasSize(20);
        assertThatThrownBy(() -> Names.tag("咖".repeat(21)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("最多 20 個字");
    }

    @Test
    void emojiCountsAsOneCharacter() {
        // Each emoji is 2 UTF-16 units; 20 of them must still fit in a 20-character tag.
        String twentyEmoji = "☕".repeat(10) + "🍰".repeat(10);
        assertThat(Names.tag(twentyEmoji)).isEqualTo(twentyEmoji);
    }

    @Test
    void optionalTurnsBlankIntoNull() {
        assertThat(Names.optional("   ", 10, "備註")).isNull();
        assertThat(Names.optional(null, 10, "備註")).isNull();
        assertThat(Names.optional(" hi ", 10, "備註")).isEqualTo("hi");
    }

    @Test
    void escapeLikeNeutralizesWildcards() {
        assertThat(Names.escapeLike("100%_\\")).isEqualTo("100\\%\\_\\\\");
    }
}
