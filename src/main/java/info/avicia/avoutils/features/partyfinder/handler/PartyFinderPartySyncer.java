package info.avicia.avoutils.features.partyfinder.handler;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.party.InGamePartyTracker;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.features.partyfinder.gui.PartyListScreen;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Partyfinder-specific side effects for in-game party tracking.
 * Performs the auto-reserve and auto-kick behavior.
 */
public class PartyFinderPartySyncer {

    private final PartyFinderClient apiClient;
    private final Consumer<List<String>> partyListListener = this::onPartyListParsed;

    // Tracked party ID set when the player is leading a party via the mod
    private volatile long trackedPartyId = -1;

    // Set of known Discord party members (lower-case)
    private final Set<String> knownDiscordMembers = ConcurrentHashMap.newKeySet();

    // Members previously reserved/in-game, used for auto-kick diffing
    private final Set<String> inGameSeenMembers = ConcurrentHashMap.newKeySet();

    public PartyFinderPartySyncer(PartyFinderClient apiClient) {
        this.apiClient = apiClient;
        InGamePartyTracker.getInstance().addPartyListListener(partyListListener);
    }

    private static boolean isPlaceholder(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String trimmed = name.trim();
        return trimmed.equalsIgnoreCase("<RESERVED>");
    }

    /**
     * Set the party ID to track for auto-reserving.
     */
    public void setTrackedPartyId(long partyId) {
        if (this.trackedPartyId != partyId) {
            this.trackedPartyId = partyId;
            knownDiscordMembers.clear();
            inGameSeenMembers.clear();
        }
    }

    public long getTrackedPartyId() {
        return trackedPartyId;
    }

    public void clearTracking() {
        trackedPartyId = -1;
        knownDiscordMembers.clear();
        inGameSeenMembers.clear();
    }

    /**
     * Add names that are already known (e.g. from the Discord party list).
     */
    public void addKnownMembers(Iterable<String> names) {
        if (names == null) return;
        for (String name : names) {
            if (isPlaceholder(name)) {
                continue;
            }
            knownDiscordMembers.add(PlayerUtil.normalizeName(name));
        }
    }

    public Set<String> getLastPartyListMembers() {
        return InGamePartyTracker.getInstance().getLastPartyListMembers();
    }

    public boolean isInParty() {
        return InGamePartyTracker.getInstance().isInParty();
    }

    public void triggerPartyList() {
        InGamePartyTracker.getInstance().triggerPartyList();
    }

    private void onPartyListParsed(List<String> members) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                if (mc.currentScreen instanceof PartyListScreen screen) {
                    screen.onPartyListUpdated();
                }
            });
        }

        if (trackedPartyId < 0) {
            AvoUtilsMod.LOGGER.debug("[AvoUtils] [PartyFinder] Skipping sync: trackedPartyId={}", trackedPartyId);
            return;
        }

        syncWithInGameParty(members);
    }

    private void syncWithInGameParty(List<String> members) {
        if (trackedPartyId < 0 || members == null || members.isEmpty()) {
            return;
        }

        AvoUtilsMod.LOGGER.info("[AvoUtils] [PartyFinder] Syncing in-game party ({} members) with PartyFinder party {}",
                members.size(), trackedPartyId);

        // Auto-reserve any unknown members
        for (String name : members) {
            if (isPlaceholder(name) || PlayerUtil.isSelf(name)) {
                continue; // skip placeholders and leader (self)
            }
            String lowerName = PlayerUtil.normalizeName(name);

            boolean isKnownDiscord = knownDiscordMembers.contains(lowerName);

            if (!isKnownDiscord) {
                AvoUtilsMod.LOGGER.info("[AvoUtils] [PartyFinder] Auto-reserving from /party list: {}", name);
                knownDiscordMembers.add(lowerName);
                inGameSeenMembers.add(lowerName);
                apiClient.reserveIngame(trackedPartyId, name).thenAccept(resp -> {
                    if (!resp.ok) {
                        knownDiscordMembers.remove(lowerName);
                        inGameSeenMembers.remove(lowerName);
                    }
                }).exceptionally(ex -> {
                    knownDiscordMembers.remove(lowerName);
                    inGameSeenMembers.remove(lowerName);
                    return null;
                });
            } else {
                inGameSeenMembers.add(lowerName);
            }
        }

        // Auto-remove members who were seen in-game or reserved for in-game but are no longer in the in-game party
        List<String> toRemove = new ArrayList<>();
        for (String name : inGameSeenMembers) {
            if (isPlaceholder(name)) {
                continue;
            }
            boolean stillInParty = members.stream().anyMatch(m -> PlayerUtil.namesEqual(m, name))
                    || PlayerUtil.isSelf(name);
            if (!stillInParty) {
                toRemove.add(name);
            }
        }

        for (String name : toRemove) {
            if (isPlaceholder(name)) {
                continue;
            }
            String lowerName = PlayerUtil.normalizeName(name);
            AvoUtilsMod.LOGGER.info("[AvoUtils] [PartyFinder] Auto-kicking member no longer in party: {}", name);
            boolean wasKnownDiscord = knownDiscordMembers.remove(lowerName);
            inGameSeenMembers.remove(lowerName);
            apiClient.kickMember(trackedPartyId, name).thenAccept(resp -> {
                if (!resp.ok) {
                    if (wasKnownDiscord) {
                        knownDiscordMembers.add(lowerName);
                    }
                    inGameSeenMembers.add(lowerName);
                }
            }).exceptionally(ex -> {
                if (wasKnownDiscord) {
                    knownDiscordMembers.add(lowerName);
                }
                inGameSeenMembers.add(lowerName);
                return null;
            });
        }
    }
}
