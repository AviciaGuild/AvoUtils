package info.avicia.partyfinder.handler;

import info.avicia.partyfinder.PartyFinderMod;
import info.avicia.partyfinder.api.PartyFinderClient;
import info.avicia.partyfinder.gui.PartyListScreen;
import net.minecraft.client.MinecraftClient;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listens to incoming chat messages to detect party events
 */
public class ChatPartyDetector {

    private static final Pattern PARTY_JOIN_PATTERN = Pattern.compile(
            "(?:\\[.+?\\] )?(.+?) has joined your party, say hello!", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§.");
    private static final Pattern RANK_PREFIX_PATTERN = Pattern.compile("^\\[.+?\\]\\s*");
    private static final Pattern NON_NAME_CHARS_PATTERN = Pattern.compile("[^A-Za-z0-9_]");

    private static final String PARTY_MEMBERS_MARKER = "Party members:";
    private static final String PARTY_JOIN_MARKER = "has joined your party";

    private final PartyFinderClient apiClient;

    // Tracked party ID set when the player is leading a party via the mod
    private long trackedPartyId = -1;

    // Set of known in-game party members
    private final Set<String> knownInGameMembers = ConcurrentHashMap.newKeySet();

    // Set of members from the last /party list parse
    private final Set<String> lastPartyListMembers = ConcurrentHashMap.newKeySet();

    // Track if the player is currently in an in-game party
    private boolean inParty = false;

    public ChatPartyDetector(PartyFinderClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Set the party ID to track for auto-reserving
     */
    public void setTrackedPartyId(long partyId) {
        this.trackedPartyId = partyId;
        knownInGameMembers.clear();
    }

    public long getTrackedPartyId() {
        return trackedPartyId;
    }

    public void clearTracking() {
        trackedPartyId = -1;
        knownInGameMembers.clear();
    }

    /**
     * Add names that are already known (e.g. from the Discord party list)
     */
    public void addKnownMembers(Iterable<String> names) {
        for (String name : names) {
            knownInGameMembers.add(name.toLowerCase());
        }
    }

    public Set<String> getLastPartyListMembers() {
        return Set.copyOf(lastPartyListMembers);
    }

    public boolean isInParty() {
        return inParty;
    }


    // ── Chat processing ──────────────────────────────────────────────────

    private String cleanPlayerName(String rawName) {
        if (rawName == null) return "";
        String name = COLOR_CODE_PATTERN.matcher(rawName).replaceAll("").trim();
        name = RANK_PREFIX_PATTERN.matcher(name).replaceFirst("").trim();
        String[] tokens = name.split("\\s+");
        if (tokens.length > 0) name = tokens[tokens.length - 1];
        return NON_NAME_CHARS_PATTERN.matcher(name).replaceAll("").trim();
    }

    public void onChatMessage(String text) {
        String lower = text.toLowerCase();

        // Check for leaving / not in party events (returned when not in a party)
        boolean notInPartyMsg = lower.contains("you must be in a party");

        // Check for joining / members list events
        boolean inPartyMsg = text.contains(PARTY_MEMBERS_MARKER)
                || text.contains(PARTY_JOIN_MARKER);

        if (!notInPartyMsg && !inPartyMsg) return;

        String trimmed = COLOR_CODE_PATTERN.matcher(text).replaceAll("").trim();

        if (notInPartyMsg) {
            inParty = false;
            lastPartyListMembers.clear();
            knownInGameMembers.clear();
            PartyFinderMod.LOGGER.info("Detected player is not in a party.");
            return;
        }

        if (inPartyMsg) {
            inParty = true;
        }

        // Handle /party list parsing
        int index = trimmed.indexOf("Party members: ");
        if (index != -1) {
            lastPartyListMembers.clear();
            String membersStr = trimmed.substring(index + "Party members: ".length()).trim();
            String[] parts = membersStr.split(",");
            for (String part : parts) {
                String cleanPart = part.replace("and", "").trim();
                String name = cleanPlayerName(cleanPart);
                if (!name.isEmpty()) {
                    lastPartyListMembers.add(name);
                }
            }
            onPartyListParsed();
            return;
        }

        // Party join detection
        Matcher joinMatch = PARTY_JOIN_PATTERN.matcher(trimmed);
        if (joinMatch.find()) {
            PartyFinderMod.LOGGER.info("Detected party join message. Requesting /party list.");
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                if (mc.player != null && mc.player.networkHandler != null) {
                    mc.player.networkHandler.sendChatCommand("party list");
                }
            });
        }
    }

    private void onPartyListParsed() {
        PartyFinderMod.LOGGER.info("Parsed /party list: {} members", lastPartyListMembers.size());

        // Notify active screen to update UI if needed
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.currentScreen instanceof PartyListScreen screen) {
                screen.onPartyListUpdated();
            }
        });

        if (trackedPartyId < 0) return;

        // Auto-reserve any unknown members
        for (String name : lastPartyListMembers) {
            if (!knownInGameMembers.contains(name.toLowerCase())) {
                PartyFinderMod.LOGGER.info("Auto-reserving from /party list: {}", name);
                apiClient.reserveIngame(trackedPartyId, name).thenAccept(resp -> {
                    if (resp.ok) {
                        knownInGameMembers.add(name.toLowerCase());
                    } else {
                        knownInGameMembers.remove(name.toLowerCase());
                    }
                });
            }
            knownInGameMembers.add(name.toLowerCase());
        }
    }
}
