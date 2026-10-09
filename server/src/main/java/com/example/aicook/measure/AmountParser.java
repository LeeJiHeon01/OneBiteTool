package com.example.aicook.measure;

import com.example.aicook.measure.ParsedAmount.Amount;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads amount text such as "1큰술", "1/2컵", "2g(1/3작은술)" or "약간". Never guesses a number. */
public class AmountParser {

    private static final Map<Character, Double> UNICODE_FRACTIONS =
            Map.of('½', 0.5, '⅓', 1.0 / 3, '⅔', 2.0 / 3, '¼', 0.25, '¾', 0.75);

    // main amount, optionally followed by one parenthesised amount
    private static final Pattern WITH_ALTERNATIVE = Pattern.compile("^([^()]*)\\(([^()]*)\\)\\s*$");
    private static final String NUMBER = "(\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:\\.\\d+)?[½⅓⅔¼¾]?|[½⅓⅔¼¾])";
    private static final Pattern AMOUNT = Pattern.compile("^\\s*" + NUMBER + "\\s*([^\\d\\s()]+)?\\s*$");
    private static final Pattern MIXED = Pattern.compile("^(\\d+)\\s+(\\d+)/(\\d+)$");
    private static final Pattern FRACTION = Pattern.compile("^(\\d+)/(\\d+)$");

    private final List<String> vagueWords;

    public AmountParser(List<String> vagueWords) {
        this.vagueWords = vagueWords;
    }

    public ParsedAmount parse(String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (isVague(text)) {
            return new ParsedAmount(text, true, null, null);
        }
        Matcher matcher = WITH_ALTERNATIVE.matcher(text);
        if (matcher.matches()) {
            return new ParsedAmount(text, false, parseAmount(matcher.group(1)), parseAmount(matcher.group(2)));
        }
        return new ParsedAmount(text, false, parseAmount(text), null);
    }

    private boolean isVague(String text) {
        boolean hasDigit = text.chars().anyMatch(Character::isDigit);
        return !hasDigit && vagueWords.stream().anyMatch(text::contains);
    }

    private Amount parseAmount(String text) {
        Matcher matcher = AMOUNT.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        Double quantity = parseNumber(matcher.group(1));
        return quantity == null ? null : new Amount(quantity, matcher.group(2));
    }

    private Double parseNumber(String text) {
        Matcher mixed = MIXED.matcher(text);
        if (mixed.matches()) {
            Double fraction = divide(mixed.group(2), mixed.group(3));
            return fraction == null ? null : Integer.parseInt(mixed.group(1)) + fraction;
        }
        Matcher fraction = FRACTION.matcher(text);
        if (fraction.matches()) {
            return divide(fraction.group(1), fraction.group(2));
        }
        char last = text.charAt(text.length() - 1);
        Double unicodeFraction = UNICODE_FRACTIONS.get(last);
        if (unicodeFraction == null) {
            return Double.parseDouble(text);
        }
        String whole = text.substring(0, text.length() - 1);
        return (whole.isEmpty() ? 0 : Double.parseDouble(whole)) + unicodeFraction;
    }

    private Double divide(String numerator, String denominator) {
        int divisor = Integer.parseInt(denominator);
        return divisor == 0 ? null : (double) Integer.parseInt(numerator) / divisor;
    }
}
