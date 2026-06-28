package info.avicia.avoutils.features.partyfinder.gui;

import info.avicia.avoutils.features.partyfinder.api.PartyData;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.core.gui.FlatButtonWidget;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

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
    private int reservedSlots = 0;
    private final Set<String> selectedRegions = new HashSet<>();
    private TextFieldWidget noteField;
    private boolean ping = true;
    private FlatButtonWidget pingButton;

    private String statusMessage = null;
    private int statusColor = 0xFFFFFF;
    private boolean submitting = false;

    // Button references for visual toggling
    private final List<FlatButtonWidget> activityButtons = new ArrayList<>();
    private final List<FlatButtonWidget> roleButtons = new ArrayList<>();
    private final List<FlatButtonWidget> regionButtons = new ArrayList<>();
    private final List<FlatButtonWidget> reservedButtons = new ArrayList<>();

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
        modalH = Math.min(partyToEdit != null ? 286 : 386, height - 40);
        modalX = (width - modalW) / 2;
        modalY = (height - modalH) / 2;

        FlatButtonWidget closeBtn = new FlatButtonWidget(modalX + modalW - 16, modalY + 6, 12, 12, Text.literal("✕"), () -> parent.closeModal());
        closeBtn.setDanger(true);
        closeBtn.setBorderless(true);
        addDrawableChild(closeBtn);

        int contentX = modalX + 12;
        int y = modalY + 38;

        // Activity selection
        int btnX = contentX;
        activityButtons.clear();
        for (String activity : ACTIVITIES) {
            FlatButtonWidget btn = new FlatButtonWidget(btnX, y + 12, 50, 18, Text.literal(activity), () -> toggleActivity(activity));
            activityButtons.add(btn);
            addDrawableChild(btn);
            btnX += 55;
        }
        y += 50;

        // Role selection
        if (partyToEdit == null) {
            btnX = contentX;
            roleButtons.clear();
            for (int i = 0; i < ROLES.length; i++) {
                final String roleVal = ROLE_VALUES[i];
                FlatButtonWidget btn = new FlatButtonWidget(btnX, y + 12, 55, 18, Text.literal(ROLES[i]), () -> selectRole(roleVal));
                roleButtons.add(btn);
                addDrawableChild(btn);
                btnX += 60;
            }
            y += 50;
        }

        // Region buttons
        btnX = contentX;
        regionButtons.clear();
        for (String reg : REGIONS) {
            final String r = reg;
            FlatButtonWidget btn = new FlatButtonWidget(btnX, y + 12, 50, 18, Text.literal(reg), () -> toggleRegion(r));
            regionButtons.add(btn);
            addDrawableChild(btn);
            btnX += 55;
        }
        y += 50;

        // Note field
        noteField = new TextFieldWidget(textRenderer, contentX + 4, y + 12 + 5, modalW - 32, 10, Text.literal("Note"));
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
            btnX = contentX;
            reservedButtons.clear();

            int minReserved = countOtherInGameMembers();
            if (reservedSlots < minReserved) {
                reservedSlots = minReserved;
            }

            for (int i = minReserved; i <= 3; i++) {
                final int slots = i;
                FlatButtonWidget btn = new FlatButtonWidget(btnX, y + 12, 30, 18, Text.literal(String.valueOf(i)), () -> selectReserved(slots));
                reservedButtons.add(btn);
                addDrawableChild(btn);
                btnX += 35;
            }
            y += 50;
        }

        // Ping toggle
        pingButton = new FlatButtonWidget(contentX, y + 12, 50, 18, Text.literal(ping ? "§aON" : "§cOFF"), () -> {
            ping = !ping;
            pingButton.setMessage(Text.literal(ping ? "§aON" : "§cOFF"));
        });
        addDrawableChild(pingButton);

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

    private void selectReserved(int slots) {
        reservedSlots = slots;
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
            ).thenAccept(resp -> {
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

        int contentX = modalX + 12;
        int y = modalY + 38;

        // Labels
        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§b§lActivity"), contentX, y, 0xFFFFFFFF);
        context.fill(modalX + 8, y + 40, modalX + modalW - 8, y + 41, 0x15FFFFFF); // divider
        y += 50;

        if (partyToEdit == null) {
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§b§lRole"), contentX, y, 0xFFFFFFFF);
            context.fill(modalX + 8, y + 40, modalX + modalW - 8, y + 41, 0x15FFFFFF); // divider
            y += 50;
        }

        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§b§lRegion"), contentX, y, 0xFFFFFFFF);
        context.fill(modalX + 8, y + 40, modalX + modalW - 8, y + 41, 0x15FFFFFF); // divider
        y += 50;

        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§b§lNote"), contentX, y, 0xFFFFFFFF);
        drawNoteFieldContainer(context, contentX, y + 12, modalW - 24, 18);
        context.fill(modalX + 8, y + 40, modalX + modalW - 8, y + 41, 0x15FFFFFF); // divider
        y += 50;

        if (partyToEdit == null) {
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§b§lReserved Slots"), contentX, y, 0xFFFFFFFF);
            context.fill(modalX + 8, y + 40, modalX + modalW - 8, y + 41, 0x15FFFFFF); // divider
            y += 50;
        }

        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§b§lPing LFG Roles"), contentX, y, 0xFFFFFFFF);

        // Highlight selected activities
        for (int i = 0; i < activityButtons.size(); i++) {
            activityButtons.get(i).setSelected(selectedActivities.contains(ACTIVITIES[i]));
        }

        // Highlight selected regions
        for (int i = 0; i < regionButtons.size(); i++) {
            regionButtons.get(i).setSelected(selectedRegions.contains(REGIONS[i]));
        }

        if (partyToEdit == null) {
            // Highlight selected role
            for (int i = 0; i < roleButtons.size(); i++) {
                roleButtons.get(i).setSelected(ROLE_VALUES[i].equals(selectedRole));
            }

            // Highlight selected reserved count
            int minReserved = countOtherInGameMembers();
            for (int i = 0; i < reservedButtons.size(); i++) {
                reservedButtons.get(i).setSelected((minReserved + i) == reservedSlots);
            }
        }

        // Status message
        if (statusMessage != null) {
            List<net.minecraft.text.OrderedText> wrappedStatus = textRenderer.wrapLines(Text.literal(statusMessage), modalW - 24);
            int statusBottomY = modalY + modalH - 32;
            int statusStartY = statusBottomY - (wrappedStatus.size() * 10 - 2);
            int currentY = statusStartY;
            for (net.minecraft.text.OrderedText line : wrappedStatus) {
                context.drawText(textRenderer, line, modalX + (modalW - textRenderer.getWidth(line)) / 2, currentY, statusColor, true);
                currentY += 10;
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean boolean_arg) {
        double mouseX = click.x();
        double mouseY = click.y();
        // Click outside modal closes it
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            parent.closeModal();
            return true;
        }
        return super.mouseClicked(click, boolean_arg);
    }

    private void drawNoteFieldContainer(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, 0xD5161622);
        int tfBorderColor = noteField.isFocused() ? 0xFF8A9CFE : 0x1A8A9CFE;
        CompatibilityHelper.drawBorder(context, x, y, w, h, tfBorderColor);
    }
}
