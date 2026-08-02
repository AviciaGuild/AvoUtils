package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.AvoUtilsMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WarDetector {

    private static final Pattern WAR_WIN = Pattern.compile(
            "You have taken control of ([\\w ']+) from \\[([^\\]]+)\\]!");
    private static final Pattern WAR_LOSS = Pattern.compile(
            "Your guild has lost the war for ([\\w ']+)\\.");

    private static final double TRACKING_RADIUS_SQ = 120.0 * 120.0;

    static String tryDetect(String cleaned) {
        Matcher win = WAR_WIN.matcher(cleaned);
        if (win.find()) {
            return formatResult("Captured", win.group(1).trim(), win.group(2).trim());
        }
        Matcher loss = WAR_LOSS.matcher(cleaned);
        if (loss.find()) {
            return formatResult("Failed", loss.group(1).trim(), null);
        }
        return null;
    }

    private static String formatResult(String outcome, String territory, String enemy) {
        List<String> warrers = collectNearbyPlayers();
        if (warrers.isEmpty()) {
            String local = getLocalPlayerName();
            if (UsernameResolver.isValid(local)) warrers = List.of(local);
        }
        AvoUtilsMod.LOGGER.info("[ChatBridge/War] {}: territory='{}' enemy='{}' warrers={}",
                outcome, territory, enemy, warrers);

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(outcome).append(": ").append(territory).append("**");
        if (enemy != null) sb.append(" vs [").append(enemy).append("]");
        if (!warrers.isEmpty()) sb.append("\n👥 ").append(String.join(", ", warrers));
        return sb.toString();
    }

    private static List<String> collectNearbyPlayers() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return List.of();
        Set<String> names = new LinkedHashSet<>();
        String local = getLocalPlayerName();
        if (UsernameResolver.isValid(local)) names.add(local.trim());
        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == mc.player || mc.player.squaredDistanceTo(other) > TRACKING_RADIUS_SQ) continue;
            String name = other.getName().getString();
            if (UsernameResolver.isValid(name)) names.add(name.trim());
        }
        return names.isEmpty() ? List.of() : List.copyOf(names);
    }

    private static String getLocalPlayerName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null ? mc.player.getName().getString() : null;
    }
}
