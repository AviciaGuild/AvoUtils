package info.avicia.avoutils.features.anniparty;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import net.minecraft.client.MinecraftClient;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Listens to local party chat to keep the Anni party checkboxes in sync.
 * <p>
 * When the local player is the {@code is_leader} of one of the Anni parties in the
 * cached roster, this detector parses {@code /party list} and join/leave messages and
 * emits {@code mod_party_update} with the full authoritative {@code in_party} list.
 */
public class AnniPartyDetector {

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
            "you must be in a party",
            "you have been kicked from your party",
            "you have left your current party",
            "your party has been disbanded"
    };
    private static final long PARTY_LIST_POLL_INTERVAL_MS = 30_000L;

    private final Set<String> lastPartyListMembers = new LinkedHashSet<>();
    private final Set<String> inGameSeenMembers = new LinkedHashSet<>();
    private volatile long hiddenPartyListExpireTime = 0;
    private volatile boolean queuedUpdate = false;
    private long lastAutoPollTime = 0;

    /**
     * Called with the latest roster from the WebSocket; the detector only acts
     * while the local player is the leader of one of the Anni parties.
     *
     * @return the party the local player leads, or null.
     */
    public AnniPartyData onRosterUpdated(AnniRoster roster) {
        if (roster == null || !roster.active) {
            return null;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        String selfName = mc.getSession() != null ? mc.getSession().getUsername() : null;
        if (selfName == null) {
            return null;
        }
        AnniPartyData ledParty = roster.findLedParty(selfName);
        if (ledParty != null && queuedUpdate) {
            queuedUpdate = false;
            sendPartyUpdate(ledParty);
        }
        return ledParty;
    }

    /**
     * Called from the chat mixin for every chat message.
     *
     * @return true if the message should be hidden from the chat.
     */
    public boolean onChatMessage(String text) {
        if (text == null) {
            return false;
        }
        if (!text.toLowerCase(java.util.Locale.ROOT).contains("party")) {
            return false;
        }

        String trimmed = COLOR_CODE_PATTERN.matcher(text).replaceAll("").trim();
        String lowerTrimmed = trimmed.toLowerCase(java.util.Locale.ROOT);

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

        if (keywordIndex == -1) {
            return false;
        }

        // Forgery prevention: if there's a colon before the keyword, it's a player message
        int colonIndex = trimmed.indexOf(":");
        if (colonIndex != -1 && colonIndex < keywordIndex) {
            return false;
        }

        boolean notInPartyMsg = "you must be in a party".equals(matchedKeyword);
        boolean isExitMsg = notInPartyMsg
                || "you have been kicked from your party".equals(matchedKeyword)
                || "you have left your current party".equals(matchedKeyword)
                || "your party has been disbanded".equals(matchedKeyword);

        boolean shouldHide = false;
        long now = System.currentTimeMillis();
        if (now < hiddenPartyListExpireTime) {
            if ("party members:".equals(matchedKeyword) || notInPartyMsg) {
                shouldHide = true;
                hiddenPartyListExpireTime = 0; // consume expectation
            }
        }

        if (isExitMsg) {
            lastPartyListMembers.clear();
            inGameSeenMembers.clear();
            return shouldHide;
        }

        // Handle /party list parsing
        if ("party members:".equals(matchedKeyword)) {
            lastPartyListMembers.clear();
            String membersStr = trimmed.substring(keywordIndex + "party members:".length()).trim();
            membersStr = membersStr.replace(" and ", ",");
            String[] parts = membersStr.split(" *, *");
            for (String part : parts) {
                String name = cleanPlayerName(part);
                if (!name.isEmpty()) {
                    lastPartyListMembers.add(name);
                }
            }
            onPartyListParsed();
            return shouldHide;
        }

        // Party event detection (join, kick, leave)
        if (("has joined your party".equals(matchedKeyword) && PARTY_JOIN_PATTERN.matcher(trimmed).find())
                || ("has been kicked from the party".equals(matchedKeyword) && PARTY_KICK_PATTERN.matcher(trimmed).find())
                || ("has left the party".equals(matchedKeyword) && PARTY_LEAVE_PATTERN.matcher(trimmed).find())) {

            triggerPartyList();
            return shouldHide;
        }

        return shouldHide;
    }

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

    public void onClientTick() {
        AnniPartyFeature feature = AvoUtilsMod.getInstance().getFeature(AnniPartyFeature.class);
        if (feature == null) {
            return;
        }
        if (feature.getLedParty() == null) {
            return; // only the leader of an active Anni party polls
        }
        long now = System.currentTimeMillis();
        if (now - lastAutoPollTime >= PARTY_LIST_POLL_INTERVAL_MS) {
            lastAutoPollTime = now;
            triggerPartyList();
        }
    }

    private void onPartyListParsed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) {
            return;
        }
        String selfName = mc.getSession().getUsername();

        // Track who we have seen in the in-game party
        for (String name : lastPartyListMembers) {
            if (!name.equalsIgnoreCase(selfName)) {
                inGameSeenMembers.add(name);
            }
        }
        Set<String> toRemove = new LinkedHashSet<>();
        for (String name : inGameSeenMembers) {
            boolean stillInParty = lastPartyListMembers.stream().anyMatch(m -> m.equalsIgnoreCase(name));
            if (!stillInParty) {
                toRemove.add(name);
            }
        }
        inGameSeenMembers.removeAll(toRemove);

        // Only send an update when we actually lead an Anni party
        AnniPartyFeature feature = AvoUtilsMod.getInstance().getFeature(AnniPartyFeature.class);
        if (feature == null) {
            return;
        }
        AnniPartyData ledParty = feature.getLedParty();
        if (ledParty == null) {
            queuedUpdate = false;
            return;
        }

        sendPartyUpdate(ledParty);
    }

    private void sendPartyUpdate(AnniPartyData ledParty) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String selfName = mc.getSession() != null ? mc.getSession().getUsername() : null;
        if (selfName == null) {
            return;
        }

        JsonArray inParty = new JsonArray();
        inParty.add(selfName);
        for (String member : inGameSeenMembers) {
            inParty.add(member);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("party_id", ledParty.party_id);
        payload.add("in_party", inParty);

        AvoWebSocketManager.getInstance().sendEvent("mod_party_update", payload);
    }
}
