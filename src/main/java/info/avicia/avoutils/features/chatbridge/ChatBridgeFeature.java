package info.avicia.avoutils.features.chatbridge;

import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import info.avicia.avoutils.core.util.PacketTextNormalizer;
import info.avicia.avoutils.core.util.WynnPillUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat bridge feature that relays messages between in-game guild chat and Discord.
 */
public class ChatBridgeFeature implements AvoFeature {
    private static final int GUILD_CHAT_COLOR = 0x55FFFF;
    private static final Pattern CHAT_PATTERN = Pattern.compile(
            "^(?:<\\d+>\\s*)?([a-zA-Z0-9_][a-zA-Z0-9_ ]*[a-zA-Z0-9_]|[a-zA-Z0-9_]{3,16})\\s*:\\s*(.*)$",
            Pattern.DOTALL);
    private static final Pattern EMOJI_SHORTCODE_PATTERN = Pattern.compile(":[a-zA-Z0-9_+\\-]+:");

    private ModConfig config;

    private static final String AVATAR_URL_BASE = "https://mc-heads.net/avatar/";
    private static final String BANK_CHEST_AVATAR_URL = "https://wynncraft.wiki.gg/images/UnidentifiedMythicBox.png";
    private static final String AVO_ICON_URL = "https://raw.githubusercontent.com/AviciaGuild/AvoUtils/refs/heads/main/src/main/resources/assets/avoutils/icon.png";

    private static final long CHAT_DEDUPE_MS = 250;
    private static final long EVENT_DEDUPE_MS = 5_000;
    private final Deduplicator chatDeduper = new Deduplicator(CHAT_DEDUPE_MS);
    private final Deduplicator raidDeduper = new Deduplicator(EVENT_DEDUPE_MS);

    private static final String EVT_DISCORD_CHAT = "discord_chat";
    private static final String EVT_GUILD_CHAT = "guild_chat";
    private static final String EVT_GUILD_BANK = "guild_bank_event";
    private static final String EVT_GUILD_RAID = "guild_raid_completion";
    private static final String EVT_GUILD_WAR = "guild_war_result";
    private static final String EVT_BRIDGE_STATUS = "bridge_status";


    public boolean isGuildMember() {
        Boolean cached = AvoAuthService.getInstance().getCachedGuildMember();
        return cached != null && cached;
    }

    public boolean isEnabled() {
        return config != null && config.chatBridgeEnabled;
    }

    private boolean isBridgeActive() {
        return isEnabled() && isGuildMember();
    }

    @Override
    public void initialize(ModConfig config) {
        this.config = config;

        if (!isGuildMember()) {
            AvoUtilsMod.LOGGER.info("[ChatBridge] User is not a guild member. Chat bridge disabled.");
        }

        // Register listener for Discord chat events
        AvoWebSocketManager.getInstance().registerListener(EVT_DISCORD_CHAT, json -> {
            if (!isBridgeActive()) return;
            if (json.has("username") && json.has("message")) {
                String username = json.get("username").getAsString();
                String message = json.get("message").getAsString();
                if (MinecraftClient.getInstance().player != null) {
                    MutableText prefix = WynnPillUtil.createPrefixedPill("AvoBridge", false);
                    MutableText formatted = prefix
                            .append(Text.literal(username).formatted(Formatting.DARK_AQUA))
                            .append(Text.literal(": ").formatted(Formatting.DARK_AQUA))
                            .append(Text.literal(message).formatted(Formatting.AQUA));
                    MinecraftClient.getInstance().player.sendMessage(formatted, false);
                }
            }
        });

        // Register listener for bridge_status events (backend pushes guild membership changes)
        AvoWebSocketManager.getInstance().registerListener(EVT_BRIDGE_STATUS, json -> {
            if (json.has("guild_member")) {
                boolean guildMember = json.get("guild_member").getAsBoolean();
                AvoAuthService.getInstance().setCachedGuildMember(guildMember);
                AvoUtilsMod.LOGGER.info("[ChatBridge] Guild membership updated via bridge_status: {}", guildMember);
            }
        });

        // Register connection demand lease (only if user has it enabled and is a guild member)
        AvoWebSocketManager.getInstance().registerConnectionDemand("chatbridge", this::isBridgeActive);

        AvoUtilsMod.LOGGER.info("[ChatBridge] Initialized.");
    }

