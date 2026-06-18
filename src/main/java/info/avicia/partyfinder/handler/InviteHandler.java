package info.avicia.partyfinder.handler;

import info.avicia.partyfinder.PartyFinderMod;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/**
 * Handles sending {@code /party invite <name>} commands in chat
 */
public class InviteHandler {

    private static final int TICKS_BETWEEN_INVITES = 15;

    private final Queue<String> inviteQueue = new ArrayDeque<>();
    private int cooldownTicks = 0;

    /**
     * Queue a list of player names to invite via /party invite
     */
    public void queueInvites(List<String> playerNames) {
        inviteQueue.addAll(playerNames);
        PartyFinderMod.LOGGER.info("Queued {} party invites.", playerNames.size());
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

        String playerName = inviteQueue.poll();
        if (playerName != null) {
            String command = "party invite " + playerName;
            PartyFinderMod.LOGGER.info("Sending: /{}", command);
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
