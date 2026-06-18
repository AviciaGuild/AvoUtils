package info.avicia.partyfinder.gui;

import info.avicia.partyfinder.PartyFinderMod;
import info.avicia.partyfinder.api.PartyData;
import info.avicia.partyfinder.api.PartyFinderClient;
import info.avicia.partyfinder.handler.ChatPartyDetector;
import info.avicia.partyfinder.handler.InviteHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Main pfinder screen. Shows all active parties as a scrollable list.
 * Parties are fetched once when the screen opens; a Refresh button re-fetches.
 * 
 * Clicking a party opens a {@link PartyDetailModal} as an overlay.
 * The "Create Party" button opens a {@link CreatePartyModal}.
 */
public class PartyListScreen extends Screen {

    private final PartyFinderClient apiClient;
    private final ChatPartyDetector chatDetector;
    private final InviteHandler inviteHandler;

    private List<PartyData> parties = new ArrayList<>();
    private boolean loading = true;
    private String errorMessage = null;

    // Scroll state
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 36;
    private static final int LIST_TOP = 50;
    private static final int SIDE_PADDING = 20;

    // Active modal overlay (null = none)
    private Screen activeModal = null;

    public PartyListScreen(PartyFinderClient apiClient, ChatPartyDetector chatDetector, InviteHandler inviteHandler) {
        super(Text.literal("Party Finder"));
        this.apiClient = apiClient;
        this.chatDetector = chatDetector;
        this.inviteHandler = inviteHandler;
    }

    @Override
    protected void init() {
        // Top bar buttons
        int buttonY = 8;

        // Refresh button (top-left)
        addDrawableChild(ButtonWidget.builder(Text.literal("⟳ Refresh"), btn -> fetchParties())
                .dimensions(SIDE_PADDING, buttonY, 80, 20)
                .build());

        // Create Party button (top-right)
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Create Party"), btn -> openCreateModal())
                .dimensions(width - SIDE_PADDING - 120, buttonY, 120, 20)
                .build());

        // Re-initialize active modal if present on resize
        if (activeModal != null) {
            if (activeModal instanceof PartyDetailModal) {
                ((PartyDetailModal) activeModal).initModal(client, width, height);
            } else if (activeModal instanceof CreatePartyModal) {
                ((CreatePartyModal) activeModal).initModal(client, width, height);
            }
        }

        // Fetch parties on open
        fetchParties();

