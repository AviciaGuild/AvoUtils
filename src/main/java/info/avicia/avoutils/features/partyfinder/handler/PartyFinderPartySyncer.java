package info.avicia.avoutils.features.partyfinder.handler;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.api.ApiException;
import info.avicia.avoutils.core.party.InGamePartyTracker;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.util.UsernameResolver;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import info.avicia.avoutils.features.partyfinder.api.PartyData;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.features.partyfinder.gui.PartyListScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * Partyfinder-specific side effects for in-game party tracking.
 * Performs the auto-reserve and auto-kick behavior when the local player leads an active party.
 */
public class PartyFinderPartySyncer {

    private final PartyFinderClient apiClient;
    private final Consumer<List<String>> partyListListener = this::onPartyListParsed;
    private final Consumer<String> memberRemovedListener = this::onMemberRemoved;

    // Tracked party ID set when the local player is leading an active PartyFinder party
    private volatile long trackedPartyId = -1;

    // Set of known Discord party members (lower-case)
    private final Set<String> knownDiscordMembers = ConcurrentHashMap.newKeySet();

    // Members previously seen/reserved in-game while leading, used for auto-kick diffing
    private final Set<String> inGameSeenMembers = ConcurrentHashMap.newKeySet();

    // In-flight mutation guards to prevent duplicate HTTP requests from concurrent events
    private final Set<String> inFlightKicks = ConcurrentHashMap.newKeySet();
    private final Set<String> inFlightReserves = ConcurrentHashMap.newKeySet();

    public PartyFinderPartySyncer(PartyFinderClient apiClient) {
        this.apiClient = apiClient;
        InGamePartyTracker tracker = InGamePartyTracker.getInstance();
        tracker.addPartyListListener(partyListListener);
        tracker.addMemberRemovedListener(memberRemovedListener);
    }

    public void unregister() {
        InGamePartyTracker tracker = InGamePartyTracker.getInstance();
        tracker.removePartyListListener(partyListListener);
        tracker.removeMemberRemovedListener(memberRemovedListener);
        clearTracking();
    }

