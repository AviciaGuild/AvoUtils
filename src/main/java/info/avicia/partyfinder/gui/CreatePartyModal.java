package info.avicia.partyfinder.gui;

import info.avicia.partyfinder.api.PartyData;
import info.avicia.partyfinder.api.PartyFinderClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Modal overlay for creating or editing a party.
 */
public class CreatePartyModal extends Screen {

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

    private String statusMessage = null;
    private int statusColor = 0xFFFFFF;
    private boolean submitting = false;

    // Button references for visual toggling
    private final List<ButtonWidget> activityButtons = new ArrayList<>();
    private final List<ButtonWidget> roleButtons = new ArrayList<>();
    private final List<ButtonWidget> regionButtons = new ArrayList<>();
    private final List<ButtonWidget> reservedButtons = new ArrayList<>();

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
        modalH = Math.min(partyToEdit != null ? 220 : 340, height - 40);
        modalX = (width - modalW) / 2;
        modalY = (height - modalH) / 2;

        int contentX = modalX + 12;
        int y = modalY + 28;

        // ── Activity selection ───────────────────────────────────────────
        y += 14;
        int btnX = contentX;
        activityButtons.clear();
        for (String activity : ACTIVITIES) {
            ButtonWidget btn = ButtonWidget.builder(Text.literal(activity), b -> toggleActivity(activity))
                    .dimensions(btnX, y, 50, 18)
                    .build();
            activityButtons.add(btn);
            addDrawableChild(btn);
            btnX += 55;
        }
        y += 26;

        if (partyToEdit == null) {
            // ── Role selection ───────────────────────────────────────────────
            y += 14;
            btnX = contentX;
            roleButtons.clear();
            for (int i = 0; i < ROLES.length; i++) {
                final String roleVal = ROLE_VALUES[i];
                ButtonWidget btn = ButtonWidget.builder(Text.literal(ROLES[i]), b -> selectRole(roleVal))
                        .dimensions(btnX, y, 55, 18)
                        .build();
                roleButtons.add(btn);
                addDrawableChild(btn);
                btnX += 60;
            }
            y += 26;
        }

        // ── Region buttons ─────────────────────────────────────────────────
        y += 14;
        btnX = contentX;
        regionButtons.clear();
        for (String reg : REGIONS) {
            final String r = reg;
            ButtonWidget btn = ButtonWidget.builder(Text.literal(reg), b -> toggleRegion(r))
                    .dimensions(btnX, y, 50, 18)
                    .build();
            regionButtons.add(btn);
            addDrawableChild(btn);
            btnX += 55;
        }
        y += 26;

        // ── Note field ───────────────────────────────────────────────────
        y += 14;
        noteField = new TextFieldWidget(textRenderer, contentX, y, modalW - 24, 16, Text.literal("Note"));
        noteField.setPlaceholder(Text.literal("Note (optional)"));
        noteField.setMaxLength(100);
        if (partyToEdit != null) {
            noteField.setText(partyToEdit.note != null ? partyToEdit.note : "");
        }
        addDrawableChild(noteField);
        y += 24;

        if (partyToEdit == null) {
            // ── Reserved slots ───────────────────────────────────────────────
            y += 14;
            btnX = contentX;
            reservedButtons.clear();

            int minReserved = countOtherInGameMembers();
            if (reservedSlots < minReserved) {
                reservedSlots = minReserved;
            }

            for (int i = minReserved; i <= 3; i++) {
                final int slots = i;
                ButtonWidget btn = ButtonWidget.builder(Text.literal(String.valueOf(i)), b -> selectReserved(slots))
                        .dimensions(btnX, y, 30, 18)
                        .build();
                reservedButtons.add(btn);
                addDrawableChild(btn);
                btnX += 35;
            }
            y += 26;
        }

        // ── Submit / Cancel ──────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal(partyToEdit != null ? "§aSave" : "§aCreate"), btn -> submit())
                .dimensions(modalX + modalW / 2 - 70, modalY + modalH - 30, 60, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> parent.closeModal())
                .dimensions(modalX + modalW / 2 + 10, modalY + modalH - 30, 60, 20)
                .build());
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
                    note
            ).thenAccept(resp -> {
                MinecraftClient.getInstance().execute(() -> {
                    submitting = false;
                    if (resp.ok) {
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
                    reservedSlots
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
        // Modal background
        context.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xEE1A1A2E);

        // Border
        int borderColor = 0xFF5555AA;
        context.fill(modalX, modalY, modalX + modalW, modalY + 1, borderColor);
        context.fill(modalX, modalY + modalH - 1, modalX + modalW, modalY + modalH, borderColor);
        context.fill(modalX, modalY, modalX + 1, modalY + modalH, borderColor);
        context.fill(modalX + modalW - 1, modalY, modalX + modalW, modalY + modalH, borderColor);

        // Title
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(partyToEdit != null ? "§b§lEdit Party" : "§b§lCreate Party"), width / 2, modalY + 8, 0xFFFFFFFF);

        int contentX = modalX + 12;
        int y = modalY + 28;

        // Labels
        CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§eActivity:"), contentX, y, 0xFFFFFFFF);

        if (partyToEdit != null) {
            y += 14 + 26;
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§eRegion:"), contentX, y, 0xFFFFFFFF);
            y += 14 + 26;
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§eNote:"), contentX, y, 0xFFFFFFFF);
        } else {
            y += 14 + 26;
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§eRole:"), contentX, y, 0xFFFFFFFF);
            y += 14 + 26;
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§eRegion:"), contentX, y, 0xFFFFFFFF);
            y += 14 + 26;
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§eNote:"), contentX, y, 0xFFFFFFFF);
            y += 14 + 24;
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal("§eReserved Slots:"), contentX, y, 0xFFFFFFFF);
        }

        // Highlight selected activities
        for (int i = 0; i < activityButtons.size(); i++) {
            ButtonWidget btn = activityButtons.get(i);
            boolean selected = selectedActivities.contains(ACTIVITIES[i]);
            btn.setMessage(Text.literal((selected ? "§a§l" : "§7") + ACTIVITIES[i]));
        }

        // Highlight selected regions
        for (int i = 0; i < regionButtons.size(); i++) {
            ButtonWidget btn = regionButtons.get(i);
            boolean selected = selectedRegions.contains(REGIONS[i]);
            btn.setMessage(Text.literal((selected ? "§a§l" : "§7") + REGIONS[i]));
        }

        if (partyToEdit == null) {
            // Highlight selected role
            for (int i = 0; i < roleButtons.size(); i++) {
                boolean selected = ROLE_VALUES[i].equals(selectedRole);
                roleButtons.get(i).setMessage(Text.literal((selected ? "§a§l" : "§7") + ROLES[i]));
            }

            // Highlight selected reserved count
            int minReserved = countOtherInGameMembers();
            for (int i = 0; i < reservedButtons.size(); i++) {
                ButtonWidget btn = reservedButtons.get(i);
                int val = minReserved + i;
                boolean selected = (val == reservedSlots);
                btn.setMessage(Text.literal((selected ? "§a§l" : "§7") + val));
            }
        }

        // Status message
        if (statusMessage != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(statusMessage),
                    width / 2, modalY + modalH - 48, statusColor);
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
}
