package info.avicia.avoutils.features.chatbridge;

/**
 * Escapes Discord markdown.
 */
final class DiscordMarkdown {
    private DiscordMarkdown() {
    }

    static String escapeUsername(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("_", "\\_");
    }
}
