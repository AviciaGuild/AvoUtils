package info.avicia.avoutils.core.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Utility for drawing styled Wynncraft resource-pack compatible pills
 */
public final class WynnPillUtil {

    private static final String PILL_CORNER_LEFT = "\uE010\u2064";
    private static final String PILL_CORNER_RIGHT = "\uE011\u2064";
    private static final String PILL_BG_BACK = "\uE00F";
    private static final String PILL_BG_FRONT = "\uE012";

    private static final Formatting PILL_FG = Formatting.BLACK;
    private static final Formatting ARROW_COLOR = Formatting.GRAY;

    private WynnPillUtil() {
    }

    public static MutableText create(String label, Formatting backgroundColor, Formatting foregroundColor) {
        MutableText pill = Text.empty();
        pill.append(Text.literal(PILL_CORNER_LEFT)
                .setStyle(Style.EMPTY.withColor(backgroundColor).withoutShadow()));

        for (int i = 0; i < label.length(); i++) {
            String glyph = toWynncraftGlyph(label.charAt(i));
            pill.append(Text.literal(PILL_BG_BACK)
                    .setStyle(Style.EMPTY.withColor(backgroundColor).withoutShadow()));
            pill.append(Text.literal(PILL_BG_FRONT + glyph)
                    .setStyle(Style.EMPTY.withColor(foregroundColor).withoutShadow()));
        }

        pill.append(Text.literal(PILL_CORNER_RIGHT)
                .setStyle(Style.EMPTY.withColor(backgroundColor).withoutShadow()));
        return pill;
    }

    public static MutableText createPrefixedPill(String label, boolean isError) {
        Formatting bg = isError ? Formatting.RED : Formatting.AQUA;
        return create(label, bg, PILL_FG).append(Text.literal(" \u203A\u203A ").formatted(ARROW_COLOR));
    }

    private static String toWynncraftGlyph(char rawChar) {
        char ch = Character.toLowerCase(rawChar);
        return switch (ch) {
            case 'a' -> "\uE040";
            case 'b' -> "\uE041";
            case 'c' -> "\uE042";
            case 'd' -> "\uE043";
            case 'e' -> "\uE044";
            case 'f' -> "\uE045";
            case 'g' -> "\uE046";
            case 'h' -> "\uE047";
            case 'i' -> "\uE048";
            case 'j' -> "\uE049";
            case 'k' -> "\uE04A";
            case 'l' -> "\uE04B";
            case 'm' -> "\uE04C";
            case 'n' -> "\uE04D";
            case 'o' -> "\uE04E";
            case 'p' -> "\uE04F";
            case 'q' -> "\uE050";
            case 'r' -> "\uE051";
            case 's' -> "\uE052";
            case 't' -> "\uE053";
            case 'u' -> "\uE054";
            case 'v' -> "\uE055";
            case 'w' -> "\uE056";
            case 'x' -> "\uE057";
            case 'y' -> "\uE058";
            case 'z' -> "\uE059";
            case '0' -> "\uE060";
            case '1' -> "\uE061";
            case '2' -> "\uE062";
            case '3' -> "\uE063";
            case '4' -> "\uE064";
            case '5' -> "\uE065";
            case '6' -> "\uE066";
            case '7' -> "\uE067";
            case '8' -> "\uE068";
            case '9' -> "\uE069";
            case ' ' -> " ";
            default -> String.valueOf(rawChar);
        };
    }
}
