package info.avicia.avoutils.features.partyfinder.gui;

import info.avicia.avoutils.features.partyfinder.api.ApiResponse;
import info.avicia.avoutils.features.partyfinder.api.PartyData;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.features.partyfinder.RoleIconUtil;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.party.InGamePartyTracker;
import info.avicia.avoutils.core.party.InviteHandler;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.FlatButtonWidget;
import info.avicia.avoutils.core.gui.ScrollViewport;
import info.avicia.avoutils.core.gui.UiIcons;
import info.avicia.avoutils.core.gui.UiStyle;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;

import info.avicia.avoutils.core.gui.ModalOverlay;
import info.avicia.avoutils.core.util.ClientThread;

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

    // Scrolling body
    private final ScrollViewport bodyViewport = new ScrollViewport();

    // Current player info
    private boolean isLeader = false;

    private String statusMessage = null;
    private int statusColor = 0xFFFFFF;

    // Per-frame snapshot of each member row's Y position and associated key.
    private record MemberRow(String memberKey, int y) {}
    private final List<MemberRow> memberRows = new ArrayList<>();
 
    // Cached note wrapping layout calculations to avoid per-frame text wrapping overhead
    private List<FormattedCharSequence> wrappedNote = null;
    private int noteLinesCount = 0;
    private List<FormattedCharSequence> wrappedStatus = null;

    private final Consumer<JsonObject> partyUpdateListener;
    private boolean closed = false;

    public PartyDetailModal(PartyListScreen parent, PartyFinderClient apiClient,
                            InviteHandler inviteHandler,
                            PartyData party) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal("Party Details"));
        this.parent = parent;
        this.apiClient = apiClient;
        this.inviteHandler = inviteHandler;
        this.party = party;
        this.partyUpdateListener = json -> {
            if (!closed && json.has("party_id") && json.get("party_id").getAsLong() == this.party.partyId) {
                refreshPartyState();
            }
        };
        AvoWebSocketManager.getInstance().registerListener("party_updated", partyUpdateListener);
        refreshPartyState();
    }

    @Override
    public void initModal(Minecraft client, int width, int height) {
        this.width = width;
        this.height = height;
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        modalW = Math.min(340, width - 40);
        modalH = Math.min(280, height - 40);
        modalX = (width - modalW) / 2;
        modalY = (height - modalH) / 2;

        // Determine player state
        isLeader = PlayerUtil.isSelf(party.leaderName);
        boolean isInParty = isLeader || (party.members != null
                && party.members.values().stream().anyMatch(m -> m != null && PlayerUtil.isSelf(m.name)));

        // Center the buttons at the bottom:
        int totalButtonsWidth = 0;
        if (isLeader) {
            totalButtonsWidth = 70 + 5 + 50 + 5 + 60 + 5 + 60; // Invite All (70) + Edit (50) + Reserve (60) + Disband (60)
        } else if (isInParty) {
            totalButtonsWidth = 60 + 5 + (60 * 4 + 15); // Leave (60) + 4 role buttons (60 each)
        } else if (!party.isFull) {
            totalButtonsWidth = 60 * 4 + 15; // 4 join buttons (60 each)
        }

        int btnY = modalY + modalH - 30;
        int btnX = modalX + (modalW - totalButtonsWidth) / 2;

        // Close button
        FlatButtonWidget closeBtn = new FlatButtonWidget(modalX + modalW - 16, modalY + 6, 12, 12, Component.empty(), () -> parent.closeModal()).withIcon(UiIcons.CROSS);
        closeBtn.setDanger(true);
        closeBtn.setBorderless(true);
        addRenderableWidget(closeBtn);

        if (isLeader) {
            // Leader controls
            addRenderableWidget(new FlatButtonWidget(btnX, btnY, 70, 20, Component.literal("Invite All"), () -> inviteAll()));
            btnX += 75;

            addRenderableWidget(new FlatButtonWidget(btnX, btnY, 50, 20, Component.literal("Edit"), () -> parent.openEditModal(party)));
            btnX += 55;

            addRenderableWidget(new FlatButtonWidget(btnX, btnY, 60, 20, Component.literal("Reserve"), () -> reserveSlot()));
            btnX += 65;

            FlatButtonWidget disbandBtn = new FlatButtonWidget(btnX, btnY, 60, 20, Component.literal("Disband"), () -> disbandParty());
            disbandBtn.setDanger(true);
            addRenderableWidget(disbandBtn);
        } else if (isInParty) {
            // Member controls
            FlatButtonWidget leaveBtn = new FlatButtonWidget(btnX, btnY, 60, 20, Component.literal("Leave"), () -> leaveParty());
            leaveBtn.setDanger(true);
            addRenderableWidget(leaveBtn);

            // Role change buttons
            btnX += 65;
            addRoleButtons(btnX, btnY, this::changeRole);
        } else if (!party.isFull) {
            // Join buttons
            addRoleButtons(btnX, btnY, this::joinParty);
        }
        updateWrappedNote();
        updateWrappedStatus();
    }

    private void addRoleButtons(int startX, int btnY, Consumer<String> onSelect) {
        int btnX = startX;
        for (int i = 0; i < RoleIconUtil.ROLE_LABELS.length; i++) {
            String label = RoleIconUtil.ROLE_LABELS[i];
            String roleValue = RoleIconUtil.ROLE_VALUES[i];
            String icon = RoleIconUtil.getStyledRolePrefix(roleValue);
            addRenderableWidget(new FlatButtonWidget(btnX, btnY, 60, 20,
                    Component.literal(icon + " " + label), () -> onSelect.accept(roleValue)));
            btnX += 65;
        }
    }

    private void updateWrappedStatus() {
        if (statusMessage != null && font != null) {
            wrappedStatus = font.split(Component.literal(statusMessage), modalW - 24);
        } else {
            wrappedStatus = null;
        }
    }

    private void updateWrappedNote() {
        if (party.note != null && !party.note.isEmpty()) {
            wrappedNote = font.split(Component.literal("§7Note: §8§o" + party.note), modalW - 24);
            noteLinesCount = wrappedNote.size();
        } else {
            wrappedNote = null;
            noteLinesCount = 0;
        }
    }

    // ── Actions ──────────────────────────────────────────────────────────

    private void runAction(CompletableFuture<ApiResponse> future, String fallbackError, Consumer<ApiResponse> onSuccess) {
        future.thenAccept(resp -> ClientThread.run(() -> {
            if (closed) {
                return;
            }
            if (resp.ok) {
                onSuccess.accept(resp);
            } else {
                setStatus(resp.error != null ? resp.error : fallbackError, 0xFFFF5555);
            }
        })).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            ClientThread.run(() -> {
                if (!closed) {
                    setStatus("Network error: " + cause.getMessage(), 0xFFFF5555);
                }
            });
            return null;
        });
    }

    private void inviteAll() {
        List<String> memberNames = party.memberNames();
        if (memberNames.isEmpty()) return;
        List<String> names = inviteHandler.inviteAll(memberNames, PlayerUtil.selfName(),
                InGamePartyTracker.getInstance().getLastPartyListMembers());
        setStatus("Inviting " + names.size() + " players...", 0xFF55FF55);
    }

    private void reserveSlot() {
        runAction(apiClient.reserveSlot(party.partyId, null, null), "Failed.", resp -> {
            setStatus("Slot reserved!", 0xFF55FF55);
            refreshPartyState();
        });
    }

    private void disbandParty() {
        runAction(apiClient.disbandParty(party.partyId), "Failed to disband.", resp -> parent.closeModal());
    }

    private void leaveParty() {
        runAction(apiClient.leaveParty(party.partyId), "Failed.", resp -> parent.closeModal());
    }

    private void joinParty(String role) {
        runAction(apiClient.joinParty(party.partyId, role), "Failed to join.", resp -> {
            setStatus(resp.message != null ? resp.message : "Joined!", 0xFF55FF55);
            refreshPartyState();
        });
    }

    private void changeRole(String role) {
        runAction(apiClient.joinParty(party.partyId, role), "Failed.", resp -> {
            setStatus(resp.message != null ? resp.message : "Role changed!", 0xFF55FF55);
            refreshPartyState();
        });
    }

    public void refreshPartyState() {
        apiClient.getParty(party.partyId).thenAccept(p -> {
            if (p == null) {
                return;
            }
            ClientThread.run(() -> {
                if (closed) {
                    return;
                }
                this.party.copyFrom(p);
                this.rebuildWidgets();
            });
        }).exceptionally(ex -> {
            ClientThread.run(() -> {
                if (!closed) {
                    parent.closeModal();
                }
            });
            return null;
        });
    }

    @Override
    public void onCloseModal() {
        closed = true;
        AvoWebSocketManager.getInstance().unregisterListener("party_updated", partyUpdateListener);
    }

    private void setStatus(String msg, int color) {
        statusMessage = msg;
        statusColor = (color & 0xFF000000) == 0 ? (color | 0xFF000000) : color;
        updateWrappedStatus();
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        CompatibilityHelper.drawModalFrame(context, modalX, modalY, modalW, modalH);

        // Title
        context.drawCenteredString(font, Component.literal("§b§l" + party.activitiesLabel()), width / 2, modalY + 8, 0xFFFFFFFF);

        // Metadata panel card (leader, region, note)
        int topPadding = 8;
        int bottomPadding = 8;
        int noteGap = 6;
        int lineH = 10;
        int cardH = topPadding + lineH + bottomPadding;
        if (noteLinesCount > 0) {
            cardH += noteGap + (noteLinesCount * 12);
        }

        List<Map.Entry<String, PartyData.MemberData>> sortedMembers = party.members != null
                ? new ArrayList<>(party.members.entrySet())
                : List.of();
        int listH = sortedMembers.isEmpty() ? 16 : sortedMembers.size() * 18 - 2;

        // Fit the body between the header and the status text / action buttons; it scrolls when a
        // long note and a full party don't fit in a clamped modal
        if (statusMessage != null && wrappedStatus == null && font != null) {
            updateWrappedStatus();
        }
        int bodyTop = modalY + 25;
        int bodyBottom = hasWrappedStatus() ? statusTopY() - 2 : modalY + modalH - 34;
        int bodyContentBottom = modalY + 30 + cardH + 15 + 14 + listH;
        bodyViewport.update(bodyTop, bodyBottom, bodyContentBottom - bodyTop);

        int textX = modalX + 12;
        int metaY = modalY + 30 - bodyViewport.getOffset();
        bodyViewport.enableScissor(context, modalX + 1, modalX + modalW - 1);

        // Panel background
        context.fill(modalX + 8, metaY, modalX + modalW - 8, metaY + cardH, UiStyle.CARD_BACKGROUND);

        // Left accent bar
        int accentColor = party.isFull ? UiStyle.ACCENT_RED : UiStyle.ACCENT_GREEN;
        context.fill(modalX + 8, metaY + 1, modalX + 10, metaY + cardH - 1, accentColor);

        // Leader and region
        String region = party.region != null ? party.region : "Any";
        String detailsStr = "§7Leader: §f" + party.leaderName + "   §8|   §7Region: §f" + region;
        CompatibilityHelper.drawTextWithShadow(context, font, Component.literal(detailsStr), modalX + 16, metaY + topPadding, 0xFFFFFFFF);

        // Note
        if (wrappedNote != null) {
            int noteY = metaY + topPadding + lineH + noteGap;
            for (FormattedCharSequence line : wrappedNote) {
                context.drawString(font, line, modalX + 16, noteY, 0xFFFFFFFF, true);
                noteY += 12;
            }
        }

        // Members section
        int membersHeaderY = metaY + cardH + 15;
        CompatibilityHelper.drawTextWithShadow(context, font, Component.literal("§b§lMembers"), textX, membersHeaderY, 0xFFFFFFFF);

        // Slots counter
        String slotsText = party.memberCount + "/" + party.maxSize;
        String slotsColorCode = party.isFull ? "§c" : "§a";
        int slotsW = font.width(slotsText);
        int slotsX = modalX + modalW - 12 - slotsW;
        CompatibilityHelper.drawTextWithShadow(context, font, Component.literal(slotsColorCode + slotsText), slotsX, membersHeaderY, 0xFFFFFFFF);

        // Render member rows
        int textY = membersHeaderY + 17;
        memberRows.clear();

        int listY = membersHeaderY + 14;

        // Members list background
        context.fill(modalX + 8, listY, modalX + modalW - 8, listY + listH, UiStyle.CARD_BACKGROUND);

        // Left accent bar
        context.fill(modalX + 8, listY + 1, modalX + 10, listY + listH - 1, accentColor);

        for (int i = 0; i < sortedMembers.size(); i++) {
            Map.Entry<String, PartyData.MemberData> entry = sortedMembers.get(i);
            PartyData.MemberData member = entry.getValue();

            // Row background
            int rowBg = (i % 2 == 0) ? 0x30000000 : 0x00000000;
            if (rowBg != 0) {
                context.fill(modalX + 8, textY - 3, modalX + modalW - 8, textY + 13, rowBg);
            }

            // Icon color
            String prefix = member.getStyledRolePrefix();
            String display = member.displayName();
            String styledLine = prefix + " " + (member.isReserved ? "§7" : "§f") + display;

            int nameColor = member.isReserved ? 0xFF888888 : 0xFFFFFFFF;
            CompatibilityHelper.drawTextWithShadow(context, font, Component.literal(styledLine), textX + 4, textY + 1, nameColor);

            // Kick button for leaders
            if (isLeader && member.name != null && !PlayerUtil.namesEqual(member.name, party.leaderName)) {
                int kickX = modalX + modalW - 24;
                boolean kickHovered = bodyViewport.contains(mouseY) && isKickButtonHovered(mouseX, mouseY, kickX, textY);
                if (kickHovered) {
                    context.fill(kickX - 4, textY - 2, kickX + 10, textY + 12, 0x40FF4D4D);
                }
                int kickColor = kickHovered ? 0xFFFF4D4D : 0xFFAA4444;
                UiIcons.CROSS.draw(context, kickX - 4, textY - 2, 14, 14, kickColor);
            }

            memberRows.add(new MemberRow(entry.getKey(), textY));
            textY += 18;
        }

        context.disableScissor();
        bodyViewport.drawScrollbar(context, modalX + modalW - 6);

        // Status message
        if (hasWrappedStatus()) {
            int currentY = statusTopY();
            for (FormattedCharSequence line : wrappedStatus) {
                context.drawString(font, line, modalX + (modalW - font.width(line)) / 2, currentY, statusColor, true);
                currentY += 10;
            }
        }

        // Render buttons
        super.render(context, mouseX, mouseY, delta);
    }

    private boolean hasWrappedStatus() {
        return statusMessage != null && wrappedStatus != null && !wrappedStatus.isEmpty();
    }

    /**
     * Top of the wrapped status text, which sits just above the action buttons.
     */
    private int statusTopY() {
        int statusBottomY = modalY + modalH - 34;
        return statusBottomY - (wrappedStatus.size() * 10 - 2);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return bodyViewport.scroll(verticalAmount) || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Check kick clicks for leader (rows scrolled out of the body are clipped and can't be kicked)
        if (isLeader && button == InputConstants.MOUSE_BUTTON_LEFT && party.members != null && bodyViewport.contains(mouseY)) {
            for (MemberRow row : memberRows) {
                PartyData.MemberData member = party.members.get(row.memberKey);
                if (member == null || member.name == null) continue;
                int kickX = modalX + modalW - 24;
                if (!PlayerUtil.namesEqual(member.name, party.leaderName)
                        && isKickButtonHovered(mouseX, mouseY, kickX, row.y)) {
                    String kickedName = member.name;
                    runAction(apiClient.kickMember(party.partyId, kickedName), "Failed.", resp -> {
                        setStatus("Kicked " + kickedName, 0xFF55FF55);
                        refreshPartyState();
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

        return super.mouseClicked(click, doubleClick);
    }

    private boolean isKickButtonHovered(double mouseX, double mouseY, int kickX, int rowY) {
        return mouseX >= kickX - 4 && mouseX <= kickX + 10
                && mouseY >= rowY - 2 && mouseY <= rowY + 12;
    }
}
