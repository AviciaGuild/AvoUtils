package info.avicia.avoutils.core.util;

import java.util.regex.Pattern;

/**
 * Normalizes raw packet-level Wynncraft chat text into a parsing-friendly form.
 * Strips colors, formatting, and private-use character codes.
 */
public final class PacketTextNormalizer {
    private static final Pattern LEGACY_FORMATTING_PATTERN = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern SECTION_ANY_PATTERN = Pattern.compile("§.");
    private static final Pattern AMPERSAND_FORMATTING_PATTERN = Pattern.compile("(?i)&[0-9A-FK-OR]");
    private static final Pattern AMPERSAND_HEX_COLOR_PATTERN = Pattern.compile("(?i)&#[0-9a-f]{6,8}");
    private static final Pattern AMPERSAND_TAG_PATTERN = Pattern.compile("&[<\\[{][^>\\]}]+[>\\]}]");
    private static final Pattern AMPERSAND_FONT_TAG_PATTERN = Pattern.compile("&\\{[^}]+\\}");
    private static final Pattern SPACE_BEFORE_PUNCTUATION_PATTERN = Pattern.compile("\\s+([,.;!?])");
    private static final Pattern SPACE_AFTER_OPENING_DELIMITER_PATTERN = Pattern.compile("([\\[(])\\s+");
    private static final Pattern SPACE_BEFORE_CLOSING_DELIMITER_PATTERN = Pattern.compile("\\s+([\\])])");
    private static final Pattern COMMA_SPACING_PATTERN = Pattern.compile("\\s*,\\s*");
    private static final Pattern SLASH_SPACING_PATTERN = Pattern.compile("(?<=\\d)\\s*/\\s*(?=\\d)");
    private static final Pattern MULTISPACE_PATTERN = Pattern.compile(" {2,}");

    private PacketTextNormalizer() {
    }

    public static String normalizeForParsing(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String strippedFormatting = LEGACY_FORMATTING_PATTERN.matcher(rawText).replaceAll(" ").replace('§', ' ');
        strippedFormatting = AMPERSAND_HEX_COLOR_PATTERN.matcher(strippedFormatting).replaceAll(" ");
        strippedFormatting = AMPERSAND_TAG_PATTERN.matcher(strippedFormatting).replaceAll(" ");
        strippedFormatting = AMPERSAND_FONT_TAG_PATTERN.matcher(strippedFormatting).replaceAll(" ");
        strippedFormatting = AMPERSAND_FORMATTING_PATTERN.matcher(strippedFormatting).replaceAll(" ");
        StringBuilder normalized = new StringBuilder(strippedFormatting.length());
        boolean previousWasSpace = false;

        for (int index = 0; index < strippedFormatting.length();) {
            int codePoint = strippedFormatting.codePointAt(index);
            index += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint) || isIgnorableForParsing(codePoint)) {
                if (!previousWasSpace) {
                    normalized.append(' ');
                    previousWasSpace = true;
                }
                continue;
            }

            normalized.appendCodePoint(codePoint);
            previousWasSpace = false;
        }

        String cleaned = normalized.toString().trim();
        cleaned = SPACE_BEFORE_PUNCTUATION_PATTERN.matcher(cleaned).replaceAll("$1");
        cleaned = SPACE_AFTER_OPENING_DELIMITER_PATTERN.matcher(cleaned).replaceAll("$1");
        cleaned = SPACE_BEFORE_CLOSING_DELIMITER_PATTERN.matcher(cleaned).replaceAll("$1");
        cleaned = COMMA_SPACING_PATTERN.matcher(cleaned).replaceAll(", ");
        cleaned = SLASH_SPACING_PATTERN.matcher(cleaned).replaceAll("/");
        cleaned = MULTISPACE_PATTERN.matcher(cleaned).replaceAll(" ");
        return cleaned.trim();
    }

    /**
     * Strips legacy section-sign codes (§ + one character, including private-use glyphs) used by
     * Wynncraft chat. Kept separate from {@link #normalizeForParsing(String)} because party-chat
     * parsing only needs code stripping, not full punctuation normalization.
     */
    public static String stripColorCodes(String rawText) {
        if (rawText == null) {
            return "";
        }
        return SECTION_ANY_PATTERN.matcher(rawText).replaceAll("").trim();
    }

    private static boolean isIgnorableForParsing(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.PRIVATE_USE,
                    Character.SURROGATE,
                    Character.UNASSIGNED -> true;
            default -> false;
        };
    }
}
