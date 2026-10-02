package info.avicia.avoutils.features.chatbridge;

/**
 * Escapes Discord markdown.
 */
public final class DiscordMarkdown {
    private DiscordMarkdown() {
    }

    public static String escapeUsername(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("_", "\\_");
    }
}
