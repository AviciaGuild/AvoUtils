package info.avicia.avoutils.features.chatbridge;

import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Models;
import com.wynntils.models.character.event.CharacterDeathEvent;
import com.wynntils.models.war.type.WarBattleInfo;
import com.wynntils.models.war.type.WarTowerState;
import com.wynntils.utils.type.RangedValue;
import info.avicia.avoutils.AvoUtilsMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects guild war outcomes using Wynntils tower state API.
 */
final class WarDetector {

    private static final double TRACKING_RADIUS_SQ = 120.0 * 120.0;

    private static String activeBattleId;
    private static WarBattleInfo activeInfo;
    private static List<String> activeWarrers;
    private static boolean submissionSent;
    private static boolean deathListenerRegistered;

    static void tick() {
        try {
            doTick();
        } catch (Throwable ignored) {
            // Wynntils not present; war detection unavailable
        }
    }

    private static void doTick() {
        if (!deathListenerRegistered) {
            try {
                WynntilsMod.registerEventListener(new Object() {
                    @SubscribeEvent
                    public void onDeath(CharacterDeathEvent e) {
                        onCharacterDeath();
                    }
                });
                deathListenerRegistered = true;
            } catch (Throwable ignored) {
                return;
            }
        }

        WarBattleInfo info = Models.GuildWarTower.getWarBattleInfo().orElse(null);
        if (info != null) {
            String battleId = info.getTerritory() + ":" + info.getInitialState().timestamp();
            if (!battleId.equals(activeBattleId)) {
                activeBattleId = battleId;
                activeInfo = info;
                activeWarrers = collectNearbyPlayers();
                submissionSent = false;
                AvoUtilsMod.LOGGER.info("[ChatBridge/War] Tracking war: territory='{}' warrers={}",
                        info.getTerritory(), activeWarrers);
            } else {
                activeInfo = info;
            }

            // Tower destroyed = win
            if (!submissionSent && info.getCurrentState().health() <= 0) {
                submitWar("Captured");
            }
        } else if (activeBattleId != null && !submissionSent) {
            // War disappeared = ended, submit if not already sent
            submitWar("Captured");
            reset();
        }
    }

    private static void onCharacterDeath() {
        if (activeBattleId != null && !submissionSent) {
            submitWar("Failed");
        }
    }

    private static void submitWar(String outcome) {
        if (activeInfo == null) return;
        submissionSent = true;

        WarTowerState initial = activeInfo.getInitialState();
        RangedValue dmg = initial.damage();
        long hp = initial.health();
        double atk = initial.attackSpeed();
        double def = initial.defense();
        int dmgLow = dmg != null ? (int) dmg.low() : 0;
        int dmgHigh = dmg != null ? (int) dmg.high() : 0;
        String territory = activeInfo.getTerritory();

        AvoUtilsMod.LOGGER.info("[ChatBridge/War] {}: territory='{}' hp={} def={}% dmg={}-{} atk={}x warrers={}",
                outcome, territory, hp, def, dmgLow, dmgHigh, atk, activeWarrers);

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(outcome).append(": ").append(territory).append("**");
        sb.append("\n❤ ").append(formatNumber(hp));
        if (def > 0) sb.append(" (").append(String.format("%.0f", def)).append("%)");
        sb.append(" · ☠ ").append(formatNumber(dmgLow)).append("-").append(formatNumber(dmgHigh));
        if (atk > 0) sb.append(" (").append(atk).append("x)");
        if (activeWarrers != null && !activeWarrers.isEmpty()) {
            sb.append("\n👥 ").append(String.join(", ", activeWarrers));
        }

        // Submit via ChatBridgeFeature
        ChatBridgeFeature cb = AvoUtilsMod.getInstance().getFeature(ChatBridgeFeature.class);
        if (cb != null) {
            cb.sendWarResult(outcome.equals("Captured") ? "Captured" : "Failed", sb.toString());
        }
    }

    static void reset() {
        activeBattleId = null;
        activeInfo = null;
        activeWarrers = null;
        submissionSent = false;
    }

    private static String formatNumber(long value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.1fk", value / 1_000.0);
        return String.valueOf(value);
    }

    private static List<String> collectNearbyPlayers() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return List.of();
        Set<String> names = new LinkedHashSet<>();
        String local = mc.player.getName().getString();
        names.add(local.trim());
        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == mc.player || mc.player.squaredDistanceTo(other) > TRACKING_RADIUS_SQ) continue;
            names.add(other.getName().getString().trim());
        }
        return List.copyOf(names);
    }
}
