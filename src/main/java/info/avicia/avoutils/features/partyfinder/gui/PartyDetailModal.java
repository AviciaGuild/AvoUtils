package info.avicia.avoutils.features.partyfinder.gui;

import info.avicia.avoutils.features.partyfinder.api.PartyData;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.features.partyfinder.handler.InviteHandler;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.FlatButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.google.gson.JsonObject;

import info.avicia.avoutils.core.gui.ModalOverlay;

/**
 * Modal overlay showing a party's details
 */
public class PartyDetailModal extends Screen implements ModalOverlay {

    private final PartyListScreen parent;
    private final PartyFinderClient apiClient;
    private final InviteHandler inviteHandler;
    private final PartyData party;

    // Modal dimensions
    private int modalX, modalY, modalW, modalH;

    // Current player info
    private String playerName;
    private boolean isLeader = false;
    private boolean isInParty = false;

    private String statusMessage = null;
    private int statusColor = 0xFFFFFF;

    // Per-frame snapshot of each member row's Y position and associated key.
    private record MemberRow(String memberKey, int y) {}
    private final List<MemberRow> memberRows = new ArrayList<>();
 
    // Cached note wrapping layout calculations to avoid per-frame text wrapping overhead
    private List<net.minecraft.text.OrderedText> wrappedNote = null;
    private int noteLinesCount = 0;

    private final Consumer<JsonObject> partyUpdateListener;

    public PartyDetailModal(PartyListScreen parent, PartyFinderClient apiClient,
                            InviteHandler inviteHandler,
                            PartyData party) {
        super(MinecraftClient.getInstance(), MinecraftClient.getInstance().textRenderer, Text.literal("Party Details"));
        this.parent = parent;
        this.apiClient = apiClient;
        this.inviteHandler = inviteHandler;
        this.party = party;
        this.partyUpdateListener = json -> {
            if (json.has("party_id") && json.get("party_id").getAsLong() == this.party.partyId) {
                refreshPartyState();
            }
        };
        refreshPartyState();
    }

    public void initModal(MinecraftClient client, int width, int height) {
        this.width = width;
        this.height = height;
        this.init();
    }

