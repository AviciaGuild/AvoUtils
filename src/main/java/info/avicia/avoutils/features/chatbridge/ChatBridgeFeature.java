package info.avicia.avoutils.features.chatbridge;

import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
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

    @Override
    public void initialize(ModConfig config) {
        this.config = config;

        // Register listener for Discord chat events
        AvoWebSocketManager.getInstance().registerListener("discord_chat", json -> {
            if (!config.chatBridgeEnabled) {
                return;
            }
            if (json.has("username") && json.has("message")) {
                String username = json.get("username").getAsString();
                String message = json.get("message").getAsString();
                
                if (MinecraftClient.getInstance().player != null) {
                    MutableText prefix = Text.literal("[AvoBridge] ").formatted(Formatting.LIGHT_PURPLE);
                    MutableText formatted = prefix
                            .append(Text.literal(username).formatted(Formatting.WHITE))
                            .append(Text.literal(": ").formatted(Formatting.GRAY))
                            .append(Text.literal(message).formatted(Formatting.WHITE));
                    MinecraftClient.getInstance().player.sendMessage(formatted, false);
                }
            }
        });

        // Register connection demand lease
        AvoWebSocketManager.getInstance().registerConnectionDemand("chatbridge", () -> config.chatBridgeEnabled);

        registerCommand();
        AvoUtilsMod.LOGGER.info("[ChatBridge] Initialized.");
    }

    public void onSystemChat(Text message) {
        if (!config.chatBridgeEnabled) {
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

        if (isDuplicateOutgoing(realUsername, content)) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("username", realUsername);
        payload.addProperty("message", content);
        
        String avatarUrl = "https://mc-heads.net/avatar/" + realUsername + "/128";
        payload.addProperty("avatar_url", avatarUrl);
        
        AvoWebSocketManager.getInstance().sendEvent("guild_chat", payload);
    }

    public static boolean hasLeadingGuildChatColor(Text message) {
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
                                config.chatBridgeEnabled = !config.chatBridgeEnabled;
                                config.save();
                                String status = config.chatBridgeEnabled ? "§aenabled" : "§cdisabled";
                                MinecraftClient.getInstance().player.sendMessage(
                                        Text.literal("§d[AvoBridge] §7Chat bridge is now " + status + "§7."),
                                        false
                                    );
                                if (!config.chatBridgeEnabled) {
                                    AvoWebSocketManager.getInstance().disconnect();
                                }
                                return 1;
                            })
            );
        });
    }
}