        // Run `/party list` automatically to sync in-game party members
        if (client != null && client.player != null) {
            client.player.networkHandler.sendChatCommand("party list");
        }
    }

    // ── Data fetching ────────────────────────────────────────────────────

    private void fetchParties() {
        loading = true;
        errorMessage = null;
        apiClient.listParties().thenAccept(result -> {
            MinecraftClient.getInstance().execute(() -> {
                parties = result;
                loading = false;

                // Track if we are leading any active party
                String selfName = MinecraftClient.getInstance().getSession().getUsername();
                boolean isLeadingAny = false;
                for (PartyData p : result) {
                    if (p.leaderName != null && p.leaderName.equalsIgnoreCase(selfName)) {
                        chatDetector.setTrackedPartyId(p.partyId);
                        isLeadingAny = true;
                        break;
                    }
                }
                if (!isLeadingAny) {
                    chatDetector.clearTracking();
                }
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            PartyFinderMod.LOGGER.warn("Failed to fetch parties: {}", cause.getMessage());
            MinecraftClient.getInstance().execute(() -> {
                errorMessage = cause.getMessage() != null ? cause.getMessage() : "Failed to load parties.";
                loading = false;
            });
            return null;
        });
    }

    // ── Modal management ─────────────────────────────────────────────────

    public void openDetailModal(PartyData party) {
        PartyDetailModal modal = new PartyDetailModal(this, apiClient, inviteHandler, party);
        modal.initModal(client, width, height);
        activeModal = modal;
    }

    private void openCreateModal() {
        CreatePartyModal modal = new CreatePartyModal(this, apiClient);
        modal.initModal(client, width, height);
        activeModal = modal;
    }

    public void openEditModal(PartyData party) {
        CreatePartyModal modal = new CreatePartyModal(this, apiClient, party);
        modal.initModal(client, width, height);
        activeModal = modal;
    }

    public void setActiveModal(Screen modal) {
        this.activeModal = modal;
    }

    public void closeModal() {
        activeModal = null;
        fetchParties();
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC0101015);

        context.drawCenteredTextWithShadow(textRenderer, Text.literal("§lParty Finder"), width / 2, 12, 0xFFFFFFFF);

        if (errorMessage != null) {
            Text text = Text.literal("§c" + errorMessage);
            int textWidth = textRenderer.getWidth(text);
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, text, (width - textWidth) / 2, height / 2, 0xFFFFFFFF);
            return;
        }

        // Render party list or status
        if (loading) {
            Text text = Text.literal("§7Loading...");
            int textWidth = textRenderer.getWidth(text);
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, text, (width - textWidth) / 2, height / 2, 0xFFFFFFFF);
        } else if (parties.isEmpty()) {
            Text text = Text.literal("§7No active parties.");
            int textWidth = textRenderer.getWidth(text);
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, text, (width - textWidth) / 2, height / 2, 0xFFFFFFFF);
        } else {
            renderPartyList(context, mouseX, mouseY);
        }

        // Render buttons
        super.render(context, mouseX, mouseY, delta);

        // Modal overlay on top of everything
        if (activeModal != null) {
            context.fill(0, 0, width, height, 0x88000000);
            activeModal.render(context, mouseX, mouseY, delta);
        }
    }

    private void renderPartyList(DrawContext context, int mouseX, int mouseY) {
        int listBottom = height - 10;

        for (int i = 0; i < parties.size(); i++) {
            int y = LIST_TOP + (i * ROW_HEIGHT) - scrollOffset;
            if (y + ROW_HEIGHT < LIST_TOP || y > listBottom) continue;

            PartyData party = parties.get(i);
            boolean hovered = mouseX >= SIDE_PADDING && mouseX <= width - SIDE_PADDING
                    && mouseY >= y && mouseY < y + ROW_HEIGHT - 4;

            // Card background
            int bgColor = hovered ? 0xDD333344 : 0xBB222233;
            context.fill(SIDE_PADDING, y, width - SIDE_PADDING, y + ROW_HEIGHT - 4, bgColor);

            // Border
            int borderColor = hovered ? 0xFF6666FF : 0xFF444466;
            // Top
            context.fill(SIDE_PADDING, y, width - SIDE_PADDING, y + 1, borderColor);
            // Bottom
            context.fill(SIDE_PADDING, y + ROW_HEIGHT - 5, width - SIDE_PADDING, y + ROW_HEIGHT - 4, borderColor);
            // Left
            context.fill(SIDE_PADDING, y, SIDE_PADDING + 1, y + ROW_HEIGHT - 4, borderColor);
            // Right
            context.fill(width - SIDE_PADDING - 1, y, width - SIDE_PADDING, y + ROW_HEIGHT - 4, borderColor);

            // Activity label
            String activities = String.join(" / ", party.activities);
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§b" + activities), SIDE_PADDING + 8, y + 6, 0xFFFFFFFF);

            // Leader name
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§7Leader: §f" + party.leaderName),
                    SIDE_PADDING + 8, y + 18, 0xFFFFFFFF);

            // Slots + region
            String slotsText = party.memberCount + "/" + party.maxSize;
            String regionText = party.region != null ? party.region : "Any";
            int slotsColor = party.isFull ? 0xFFFF5555 : 0xFF55FF55;
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal(slotsText),
                    width - SIDE_PADDING - 80, y + 6, slotsColor);
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§7" + regionText),
                    width - SIDE_PADDING - 80, y + 18, 0xFFFFFFFF);
        }
    }

    // ── Input handling ───────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean boolean_arg) {
        if (errorMessage != null) {
            return false;
        }

        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (activeModal != null) {
            return activeModal.mouseClicked(click, boolean_arg);
        }

        // Check if a party row was clicked
        if (button == 0 && !loading && !parties.isEmpty()) {
            for (int i = 0; i < parties.size(); i++) {
                int y = LIST_TOP + (i * ROW_HEIGHT) - scrollOffset;
                if (mouseX >= SIDE_PADDING && mouseX <= width - SIDE_PADDING
                        && mouseY >= y && mouseY < y + ROW_HEIGHT - 4) {
                    openDetailModal(parties.get(i));
                    return true;
                }
            }
        }

        return super.mouseClicked(click, boolean_arg);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (errorMessage != null) {
            return false;
        }
        if (activeModal != null) {
            return activeModal.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int maxScroll = Math.max(0, (parties.size() * ROW_HEIGHT) - (height - LIST_TOP - 10));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (verticalAmount * 20)));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        int keyCode = keyInput.key();
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            if (activeModal != null) {
                closeModal();
            } else {
                this.close();
            }
            return true;
        }

        if (errorMessage != null) {
            return false;
        }

        if (activeModal != null) {
            return activeModal.keyPressed(keyInput);
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput charInput) {
        if (errorMessage != null) {
            return false;
        }
        if (activeModal != null) {
            return activeModal.charTyped(charInput);
        }
        return super.charTyped(charInput);
    }

    public void onPartyListUpdated() {
        if (activeModal instanceof CreatePartyModal) {
            ((CreatePartyModal) activeModal).initModal(client, width, height);
        }
    }

    public ChatPartyDetector getChatDetector() {
        return chatDetector;
    }
}
