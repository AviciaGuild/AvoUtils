package info.avicia.avoutils.features.partyfinder.gui;

import info.avicia.avoutils.features.partyfinder.api.PartyData;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.features.partyfinder.RoleIconUtil;
import info.avicia.avoutils.core.gui.FlatButtonWidget;
import info.avicia.avoutils.core.gui.UiStyle;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.FlatToggleWidget;
import info.avicia.avoutils.core.gui.FlatSliderWidget;
import info.avicia.avoutils.core.gui.ScrollViewport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import info.avicia.avoutils.core.gui.ModalOverlay;

/**
 * Modal overlay for creating or editing a party
 */
public class CreatePartyModal extends Screen implements ModalOverlay {

    private static final String[] ACTIVITIES = {"NOTG", "NOL", "TCC", "TNA", "WTP"};
    private static final String[] ROLES = {"DPS", "Healer", "Tank", "Other"};
    private static final String[] ROLE_VALUES = {"dps", "healer", "tank", "other"};
    private static final String[] REGIONS = {"NA", "EU", "AS"};

    private final PartyListScreen parent;
    private final PartyFinderClient apiClient;
    private final PartyData partyToEdit; // null if creating, non-null if editing

    // Modal dimensions
    private int modalX, modalY, modalW, modalH;

    // Scrolling body
    private final ScrollViewport bodyViewport = new ScrollViewport();
    private int bodyContentBottom; // unscrolled Y of the last card's bottom edge
    private FlatButtonWidget closeButton;
    private FlatButtonWidget saveButton;

    // Form state
    private final Set<String> selectedActivities = new HashSet<>();
    private String selectedRole = "dps";
    private String initialRole = null;
    private int reservedSlots = 0;
    private final Set<String> selectedRegions = new HashSet<>();
    private EditBox noteField;
    private boolean ping = true;
    private FlatToggleWidget pingToggle;
    private FlatSliderWidget reservedSlider;

    private String statusMessage = null;
    private int statusColor = 0xFFFFFF;
    private List<FormattedCharSequence> wrappedStatus = null;
    private boolean submitting = false;

    // Button references for visual toggling
    private final List<FlatButtonWidget> activityButtons = new ArrayList<>();
    private final List<FlatButtonWidget> roleButtons = new ArrayList<>();
    private final List<FlatButtonWidget> regionButtons = new ArrayList<>();

    public CreatePartyModal(PartyListScreen parent, PartyFinderClient apiClient) {
        this(parent, apiClient, null);
    }

