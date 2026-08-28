package info.avicia.avoutils.features.partyfinder.handler;

import info.avicia.avoutils.AvoUtilsMod;
import net.minecraft.client.MinecraftClient;

import info.avicia.avoutils.features.partyfinder.api.PartyData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Handles sending {@code /party invite <name>} commands in chat
 */
public class InviteHandler {

    private static final int TICKS_BETWEEN_INVITES = 15;

    private final PartyFinderPartySyncer partySyncer;
    private final Queue<String> inviteQueue = new ArrayDeque<>();
    private int cooldownTicks = 0;

    public InviteHandler(PartyFinderPartySyncer partySyncer) {
        this.partySyncer = partySyncer;
    }

    /**
     * Invite all players from a party who are not already in-game, not reserved, and not the current player
     */
    public List<String> inviteAll(PartyData party, String selfName) {
        List<String> names = new ArrayList<>();
        for (PartyData.MemberData member : party.members.values()) {
            if (member.name != null 
                    && !member.name.isEmpty() 
                    && !member.name.equalsIgnoreCase("<RESERVED>") 
                    && !member.name.equalsIgnoreCase(selfName)) {

                boolean alreadyInGame = false;
                for (String inGameName : partySyncer.getLastPartyListMembers()) {
                    if (inGameName.equalsIgnoreCase(member.name)) {
                        alreadyInGame = true;
                        break;
                    }
                }
                if (!alreadyInGame) {
                    names.add(member.name);
                }
            }
        }
        queueInvites(names);
        return names;
    }

    /**
     * Queue a list of player names to invite via /party invite
     */
    public void queueInvites(List<String> playerNames) {
        if (!partySyncer.isInParty() && !playerNames.isEmpty()) {
            inviteQueue.add("__CREATE__");
        }
        inviteQueue.addAll(playerNames);
        AvoUtilsMod.LOGGER.info("Queued {} party invites. Need party creation: {}", playerNames.size(), !partySyncer.isInParty());
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
