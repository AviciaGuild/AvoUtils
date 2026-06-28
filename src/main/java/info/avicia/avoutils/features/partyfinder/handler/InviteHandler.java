package info.avicia.avoutils.features.partyfinder.handler;

import info.avicia.avoutils.AvoUtilsMod;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/**
 * Handles sending {@code /party invite <name>} commands in chat
 */
public class InviteHandler {

    private static final int TICKS_BETWEEN_INVITES = 15;

    private final ChatPartyDetector chatDetector;
    private final Queue<String> inviteQueue = new ArrayDeque<>();
    private int cooldownTicks = 0;

    public InviteHandler(ChatPartyDetector chatDetector) {
        this.chatDetector = chatDetector;
    }

    /**
     * Queue a list of player names to invite via /party invite
     */
    public void queueInvites(List<String> playerNames) {
        if (!chatDetector.isInParty() && !playerNames.isEmpty()) {
            inviteQueue.add("__CREATE__");
        }
        inviteQueue.addAll(playerNames);
        AvoUtilsMod.LOGGER.info("Queued {} party invites. Need party creation: {}", playerNames.size(), !chatDetector.isInParty());
    }

    /**
     * Called every client tick to process the invite queue
     */
    public void tick(MinecraftClient client) {
        if (inviteQueue.isEmpty()) return;
        if (client.player == null) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        String action = inviteQueue.poll();
        if (action != null) {
            String command;
            if (action.equals("__CREATE__")) {
                command = "party create";
            } else {
                command = "party invite " + action;
            }
            AvoUtilsMod.LOGGER.info("Sending: /{}", command);
            client.player.networkHandler.sendChatCommand(command);
            cooldownTicks = TICKS_BETWEEN_INVITES;
        }
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