    @Override
    protected void init() {
        modalW = Math.min(340, width - 40);
        modalH = Math.min(280, height - 40);
        modalX = (width - modalW) / 2;
        modalY = (height - modalH) / 2;

        // Register WebSocket listener
        AvoWebSocketManager.getInstance().unregisterListener("party_updated", partyUpdateListener);
        AvoWebSocketManager.getInstance().registerListener("party_updated", partyUpdateListener);

        // Determine player state
        MinecraftClient mc = MinecraftClient.getInstance();
        // Use session username as fallback
        playerName = mc.getSession().getUsername();

        // Check if player is in the party (by name)
        for (PartyData.MemberData member : party.members.values()) {
            if (member.name.equalsIgnoreCase(playerName)) {
                isInParty = true;
                if (member.name.equalsIgnoreCase(party.leaderName)) {
                    isLeader = true;
                }
                break;
            }
        }
        // Also check leader name directly
        if (playerName.equalsIgnoreCase(party.leaderName)) {
            isLeader = true;
            isInParty = true;
        }

        // Center the buttons at the bottom:
        int totalButtonsWidth = 0;
        if (isLeader) {
            totalButtonsWidth = 70 + 5 + 50 + 5 + 60 + 5 + 60; // Invite All (70) + Edit (50) + Reserve (60) + Disband (60)
        } else if (isInParty) {
            totalButtonsWidth = 60 + 5 + (50 * 4 + 15); // Leave (60) + 4 role buttons (50 each)
        } else if (!party.isFull) {
            totalButtonsWidth = 75 * 4 + 15; // 4 join buttons (75 each)
        }

        int btnY = modalY + modalH - 30;
        int btnX = modalX + (modalW - totalButtonsWidth) / 2;

        // Close button
        FlatButtonWidget closeBtn = new FlatButtonWidget(modalX + modalW - 16, modalY + 6, 12, 12, Text.literal("✕"), () -> parent.closeModal());
        closeBtn.setDanger(true);
        closeBtn.setBorderless(true);
        addDrawableChild(closeBtn);

        if (isLeader) {
            // Leader controls
            addDrawableChild(new FlatButtonWidget(btnX, btnY, 70, 20, Text.literal("Invite All"), () -> inviteAll()));
            btnX += 75;

            addDrawableChild(new FlatButtonWidget(btnX, btnY, 50, 20, Text.literal("Edit"), () -> parent.openEditModal(party)));
            btnX += 55;

            addDrawableChild(new FlatButtonWidget(btnX, btnY, 60, 20, Text.literal("Reserve"), () -> reserveSlot()));
            btnX += 65;

            FlatButtonWidget disbandBtn = new FlatButtonWidget(btnX, btnY, 60, 20, Text.literal("Disband"), () -> confirmDisband());
            disbandBtn.setDanger(true);
            addDrawableChild(disbandBtn);
        } else if (isInParty) {
            // Member controls
            FlatButtonWidget leaveBtn = new FlatButtonWidget(btnX, btnY, 60, 20, Text.literal("Leave"), () -> leaveParty());
            leaveBtn.setDanger(true);
            addDrawableChild(leaveBtn);

            // Role change buttons
            btnX += 65;
            for (String role : new String[]{"DPS", "Healer", "Tank", "Other"}) {
                final String r = role;
                addDrawableChild(new FlatButtonWidget(btnX, btnY, 50, 20, Text.literal(role), () -> changeRole(r.toLowerCase())));
                btnX += 55;
            }
        } else if (!party.isFull) {
            // Join buttons
            for (String role : new String[]{"DPS", "Healer", "Tank", "Other"}) {
                final String r = role;
                addDrawableChild(new FlatButtonWidget(btnX, btnY, 75, 20, Text.literal("Join as " + role), () -> joinParty(r.toLowerCase())));
                btnX += 80;
            }
        }
        updateWrappedNote();
    }
 
    private void updateWrappedNote() {
        if (party.note != null && !party.note.isEmpty()) {
            wrappedNote = textRenderer.wrapLines(Text.literal("§7Note: §8§o" + party.note), modalW - 24);
            noteLinesCount = wrappedNote.size();
        } else {
            wrappedNote = null;
            noteLinesCount = 0;
        }
    }

    // ── Actions ──────────────────────────────────────────────────────────

