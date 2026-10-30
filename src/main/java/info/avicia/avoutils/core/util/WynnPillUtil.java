package info.avicia.avoutils.core.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Utility for drawing styled Wynncraft resource-pack compatible pills
 */
public final class WynnPillUtil {

    private static final String PILL_CORNER_LEFT = "\uE010\u2064";
    private static final String PILL_CORNER_RIGHT = "\uE011\u2064";
    private static final String PILL_BG_BACK = "\uE00F";
    private static final String PILL_BG_FRONT = "\uE012";

    private static final ChatFormatting PILL_FG = ChatFormatting.BLACK;
    private static final ChatFormatting ARROW_COLOR = ChatFormatting.GRAY;

    private static volatile Boolean usePillOverride = null;

    private WynnPillUtil() {
    }

    public static boolean canUsePill() {
        if (usePillOverride != null) {
            return usePillOverride;
        }
        return WynncraftServerPolicy.isOnWynncraft();
    }

    public static void setUsePillOverride(Boolean override) {
        usePillOverride = override;
    }

    public static MutableComponent create(String label, ChatFormatting backgroundColor, ChatFormatting foregroundColor) {
        if (!canUsePill()) {
            return createFallback(label, backgroundColor, foregroundColor);
        }
        MutableComponent pill = Component.empty();
        pill.append(Component.literal(PILL_CORNER_LEFT)
                .setStyle(Style.EMPTY.withColor(backgroundColor).withoutShadow()));

        for (int i = 0; i < label.length(); i++) {
            String glyph = toWynncraftGlyph(label.charAt(i));
            pill.append(Component.literal(PILL_BG_BACK)
                    .setStyle(Style.EMPTY.withColor(backgroundColor).withoutShadow()));
            pill.append(Component.literal(PILL_BG_FRONT + glyph)
                    .setStyle(Style.EMPTY.withColor(foregroundColor).withoutShadow()));
        }

        pill.append(Component.literal(PILL_CORNER_RIGHT)
                .setStyle(Style.EMPTY.withColor(backgroundColor).withoutShadow()));
        return pill;
    }

    public static MutableComponent createFallback(String label, ChatFormatting backgroundColor, ChatFormatting foregroundColor) {
        return Component.empty().append(Component.literal("[" + label + "]").withStyle(backgroundColor, ChatFormatting.BOLD));
    }

    public static MutableComponent createPrefixedPill(String label, boolean isError) {
        ChatFormatting bg = isError ? ChatFormatting.RED : ChatFormatting.AQUA;
        return create(label, bg, PILL_FG).append(Component.literal(" \u203A\u203A ").withStyle(ARROW_COLOR));
    }

    /**
     * Sends a toggle feedback message: "[pill] messagePrefix enabled/disabled."
     *
     * @param messagePrefix e.g. "Chat bridge is now " or "Storage threshold notifications are now "
     */
    public static void sendToggleFeedback(String pillLabel, String messagePrefix, boolean enabled) {
        ChatFormatting statusColor = enabled ? ChatFormatting.GREEN : ChatFormatting.RED;
        String statusWord = enabled ? "enabled" : "disabled";
        MutableComponent msg = createPrefixedPill(pillLabel, false)
                .append(Component.literal(messagePrefix).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(statusWord).withStyle(statusColor))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY));
        sendToPlayer(msg);
    }

    /**
     * Sends a not-a-member feedback message with the given error text.
     */
    public static void sendNotMemberFeedback(String pillLabel, String message) {
        MutableComponent msg = createPrefixedPill(pillLabel, true)
                .append(Component.literal(message).withStyle(ChatFormatting.RED));
        sendToPlayer(msg);
    }

    public static void sendToPlayer(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.displayClientMessage(message, false);
                }
            });
        }
    }

    public static MutableComponent createClickable(String label, ChatFormatting backgroundColor, ChatFormatting foregroundColor, String command, String hoverText) {
        MutableComponent pill = create(label, backgroundColor, foregroundColor);
        ClickEvent clickEvent = new ClickEvent.RunCommand(command);
        HoverEvent hoverEvent = hoverText != null ? new HoverEvent.ShowText(Component.literal(hoverText)) : null;
        pill.withStyle(style -> style.withClickEvent(clickEvent).withHoverEvent(hoverEvent));
        for (Component sibling : pill.getSiblings()) {
            if (sibling instanceof MutableComponent mutableSibling) {
                mutableSibling.withStyle(style -> style.withClickEvent(clickEvent).withHoverEvent(hoverEvent));
            }
        }
        return pill;
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
