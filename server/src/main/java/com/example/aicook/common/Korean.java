package com.example.aicook.common;

/** Picks the Korean particle that fits the last syllable of a word. */
public final class Korean {

    private static final int HANGUL_START = 0xAC00;
    private static final int HANGUL_END = 0xD7A3;
    private static final int FINAL_CONSONANTS = 28;
    private static final int RIEUL = 8;

    private Korean() {
    }

    /** "간장" + 은/는 -> "간장은", "대파" + 은/는 -> "대파는". */
    public static String topic(String word) {
        return word + (finalConsonant(word) == 0 ? "는" : "은");
    }

    /** "간장" + 을/를 -> "간장을". */
    public static String object(String word) {
        return word + (finalConsonant(word) == 0 ? "를" : "을");
    }

    /** "밥숟가락" + (으)로 -> "밥숟가락으로", "칼" -> "칼로", "가위" -> "가위로". */
    public static String with(String word) {
        int consonant = finalConsonant(word);
        return word + (consonant == 0 || consonant == RIEUL ? "로" : "으로");
    }

    /** Index of the final consonant of the last syllable; 0 when there is none or it is not Hangul. */
    private static int finalConsonant(String word) {
        if (word == null || word.isEmpty()) {
            return 0;
        }
        char last = word.charAt(word.length() - 1);
        if (last < HANGUL_START || last > HANGUL_END) {
            return 0;
        }
        return (last - HANGUL_START) % FINAL_CONSONANTS;
    }
}
