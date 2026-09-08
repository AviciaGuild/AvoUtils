package info.avicia.avoutils.core.party;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.PlayerUtil;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Queues in-game party invitations ({@code /party invite <name>}) and sends them with a cooldown.
 */
public class InviteHandler {

    private static final int TICKS_BETWEEN_INVITES = 15;

    private final Queue<String> inviteQueue = new ArrayDeque<>();
    private int cooldownTicks = 0;

    public InviteHandler() {
    }

    /**
     * Build an invite list from member names, skipping reserved/empty/self entries and anyone
     * already present in {@code alreadyInGameNames}, then queue the invites.
     *
     * @return the names that were queued.
     */
    public List<String> inviteAll(Iterable<String> memberNames, String selfName, Set<String> alreadyInGameNames) {
        List<String> names = new ArrayList<>();
        for (String name : memberNames) {
            if (name == null || name.isEmpty() || name.equalsIgnoreCase("<RESERVED>")) {
                continue;
            }
            if (PlayerUtil.isSelf(name) || PlayerUtil.namesEqual(name, selfName)) {
                continue;
            }
            boolean alreadyInGame = false;
            if (alreadyInGameNames != null) {
                for (String inGameName : alreadyInGameNames) {
                    if (PlayerUtil.namesEqual(inGameName, name)) {
                        alreadyInGame = true;
                        break;
                    }
                }
            }
            if (!alreadyInGame) {
                names.add(name);
            }
        }
        queueInvites(names);
        return names;
    }

    /**
     * Queue a list of player names to invite via /party invite.
     */
    public void queueInvites(List<String> playerNames) {
        if (!InGamePartyTracker.getInstance().isInParty() && !playerNames.isEmpty()) {
            inviteQueue.add("__CREATE__");
        }
        inviteQueue.addAll(playerNames);
        AvoUtilsMod.LOGGER.info("[AvoUtils] Queued {} party invites. Need party creation: {}",
                playerNames.size(), !InGamePartyTracker.getInstance().isInParty());
    }

    /**
     * Called every client tick to process the invite queue.
     */
    public void tick(MinecraftClient client) {
        if (inviteQueue.isEmpty()) return;
        if (client.player == null || client.player.networkHandler == null) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        String action = inviteQueue.poll();
        if (action != null) {
            String command = commandFor(action);
            AvoUtilsMod.LOGGER.info("[AvoUtils] Sending: /{}", command);
            client.player.networkHandler.sendChatCommand(command);
            cooldownTicks = TICKS_BETWEEN_INVITES;
        }
    }

    /**
     * Builds the chat command for a queued action.
     */
    static String commandFor(String action) {
        return "__CREATE__".equals(action) ? "party create" : "party invite " + action;
    }

    public void clear() {
        inviteQueue.clear();
    }

    public boolean hasPending() {
        return !inviteQueue.isEmpty();
    }

    public int pendingCount() {
        return inviteQueue.size();
    }
}