    /** Called by WarDetector when a war completes via Wynntils API. */
    void sendWarResult(String outcome, String message) {
        sendEvent(EVT_GUILD_WAR, "War Result", message, AVO_ICON_URL);
    }

    /** Called when a system message is received. */
    public void onSystemChat(Text message) {
        if (!AvoWebSocketManager.getInstance().isConnected()) return;
        if (!isBridgeActive()) return;

        WarDetector.tick();

        String cleaned = PacketTextNormalizer.normalizeForParsing(message.getString());

        String raidMsg = RaidDetector.tryDetect(cleaned, message);
        if (raidMsg != null && !raidDeduper.isDuplicate(raidMsg)) {
            sendEvent(EVT_GUILD_RAID, "Raid Complete", raidMsg, AVO_ICON_URL);
            return;
        }

        if (!hasLeadingGuildChatColor(message)) return;

        Matcher matcher = CHAT_PATTERN.matcher(cleaned);
        if (!matcher.find()) {
            BankDetector.Result bankResult = BankDetector.tryDetect(cleaned, message);
            if (bankResult != null) {
                sendEvent(EVT_GUILD_BANK, bankResult.displayName(), bankResult.formattedMessage(), BANK_CHEST_AVATAR_URL);
            }
            return;
        }

        String displayedName = matcher.group(1).trim();
        String content = matcher.group(2).trim();
        if (content.isEmpty()) return;

        String realUsername = UsernameResolver.resolve(message, displayedName);
        if (realUsername == null) {
            AvoUtilsMod.LOGGER.warn("[ChatBridge] Could not resolve username from '{}'", displayedName);
            return;
        }

        String dedupContent = EMOJI_SHORTCODE_PATTERN.matcher(content)
                .replaceAll("").replaceAll("\\s+", " ").trim();
        String dedupKey = realUsername + "\u0000" + dedupContent;
        if (!chatDeduper.isDuplicate(dedupKey)) {
            sendEvent(EVT_GUILD_CHAT, realUsername, content, AVATAR_URL_BASE + realUsername + "/128");
        }
    }

    private static boolean hasLeadingGuildChatColor(Text message) {
        if (message == null) return false;
        TextColor rootColor = message.getStyle().getColor();
        if (rootColor != null) return rootColor.getRgb() == GUILD_CHAT_COLOR;
        Optional<Boolean> leadingColorIsGuild = message.visit((style, text) -> {
            if (text == null || text.isBlank()) return Optional.empty();
            TextColor color = style.getColor();
            return Optional.of(color != null && color.getRgb() == GUILD_CHAT_COLOR);
        }, Style.EMPTY);
        return leadingColorIsGuild.orElse(false);
    }

    private void sendEvent(String eventType, String username, String message, String avatarUrl) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("message", message);
        payload.addProperty("avatar_url", avatarUrl);
        AvoWebSocketManager.getInstance().sendEvent(eventType, payload);
    }

    public void toggleBridge() {
        if (!config.chatBridgeEnabled && !isGuildMember()) {
            MutableText blocked = WynnPillUtil.createPrefixedPill("AvoBridge", true)
                    .append(Text.literal("Chat bridge is unavailable: you are not in Avicia.")
                            .formatted(Formatting.RED));
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(blocked, false);
            }
            return;
        }

        config.chatBridgeEnabled = !config.chatBridgeEnabled;
        config.save();
        Formatting statusColor = config.chatBridgeEnabled ? Formatting.GREEN : Formatting.RED;
        String statusWord = config.chatBridgeEnabled ? "enabled" : "disabled";
        MutableText formatted = WynnPillUtil.createPrefixedPill("AvoBridge", false)
                .append(Text.literal("Chat bridge is now ").formatted(Formatting.GRAY))
                .append(Text.literal(statusWord).formatted(statusColor))
                .append(Text.literal(".").formatted(Formatting.GRAY));
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(formatted, false);
        }
    }
}
