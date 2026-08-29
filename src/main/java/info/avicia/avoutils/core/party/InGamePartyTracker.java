package info.avicia.avoutils.core.party;

import info.avicia.avoutils.AvoUtilsMod;
import net.minecraft.client.MinecraftClient;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Single chat entry point for in-game party tracking. Parses party chat messages, keeps the
 * current in-game party member list, hides the bot-generated {@code /party list} output, and
 * dispatches parsed party lists to feature listeners.
 */
public class InGamePartyTracker {

    private static final long PARTY_LIST_HIDE_WINDOW_MS = 3000L;

    private static final InGamePartyTracker INSTANCE = new InGamePartyTracker();

    private final Set<String> lastPartyListMembers = new LinkedHashSet<>();
    private final List<Consumer<List<String>>> partyListListeners = new CopyOnWriteArrayList<>();

    private volatile long hiddenPartyListExpireTime = 0;
    private volatile boolean inParty = false;

    private InGamePartyTracker() {
    }

    public static InGamePartyTracker getInstance() {
        return INSTANCE;
    }

    /**
     * Process a chat message.
     *
     * @return true if the message should be hidden from the chat.
     */
    public boolean onChatMessage(String text) {
        PartyMessageParser.Result result = PartyMessageParser.parse(text);
        if (result == null) {
            return false;
        }

        boolean shouldHide = false;
        long now = System.currentTimeMillis();
        if (now < hiddenPartyListExpireTime) {
            if (result.event() == PartyMessageParser.Event.PARTY_LIST
                    || result.event() == PartyMessageParser.Event.NOT_IN_PARTY) {
                shouldHide = true;
                hiddenPartyListExpireTime = 0;
            }
        }

        switch (result.event()) {
            case NOT_IN_PARTY, KICKED_FROM_PARTY, LEFT_PARTY, DISBANDED -> {
                inParty = false;
                lastPartyListMembers.clear();
                return shouldHide;
            }
            case PARTY_LIST -> {
                inParty = true;
                lastPartyListMembers.clear();
                lastPartyListMembers.addAll(result.partyListMembers());
                notifyPartyListListeners();
                return shouldHide;
            }
            case JOIN, KICK, LEAVE -> {
                inParty = true;
                triggerPartyList();
                return shouldHide;
            }
        }
        return shouldHide;
    }

    public void triggerPartyList() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null && mc.player.networkHandler != null) {
                hiddenPartyListExpireTime = System.currentTimeMillis() + PARTY_LIST_HIDE_WINDOW_MS;
                mc.player.networkHandler.sendChatCommand("party list");
            }
        });
    }

    public void addPartyListListener(Consumer<List<String>> listener) {
        partyListListeners.add(listener);
    }

    public void removePartyListListener(Consumer<List<String>> listener) {
        partyListListeners.remove(listener);
    }

    /**
     * The members from the last parsed {@code /party list} output.
     */
    public Set<String> getLastPartyListMembers() {
        return Set.copyOf(lastPartyListMembers);
    }

    public boolean isInParty() {
        return inParty;
    }

    private void notifyPartyListListeners() {
        List<String> snapshot = List.copyOf(lastPartyListMembers);
        for (Consumer<List<String>> listener : partyListListeners) {
            try {
                listener.accept(snapshot);
            } catch (Exception e) {
                AvoUtilsMod.LOGGER.error("[InGamePartyTracker] Error in party-list listener", e);
            }
        }
    }
}