    private void inviteAll() {
        List<String> names = new ArrayList<>();
        for (PartyData.MemberData member : party.members.values()) {
            if (member.name != null 
                    && !member.name.isEmpty() 
                    && !member.name.equalsIgnoreCase("<RESERVED>") 
                    && !member.name.equalsIgnoreCase(playerName)) {

                boolean alreadyInGame = false;
                for (String inGameName : parent.getChatDetector().getLastPartyListMembers()) {
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
        inviteHandler.queueInvites(names);
        setStatus("Inviting " + names.size() + " players...", 0x55FF55);
    }

    private void reserveSlot() {
        apiClient.reserveSlot(party.partyId, null, null).thenAccept(resp -> {
            MinecraftClient.getInstance().execute(() -> {
                if (resp.ok) {
                    setStatus("Slot reserved!", 0xFF55FF55);
                    refreshPartyState();
                } else {
                    setStatus(resp.error != null ? resp.error : "Failed.", 0xFFFF5555);
                }
            });
        });
    }

    private void confirmDisband() {
        apiClient.disbandParty(party.partyId).thenAccept(resp -> {
            MinecraftClient.getInstance().execute(() -> {
                if (resp.ok) {
                    parent.closeModal();
                } else {
                    setStatus(resp.error != null ? resp.error : "Failed to disband.", 0xFFFF5555);
                }
            });
        });
    }

    private void leaveParty() {
        apiClient.leaveParty(party.partyId).thenAccept(resp -> {
            MinecraftClient.getInstance().execute(() -> {
                if (resp.ok) {
                    parent.closeModal();
                } else {
                    setStatus(resp.error != null ? resp.error : "Failed.", 0xFFFF5555);
                }
            });
        });
    }

    private void joinParty(String role) {
        apiClient.joinParty(party.partyId, role).thenAccept(resp -> {
            MinecraftClient.getInstance().execute(() -> {
                if (resp.ok) {
                    setStatus(resp.message != null ? resp.message : "Joined!", 0xFF55FF55);
                    refreshPartyState();
                } else {
                    setStatus(resp.error != null ? resp.error : "Failed to join.", 0xFFFF5555);
                }
            });
        });
    }

    private void changeRole(String role) {
        apiClient.joinParty(party.partyId, role).thenAccept(resp -> {
            MinecraftClient.getInstance().execute(() -> {
                if (resp.ok) {
                    setStatus(resp.message != null ? resp.message : "Role changed!", 0xFF55FF55);
                    refreshPartyState();
                } else {
                    setStatus(resp.error != null ? resp.error : "Failed.", 0xFFFF5555);
                }
            });
        });
    }

    public void refreshPartyState() {
        apiClient.getParty(party.partyId).thenAccept(p -> {
            MinecraftClient.getInstance().execute(() -> {
                this.party.memberCount = p.memberCount;
                this.party.maxSize = p.maxSize;
                this.party.isFull = p.isFull;
                this.party.members.clear();
                this.party.members.putAll(p.members);
                this.party.region = p.region;
                this.party.note = p.note;
                this.party.activities = p.activities;
                this.party.leaderName = p.leaderName;
                this.party.leaderRole = p.leaderRole;
                this.party.creatorId = p.creatorId;
                this.clearAndInit();
            });
        }).exceptionally(ex -> {
            MinecraftClient.getInstance().execute(() -> {
                parent.closeModal();
            });
            return null;
        });
    }

    public void onCloseModal() {
        AvoWebSocketManager.getInstance().unregisterListener("party_updated", partyUpdateListener);
    }

    private void setStatus(String msg, int color) {
        statusMessage = msg;
        statusColor = color;
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CompatibilityHelper.drawModalFrame(context, modalX, modalY, modalW, modalH);

        // Title
        String title = String.join(" / ", party.activities);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("§b§l" + title), width / 2, modalY + 8, 0xFFFFFFFF);

        int textX = modalX + 12;
        int metaY = modalY + 30;

        // Metadata panel card (leader, region, note) - uses pre-wrapped fields cached in init()

        int cardH = 20 + (noteLinesCount > 0 ? noteLinesCount * 12 : 0);

        // Panel background
        context.fill(modalX + 8, metaY, modalX + modalW - 8, metaY + cardH, 0x1A8A9CFE);

        // Panel borders
        int metaBorder = 0x308A9CFE;
        CompatibilityHelper.drawBorder(context, modalX + 8, metaY, modalW - 16, cardH, metaBorder);

        // Leader and region
        String region = party.region != null ? party.region : "Any";
        String detailsStr = "§7Leader: §f" + party.leaderName + "   §8|   §7Region: §f" + region;
        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal(detailsStr), textX + 4, metaY + 6, 0xFFFFFFFF);

        // Note
        if (wrappedNote != null) {
            int noteY = metaY + 18;
            for (net.minecraft.text.OrderedText line : wrappedNote) {
                context.drawText(textRenderer, line, textX + 4, noteY, 0xFFFFFFFF, true);
                noteY += 12;
            }
        }

        // Members section
        int membersHeaderY = metaY + cardH + 15;
        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§b§lMembers"), textX, membersHeaderY, 0xFFFFFFFF);

        // Slots counter
        String slotsText = party.memberCount + "/" + party.maxSize;
        String slotsColorCode = party.isFull ? "§c" : "§a";
        int slotsW = textRenderer.getWidth(slotsText);
        int slotsX = modalX + modalW - 12 - slotsW;
        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal(slotsColorCode + slotsText), slotsX, membersHeaderY, 0xFFFFFFFF);

        // Header separator line
        context.fill(modalX + 8, membersHeaderY + 12, modalX + modalW - 8, membersHeaderY + 13, 0x20FFFFFF);

        // Render member rows
        int textY = membersHeaderY + 18;
        memberRows.clear();

        List<Map.Entry<String, PartyData.MemberData>> sortedMembers = new ArrayList<>(party.members.entrySet());
        for (int i = 0; i < sortedMembers.size(); i++) {
            Map.Entry<String, PartyData.MemberData> entry = sortedMembers.get(i);
            PartyData.MemberData member = entry.getValue();

            // Row background
            int rowBg = (i % 2 == 0) ? 0x10FFFFFF : 0x05FFFFFF;
            context.fill(modalX + 8, textY - 2, modalX + modalW - 8, textY + 12, rowBg);

            // Icon color
            String prefix = member.getStyledRolePrefix();
            String display = member.displayName();
            String styledLine = prefix + " " + (member.isReserved ? "§7" : "§f") + display;

            int nameColor = member.isReserved ? 0xFF888888 : 0xFFFFFFFF;
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal(styledLine), textX + 4, textY + 1, nameColor);

            // Kick button for leaders
            if (isLeader && !member.name.equalsIgnoreCase(party.leaderName)) {
                int kickX = modalX + modalW - 24;
                boolean kickHovered = isKickButtonHovered(mouseX, mouseY, kickX, textY);
                if (kickHovered) {
                    context.fill(kickX - 4, textY - 1, kickX + 10, textY + 11, 0x40FF4D4D);
                }
                int kickColor = kickHovered ? 0xFFFF4D4D : 0xFFAA4444;
                CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("✕"), kickX, textY + 1, kickColor);
            }

