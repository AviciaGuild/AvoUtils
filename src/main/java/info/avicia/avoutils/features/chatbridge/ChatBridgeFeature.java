package info.avicia.avoutils.features.chatbridge;

import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import info.avicia.avoutils.core.util.WynnPillUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;

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
    private static final Pattern HOVER_REAL_NAME_PATTERN = Pattern.compile(
            "(?:'(?:s)? real name is\\s+|Real Username:\\s*)([a-zA-Z0-9_]{3,16})",
            Pattern.CASE_INSENSITIVE);

    private ModConfig config;

    // Deduplication state for outgoing messages
    private String lastOutgoingKey = null;
    private long lastOutgoingTime = 0;
    private static final long DEDUPE_WINDOW_MS = 750;
    private static final String AVATAR_URL_BASE = "https://mc-heads.net/avatar/";

    // Event type constants
    private static final String EVT_DISCORD_CHAT = "discord_chat";
    private static final String EVT_GUILD_CHAT = "guild_chat";
    private static final String EVT_BRIDGE_STATUS = "bridge_status";

    private static final Formatting PILL_BG = Formatting.AQUA;
    private static final Formatting PILL_FG = Formatting.BLACK;
    private static final Formatting PILL_ERR_BG = Formatting.RED;
    private static final Formatting ARROW_COLOR = Formatting.GRAY;

    private static MutableText createChatPrefix() {
        return WynnPillUtil.create("AvoBridge", PILL_BG, PILL_FG)
                .append(Text.literal(" \u203A\u203A ").formatted(ARROW_COLOR));
    }

    private static MutableText createErrorPrefix() {
        return WynnPillUtil.create("AvoBridge", PILL_ERR_BG, PILL_FG)
                .append(Text.literal(" \u203A\u203A ").formatted(ARROW_COLOR));
    }

    private boolean isGuildMember() {
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
                    MutableText prefix = createChatPrefix();
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

        registerCommand();
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
            return;
        }

        String displayedName = matcher.group(1).trim();
        String content = matcher.group(2).trim();
        if (content.isEmpty()) {
            return;
        }

        String realUsername = findRealUsername(message);
        if (realUsername == null) {
            String cleanedDisplay = displayedName.replaceAll("[^a-zA-Z0-9_]", "");
            if (isValidUsername(cleanedDisplay)) {
                realUsername = cleanedDisplay;
            } else {
                return;
            }
        }

        if (!isValidUsername(realUsername)) {
            AvoUtilsMod.LOGGER.warn("[ChatBridge] Dropping guild chat: username '{}' does not match Minecraft pattern", realUsername);
            return;
        }

        if (isDuplicateOutgoing(realUsername, content)) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("username", realUsername);
        payload.addProperty("message", content);

        String avatarUrl = AVATAR_URL_BASE + realUsername + "/128";
        payload.addProperty("avatar_url", avatarUrl);

        AvoWebSocketManager.getInstance().sendEvent(EVT_GUILD_CHAT, payload);
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

    private synchronized boolean isDuplicateOutgoing(String username, String message) {
        String key = username + "\u0000" + message;
        long now = System.currentTimeMillis();
        if (key.equals(lastOutgoingKey) && (now - lastOutgoingTime) < DEDUPE_WINDOW_MS) {
            return true;
        }
        lastOutgoingKey = key;
        lastOutgoingTime = now;
        return false;
    }

    private void registerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("avobridge")
                            .executes(context -> {
                                // Block enabling the bridge if user is not a guild member
                                if (!config.chatBridgeEnabled && !isGuildMember()) {
                                    MutableText blocked = createErrorPrefix()
                                            .append(Text.literal("Chat bridge is unavailable: you are not in Avicia.").formatted(Formatting.RED));
                                    MinecraftClient.getInstance().player.sendMessage(blocked, false);
                                    return 1;
                                }

                                // Toggle the chat bridge enabled state and save the config
                                config.chatBridgeEnabled = !config.chatBridgeEnabled;
                                config.save();
                                Formatting statusColor = config.chatBridgeEnabled ? Formatting.GREEN : Formatting.RED;
                                String statusWord = config.chatBridgeEnabled ? "enabled" : "disabled";
                                MutableText formatted = createChatPrefix()
                                        .append(Text.literal("Chat bridge is now ").formatted(Formatting.GRAY))
                                        .append(Text.literal(statusWord).formatted(statusColor))
                                        .append(Text.literal(".").formatted(Formatting.GRAY));
                                MinecraftClient.getInstance().player.sendMessage(formatted, false);
                                return 1;
                            })
            );
        });
    }
}
