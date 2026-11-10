package info.avicia.avoutils.features.chatbridge;

import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.chat.GuildChat;
import info.avicia.avoutils.core.chat.RaidDetector;
import info.avicia.avoutils.core.chat.RewardDetector;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.Deduplicator;
import info.avicia.avoutils.core.util.PacketTextNormalizer;
import info.avicia.avoutils.core.util.WynnPillUtil;
import info.avicia.avoutils.core.util.WynncraftServerPolicy.Scope;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Coordinates the chat bridge between in-game Wynncraft chat/events and Discord.
 */
public class ChatBridgeFeature implements AvoFeature {

    private static final String BANK_CHEST_AVATAR_URL =
            "https://wynncraft.wiki.gg/images/UnidentifiedMythicBox.png";
    private static final String AVO_ICON_URL =
            "https://raw.githubusercontent.com/AviciaGuild/AvoUtils/refs/heads/main/src/main/resources/assets/avoutils/icon.png";

    private static final long CHAT_DEDUPE_MS = 250;
    private static final long EVENT_DEDUPE_MS = 5_000;

    private static final String EVT_DISCORD_CHAT = "discord_chat";
    private static final String EVT_BRIDGE_STATUS = "bridge_status";
    private static final String EVT_GUILD_CHAT = "guild_chat";
    private static final String EVT_GUILD_BANK = "guild_bank_event";
    private static final String EVT_GUILD_RAID = "guild_raid_completion";
    private static final String EVT_GUILD_REWARD = "guild_reward";
    private static final String EVT_GUILD_TERRITORY = "guild_territory_event";
    private static final String EVT_GUILD_ALLY = "guild_ally_event";
    private static final String EVT_GUILD_MEMBER = "guild_member_event";

    private ModConfig config;
    private final Deduplicator chatDeduper = new Deduplicator(CHAT_DEDUPE_MS);
    private final Deduplicator eventDeduper = new Deduplicator(EVENT_DEDUPE_MS);
    private final TerritoryEventDetector territoryDetector = new TerritoryEventDetector();

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

        AvoWebSocketManager ws = AvoWebSocketManager.getInstance();
        ws.registerListener(EVT_DISCORD_CHAT, this::onDiscordChat);
        ws.registerListener(EVT_BRIDGE_STATUS, this::onBridgeStatus);
        ws.registerConnectionDemand("chatbridge", this::isBridgeActive);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> territoryDetector.reset());

        AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge] Initialized.");
    }

    @Override
    public void onServerScopeChanged(Scope newScope) {
        if (newScope != Scope.MAIN) {
            territoryDetector.reset();
        }
    }

    /** Called when a system chat packet is received. */
    public void onSystemChat(Component message) {
        if (message == null || !isBridgeActive() || !AvoWebSocketManager.getInstance().isConnected()) {
            return;
        }

        // ── Territory war alerts (§c / 0xFF5555) ──────────────────────
        if (GuildChat.isTerritoryAlert(message)) {
            String cleaned = PacketTextNormalizer.normalizeForParsing(message.getString());
            TerritoryEventDetector.Result territory = territoryDetector.tryDetect(cleaned);
            if (territory != null) {
                sendDedupedEvent(EVT_GUILD_TERRITORY, "Territory Alert", territory.formattedMessage(), AVO_ICON_URL);
            }
            return;
        }

        // ── Guild chat & announcements (§b / 0x55FFFF) ────────────────
        if (!GuildChat.isGuildChat(message)) {
            return;
        }

        String cleaned = PacketTextNormalizer.normalizeForParsing(message.getString());
        if (cleaned.isEmpty()) {
            return;
        }

        RaidDetector.RaidResult raid = RaidDetector.tryDetect(cleaned, message);
        if (raid != null) {
            sendDedupedEvent(EVT_GUILD_RAID, "Raid Complete", raid.formattedMessage(), AVO_ICON_URL);
            return;
        }

        RewardDetector.RewardResult reward = RewardDetector.tryDetect(cleaned, message);
        if (reward != null) {
            sendEvent(EVT_GUILD_REWARD, "Guild Rewards", reward.formattedMessage(), AVO_ICON_URL);
            return;
        }

        BankDetector.Result bank = BankDetector.tryDetect(cleaned, message);
        if (bank != null) {
            sendEvent(EVT_GUILD_BANK, bank.displayName(), bank.formattedMessage(), BANK_CHEST_AVATAR_URL);
            return;
        }

        AllyDetector.Result ally = AllyDetector.tryDetect(cleaned, message);
        if (ally != null) {
            sendDedupedEvent(EVT_GUILD_ALLY, "Guild Diplomacy", ally.formattedMessage(), AVO_ICON_URL);
            return;
        }

        GuildMemberDetector.Result member = GuildMemberDetector.tryDetect(cleaned, message);
        if (member != null) {
            sendDedupedEvent(EVT_GUILD_MEMBER, "Guild Members", member.formattedMessage(), AVO_ICON_URL);
            return;
        }

        GuildChatDetector.Result chat = GuildChatDetector.tryDetect(cleaned, message);
        if (chat != null && !chatDeduper.isDuplicate(chat.dedupKey())) {
            sendEvent(EVT_GUILD_CHAT, chat.username(), chat.content(), chat.avatarUrl());
        }
    }

    private void onDiscordChat(JsonObject json) {
        if (!isBridgeActive() || !json.has("username") || !json.has("message")) {
            return;
        }
        if (Minecraft.getInstance().player == null) {
            return;
        }
        String username = json.get("username").getAsString();
        String message = json.get("message").getAsString();
        MutableComponent formatted = WynnPillUtil.createPrefixedPill("AvoBridge", false)
                .append(Component.literal(username).withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal(message).withStyle(ChatFormatting.AQUA));
        Minecraft.getInstance().player.displayClientMessage(formatted, false);
    }

    private void onBridgeStatus(JsonObject json) {
        if (json.has("guild_member")) {
            boolean guildMember = json.get("guild_member").getAsBoolean();
            AvoAuthService.getInstance().setCachedGuildMember(guildMember);
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge] Guild membership updated via bridge_status: {}", guildMember);
        }
    }

    private void sendDedupedEvent(String eventType, String username, String message, String avatarUrl) {
        if (!eventDeduper.isDuplicate(eventType + "\u0000" + message)) {
            sendEvent(eventType, username, message, avatarUrl);
        }
    }

    private void sendEvent(String eventType, String username, String message, String avatarUrl) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("message", message);
        payload.addProperty("avatar_url", avatarUrl);
        AvoWebSocketManager.getInstance().sendEvent(eventType, payload);
    }
}