    public void registerWebSocketListeners() {
        AvoWebSocketManager ws = AvoWebSocketManager.getInstance();

        ws.registerListener("bridge_status", json -> {
            if (!ws.isVersionUnsupported()) {
                refreshOwnedParty();
            }
        });

        ws.registerListener("party_created", json -> {
            if (!json.has("leader_name") || !json.has("party_id")) {
                return;
            }
            String leaderName = json.get("leader_name").getAsString();
            long partyId = json.get("party_id").getAsLong();
            if (PlayerUtil.isSelf(leaderName) && trackedPartyId != partyId) {
                apiClient.getParty(partyId).thenAccept(party -> {
                    if (party != null) {
                        syncFromOwnedParty(party);
                        InGamePartyTracker.getInstance().triggerPartyList();
                    }
                }).exceptionally(ex -> null);
            }
        });

        ws.registerListener("party_updated", json -> {
            long currentId = trackedPartyId;
            if (currentId < 0 || !json.has("party_id") || json.get("party_id").getAsLong() != currentId) {
                return;
            }
            apiClient.getParty(currentId).thenAccept(party -> {
                if (party != null && trackedPartyId == currentId) {
                    syncFromOwnedParty(party);
                }
            }).exceptionally(ex -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof ApiException apiEx && apiEx.status() == 404 && trackedPartyId == currentId) {
                    clearTracking();
                }
                return null;
            });
        });
    }

    private void refreshOwnedParty() {
        apiClient.listParties().thenAccept(parties -> {
            PartyData owned = null;
            for (PartyData party : parties) {
                if (PlayerUtil.isSelf(party.leaderName)) {
                    owned = party;
                    break;
                }
            }
            syncFromOwnedParty(owned);
            if (owned != null) {
                InGamePartyTracker.getInstance().triggerPartyList();
            }
        }).exceptionally(ex -> null);
    }

    private static boolean isPlaceholder(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        return name.trim().equalsIgnoreCase("<RESERVED>");
    }

    private void resetMemberSets() {
        knownDiscordMembers.clear();
        inGameSeenMembers.clear();
        inFlightKicks.clear();
        inFlightReserves.clear();
    }

    /**
     * Update the tracked party ID without clearing member tracking state (e.g. when editing
     * activities recreates the Discord listing message with a new ID).
     */
    public void updateTrackedPartyId(long newPartyId) {
        this.trackedPartyId = newPartyId;
    }

    /**
     * Begin tracking a newly created party with initial in-game member names.
     */
    public void trackParty(long partyId, Iterable<String> initialMembers) {
        this.trackedPartyId = partyId;
        resetMemberSets();
        addKnownMembers(initialMembers);
        addInGameSeenMembers(initialMembers);
        seedSeenFromCurrentInGameList();
    }

    /**
     * Synchronize local tracking state with the latest backend snapshot of the player's owned party
     * (or clear tracking if {@code ownedParty} is null).
     */
    public void syncFromOwnedParty(PartyData ownedParty) {
        if (ownedParty == null) {
            clearTracking();
            return;
        }
        if (this.trackedPartyId != ownedParty.partyId) {
            this.trackedPartyId = ownedParty.partyId;
            resetMemberSets();
        }

        Set<String> freshDiscord = ConcurrentHashMap.newKeySet();
        for (String name : ownedParty.memberNames()) {
            if (isPlaceholder(name) || PlayerUtil.isSelf(name)) {
                continue;
            }
            String lower = PlayerUtil.normalizeName(name);
            if (!inFlightKicks.contains(lower)) {
                freshDiscord.add(lower);
            }
        }
        freshDiscord.addAll(inFlightReserves);

        knownDiscordMembers.clear();
        knownDiscordMembers.addAll(freshDiscord);
        inGameSeenMembers.retainAll(freshDiscord);
        seedSeenFromCurrentInGameList();
    }

    long getTrackedPartyId() {
        return trackedPartyId;
    }

    public void clearTracking() {
        trackedPartyId = -1;
        resetMemberSets();
    }

    void addKnownMembers(Iterable<String> names) {
        if (names == null) return;
        for (String name : names) {
            if (isPlaceholder(name)) {
                continue;
            }
            knownDiscordMembers.add(PlayerUtil.normalizeName(name));
        }
    }

    private void addInGameSeenMembers(Iterable<String> names) {
        if (names == null) return;
        for (String name : names) {
            if (isPlaceholder(name) || PlayerUtil.isSelf(name)) {
                continue;
            }
            inGameSeenMembers.add(PlayerUtil.normalizeName(name));
        }
    }

    private void seedSeenFromCurrentInGameList() {
        for (String inGameName : InGamePartyTracker.getInstance().getLastPartyListMembers()) {
            if (isPlaceholder(inGameName) || PlayerUtil.isSelf(inGameName)) {
                continue;
            }
            String lower = PlayerUtil.normalizeName(inGameName);
            if (knownDiscordMembers.contains(lower) && !inFlightKicks.contains(lower)) {
                inGameSeenMembers.add(lower);
            }
        }
    }

    void onMemberRemoved(String player) {
        long partyId = trackedPartyId;
        if (partyId < 0 || !UsernameResolver.isValid(player)) {
            return;
        }
        kickTrackedMember(partyId, player);
    }

    private void onPartyListParsed(List<String> members) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                if (mc.screen instanceof PartyListScreen screen) {
                    screen.onInGamePartyChanged();
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
        long partyId = trackedPartyId;
        if (partyId < 0 || members == null || members.isEmpty()) {
            return;
        }

        AvoUtilsMod.LOGGER.info("[AvoUtils] [PartyFinder] Syncing in-game party ({} members) with PartyFinder party {}",
                members.size(), partyId);

        // Auto-reserve any unknown members
        for (String name : members) {
            if (isPlaceholder(name) || PlayerUtil.isSelf(name)) {
                continue;
            }
            String lowerName = PlayerUtil.normalizeName(name);
            if (inFlightKicks.contains(lowerName)) {
                continue;
            }

            if (!knownDiscordMembers.contains(lowerName)) {
                if (!inFlightReserves.add(lowerName)) {
                    continue;
                }
                AvoUtilsMod.LOGGER.info("[AvoUtils] [PartyFinder] Auto-reserving from /party list: {}", name);
                knownDiscordMembers.add(lowerName);
                inGameSeenMembers.add(lowerName);
                apiClient.reserveIngame(partyId, name).whenComplete((resp, ex) -> {
                    inFlightReserves.remove(lowerName);
                    if ((ex != null || resp == null || !resp.ok) && trackedPartyId == partyId) {
                        knownDiscordMembers.remove(lowerName);
                        inGameSeenMembers.remove(lowerName);
                    }
                });
            } else {
                inGameSeenMembers.add(lowerName);
            }
        }

        // Auto-remove members who were seen in-game but are no longer in the in-game party
        List<String> toRemove = new ArrayList<>();
        for (String name : inGameSeenMembers) {
            boolean stillInParty = members.stream().anyMatch(m -> PlayerUtil.namesEqual(m, name))
                    || PlayerUtil.isSelf(name);
            if (!stillInParty) {
                toRemove.add(name);
            }
        }

        for (String name : toRemove) {
            kickTrackedMember(partyId, name);
        }
    }

    private void kickTrackedMember(long partyId, String playerName) {
        if (partyId < 0 || isPlaceholder(playerName) || PlayerUtil.isSelf(playerName)) {
            return;
        }
        String lowerName = PlayerUtil.normalizeName(playerName);
        if (!inFlightKicks.add(lowerName)) {
            return;
        }

        boolean wasKnownDiscord = knownDiscordMembers.remove(lowerName);
        boolean wasSeenInGame = inGameSeenMembers.remove(lowerName);
        if (!wasKnownDiscord && !wasSeenInGame) {
            inFlightKicks.remove(lowerName);
            return;
        }

        AvoUtilsMod.LOGGER.info("[AvoUtils] [PartyFinder] Auto-kicking member no longer in party: {}", playerName);
        apiClient.kickMember(partyId, playerName).whenComplete((resp, ex) -> {
            inFlightKicks.remove(lowerName);
            if ((ex != null || resp == null || !resp.ok) && trackedPartyId == partyId) {
                if (wasKnownDiscord) {
                    knownDiscordMembers.add(lowerName);
                }
                if (wasSeenInGame) {
                    inGameSeenMembers.add(lowerName);
                }
            }
        });
    }
}
