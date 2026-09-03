package info.avicia.avoutils.features.emojis;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

/**
 * Encodes and decodes emoji hover metadata for tooltips.
 */
public final class EmojiTooltipHelper {

    public static final String EMOJI_HOVER_PREFIX = "\u00a7e\u00a7m\u00a7o\u00a7j\u00a7i:";

    public record EmojiHoverData(String replacement, String emojiName) {
    }

    private EmojiTooltipHelper() {
    }

    public static HoverEvent createEmojiHover(String replacement, String emojiName) {
        return new HoverEvent.ShowText(Text.literal(EMOJI_HOVER_PREFIX + replacement + ":" + emojiName));
    }

    public static EmojiHoverData parseEmojiHover(HoverEvent hoverEvent) {
        if (hoverEvent instanceof HoverEvent.ShowText showText) {
            String raw = showText.value().getString();
            if (raw.startsWith(EMOJI_HOVER_PREFIX)) {
                String payload = raw.substring(EMOJI_HOVER_PREFIX.length());
                int colonIdx = payload.indexOf(':');
                if (colonIdx > 0 && colonIdx < payload.length() - 1) {
                    String replacement = payload.substring(0, colonIdx);
                    String emojiName = payload.substring(colonIdx + 1);
                    return new EmojiHoverData(replacement, emojiName);
                }
            }
        }
        return null;
    }
}

