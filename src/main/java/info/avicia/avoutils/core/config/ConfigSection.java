package info.avicia.avoutils.core.config;

/**
 * The config screen's cards, top to bottom.
 */
public enum ConfigSection {
    CHAT_BRIDGE("Chat Bridge"),
    EMOJIS("Emojis"),
    PARTY_FINDER("Party Finder"),
    GUILD_STORAGE("Guild Rewards Storage"),
    UPDATES("Updates");

    private final String title;

    ConfigSection(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}

