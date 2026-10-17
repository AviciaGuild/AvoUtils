package info.avicia.avoutils.features.chatbridge;

import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import info.avicia.avoutils.features.guildstorage.GuildStorageNotifier;
import info.avicia.avoutils.core.util.PacketTextNormalizer;
import info.avicia.avoutils.core.util.UsernameResolver;
import info.avicia.avoutils.core.util.WynnPillUtil;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

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
    private static final String EVT_GUILD_REWARD = "guild_reward";
    private static final String EVT_BRIDGE_STATUS = "bridge_status";

    public boolean isGuildMember() {
        return AvoAuthService.getInstance().isGuildMember();
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
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge] User is not a guild member. Chat bridge disabled.");
        }

        // Register listener for Discord chat events
        AvoWebSocketManager.getInstance().registerListener(EVT_DISCORD_CHAT, json -> {
            if (!isBridgeActive()) return;
            if (json.has("username") && json.has("message")) {
                String username = json.get("username").getAsString();
                String message = json.get("message").getAsString();
                if (Minecraft.getInstance().player != null) {
                    MutableComponent prefix = WynnPillUtil.createPrefixedPill("AvoBridge", false);
                    MutableComponent formatted = prefix
                            .append(Component.literal(username).withStyle(ChatFormatting.DARK_AQUA))
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_AQUA))
                            .append(Component.literal(message).withStyle(ChatFormatting.AQUA));
                    Minecraft.getInstance().player.displayClientMessage(formatted, false);
                }
            }
        });

        // Register listener for bridge_status events (backend pushes guild membership changes)
        AvoWebSocketManager.getInstance().registerListener(EVT_BRIDGE_STATUS, json -> {
            if (json.has("guild_member")) {
                boolean guildMember = json.get("guild_member").getAsBoolean();
                AvoAuthService.getInstance().setCachedGuildMember(guildMember);
                AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge] Guild membership updated via bridge_status: {}", guildMember);
            }
        });

        // Register connection demand lease (only if user has it enabled and is a guild member)
        AvoWebSocketManager.getInstance().registerConnectionDemand("chatbridge", this::isBridgeActive);

        AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge] Initialized.");
    }

    /** Called when a system message is received. */
    public void onSystemChat(Component message) {
        if (message == null) return;
        if (!AvoWebSocketManager.getInstance().isConnected()) return;
        if (!isBridgeActive()) return;

        String cleaned = PacketTextNormalizer.normalizeForParsing(message.getString());
        AvoUtilsMod mod = AvoUtilsMod.getInstance();
        GuildStorageNotifier storage = mod != null ? mod.getFeature(GuildStorageNotifier.class) : null;

        if (!hasLeadingGuildChatColor(message)) return;

        // ── Raid completions ──────────────────────────────────────────
        RaidDetector.RaidResult raidResult = RaidDetector.tryDetect(cleaned, message);
        if (raidResult != null && !raidDeduper.isDuplicate(raidResult.formattedMessage())) {
            sendEvent(EVT_GUILD_RAID, "Raid Complete", raidResult.formattedMessage(), AVO_ICON_URL);
            if (storage != null) {
                storage.onRaidDelta(raidResult.emeralds(), raidResult.aspects());
            }
        }

        // ── Reward grants ─────────────────────────────────────────────
        RewardDetector.RewardResult rewardResult = RewardDetector.tryDetect(cleaned, message);
        if (rewardResult != null) {
            sendEvent(EVT_GUILD_REWARD, "Guild Rewards", rewardResult.formattedMessage(), AVO_ICON_URL);
            if (storage != null && rewardResult.tomeCount() == 0) {
                storage.onRewardDelta(-rewardResult.emeraldAmount(), -rewardResult.aspectAmount());
            }
        }

        // ── Guild chat messages ───────────────────────────────────────
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
            AvoUtilsMod.LOGGER.warn("[AvoUtils] [ChatBridge] Could not resolve username from '{}'", displayedName);
            return;
        }

        String dedupContent = EMOJI_SHORTCODE_PATTERN.matcher(content)
                .replaceAll("").replaceAll("\\s+", " ").trim();
        String dedupKey = realUsername + "\u0000" + dedupContent;
        if (!chatDeduper.isDuplicate(dedupKey)) {
            sendEvent(EVT_GUILD_CHAT, realUsername, content, AVATAR_URL_BASE + realUsername + "/128");
        }
    }

    private static boolean hasLeadingGuildChatColor(Component message) {
        if (message == null) return false;
        TextColor rootColor = message.getStyle().getColor();
        if (rootColor != null) return rootColor.getValue() == GUILD_CHAT_COLOR;
        Optional<Boolean> leadingColorIsGuild = message.visit((style, text) -> {
            if (text == null || text.isBlank()) return Optional.empty();
            TextColor color = style.getColor();
            return Optional.of(color != null && color.getValue() == GUILD_CHAT_COLOR);
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
}
