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
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Formatting;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat bridge feature that relays messages between in-game guild chat and Discord
 */
public class ChatBridgeFeature implements AvoFeature {
    private static final int GUILD_CHAT_COLOR = 0x55FFFF;
    private static final Pattern CHAT_PATTERN = Pattern.compile(
            "^(?:<\\d+>\\s*)?([a-zA-Z0-9_][a-zA-Z0-9_ ]*[a-zA-Z0-9_]|[a-zA-Z0-9_]{3,16})\\s*:\\s*(.*)$",
            Pattern.DOTALL);
    private static final Pattern BANK_PATTERN = Pattern.compile(
            "^(.+?)\\s+(deposited|withdrew)\\s+(.+?)\\s+(to|from)\\s+the Guild Bank\\s+\\((.+)\\)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK_NO_TIER_PATTERN = Pattern.compile(
            "^(.+?)\\s+(deposited|withdrew)\\s+(.+?)\\s+(to|from)\\s+the Guild(?: Bank)?$",
            Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_ACCESS_TIER = "Unknown";
    private static final Pattern HOVER_REAL_NAME_PATTERN = Pattern.compile(
            "(?:'(?:s)? real name is\\s+|Real Username:\\s*)([a-zA-Z0-9_]{3,16})",
            Pattern.CASE_INSENSITIVE);

    private ModConfig config;

    private static final String AVATAR_URL_BASE = "https://mc-heads.net/avatar/";
    private static final String BANK_CHEST_AVATAR_URL = "https://wynncraft.wiki.gg/images/UnidentifiedMythicBox.png";

    private static final long DEDUPE_WINDOW_MS = 750;
    private final Deduplicator chatDeduper = new Deduplicator(DEDUPE_WINDOW_MS);
    private final Deduplicator bankLogDeduper = new Deduplicator(DEDUPE_WINDOW_MS);

    private static final String EVT_DISCORD_CHAT = "discord_chat";
    private static final String EVT_GUILD_CHAT = "guild_chat";
    private static final String EVT_GUILD_BANK = "guild_bank_event";
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
            if (!isBridgeActive()) {
                return;
            }
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

    public void onSystemChat(Text message) {
        if (!isBridgeActive()) {
            return;
        }
        if (!AvoWebSocketManager.getInstance().isConnected()) {
            return;
        }
        if (!hasLeadingGuildChatColor(message)) {
            return;
        }

        String raw = message.getString();
        String cleaned = PacketTextNormalizer.normalizeForParsing(raw);
        Matcher matcher = CHAT_PATTERN.matcher(cleaned);
        if (!matcher.find()) {
            handleBankLog(cleaned, message);
            return;
        }

        String displayedName = matcher.group(1).trim();
        String content = matcher.group(2).trim();
        if (content.isEmpty()) {
            return;
        }

        String realUsername = resolveUsername(message, displayedName);
        if (realUsername == null) {
            return;
        }

        sendBridgeMessage(chatDeduper, EVT_GUILD_CHAT, realUsername, content, AVATAR_URL_BASE + realUsername + "/128");
    }

    private static boolean hasLeadingGuildChatColor(Text message) {
        if (message == null) {
            return false;
        }

        TextColor rootColor = message.getStyle().getColor();
        if (rootColor != null) {
            return rootColor.getRgb() == GUILD_CHAT_COLOR;
        }

        Optional<Boolean> leadingColorIsGuild = message.visit((style, text) -> {
            if (text == null || text.isBlank()) {
                return Optional.empty();
            }
            TextColor color = style.getColor();
            return Optional.of(color != null && color.getRgb() == GUILD_CHAT_COLOR);
        }, Style.EMPTY);
        return leadingColorIsGuild.orElse(false);
    }

    private String findRealUsername(Text text) {
        if (text == null) {
            return null;
        }

        String direct = extractDirectUsername(text.getStyle());
        if (direct != null) {
            return direct;
        }

        for (Text sibling : text.getSiblings()) {
            String found = findRealUsername(sibling);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private String extractDirectUsername(Style style) {
        if (style == null) {
            return null;
        }

        String insertion = style.getInsertion();
        if (isValidUsername(insertion)) {
            return insertion;
        }

        HoverEvent hoverEvent = style.getHoverEvent();
        if (hoverEvent instanceof HoverEvent.ShowText showTextEvent) {
            Text hoverComponent = showTextEvent.value();
            if (hoverComponent != null) {
                String hoverText = hoverComponent.getString()
                        .replace('\u2019', '\'')
                        .replace('\u2018', '\'');
                Matcher matcher = HOVER_REAL_NAME_PATTERN.matcher(hoverText);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }

        return null;
    }

    private static boolean isValidUsername(String name) {
        return name != null && name.matches("[a-zA-Z0-9_]{3,16}");
    }

    private String resolveUsername(Text message, String fallbackDisplayName) {
        String realUsername = findRealUsername(message);
        if (realUsername != null) {
            return isValidUsername(realUsername) ? realUsername : null;
        }
        String cleaned = fallbackDisplayName.replaceAll("[^a-zA-Z0-9_]", "");
        if (isValidUsername(cleaned)) {
            return cleaned;
        }
        AvoUtilsMod.LOGGER.warn("[ChatBridge] Could not resolve username from fallback: '{}'", fallbackDisplayName);
        return null;
    }

    private void sendBridgeMessage(Deduplicator deduper, String eventType, String username, String message, String avatarUrl) {
        String key = username + "\u0000" + message;
        if (deduper.isDuplicate(key)) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("message", message);
        payload.addProperty("avatar_url", avatarUrl);
        AvoWebSocketManager.getInstance().sendEvent(eventType, payload);
    }

    private void handleBankLog(String cleaned, Text message) {
        if (!cleaned.contains("Guild") || (!cleaned.contains("deposited") && !cleaned.contains("withdrew"))) {
            return;
        }

        Matcher matcher = BANK_PATTERN.matcher(cleaned);
        String accessTier;
        if (matcher.matches()) {
            accessTier = matcher.group(5).trim();
        } else {
            matcher = BANK_NO_TIER_PATTERN.matcher(cleaned);
            if (!matcher.matches()) {
                return;
            }
            accessTier = DEFAULT_ACCESS_TIER;
        }

        String displayedPlayer = matcher.group(1).trim();
        String action = matcher.group(2).toLowerCase();
        String itemBlock = matcher.group(3).trim();
        if (displayedPlayer.isEmpty() || itemBlock.isEmpty()) {
            return;
        }

        String realUsername = resolveUsername(message, displayedPlayer);
        if (realUsername == null) {
            return;
        }

        String formattedMessage = "**" + realUsername + "** " + action + " **" + itemBlock + "**";

        String displayName = DEFAULT_ACCESS_TIER.equals(accessTier)
                ? "Guild Bank"
                : "Guild Bank (" + accessTier + ")";

        sendBridgeMessage(bankLogDeduper, EVT_GUILD_BANK, displayName, formattedMessage, BANK_CHEST_AVATAR_URL);
    }

    public void toggleBridge() {
        // Block enabling the bridge if user is not a guild member
        if (!config.chatBridgeEnabled && !isGuildMember()) {
            MutableText blocked = WynnPillUtil.createPrefixedPill("AvoBridge", true)
                    .append(Text.literal("Chat bridge is unavailable: you are not in Avicia.").formatted(Formatting.RED));
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(blocked, false);
            }
            return;
        }

        // Toggle the chat bridge enabled state and save the config
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
