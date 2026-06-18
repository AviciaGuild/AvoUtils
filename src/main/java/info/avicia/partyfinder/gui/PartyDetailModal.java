package info.avicia.partyfinder.gui;

import info.avicia.partyfinder.api.PartyData;
import info.avicia.partyfinder.api.PartyFinderClient;
import info.avicia.partyfinder.handler.InviteHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modal overlay showing a party's details.
 */
public class PartyDetailModal extends Screen {

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

    public PartyDetailModal(PartyListScreen parent, PartyFinderClient apiClient,
                            InviteHandler inviteHandler,
                            PartyData party) {
        super(MinecraftClient.getInstance(), MinecraftClient.getInstance().textRenderer, Text.literal("Party Details"));
        this.parent = parent;
        this.apiClient = apiClient;
        this.inviteHandler = inviteHandler;
        this.party = party;
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
        modalH = Math.min(380, height - 40);
        modalX = (width - modalW) / 2;
        modalY = (height - modalH) / 2;

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
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), btn -> parent.closeModal())
                .dimensions(modalX + modalW - 60, modalY + 5, 50, 16)
                .build());

        if (isLeader) {
            // Leader controls
            addDrawableChild(ButtonWidget.builder(Text.literal("Invite All"), btn -> inviteAll())
                    .dimensions(btnX, btnY, 70, 20)
                    .build());
            btnX += 75;

            addDrawableChild(ButtonWidget.builder(Text.literal("Edit"), btn -> parent.openEditModal(party))
                    .dimensions(btnX, btnY, 50, 20)
                    .build());
            btnX += 55;

            addDrawableChild(ButtonWidget.builder(Text.literal("Reserve"), btn -> reserveSlot())
                    .dimensions(btnX, btnY, 60, 20)
                    .build());
            btnX += 65;

            addDrawableChild(ButtonWidget.builder(Text.literal("§cDisband"), btn -> confirmDisband())
                    .dimensions(btnX, btnY, 60, 20)
                    .build());
        } else if (isInParty) {
            // Member controls
            addDrawableChild(ButtonWidget.builder(Text.literal("Leave"), btn -> leaveParty())
                    .dimensions(btnX, btnY, 60, 20)
                    .build());

            // Role change buttons
            btnX += 65;
            for (String role : new String[]{"DPS", "Healer", "Tank", "Other"}) {
                final String r = role;
                addDrawableChild(ButtonWidget.builder(Text.literal(role), btn -> changeRole(r.toLowerCase()))
                        .dimensions(btnX, btnY, 50, 20)
                        .build());
                btnX += 55;
            }
        } else if (!party.isFull) {
            // Join buttons
            for (String role : new String[]{"DPS", "Healer", "Tank", "Other"}) {
                final String r = role;
                addDrawableChild(ButtonWidget.builder(Text.literal("Join as " + role), btn -> joinParty(r.toLowerCase()))
                        .dimensions(btnX, btnY, 75, 20)
                        .build());
                btnX += 80;
            }
        }
    }

    // ── Actions ──────────────────────────────────────────────────────────

    private void inviteAll() {
        List<String> names = new ArrayList<>();
        for (PartyData.MemberData member : party.members.values()) {
            if (!member.isReserved && !member.name.equalsIgnoreCase(playerName)) {
                names.add(member.name);
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

    private void refreshPartyState() {
        apiClient.listParties().thenAccept(list -> {
            MinecraftClient.getInstance().execute(() -> {
                boolean found = false;
                for (PartyData p : list) {
                    if (p.partyId == party.partyId) {
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
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    parent.closeModal();
                } else {
                    this.clearAndInit();
                }
            });
        });
    }

    private void setStatus(String msg, int color) {
        statusMessage = msg;
        statusColor = color;
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Modal background
        context.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xEE1A1A2E);

        // Border
        int borderColor = 0xFF5555AA;
        context.fill(modalX, modalY, modalX + modalW, modalY + 1, borderColor);
        context.fill(modalX, modalY + modalH - 1, modalX + modalW, modalY + modalH, borderColor);
        context.fill(modalX, modalY, modalX + 1, modalY + modalH, borderColor);
        context.fill(modalX + modalW - 1, modalY, modalX + modalW, modalY + modalH, borderColor);

        // Title
        String title = String.join(" / ", party.activities);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("§b§l" + title), width / 2, modalY + 8, 0xFFFFFFFF);

        int textX = modalX + 12;
        int textY = modalY + 28;

        // Leader
        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§7Leader: §f" + party.leaderName), textX, textY, 0xFFFFFFFF);
        textY += 12;

        // Region
        String region = party.region != null ? party.region : "Any";
        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§7Region: §f" + region), textX, textY, 0xFFFFFFFF);
        textY += 12;

        // Slots
        int slotsColor = party.isFull ? 0xFFFF5555 : 0xFF55FF55;
        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§7Slots: "), textX, textY, 0xFFFFFFFF);
        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal(party.memberCount + "/" + party.maxSize),
                textX + textRenderer.getWidth("Slots: "), textY, slotsColor);
        textY += 12;

        // Note
        if (party.note != null && !party.note.isEmpty()) {
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§7Note: §f" + party.note), textX, textY, 0xFFFFFFFF);
            textY += 12;
        }

        textY += 6;

        // Members header
        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§e§lMembers"), textX, textY, 0xFFFFFFFF);
        textY += 14;

        // Member list
        memberRows.clear();
        for (Map.Entry<String, PartyData.MemberData> entry : party.members.entrySet()) {
            PartyData.MemberData member = entry.getValue();
            String icon = member.roleIcon();
            String display = member.displayName();
            String line = icon + " " + display;

            int nameColor = member.isReserved ? 0xFF888888 : 0xFFFFFFFF;
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal(line), textX + 4, textY, nameColor);

            // Kick button for leaders (not self)
            if (isLeader && !member.name.equalsIgnoreCase(party.leaderName)) {
                int kickX = modalX + modalW - 40;
                boolean kickHovered = mouseX >= kickX && mouseX <= kickX + 30
                        && mouseY >= textY - 1 && mouseY <= textY + 10;
                int kickColor = kickHovered ? 0xFFFF5555 : 0xFFAA4444;
                CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§c[X]"), kickX, textY, kickColor);
            }

            memberRows.add(new MemberRow(entry.getKey(), textY));
            textY += 12;
        }

        // Status message
        if (statusMessage != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(statusMessage),
                    width / 2, modalY + modalH - 48, statusColor);
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
                int kickX = modalX + modalW - 40;
                if (!member.name.equalsIgnoreCase(party.leaderName)
                        && mouseX >= kickX && mouseX <= kickX + 30
                        && mouseY >= row.y - 1 && mouseY <= row.y + 10) {
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
}
