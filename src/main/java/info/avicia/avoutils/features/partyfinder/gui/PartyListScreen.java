package info.avicia.avoutils.features.partyfinder.gui;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.features.partyfinder.api.PartyData;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.features.partyfinder.handler.PartyFinderPartySyncer;
import info.avicia.avoutils.core.party.InGamePartyTracker;
import info.avicia.avoutils.core.party.InviteHandler;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.FlatButtonWidget;
import info.avicia.avoutils.core.gui.ModalOverlay;
import info.avicia.avoutils.core.gui.ScrollableListScreen;
import info.avicia.avoutils.core.gui.UiIcons;
import info.avicia.avoutils.core.gui.UiStyle;
import info.avicia.avoutils.core.util.PlayerUtil;
import com.mojang.blaze3d.platform.InputConstants;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import info.avicia.avoutils.core.util.ClientThread;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.google.gson.JsonObject;

/**
 * Main pfinder screen. Shows all active parties as a scrollable list.
 * Parties are fetched once when the screen opens; a refresh button re-fetches.
 * 
 * Clicking a party opens a {@link PartyDetailModal} as an overlay.
 * The "Create Party" button opens a {@link CreatePartyModal}.
 */
public class PartyListScreen extends ScrollableListScreen {

    private final PartyFinderClient apiClient;
    private final PartyFinderPartySyncer partySyncer;
    private final InviteHandler inviteHandler;

    private List<PartyData> parties = new ArrayList<>();
    private boolean initialLoading = true;
    private boolean fetchInFlight = false;
    private boolean fetchQueued = false;
    private boolean initialized = false;
    private boolean closed = false;
    private String errorMessage = null;

    private FlatButtonWidget createPartyButton;
    private FlatButtonWidget viewPartyButton;
    private FlatButtonWidget inviteAllButton;

    private final Consumer<JsonObject> partyListListener = json -> fetchParties();

    private static final int ROW_HEIGHT = 42;
    private static final int LIST_TOP = 50;
    private static final int SIDE_PADDING = 20;

    private Screen activeModal = null;
    private String joinTargetLeaderName;

    public PartyListScreen(PartyFinderClient apiClient, PartyFinderPartySyncer partySyncer,
                           InviteHandler inviteHandler, String joinTargetLeaderName) {
        super(Component.literal("Party Finder"));
        this.apiClient = apiClient;
        this.partySyncer = partySyncer;
        this.inviteHandler = inviteHandler;
        this.joinTargetLeaderName = joinTargetLeaderName;
    }

    @Override
    protected void init() {
        // Top bar buttons
        int buttonY = 8;

        // Refresh button
        addRenderableWidget(new FlatButtonWidget(SIDE_PADDING, buttonY, 80, 20, Component.literal("Refresh"), () -> fetchParties()).withIcon(UiIcons.REFRESH));

        // Create Party button
        createPartyButton = addRenderableWidget(new FlatButtonWidget(width - SIDE_PADDING - 120, buttonY, 120, 20, Component.literal("Create Party"), () -> openCreateModal()).withIcon(UiIcons.PLUS));
        createPartyButton.visible = true;

        // View Party button (when in an active party)
        viewPartyButton = addRenderableWidget(new FlatButtonWidget(width - SIDE_PADDING - 80, buttonY, 80, 20, Component.literal("View Party"), () -> {
            PartyData activeParty = getActiveParty();
            if (activeParty != null) {
                openDetailModal(activeParty);
            }
        }));
        viewPartyButton.visible = false;

        // Invite All button (when party owned)
        inviteAllButton = addRenderableWidget(new FlatButtonWidget(width - SIDE_PADDING - 155, buttonY, 70, 20, Component.literal("Invite All"), () -> {
            PartyData ownedParty = getOwnedParty();
            if (ownedParty != null) {
                inviteHandler.inviteAll(ownedParty.memberNames(), PlayerUtil.selfName(), InGamePartyTracker.getInstance().getLastPartyListMembers());
            }
        }));
        inviteAllButton.visible = false;

        updateButtonVisibility();
        applyScroll(parties.size() * ROW_HEIGHT, LIST_TOP, 0);

        // Re-initialize active modal if present on resize
        if (activeModal instanceof ModalOverlay modal) {
            modal.initModal(minecraft, width, height);
        }

        // Register WebSocket listener
        AvoWebSocketManager.getInstance().unregisterListener("party_list_updated", partyListListener);
        AvoWebSocketManager.getInstance().registerListener("party_list_updated", partyListListener);

        if (!initialized) {
            initialized = true;
            fetchParties();
            InGamePartyTracker.getInstance().triggerPartyList();
        }
    }

