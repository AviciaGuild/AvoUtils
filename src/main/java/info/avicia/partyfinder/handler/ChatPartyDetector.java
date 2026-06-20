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
    private static final Pattern PARTY_KICK_PATTERN = Pattern.compile(
            "(?:\\[.+?\\] )?(.+?) has been kicked from the party!", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PARTY_LEAVE_PATTERN = Pattern.compile(
            "(?:\\[.+?\\] )?(.+?) has left the party!", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§.");
    private static final Pattern NON_NAME_CHARS_PATTERN = Pattern.compile("[^A-Za-z0-9_]");
    private static final String[] EVENT_KEYWORDS = {
            "party members:",
            "has joined your party",
            "has been kicked from the party",
            "has left the party",
            "you must be in a party"
    };

    private final PartyFinderClient apiClient;

    // Tracked party ID set when the player is leading a party via the mod
    private long trackedPartyId = -1;

    // Set of known in-game party members
    private final Set<String> knownInGameMembers = ConcurrentHashMap.newKeySet();

    // Set of members from the last /party list parse
    private final Set<String> lastPartyListMembers = ConcurrentHashMap.newKeySet();

    // Track if the player is currently in an in-game party
    private boolean inParty = false;

    private volatile long hiddenPartyListExpireTime = 0;

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
        return NON_NAME_CHARS_PATTERN.matcher(name).replaceAll("").trim();
    }

    public void triggerPartyList() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null && mc.player.networkHandler != null) {
                hiddenPartyListExpireTime = System.currentTimeMillis() + 3000;
                mc.player.networkHandler.sendChatCommand("party list");
            }
        });
    }

    public boolean onChatMessage(String text) {
        String trimmed = COLOR_CODE_PATTERN.matcher(text).replaceAll("").trim();
        String lowerTrimmed = trimmed.toLowerCase();

        String matchedKeyword = null;
        int keywordIndex = -1;
        for (String keyword : EVENT_KEYWORDS) {
            int index = lowerTrimmed.indexOf(keyword);
            if (index != -1) {
                matchedKeyword = keyword;
                keywordIndex = index;
                break;
            }
        }

        if (keywordIndex == -1) return false;

        // Forgery prevention: if there's a colon before the keyword, it's a player message
        int colonIndex = trimmed.indexOf(":");
        if (colonIndex != -1 && colonIndex < keywordIndex) {
            return false;
        }

        boolean notInPartyMsg = "you must be in a party".equals(matchedKeyword);

        boolean shouldHide = false;
        long now = System.currentTimeMillis();
        if (now < hiddenPartyListExpireTime) {
            if ("party members:".equals(matchedKeyword) || notInPartyMsg) {
                shouldHide = true;
                hiddenPartyListExpireTime = 0; // consume expectation
            }
        }

        if (notInPartyMsg) {
            inParty = false;
            lastPartyListMembers.clear();
            knownInGameMembers.clear();
            PartyFinderMod.LOGGER.info("Detected player is not in a party.");
            return shouldHide;
        }

        inParty = true;

        // Handle /party list parsing
        if ("party members:".equals(matchedKeyword)) {
            lastPartyListMembers.clear();
            String membersStr = trimmed.substring(keywordIndex + "party members:".length()).trim();
            String[] parts = membersStr.split(",");
            for (String part : parts) {
                String cleanPart = part.replace("and", "").trim();
                String name = cleanPlayerName(cleanPart);
                if (!name.isEmpty()) {
                    lastPartyListMembers.add(name);
                }
            }
            onPartyListParsed();
            return shouldHide;
        }

        // Party join detection
        if ("has joined your party".equals(matchedKeyword)) {
            Matcher joinMatch = PARTY_JOIN_PATTERN.matcher(trimmed);
            if (joinMatch.find()) {
                PartyFinderMod.LOGGER.info("Detected party join message. Requesting /party list.");
                triggerPartyList();
                return shouldHide;
            }
        }

        // Party kick detection
        if ("has been kicked from the party".equals(matchedKeyword)) {
            Matcher kickMatch = PARTY_KICK_PATTERN.matcher(trimmed);
            if (kickMatch.find()) {
                PartyFinderMod.LOGGER.info("Detected party kick message. Requesting /party list.");
                triggerPartyList();
                return shouldHide;
            }
        }

        // Party leave detection
        if ("has left the party".equals(matchedKeyword)) {
            Matcher leaveMatch = PARTY_LEAVE_PATTERN.matcher(trimmed);
            if (leaveMatch.find()) {
                PartyFinderMod.LOGGER.info("Detected party leave message. Requesting /party list.");
                triggerPartyList();
                return shouldHide;
            }
        }

        return shouldHide;
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

        // Auto-remove members who are no longer in the in-game party
        java.util.List<String> toRemove = new java.util.ArrayList<>();
        for (String name : knownInGameMembers) {
            boolean stillInParty = false;
            for (String m : lastPartyListMembers) {
                if (m.equalsIgnoreCase(name)) {
                    stillInParty = true;
                    break;
                }
            }
            if (mc.player != null) {
                String selfName = mc.player.getName().getString();
                if (name.equalsIgnoreCase(selfName)) {
                    stillInParty = true;
                }
            }
            if (!stillInParty) {
                toRemove.add(name);
            }
        }

        for (String name : toRemove) {
            PartyFinderMod.LOGGER.info("Auto-kicking member no longer in party: {}", name);
            apiClient.kickMember(trackedPartyId, name).thenAccept(resp -> {
                if (resp.ok) {
                    knownInGameMembers.remove(name.toLowerCase());
                }
            });
        }
    }
}
