package info.avicia.avoutils.features.partyfinder.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import info.avicia.avoutils.core.util.WynnPillUtil;

import java.util.ArrayList;
import java.util.List;

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

    private static boolean isSelf(String leaderName) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && leaderName.equalsIgnoreCase(mc.getSession().getUsername());
    }

    public void register() {
        AvoWebSocketManager.getInstance().registerListener(EVT_PARTY_MEMBER_JOINED, json -> {
            if (json.has("leader_name") && json.has("username")) {
                String leaderName = json.get("leader_name").getAsString();
                String username = json.get("username").getAsString();

                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null && isSelf(leaderName) && !isSelf(username)) {
                    MutableText formatted = WynnPillUtil.createPrefixedPill("AvoUtils", false)
                            .append(Text.literal(username).formatted(Formatting.WHITE))
                            .append(Text.literal(" has joined your party!").formatted(Formatting.GRAY));
                    mc.player.sendMessage(formatted, false);
                    if (config.notificationSoundsEnabled) {
                        mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }
                }
            }
        });

        AvoWebSocketManager.getInstance().registerListener(EVT_PARTY_FILLED, json -> {
            if (json.has("leader_name")) {
                String leaderName = json.get("leader_name").getAsString();

                MinecraftClient mc = MinecraftClient.getInstance();
                if (isSelf(leaderName) && mc.player != null) {
                    MutableText formatted = WynnPillUtil.createPrefixedPill("AvoUtils", false)
                            .append(Text.literal("Your party is now full!").formatted(Formatting.GREEN));
                    mc.player.sendMessage(formatted, false);
                    if (config.notificationSoundsEnabled) {
                        mc.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
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

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || isSelf(leaderName)) {
                return;
            }

            // Build activity string
            List<String> activityNames = new ArrayList<>();
            for (JsonElement elem : activitiesArr) {
                activityNames.add(elem.getAsString());
            }
            String activityStr = String.join(", ", activityNames);

            // Build announcement message
            MutableText formatted = WynnPillUtil.createPrefixedPill("AvoUtils", false)
                    .append(Text.literal(leaderName).formatted(Formatting.WHITE))
                    .append(Text.literal(" started a ").formatted(Formatting.GRAY))
                    .append(Text.literal(activityStr).formatted(Formatting.WHITE))
                    .append(Text.literal(" party! ").formatted(Formatting.GRAY));

            MutableText joinPill = WynnPillUtil.create("JOIN", Formatting.GREEN, Formatting.BLACK)
                    .styled(style -> style.withClickEvent(new ClickEvent.RunCommand("/apf join " + leaderName)));
            formatted.append(joinPill);

            mc.player.sendMessage(formatted, false);
            if (config.notificationSoundsEnabled) {
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        });

        AvoUtilsMod.LOGGER.info("[PartyFinder] Notification handler registered.");
    }
}
