package info.avicia.avoutils.features.partyfinder.gui;

import info.avicia.avoutils.features.partyfinder.api.PartyData;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.core.gui.FlatButtonWidget;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.FlatToggleWidget;
import info.avicia.avoutils.core.gui.FlatSliderWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // Form state
    private final Set<String> selectedActivities = new HashSet<>();
    private String selectedRole = "dps";
    private String initialRole = null;
    private int reservedSlots = 0;
    private final Set<String> selectedRegions = new HashSet<>();
    private TextFieldWidget noteField;
    private boolean ping = true;
    private FlatToggleWidget pingToggle;
    private FlatSliderWidget reservedSlider;

    private String statusMessage = null;
    private int statusColor = 0xFFFFFF;
    private boolean submitting = false;

    // Button references for visual toggling
    private final List<FlatButtonWidget> activityButtons = new ArrayList<>();
    private final List<FlatButtonWidget> roleButtons = new ArrayList<>();
    private final List<FlatButtonWidget> regionButtons = new ArrayList<>();

    public CreatePartyModal(PartyListScreen parent, PartyFinderClient apiClient) {
        this(parent, apiClient, null);
    }

    public CreatePartyModal(PartyListScreen parent, PartyFinderClient apiClient, PartyData partyToEdit) {
        super(MinecraftClient.getInstance(), MinecraftClient.getInstance().textRenderer,
                Text.literal(partyToEdit != null ? "Edit Party" : "Create Party"));
        this.parent = parent;
        this.apiClient = apiClient;
        this.partyToEdit = partyToEdit;

        if (partyToEdit != null) {
            this.selectedActivities.addAll(partyToEdit.activities);
            if (partyToEdit.region != null && !partyToEdit.region.isEmpty()) {
                for (String part : partyToEdit.region.split("/")) {
                    String trimmed = part.trim().toUpperCase();
                    if (trimmed.equals("NA") || trimmed.equals("EU") || trimmed.equals("AS")) {
                        this.selectedRegions.add(trimmed);
                    }
                }
            }
            this.ping = partyToEdit.ping;

            String selfName = MinecraftClient.getInstance().getSession().getUsername();
            if (partyToEdit.members != null) {
                for (PartyData.MemberData member : partyToEdit.members.values()) {
                    if (member.name != null && member.name.equalsIgnoreCase(selfName)) {
                        this.selectedRole = member.role;
                        this.initialRole = member.role;
                        break;
                    }
                }
            }
        }
    }

    public void initModal(MinecraftClient client, int width, int height) {
        this.width = width;
        this.height = height;
        this.init();
    }

    @Override
    protected void init() {
        modalW = Math.min(320, width - 40);
        modalH = Math.min(partyToEdit != null ? 296 : 334, height - 40);
        modalX = (width - modalW) / 2;
        modalY = (height - modalH) / 2;

        FlatButtonWidget closeBtn = new FlatButtonWidget(modalX + modalW - 16, modalY + 6, 12, 12, Text.literal("✕"), () -> parent.closeModal());
        closeBtn.setDanger(true);
        closeBtn.setBorderless(true);
        addDrawableChild(closeBtn);

        int y = modalY + 38;

        // Activity selection
        int btnX = modalX + modalW - 250;
        activityButtons.clear();
        for (String activity : ACTIVITIES) {
            FlatButtonWidget btn = new FlatButtonWidget(btnX, y + 8, 42, 18, Text.literal(activity), () -> toggleActivity(activity));
            activityButtons.add(btn);
            addDrawableChild(btn);
            btnX += 47;
        }
        y += 38;

        // Role selection
        btnX = modalX + modalW - 255;
        roleButtons.clear();
        for (int i = 0; i < ROLES.length; i++) {
            final String roleVal = ROLE_VALUES[i];
            String icon = PartyData.MemberData.getStyledRolePrefix(roleVal);
            FlatButtonWidget btn = new FlatButtonWidget(btnX, y + 8, 55, 18, Text.literal(icon + " " + ROLES[i]), () -> selectRole(roleVal));
            switch (roleVal) {
                case "dps" -> btn.setSelectedColors(0xFFFF4D4D, 0x25FF4D4D, 0xFFFF4D4D);
                case "healer" -> btn.setSelectedColors(0xFFFF66FF, 0x25FF66FF, 0xFFFF66FF);
                case "tank" -> btn.setSelectedColors(0xFF5555FF, 0x255555FF, 0xFF5555FF);
                case "other" -> btn.setSelectedColors(0xFF888888, 0x25888888, 0xFF888888);
            }
            roleButtons.add(btn);
            addDrawableChild(btn);
            btnX += 60;
        }
        y += 38;

        // Region buttons
        btnX = modalX + modalW - 180;
        regionButtons.clear();
        for (String reg : REGIONS) {
            final String r = reg;
            FlatButtonWidget btn = new FlatButtonWidget(btnX, y + 8, 50, 18, Text.literal(reg), () -> toggleRegion(r));
            regionButtons.add(btn);
            addDrawableChild(btn);
            btnX += 55;
        }
        y += 38;

        // Note field
        noteField = new TextFieldWidget(textRenderer, modalX + 16, y + 24, modalW - 32, 10, Text.literal("Note"));
        noteField.setPlaceholder(Text.literal("Note (optional)"));
        noteField.setMaxLength(100);
        noteField.setDrawsBackground(false);
        if (partyToEdit != null) {
            noteField.setText(partyToEdit.note != null ? partyToEdit.note : "");
        }
        addDrawableChild(noteField);
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
            addDrawableChild(reservedSlider);
            y += 38;
        }

        // Ping toggle
        pingToggle = new FlatToggleWidget(modalX + modalW - 50, y + 8, 30, 16, ping, val -> {
            ping = val;
        });
        addDrawableChild(pingToggle);

        // Submit
        FlatButtonWidget saveBtn = new FlatButtonWidget(modalX + (modalW - 80) / 2, modalY + modalH - 26, 80, 18, Text.literal(partyToEdit != null ? "§aSave" : "§aCreate"), () -> submit());
        addDrawableChild(saveBtn);
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
        String note = noteField.getText().trim().isEmpty() ? null : noteField.getText().trim();

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
                    return java.util.concurrent.CompletableFuture.completedFuture(resp);
                }
            }).thenAccept(resp -> {
                MinecraftClient.getInstance().execute(() -> {
                    submitting = false;
                    if (resp.ok) {
                        if (resp.data != null && resp.data.partyId != null) {
                            parent.getChatDetector().setTrackedPartyId(resp.data.partyId);
                        }
                        parent.closeModal();
                    } else {
                        setStatus(resp.error != null ? resp.error : "Failed to edit party.", 0xFFFF5555);
                    }
                });
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
                MinecraftClient.getInstance().execute(() -> {
                    submitting = false;
                    if (resp.ok) {
                        if (resp.data != null && resp.data.partyId != null) {
                            long newPartyId = resp.data.partyId;
                            parent.getChatDetector().setTrackedPartyId(newPartyId);
                            // Pre-reserve slots for all other in-game party members
                            String selfName = MinecraftClient.getInstance().getSession().getUsername();
                            for (String memberName : parent.getChatDetector().getLastPartyListMembers()) {
                                if (!memberName.equalsIgnoreCase(selfName)) {
                                    apiClient.reserveIngame(newPartyId, memberName).thenAccept(reserveResp -> {
                                        if (reserveResp.ok) {
                                            parent.getChatDetector().addKnownMembers(List.of(memberName));
                                        }
                                    });
                                }
                            }
                        }
                        parent.closeModal();
                    } else {
                        setStatus(resp.error != null ? resp.error : "Failed to create party.", 0xFFFF5555);
                    }
                });
            });
        }
    }

    private void setStatus(String msg, int color) {
        statusMessage = msg;
        statusColor = color;
    }

    // ── Rendering ────────────────────────────────────────────────────────

    /** Returns the number of in-game party members who are not the local player. */
    private int countOtherInGameMembers() {
        String selfName = MinecraftClient.getInstance().getSession().getUsername();
        int count = 0;
        for (String name : parent.getChatDetector().getLastPartyListMembers()) {
            if (!name.equalsIgnoreCase(selfName)) count++;
        }
        return count;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CompatibilityHelper.drawModalFrame(context, modalX, modalY, modalW, modalH);

        // Title
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(partyToEdit != null ? "§b§lEdit Party" : "§b§lCreate Party"), width / 2, modalY + 8, 0xFFFFFFFF);

        int y = modalY + 38;

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
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal(String.valueOf(reservedSlots)), modalX + modalW - 24, y + 13, 0xFFFFFFFF);
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

        // Status message
        if (statusMessage != null) {
            List<OrderedText> wrappedStatus = textRenderer.wrapLines(Text.literal(statusMessage), modalW - 24);
            int statusBottomY = modalY + modalH - 32;
            int statusStartY = statusBottomY - (wrappedStatus.size() * 10 - 2);
            int currentY = statusStartY;
            for (OrderedText line : wrappedStatus) {
                context.drawText(textRenderer, line, modalX + (modalW - textRenderer.getWidth(line)) / 2, currentY, statusColor, true);
                currentY += 10;
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean boolean_arg) {
        double mouseX = click.x();
        double mouseY = click.y();
        // Click outside modal closes it
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            parent.closeModal();
            return true;
        }
        return super.mouseClicked(click, boolean_arg);
    }

    private void drawSectionCard(DrawContext context, String label, int y, int cardH, boolean highlightBorder) {
        int cardY = y + 2;
        
        // Card background
        context.fill(modalX + 8, cardY, modalX + modalW - 8, cardY + cardH, 0xD5161622);
        
        // Card border
        int borderColor = highlightBorder ? 0xFF8A9CFE : 0x1A8A9CFE;
        CompatibilityHelper.drawBorder(context, modalX + 8, cardY, modalW - 16, cardH, borderColor);
        
        // Left accent bar
        context.fill(modalX + 8, cardY + 1, modalX + 10, cardY + cardH - 1, 0x408A9CFE);
        
        // Section label text
        int textOffset = (cardH == 30) ? 11 : 6;
        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§7" + label), modalX + 16, cardY + textOffset, 0xFFFFFFFF);
    }
}
