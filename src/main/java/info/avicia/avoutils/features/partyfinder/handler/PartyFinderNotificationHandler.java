package info.avicia.avoutils.features.partyfinder.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.util.WynnPillUtil;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

/**
 * Handles playing sounds and printing notifications for party finder events
 */
public class PartyFinderNotificationHandler {

    private static final String EVT_PARTY_MEMBER_JOINED = "party_member_joined";
    private static final String EVT_PARTY_FILLED = "party_filled";
    private static final String EVT_PARTY_CREATED = "party_created";

    private final ModConfig config;

    public PartyFinderNotificationHandler(ModConfig config) {
        this.config = config;
    }

    public void register() {
        AvoWebSocketManager.getInstance().registerListener(EVT_PARTY_MEMBER_JOINED, json -> {
            if (json.has("leader_name") && json.has("username")) {
                String leaderName = json.get("leader_name").getAsString();
                String username = json.get("username").getAsString();

                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && PlayerUtil.isSelf(leaderName) && !PlayerUtil.isSelf(username)) {
                    MutableComponent formatted = WynnPillUtil.createPrefixedPill("AvoUtils", false)
                            .append(Component.literal(username).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" has joined your party!").withStyle(ChatFormatting.GRAY));
                    mc.player.displayClientMessage(formatted, false);
                    if (config.notificationSoundsEnabled) {
                        mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }
                }
            }
        });

        AvoWebSocketManager.getInstance().registerListener(EVT_PARTY_FILLED, json -> {
            if (json.has("leader_name")) {
                String leaderName = json.get("leader_name").getAsString();

                Minecraft mc = Minecraft.getInstance();
                if (PlayerUtil.isSelf(leaderName) && mc.player != null) {
                    MutableComponent formatted = WynnPillUtil.createPrefixedPill("AvoUtils", false)
                            .append(Component.literal("Your party is now full!").withStyle(ChatFormatting.GREEN));
                    mc.player.displayClientMessage(formatted, false);
                    if (config.notificationSoundsEnabled) {
                        mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                }
            }
        });

        AvoWebSocketManager.getInstance().registerListener(EVT_PARTY_CREATED, json -> {
            if (!config.newPartyNotifsEnabled) {
                return;
            }
            if (!json.has("leader_name") || !json.has("activities")) {
                return;
            }

            String leaderName = json.get("leader_name").getAsString();
            JsonArray activitiesArr = json.getAsJsonArray("activities");

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || PlayerUtil.isSelf(leaderName)) {
                return;
            }

            // Build activity string
            List<String> activityNames = new ArrayList<>();
            for (JsonElement elem : activitiesArr) {
                activityNames.add(elem.getAsString());
            }
            String activityStr = String.join(", ", activityNames);

            // Build announcement message
            MutableComponent formatted = WynnPillUtil.createPrefixedPill("AvoUtils", false)
                    .append(Component.literal(leaderName).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" started a ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(activityStr).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" party! ").withStyle(ChatFormatting.GRAY));

            MutableComponent joinPill = WynnPillUtil.createClickable("JOIN", ChatFormatting.GREEN, ChatFormatting.BLACK,
                    "/apf join " + leaderName, "Click to join party");
            formatted.append(joinPill);

            mc.player.displayClientMessage(formatted, false);
            if (config.notificationSoundsEnabled) {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        });

        AvoUtilsMod.LOGGER.info("[AvoUtils] [PartyFinder] Notification handler registered.");
    }
}