    public CreatePartyModal(PartyListScreen parent, PartyFinderClient apiClient, PartyData partyToEdit) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font,
                Component.literal(partyToEdit != null ? "Edit Party" : "Create Party"));
        this.parent = parent;
        this.apiClient = apiClient;
        this.partyToEdit = partyToEdit;

        if (partyToEdit != null) {
            if (partyToEdit.activities != null) {
                this.selectedActivities.addAll(partyToEdit.activities);
            }
            if (partyToEdit.region != null && !partyToEdit.region.isEmpty()) {
                for (String part : partyToEdit.region.split("/")) {
                    String trimmed = part.trim().toUpperCase();
                    if (trimmed.equals("NA") || trimmed.equals("EU") || trimmed.equals("AS")) {
                        this.selectedRegions.add(trimmed);
                    }
                }
            }
            this.ping = partyToEdit.ping;

            if (partyToEdit.members != null) {
                for (PartyData.MemberData member : partyToEdit.members.values()) {
                    if (member != null && PlayerUtil.isSelf(member.name)) {
                        this.selectedRole = member.role != null ? member.role.toLowerCase(Locale.ROOT) : "dps";
                        this.initialRole = this.selectedRole;
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void initModal(Minecraft client, int width, int height) {
        this.width = width;
        this.height = height;
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        modalW = Math.min(320, width - 40);
        modalH = Math.min(partyToEdit != null ? 296 : 334, height - 40);
        modalX = (width - modalW) / 2;
        modalY = (height - modalH) / 2;
        bodyViewport.clearTracked();

        closeButton = new FlatButtonWidget(modalX + modalW - 16, modalY + 6, 12, 12, Component.literal("✕"), () -> parent.closeModal());
        closeButton.setDanger(true);
        closeButton.setBorderless(true);
        addRenderableWidget(closeButton);

        int y = modalY + 38;

        // Activity selection
        int btnX = modalX + modalW - 250;
        activityButtons.clear();
        for (String activity : ACTIVITIES) {
            FlatButtonWidget btn = new FlatButtonWidget(btnX, y + 8, 42, 18, Component.literal(activity), () -> toggleActivity(activity));
            activityButtons.add(btn);
            addBody(btn);
            btnX += 47;
        }
        y += 38;

        // Role selection
        btnX = modalX + modalW - 255;
        roleButtons.clear();
        for (int i = 0; i < ROLES.length; i++) {
            final String roleVal = ROLE_VALUES[i];
            String icon = RoleIconUtil.getStyledRolePrefix(roleVal);
            FlatButtonWidget btn = new FlatButtonWidget(btnX, y + 8, 55, 18, Component.literal(icon + " " + ROLES[i]), () -> selectRole(roleVal));
            switch (roleVal) {
                case "dps" -> btn.setSelectedColors(0xFFFF4D4D, 0x25FF4D4D, 0xFFFF4D4D);
                case "healer" -> btn.setSelectedColors(0xFFFF66FF, 0x25FF66FF, 0xFFFF66FF);
                case "tank" -> btn.setSelectedColors(0xFF5555FF, 0x255555FF, 0xFF5555FF);
                case "other" -> btn.setSelectedColors(0xFF888888, 0x25888888, 0xFF888888);
            }
            roleButtons.add(btn);
            addBody(btn);
            btnX += 60;
        }
        y += 38;

        // Region buttons
        btnX = modalX + modalW - 180;
        regionButtons.clear();
        for (String reg : REGIONS) {
            final String r = reg;
            FlatButtonWidget btn = new FlatButtonWidget(btnX, y + 8, 50, 18, Component.literal(reg), () -> toggleRegion(r));
            regionButtons.add(btn);
            addBody(btn);
            btnX += 55;
        }
        y += 38;

        // Note field
        String currentNote = (noteField != null) ? noteField.getValue() : (partyToEdit != null && partyToEdit.note != null ? partyToEdit.note : "");
        boolean wasFocused = (noteField != null && noteField.isFocused());
        noteField = new EditBox(font, modalX + 16, y + 24, modalW - 32, 10, Component.literal("Note"));
        noteField.setHint(Component.literal("Note (optional)"));
        noteField.setMaxLength(100);
        noteField.setBordered(false);
        noteField.setValue(currentNote);
        noteField.setFocused(wasFocused);
        if (wasFocused) {
            setFocused(noteField);
        }
        addBody(noteField);
        y += 50;

        // Reserved slots
        if (partyToEdit == null) {
            int minReserved = countOtherInGameMembers();
            if (reservedSlots < minReserved) {
                reservedSlots = minReserved;
            }

            reservedSlider = new FlatSliderWidget(modalX + 120, y + 8, modalW - 155, 18, minReserved, 3, reservedSlots, val -> {
                reservedSlots = val;
            });
            addBody(reservedSlider);
            y += 38;
        }

        // Ping toggle
        pingToggle = new FlatToggleWidget(modalX + modalW - 50, y + 8, 30, 16, ping, val -> {
            ping = val;
        });
        addBody(pingToggle);
        bodyContentBottom = y + 32; // bottom edge of the Ping card

        // Submit
        saveButton = new FlatButtonWidget(modalX + (modalW - 80) / 2, modalY + modalH - 26, 80, 18, Component.literal(partyToEdit != null ? "§aSave" : "§aCreate"), () -> submit());
        addRenderableWidget(saveButton);
        updateWrappedStatus();
        updateBodyViewport();
    }

    /**
     * Adds a form widget that scrolls with the body. These aren't drawable children because
     * {@link #render} draws them clipped to the body.
     */
    private <T extends AbstractWidget> T addBody(T widget) {
        return addWidget(bodyViewport.track(widget));
    }

    /**
     * Fits the scrolling body between the header and the status text (or the Save button when
     * there's no status), so every card stays reachable when the modal is clamped.
     */
    private void updateBodyViewport() {
        int top = modalY + 25;
        int bottom = hasWrappedStatus() ? statusTopY() - 2 : modalY + modalH - 32;
        bodyViewport.update(top, bottom, bodyContentBottom - top);
    }

    private boolean hasWrappedStatus() {
        return statusMessage != null && wrappedStatus != null && !wrappedStatus.isEmpty();
    }

    /**
     * Top of the wrapped status text, which sits just above the Save button.
     */
    private int statusTopY() {
        int statusBottomY = modalY + modalH - 32;
        return statusBottomY - (wrappedStatus.size() * 10 - 2);
    }

    // ── Form actions ─────────────────────────────────────────────────────

    private void toggleActivity(String activity) {
        if (!selectedActivities.remove(activity)) {
            selectedActivities.add(activity);
        }
    }

    private void selectRole(String role) {
        selectedRole = role;
    }

    private void toggleRegion(String region) {
        if (!selectedRegions.remove(region)) {
            selectedRegions.add(region);
        }
    }

    private void submit() {
        if (submitting) return;

        if (selectedActivities.isEmpty()) {
            setStatus("Select at least one activity!", 0xFFFF5555);
            return;
        }

        // Validate party size if creating
        if (partyToEdit == null) {
            int otherCount = countOtherInGameMembers();
            if (otherCount >= 4) {
                setStatus("Your in-game party has too many players (max 4)!", 0xFFFF5555);
                return;
            }
            if (reservedSlots < otherCount) {
                reservedSlots = otherCount;
            }
        }

        submitting = true;
        setStatus(partyToEdit != null ? "Saving party..." : "Creating party...", 0xFFAAAA00);

        String region = null;
        if (!selectedRegions.isEmpty()) {
            List<String> sortedRegions = new ArrayList<>();
            for (String r : REGIONS) {
                if (selectedRegions.contains(r)) sortedRegions.add(r);
            }
            region = String.join("/", sortedRegions);
        }
        String note = noteField.getValue().trim().isEmpty() ? null : noteField.getValue().trim();

        if (partyToEdit != null) {
            apiClient.editParty(
                    partyToEdit.partyId,
                    new ArrayList<>(selectedActivities),
                    region,
                    note,
                    ping
            ).thenCompose(resp -> {
                if (resp.ok && !selectedRole.equalsIgnoreCase(initialRole)) {
                    return apiClient.joinParty(partyToEdit.partyId, selectedRole);
                } else {
                    return CompletableFuture.completedFuture(resp);
                }
            }).thenAccept(resp -> {
                runOnClient(() -> {
                    submitting = false;
                    if (resp.ok) {
                        if (resp.data != null && resp.data.partyId != null) {
                            parent.getPartySyncer().setTrackedPartyId(resp.data.partyId);
                        }
                        parent.closeModal();
                    } else {
                        setStatus(resp.error != null ? resp.error : "Failed to edit party.", 0xFFFF5555);
                    }
                });
            }).exceptionally(ex -> {
                runOnClient(() -> {
                    submitting = false;
                    setStatus("Network error: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()), 0xFFFF5555);
                });
                return null;
            });
        } else {
            apiClient.createParty(
                    selectedRole,
                    new ArrayList<>(selectedActivities),
                    region,
                    note,
                    reservedSlots,
                    ping
            ).thenAccept(resp -> {
                runOnClient(() -> {
                    submitting = false;
                    if (resp.ok) {
                        if (resp.data != null && resp.data.partyId != null) {
                            long newPartyId = resp.data.partyId;
                            parent.getPartySyncer().setTrackedPartyId(newPartyId);
                            // Pre-reserve slots for all other in-game party members
                            for (String memberName : parent.getPartySyncer().getLastPartyListMembers()) {
                                if (!PlayerUtil.isSelf(memberName)) {
                                    apiClient.reserveIngame(newPartyId, memberName).thenAccept(reserveResp -> {
                                        if (reserveResp.ok) {
                                            parent.getPartySyncer().addKnownMembers(List.of(memberName));
                                        }
                                    }).exceptionally(reserveEx -> null);
                                }
                            }
                        }
                        parent.closeModal();
                    } else {
                        setStatus(resp.error != null ? resp.error : "Failed to create party.", 0xFFFF5555);
                    }
                });
            }).exceptionally(ex -> {
                runOnClient(() -> {
                    submitting = false;
                    setStatus("Network error: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()), 0xFFFF5555);
                });
                return null;
            });
        }
    }

    private void runOnClient(Runnable action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(action);
        }
    }

    private void setStatus(String msg, int color) {
        statusMessage = msg;
        statusColor = (color & 0xFF000000) == 0 ? (color | 0xFF000000) : color;
        updateWrappedStatus();
        updateBodyViewport();
    }

    private void updateWrappedStatus() {
        if (statusMessage != null && font != null) {
            wrappedStatus = font.split(Component.literal(statusMessage), modalW - 24);
        } else {
            wrappedStatus = null;
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────

    /** Returns the number of in-game party members who are not the local player. */
    private int countOtherInGameMembers() {
        int count = 0;
        for (String name : parent.getPartySyncer().getLastPartyListMembers()) {
            if (!PlayerUtil.isSelf(name)) count++;
        }
        return count;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        CompatibilityHelper.drawModalFrame(context, modalX, modalY, modalW, modalH);

        // Title
        context.drawCenteredString(font, Component.literal(partyToEdit != null ? "§b§lEdit Party" : "§b§lCreate Party"), width / 2, modalY + 8, 0xFFFFFFFF);

        // The status height decides where the body ends, so wrap it before laying out the body
        if (statusMessage != null && wrappedStatus == null && font != null) {
            updateWrappedStatus();
        }
        updateBodyViewport();

        int y = modalY + 38 - bodyViewport.getOffset();
        bodyViewport.enableScissor(context, modalX + 1, modalX + modalW - 1);

        // Selection cards
        drawSectionCard(context, "Activity", y, 30, false);
        y += 38;

        drawSectionCard(context, "Role", y, 30, false);
        y += 38;

        drawSectionCard(context, "Region", y, 30, false);
        y += 38;

        drawSectionCard(context, "Note", y, 42, noteField.isFocused());
        y += 50;

        if (partyToEdit == null) {
            drawSectionCard(context, "Reserved Slots", y, 30, false);
            CompatibilityHelper.drawTextWithShadow(context, font, Component.literal(String.valueOf(reservedSlots)), modalX + modalW - 24, y + 13, 0xFFFFFFFF);
            y += 38;
        }

        drawSectionCard(context, "Ping LFG Roles", y, 30, false);

        // Highlight selected activities
        for (int i = 0; i < activityButtons.size(); i++) {
            activityButtons.get(i).setSelected(selectedActivities.contains(ACTIVITIES[i]));
        }

        // Highlight selected regions
        for (int i = 0; i < regionButtons.size(); i++) {
            regionButtons.get(i).setSelected(selectedRegions.contains(REGIONS[i]));
        }

        // Highlight selected role
        for (int i = 0; i < roleButtons.size(); i++) {
            roleButtons.get(i).setSelected(ROLE_VALUES[i].equals(selectedRole));
        }

        bodyViewport.renderTracked(context, mouseX, mouseY, delta);
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

        // Fixed widgets (close, Save)
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        // Click outside modal closes it
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            parent.closeModal();
            return true;
        }
        // Form widgets scrolled under the header or footer are clipped there, so outside the body
        // only the fixed buttons may take the click
        if (!bodyViewport.contains(mouseY)) {
            for (FlatButtonWidget fixed : new FlatButtonWidget[]{closeButton, saveButton}) {
                if (fixed != null && fixed.mouseClicked(click, doubleClick)) {
                    setFocused(fixed);
                    return true;
                }
            }
            return false;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return bodyViewport.scroll(verticalAmount) || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        if (keyInput.key() == GLFW.GLFW_KEY_ENTER || keyInput.key() == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyInput);
    }

    private void drawSectionCard(GuiGraphics context, String label, int y, int cardH, boolean highlightBorder) {
        int cardY = y + 2;
        
        // Card background
        context.fill(modalX + 8, cardY, modalX + modalW - 8, cardY + cardH, UiStyle.CARD_BACKGROUND);

        // Card border
        int borderColor = highlightBorder ? UiStyle.ACCENT_BLUE : UiStyle.BORDER_FAINT;
        CompatibilityHelper.drawBorder(context, modalX + 8, cardY, modalW - 16, cardH, borderColor);

        // Left accent bar
        context.fill(modalX + 8, cardY + 1, modalX + 10, cardY + cardH - 1, UiStyle.ACCENT_BAR);
        
        // Section label text
        int textOffset = (cardH == 30) ? 11 : 6;
        CompatibilityHelper.drawTextWithShadow(context, font, Component.literal("§7" + label), modalX + 16, cardY + textOffset, 0xFFFFFFFF);
    }
}