            memberRows.add(new MemberRow(entry.getKey(), textY));
            textY += 16;
        }

        // Status message
        if (statusMessage != null) {
            List<net.minecraft.text.OrderedText> wrappedStatus = textRenderer.wrapLines(Text.literal(statusMessage), modalW - 24);
            int statusBottomY = modalY + modalH - 34;
            int statusStartY = statusBottomY - (wrappedStatus.size() * 10 - 2);
            int currentY = statusStartY;
            for (net.minecraft.text.OrderedText line : wrappedStatus) {
                context.drawText(textRenderer, line, modalX + (modalW - textRenderer.getWidth(line)) / 2, currentY, statusColor, true);
                currentY += 10;
            }
        }

        // Render buttons
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean boolean_arg) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Check kick clicks for leader
        if (isLeader && button == 0) {
            for (MemberRow row : memberRows) {
                PartyData.MemberData member = party.members.get(row.memberKey);
                if (member == null) continue;
                int kickX = modalX + modalW - 24;
                if (!member.name.equalsIgnoreCase(party.leaderName)
                        && isKickButtonHovered(mouseX, mouseY, kickX, row.y)) {
                    apiClient.kickMember(party.partyId, member.name).thenAccept(resp -> {
                        MinecraftClient.getInstance().execute(() -> {
                            if (resp.ok) {
                                setStatus("Kicked " + member.name, 0x55FF55);
                                refreshPartyState();
                            } else {
                                setStatus(resp.error != null ? resp.error : "Failed.", 0xFF5555);
                            }
                        });
                    });
                    return true;
                }
            }
        }

        // Check if click is outside modal (close it)
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            parent.closeModal();
            return true;
        }

        return super.mouseClicked(click, boolean_arg);
    }

    private boolean isKickButtonHovered(double mouseX, double mouseY, int kickX, int rowY) {
        return mouseX >= kickX - 4 && mouseX <= kickX + 10
                && mouseY >= rowY - 2 && mouseY <= rowY + 12;
    }
}
