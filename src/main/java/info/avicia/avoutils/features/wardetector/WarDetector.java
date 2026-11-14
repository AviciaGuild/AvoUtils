package info.avicia.avoutils.features.wardetector;

import com.wynntils.core.components.Models;
import com.wynntils.models.war.type.WarBattleInfo;
import com.wynntils.models.war.type.WarTowerState;
import com.wynntils.utils.type.RangedValue;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.util.UsernameResolver;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Detects guild war stats, participants, and outcomes using the Wynntils guild war tower model.
 */
public final class WarDetector {

    public record TowerStats(long health, double defense, int damageLow, int damageHigh, double attackSpeed) {}

    public record WarResult(String outcome, String territory, TowerStats tower, List<String> warrers,
                            long durationSeconds, long dps) {
        public WarResult {
            Objects.requireNonNull(tower, "tower");
            warrers = warrers == null ? List.of() : List.copyOf(warrers);
        }
    }

    record TowerState(long health, double defense, int damageLow, int damageHigh,
                      double attackSpeed, long timestamp) {
        long effectiveHealth() {
            if (defense <= 0.0 || defense >= 100.0) return health;
            return (long) Math.floor(health / (1.0 - defense / 100.0));
        }

        TowerStats toStats() {
            return new TowerStats(health, defense, damageLow, damageHigh, attackSpeed);
        }
    }

    record BattleSnapshot(String territory, TowerState initial, Supplier<TowerState> liveCurrent) {
        BattleSnapshot(String territory, TowerState initial, TowerState current) {
            this(territory, initial, () -> current);
        }

        TowerState current() {
            return liveCurrent != null ? liveCurrent.get() : null;
        }
    }

    private static final double TRACKING_RADIUS_SQ = 120.0 * 120.0;
    static final long GRACE_PERIOD_MS = 15_000L;

    private static final Pattern TERRITORY_CAPTURED = Pattern.compile("(?i)Territory\\s+Captured");
    private static final Pattern WAR_LOST = Pattern.compile("(?i)lost\\s+the\\s+war\\s+for");

    private final BooleanSupplier currentlyInWarCheck;
    private final Supplier<List<String>> nearbyPlayersSupplier;
    private final Supplier<String> selfNameSupplier;

    private String activeTerritory;
    private TowerState activeInitialState;
    private TowerState activeCurrentState;
    private Supplier<TowerState> activeLiveCurrentSupplier;
    private final Set<String> activeWarrers = new LinkedHashSet<>();
    private boolean submissionSent;
    private long warDisappearedAt;

    public WarDetector() {
        this(
                WynntilsBridge::hasActiveTowerBar,
                WarDetector::collectNearbyPlayers,
                WarDetector::localUsername);
    }

    WarDetector(
            BooleanSupplier currentlyInWarCheck,
            Supplier<List<String>> nearbyPlayersSupplier,
            Supplier<String> selfNameSupplier) {
        this.currentlyInWarCheck = currentlyInWarCheck;
        this.nearbyPlayersSupplier = nearbyPlayersSupplier;
        this.selfNameSupplier = selfNameSupplier;
    }

    public synchronized boolean isTrackingWar() {
        return activeTerritory != null && !submissionSent;
    }

    public synchronized void tick() {
        try {
            updateState(System.currentTimeMillis(), WynntilsBridge.poll());
        } catch (Throwable ignored) {
            // Wynntils not present; war detection unavailable
        }
    }

    synchronized void updateState(long now, BattleSnapshot snapshot) {
        if (snapshot != null) {
            warDisappearedAt = 0;
            String rawTerritory = snapshot.territory();
            String territory = (rawTerritory == null || rawTerritory.isBlank()) ? "Unknown" : rawTerritory.trim();
            TowerState initial = snapshot.initial();
            boolean sameActiveWar = activeTerritory != null && territory.equalsIgnoreCase(activeTerritory);

            if (!sameActiveWar) {
                activeTerritory = territory;
                activeInitialState = initial;
                activeCurrentState = snapshot.current();
                activeLiveCurrentSupplier = snapshot.liveCurrent();
                activeWarrers.clear();
                activeWarrers.addAll(nearbyPlayersSupplier.get());
                submissionSent = false;
                AvoUtilsMod.LOGGER.info("[AvoUtils] [WarDetector] Tracking war: territory='{}' warrers={}",
                        territory, activeWarrers);
            } else if (!submissionSent) {
                if (activeInitialState == null && initial != null) {
                    activeInitialState = initial;
                }
                TowerState latest = snapshot.current();
                if (latest != null) {
                    activeCurrentState = latest;
                }
                if (snapshot.liveCurrent() != null) {
                    activeLiveCurrentSupplier = snapshot.liveCurrent();
                }
                activeWarrers.addAll(nearbyPlayersSupplier.get());
            }
            return;
        }

        if (activeTerritory != null) {
            if (submissionSent) {
                reset();
                return;
            }
            if (warDisappearedAt == 0) {
                warDisappearedAt = now;
            }
            if (now - warDisappearedAt > GRACE_PERIOD_MS) {
                AvoUtilsMod.LOGGER.warn("[AvoUtils] [WarDetector] War disappeared without outcome chat; resetting");
                reset();
            }
        }
    }

    /**
     * Called from {@link WarDetectorFeature#onSystemChat} for system messages while tracking a war.
     * Returns a structured war result if a genuine war outcome chat line is detected.
     */
    public synchronized WarResult tryDetectOutcome(String cleaned) {
        if (cleaned == null || activeTerritory == null || submissionSent) return null;

        Matcher captured = TERRITORY_CAPTURED.matcher(cleaned);
        if (captured.find() && !hasChatPrefixBeforeMatch(cleaned, captured.start())) {
            return buildWarResult("Captured");
        }

        Matcher lost = WAR_LOST.matcher(cleaned);
        if (lost.find() && !hasChatPrefixBeforeMatch(cleaned, lost.start())) {
            return buildWarResult("Failed");
        }

        return null;
    }