    // ── Data fetching ────────────────────────────────────────────────────

    private void fetchParties() {
        if (closed) {
            return;
        }
        if (fetchInFlight) {
            fetchQueued = true;
            return;
        }
        fetchInFlight = true;
        if (errorMessage != null && parties.isEmpty()) {
            initialLoading = true;
        }
        errorMessage = null;

        apiClient.listParties().thenAccept(result -> {
            ClientThread.run(() -> {
                fetchInFlight = false;
                if (closed) {
                    return;
                }
                parties = result;
                initialLoading = false;
                applyScroll(parties.size() * ROW_HEIGHT, LIST_TOP, 0);

                PartyData ownedParty = getOwnedParty();
                partySyncer.syncFromOwnedParty(ownedParty);
                updateButtonVisibility();

                // Auto-open detail modal for join target
                if (joinTargetLeaderName != null && activeModal == null) {
                    for (PartyData p : result) {
                        if (PlayerUtil.namesEqual(p.leaderName, joinTargetLeaderName)) {
                            openDetailModal(p);
                            joinTargetLeaderName = null;
                            break;
                        }
                    }
                }

                if (fetchQueued) {
                    fetchQueued = false;
                    fetchParties();
                }
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            AvoUtilsMod.LOGGER.warn("[AvoUtils] [PartyFinder] Failed to fetch parties: {}", cause.getMessage());
            ClientThread.run(() -> {
                fetchInFlight = false;
                if (closed) {
                    return;
                }
                initialLoading = false;
                if (parties.isEmpty()) {
                    errorMessage = cause.getMessage() != null ? cause.getMessage() : "Failed to load parties.";
                }
                if (fetchQueued) {
                    fetchQueued = false;
                    fetchParties();
                }
            });
            return null;
        });
    }

    private void updateButtonVisibility() {
        PartyData ownedParty = getOwnedParty();
        PartyData activeParty = ownedParty != null ? ownedParty : getJoinedParty();
        if (createPartyButton != null) createPartyButton.visible = activeParty == null;
        if (viewPartyButton != null) viewPartyButton.visible = activeParty != null;
        if (inviteAllButton != null) inviteAllButton.visible = ownedParty != null;
    }

    private PartyData getActiveParty() {
        PartyData ownedParty = getOwnedParty();
        return ownedParty != null ? ownedParty : getJoinedParty();
    }

    private PartyData getOwnedParty() {
        for (PartyData p : parties) {
            if (PlayerUtil.isSelf(p.leaderName)) {
                return p;
            }
        }
        return null;
    }

    private PartyData getJoinedParty() {
        for (PartyData p : parties) {
            if (p.members == null) continue;
            for (PartyData.MemberData m : p.members.values()) {
                if (m != null && !m.isReserved && PlayerUtil.isSelf(m.name)) {
                    return p;
                }
            }
        }
        return null;
    }

    // ── Modal management ─────────────────────────────────────────────────

    public void openDetailModal(PartyData party) {
        PartyDetailModal modal = new PartyDetailModal(this, apiClient, inviteHandler, party);
        modal.initModal(minecraft, width, height);
        setActiveModal(modal);
    }

    private void openCreateModal() {
        CreatePartyModal modal = new CreatePartyModal(this, apiClient);
        modal.initModal(minecraft, width, height);
        setActiveModal(modal);
    }

    public void openEditModal(PartyData party) {
        CreatePartyModal modal = new CreatePartyModal(this, apiClient, party);
        modal.initModal(minecraft, width, height);
        setActiveModal(modal);
    }

    public void setActiveModal(Screen modal) {
        if (this.activeModal != modal && this.activeModal instanceof ModalOverlay overlay) {
            overlay.onCloseModal();
        }
        this.activeModal = modal;
    }

    public void closeModal() {
        setActiveModal(null);
        fetchParties();
    }

    private void cleanup() {
        closed = true;
        fetchQueued = false;
        AvoWebSocketManager.getInstance().unregisterListener("party_list_updated", partyListListener);
        setActiveModal(null);
    }

    @Override
    public void onClose() {
        cleanup();
        super.onClose();
    }

    @Override
    public void removed() {
        cleanup();
        super.removed();
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Background
        context.fill(0, 0, width, height, UiStyle.SCREEN_BACKGROUND);
        // Title
        CompatibilityHelper.drawScreenTitle(context, font, width, "§b§lAVICIA §f§lPARTY FINDER");

        if (errorMessage != null) {
            CompatibilityHelper.drawCenteredMessage(context, font, width, height, "§c" + errorMessage);
        } else if (initialLoading) {
            CompatibilityHelper.drawCenteredMessage(context, font, width, height, "§7Loading active parties...");
        } else if (parties.isEmpty()) {
            CompatibilityHelper.drawCenteredMessage(context, font, width, height, "§7No active parties. Create one to get started!");
        } else {
            renderPartyList(context, mouseX, mouseY);
        }

        // Render buttons
        super.render(context, mouseX, mouseY, delta);

        // Modal overlay on top of everything
        if (activeModal != null) {
            context.fill(0, 0, width, height, UiStyle.MODAL_OVERLAY);
            activeModal.render(context, mouseX, mouseY, delta);
        }
    }

    private void renderPartyList(GuiGraphics context, int mouseX, int mouseY) {
        int listBottom = getListBottom();
        enableListScissor(context, LIST_TOP);

        for (int i = 0; i < parties.size(); i++) {
            int y = LIST_TOP + (i * ROW_HEIGHT) - scrollOffset;
            if (y + ROW_HEIGHT < LIST_TOP || y > listBottom) continue;

            PartyData party = parties.get(i);
            boolean hovered = isRowHovered(mouseX, mouseY, y);

            // Card drop shadow
            context.fill(SIDE_PADDING + 1, y + 1, width - SIDE_PADDING + 1, y + ROW_HEIGHT - 3, UiStyle.CARD_SHADOW);

            // Card background
            int bgColor = hovered ? UiStyle.CARD_BACKGROUND_HOVERED : UiStyle.CARD_BACKGROUND;
            context.fill(SIDE_PADDING, y, width - SIDE_PADDING, y + ROW_HEIGHT - 4, bgColor);

            // Left status accent bar
            int accentColor = party.isFull ? UiStyle.ACCENT_RED : UiStyle.ACCENT_GREEN;
            context.fill(SIDE_PADDING + 1, y + 1, SIDE_PADDING + 4, y + ROW_HEIGHT - 5, accentColor);

            // Border outline
            int borderColor = hovered ? UiStyle.ACCENT_BLUE : UiStyle.BORDER_FAINT;
            CompatibilityHelper.drawBorder(context, SIDE_PADDING, y, width - 2 * SIDE_PADDING, ROW_HEIGHT - 4, borderColor);

            // Card text and badges
            int leftTextX = SIDE_PADDING + 12;

            // Activity label
            CompatibilityHelper.drawTextWithShadow(context, font, Component.literal("§b§l" + party.activitiesLabel()), leftTextX, y + 7, 0xFFFFFFFF);

            // Leader name
            CompatibilityHelper.drawTextWithShadow(context, font, Component.literal("§7Leader: §f" + party.leaderName), leftTextX, y + 21, 0xFFFFFFFF);

            // Slots badge / pill on the right
            String slotsText = party.memberCount + "/" + party.maxSize;
            int slotsWidth = font.width(slotsText);
            int badgeRight = width - SIDE_PADDING - 10;
            int badgeLeft = badgeRight - slotsWidth - 8;

            // Fill slots pill background
            int pillBgColor = party.isFull ? UiStyle.PILL_BG_RED : UiStyle.PILL_BG_GREEN;
            context.fill(badgeLeft, y + 6, badgeRight, y + 18, pillBgColor);

            // Slots pill border
            int pillBorderColor = party.isFull ? UiStyle.PILL_BORDER_RED : UiStyle.PILL_BORDER_GREEN;
            CompatibilityHelper.drawBorder(context, badgeLeft, y + 5, badgeRight - badgeLeft, 14, pillBorderColor);

            // Slots text inside badge
            int slotsColor = party.isFull ? UiStyle.ACCENT_RED : UiStyle.ACCENT_GREEN;
            CompatibilityHelper.drawTextWithShadow(context, font, Component.literal(slotsText), badgeLeft + 4, y + 8, slotsColor);

            // Region label
            String regionText = party.region != null ? party.region : "Any";
            String regionLabel = "§7Region: §e" + regionText;
            int regionWidth = font.width(regionLabel);
            CompatibilityHelper.drawTextWithShadow(context, font, Component.literal(regionLabel), badgeRight - regionWidth, y + 21, 0xFFFFFFFF);
        }

        context.disableScissor();
    }

    // ── Input handling ───────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (activeModal != null) {
            return activeModal.mouseClicked(click, doubleClick);
        }

        // Check if a party row was clicked
        if (button == InputConstants.MOUSE_BUTTON_LEFT && errorMessage == null && !initialLoading && !parties.isEmpty()) {
            for (int i = 0; i < parties.size(); i++) {
                int y = LIST_TOP + (i * ROW_HEIGHT) - scrollOffset;
                if (isRowHovered(mouseX, mouseY, y)) {
                    openDetailModal(parties.get(i));
                    return true;
                }
            }
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (activeModal != null) {
            return activeModal.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (errorMessage != null) {
            return false;
        }
        applyScroll(parties.size() * ROW_HEIGHT, LIST_TOP, verticalAmount);
        return true;
    }

    // Modal widgets aren't children of this screen, so drags and releases must be forwarded too, or
    // the modal's slider never sees them
    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (activeModal != null) {
            return activeModal.mouseDragged(click, deltaX, deltaY);
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (activeModal != null) {
            return activeModal.mouseReleased(click);
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        if (keyInput.isEscape()) {
            if (activeModal != null) {
                closeModal();
            } else {
                this.onClose();
            }
            return true;
        }

        if (activeModal != null) {
            return activeModal.keyPressed(keyInput);
        }

        if (errorMessage != null) {
            return false;
        }

        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharacterEvent charInput) {
        if (activeModal != null) {
            return activeModal.charTyped(charInput);
        }
        if (errorMessage != null) {
            return false;
        }
        return super.charTyped(charInput);
    }

    public void onInGamePartyChanged() {
        if (activeModal instanceof CreatePartyModal createModal) {
            createModal.onInGamePartyChanged();
        }
    }

    public PartyFinderPartySyncer getPartySyncer() {
        return partySyncer;
    }

    private boolean isRowHovered(double mouseX, double mouseY, int rowY) {
        return isInListViewport(mouseY, LIST_TOP)
                && mouseX >= SIDE_PADDING && mouseX <= width - SIDE_PADDING
                && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 4;
    }
}