    private static boolean hasChatPrefixBeforeMatch(String text, int matchStart) {
        if (matchStart <= 0) return false;
        String prefix = text.substring(0, matchStart);
        return prefix.indexOf(':') >= 0 || prefix.contains("->");
    }

    private WarResult buildWarResult(String outcome) {
        TowerState liveCurrent = activeLiveCurrentSupplier != null ? activeLiveCurrentSupplier.get() : null;
        if (liveCurrent != null) {
            activeCurrentState = liveCurrent;
        }
        if (activeInitialState == null && activeCurrentState == null) return null;
        submissionSent = true;

        TowerState initial = activeInitialState != null ? activeInitialState : activeCurrentState;
        TowerState current = activeCurrentState != null ? activeCurrentState : initial;

        long durationSeconds = 0L;
        long dps = 0L;
        double elapsedSeconds = (current.timestamp() - initial.timestamp()) / 1000.0;
        if (elapsedSeconds > 0.0) {
            durationSeconds = (long) Math.ceil(elapsedSeconds);
            long damageDealt = initial.effectiveHealth() - current.effectiveHealth();
            dps = Math.max(0L, (long) Math.floor(damageDealt / elapsedSeconds));
        }

        // Only merge nearby players if the tower boss bar is still present, avoiding main-world
        // bystanders when the outcome message arrives right after teleporting out of the war.
        if (currentlyInWarCheck.getAsBoolean()) {
            activeWarrers.addAll(nearbyPlayersSupplier.get());
        }

        List<String> warrers = sanitizeWarrers(activeWarrers);
        if (warrers.isEmpty()) {
            String local = selfNameSupplier.get();
            if (UsernameResolver.isValid(local)) warrers = List.of(local.trim());
        }

        TowerStats tower = initial.toStats();
        AvoUtilsMod.LOGGER.info(
                "[AvoUtils] [WarDetector] {}: territory='{}' hp={} def={}% dmg={}-{} atk={}x duration={}s dps={} warrers={}",
                outcome, activeTerritory, tower.health(), tower.defense(), tower.damageLow(),
                tower.damageHigh(), tower.attackSpeed(), durationSeconds, dps, warrers);

        return new WarResult(outcome, activeTerritory, tower, warrers, durationSeconds, dps);
    }

    public synchronized void reset() {
        activeTerritory = null;
        activeInitialState = null;
        activeCurrentState = null;
        activeLiveCurrentSupplier = null;
        activeWarrers.clear();
        submissionSent = false;
        warDisappearedAt = 0;
    }

    private static List<String> collectNearbyPlayers() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return List.of();
        Set<String> names = new LinkedHashSet<>();
        String local = localUsername();
        if (UsernameResolver.isValid(local)) names.add(local.trim());
        for (Player other : mc.level.players()) {
            if (other == null || other == mc.player) continue;
            if (mc.player.distanceToSqr(other) > TRACKING_RADIUS_SQ) continue;
            String name = extractUsername(other);
            if (UsernameResolver.isValid(name)) names.add(name.trim());
        }
        return List.copyOf(names);
    }

    private static String localUsername() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            String profileName = extractUsername(mc.player);
            if (UsernameResolver.isValid(profileName)) return profileName;
        }
        return PlayerUtil.selfName();
    }

    private static String extractUsername(Player player) {
        if (player == null) return null;
        if (player.getGameProfile() != null && player.getGameProfile().name() != null) {
            String name = player.getGameProfile().name();
            if (!name.isBlank()) return name;
        }
        return player.getName() != null ? player.getName().getString() : null;
    }

    static List<String> sanitizeWarrers(Collection<String> warrers) {
        if (warrers == null || warrers.isEmpty()) return List.of();
        Map<String, String> unique = new LinkedHashMap<>();
        for (String warrer : warrers) {
            if (UsernameResolver.isValid(warrer)) {
                String trimmed = warrer.trim();
                unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
        }
        return List.copyOf(unique.values());
    }

    synchronized String getActiveTerritory() {
        return activeTerritory;
    }

    synchronized TowerState getActiveInitialState() {
        return activeInitialState;
    }

    synchronized TowerState getActiveCurrentState() {
        return activeCurrentState;
    }

    synchronized Set<String> getActiveWarrers() {
        return new LinkedHashSet<>(activeWarrers);
    }

    synchronized boolean isSubmissionSent() {
        return submissionSent;
    }

    /**
     * Isolates optional Wynntils model classes so {@link WarDetector} can be loaded and tested
     * when Wynntils is absent from the classpath.
     */
    private static final class WynntilsBridge {
        static BattleSnapshot poll() {
            WarBattleInfo info = Models.GuildWarTower.getWarBattleInfo().orElse(null);
            if (info == null) return null;
            return new BattleSnapshot(
                    info.getTerritory(),
                    toTowerState(info.getInitialState()),
                    () -> toTowerState(info.getCurrentState()));
        }

        static boolean hasActiveTowerBar() {
            try {
                return Models.GuildWarTower.getWarBattleInfo().isPresent();
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static TowerState toTowerState(WarTowerState state) {
            if (state == null) return null;
            RangedValue dmg = state.damage();
            int low = dmg != null ? dmg.low() : 0;
            int high = dmg != null ? dmg.high() : 0;
            return new TowerState(
                    state.health(),
                    state.defense(),
                    low,
                    high,
                    state.attackSpeed(),
                    state.timestamp());
        }
    }
}
