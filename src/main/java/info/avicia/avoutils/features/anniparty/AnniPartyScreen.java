package info.avicia.avoutils.features.anniparty;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.FlatButtonWidget;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Displays the live Anni party roster from the backend.
 * <ul>
 *   <li>Renders only parties that actually have members assigned.</li>
 *   <li>Each party is a collapsible header row (click to expand/collapse; several
 *       may be open at once).</li>
 *   <li>Expanded parties show the member list (name, role, in-party status) and,
 *       for the local player's <b>led</b> party, manual toggle checkboxes.</li>
 *   <li>Highlights the party the local player is assigned to.</li>
 * </ul>
 * The roster is kept live by listening for {@code anni_roster_sync} broadcasts and is
 * initially requested via {@code req_anni_roster} when the screen opens.
 */
public class AnniPartyScreen extends Screen {

    private static final String EVT_ANNI_ROSTER_SYNC = "anni_roster_sync";
    private static final String REQ_ANNI_ROSTER = "req_anni_roster";
    private static final String EVT_MOD_PARTY_UPDATE = "mod_party_update";

    private AnniPartyDetector detector;
    private final Gson gson = new Gson();

    private AnniRoster roster = new AnniRoster();
    private boolean active = false;

    private int scrollOffset = 0;
    private static final int LIST_TOP = 42;
    private static final int SIDE_PADDING = 14;
    private static final int HEADER_HEIGHT = 26;
    private static final int MEMBER_ROW_HEIGHT = 14;
    private static final int PARTY_GAP = 4;

    private final Set<Integer> expandedPartyIds = new HashSet<>();
    private final List<FlatButtonWidget> toggleButtons = new ArrayList<>();
    private final List<HeaderHitbox> headerHitboxes = new ArrayList<>();

    public AnniPartyScreen(AnniPartyDetector detector) {
        super(Text.literal("Anni Parties"));
        this.detector = detector;
    }

    @Override
    protected void init() {
        // Register WebSocket listener for live roster updates
        AvoWebSocketManager.getInstance().unregisterListener(EVT_ANNI_ROSTER_SYNC, rosterListener);
        AvoWebSocketManager.getInstance().registerListener(EVT_ANNI_ROSTER_SYNC, rosterListener);

        // Request the cached roster from the backend
        AvoWebSocketManager.getInstance().sendEvent(REQ_ANNI_ROSTER, new JsonObject());

        rebuildWidgets();
    }

    private List<AnniPartyData> activeParties() {
        List<AnniPartyData> result = new ArrayList<>();
        if (roster == null || !roster.active) {
            return result;
        }
        for (AnniPartyData party : roster.getParties()) {
            if (!party.getMembers().isEmpty()) {
                result.add(party);
            }
        }
        return result;
    }

    private boolean isExpanded(AnniPartyData party) {
        return expandedPartyIds.contains(party.party_id);
    }

    private int partyHeight(AnniPartyData party) {
        int h = HEADER_HEIGHT;
        if (isExpanded(party)) {
            h += party.getMembers().size() * MEMBER_ROW_HEIGHT;
        }
        return h;
    }

    private int partyTop(int index) {
        int y = LIST_TOP;
        List<AnniPartyData> parties = activeParties();
        for (int i = 0; i < index && i < parties.size(); i++) {
            y += partyHeight(parties.get(i)) + PARTY_GAP;
        }
        return y;
    }

    private int contentHeight() {
        int total = 0;
        List<AnniPartyData> parties = activeParties();
        for (int i = 0; i < parties.size(); i++) {
            total += partyHeight(parties.get(i));
            if (i < parties.size() - 1) {
                total += PARTY_GAP;
            }
        }
        return total;
    }

    private void rebuildWidgets() {
        this.clearChildren();
        this.headerHitboxes.clear();
        this.toggleButtons.clear();

        addDrawableChild(new FlatButtonWidget(SIDE_PADDING, 8, 80, 20, Text.literal("⟳ Refresh"), this::requestRoster));

        List<AnniPartyData> parties = activeParties();
        if (parties.isEmpty()) {
            return;
        }

        // Drop expand state for parties that no longer exist
        Set<Integer> validIds = new HashSet<>();
        for (AnniPartyData p : parties) {
            validIds.add(p.party_id);
        }
        expandedPartyIds.retainAll(validIds);

        String selfName = getSelfName();
        AnniPartyData ledParty = roster.findLedParty(selfName);
        int listBottom = height - 10;

        for (int i = 0; i < parties.size(); i++) {
            AnniPartyData party = parties.get(i);
            int top = partyTop(i) - scrollOffset;
            int headerBottom = top + HEADER_HEIGHT;

            if (headerBottom > LIST_TOP && top < listBottom) {
                headerHitboxes.add(new HeaderHitbox(party.party_id, SIDE_PADDING, top, width - 2 * SIDE_PADDING, HEADER_HEIGHT));
            }

            // Leader-only toggle checkboxes, rendered only while the party is expanded
            if (ledParty != null && ledParty.party_id == party.party_id && isExpanded(party)) {
                int memberTop = top + HEADER_HEIGHT;
                for (int m = 0; m < party.getMembers().size(); m++) {
                    AnniMemberData member = party.getMembers().get(m);
                    if (selfName != null && member.name != null && member.name.equalsIgnoreCase(selfName)) {
                        continue; // leaders don't toggle themselves
                    }
                    int btnY = memberTop + (m * MEMBER_ROW_HEIGHT) + 2;
                    String label = member.in_party ? "✓" : "✗";
                    FlatButtonWidget toggle = new FlatButtonWidget(
                            width - SIDE_PADDING - 26, btnY, 18, 10, Text.literal(label), () -> toggleMember(party, member));
                    toggle.setSelected(member.in_party);
                    toggle.setSelectedColors(0xFF00FF66, 0x2200FF66, 0xFF00FF66);
                    addDrawableChild(toggle);
                    toggleButtons.add(toggle);
                }
            }
        }
    }

    private void requestRoster() {
        AvoWebSocketManager.getInstance().sendEvent(REQ_ANNI_ROSTER, new JsonObject());
    }

    private void toggleMember(AnniPartyData party, AnniMemberData member) {
        boolean newState = !member.in_party;
        member.in_party = newState; // optimistic local update

        JsonObject setEntry = new JsonObject();
        setEntry.addProperty("name", member.name);
        setEntry.addProperty("in_party", newState);

        JsonArray setArr = new JsonArray();
        setArr.add(setEntry);

        JsonObject payload = new JsonObject();
        payload.addProperty("party_id", party.party_id);
        payload.add("set", setArr);

        AvoWebSocketManager.getInstance().sendEvent(EVT_MOD_PARTY_UPDATE, payload);
        rebuildWidgets();
    }

    private final java.util.function.Consumer<JsonObject> rosterListener = json -> {
        AnniRoster newRoster = gson.fromJson(json, AnniRoster.class);
        if (newRoster != null) {
            roster = newRoster;
            active = newRoster.active;
            if (detector != null) {
                detector.onRosterUpdated(newRoster);
            }
            rebuildWidgets();
        }
    };

    @Override
    public void close() {
        AvoWebSocketManager.getInstance().unregisterListener(EVT_ANNI_ROSTER_SYNC, rosterListener);
        super.close();
    }

    private String getSelfName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.getSession() != null ? mc.getSession().getUsername() : null;
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xD80A0A0F);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("§b§lAVICIA §f§lANNI PARTIES"), width / 2, 12, 0xFFFFFFFF);

        List<AnniPartyData> parties = activeParties();
        if (parties.isEmpty()) {
            String msg = active ? "§7No parties assigned yet..." : "§7No active Anni event. Parties will appear once generated.";
            int textWidth = textRenderer.getWidth(Text.literal(msg));
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal(msg), (width - textWidth) / 2, height / 2, 0xFFFFFFFF);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        String selfName = getSelfName();
        AnniPartyData myParty = roster.findPartyContaining(selfName);
        int listBottom = height - 10;

        for (int i = 0; i < parties.size(); i++) {
            AnniPartyData party = parties.get(i);
            int top = partyTop(i) - scrollOffset;
            int headerBottom = top + HEADER_HEIGHT;
            if (headerBottom < LIST_TOP || top > listBottom) {
                continue;
            }

            boolean isMine = myParty != null && myParty.party_id == party.party_id;
            boolean expanded = isExpanded(party);

            // Header card
            context.fill(SIDE_PADDING + 1, top + 1, width - SIDE_PADDING + 1, headerBottom - 3, 0x3F000000);
            context.fill(SIDE_PADDING, top, width - SIDE_PADDING, headerBottom - 2, isMine ? 0xF2253530 : 0xD5161622);
            context.fill(SIDE_PADDING + 1, top + 1, SIDE_PADDING + 4, headerBottom - 3, isMine ? 0xFF00FF66 : 0xFF8A9CFE);
            CompatibilityHelper.drawBorder(context, SIDE_PADDING, top, width - 2 * SIDE_PADDING, HEADER_HEIGHT - 2, isMine ? 0x6600FF66 : 0x1A8A9CFE);

            int leftTextX = SIDE_PADDING + 12;
            String indicator = expanded ? "§b▼" : "§b▶";
            String header = indicator + " §b§lParty " + party.party_id + " §7(" + party.getMembers().size() + "/10)  §7World: §e" + party.getServerDisplay();
            if (isMine) {
                header += " §7← You";
            }
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal(header), leftTextX, top + 9, 0xFFFFFFFF);

            // Leader name, right-aligned on the same header row
            String leaderName = "?";
            for (AnniMemberData m : party.getMembers()) {
                if (m.is_leader) {
                    leaderName = m.getDisplayName();
                    break;
                }
            }
            String leaderText = "§7Leader: §f" + leaderName;
            int leaderWidth = textRenderer.getWidth(Text.literal(leaderText));
            int leaderX = width - SIDE_PADDING - 12 - leaderWidth;
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal(leaderText), leaderX, top + 9, 0xFFFFFFFF);

            // Member rows
            if (expanded) {
                int memberTop = top + HEADER_HEIGHT;
                for (int m = 0; m < party.getMembers().size(); m++) {
                    AnniMemberData member = party.getMembers().get(m);
                    int lineY = memberTop + (m * MEMBER_ROW_HEIGHT);
                    if (lineY + MEMBER_ROW_HEIGHT < LIST_TOP || lineY > listBottom) {
                        continue;
                    }
                    String check = member.in_party ? "§a✓" : "§c✗";
                    String role = member.role != null && !member.role.isBlank() ? member.role : "No role";
                    String line = check + " §f" + member.getDisplayName() + " §7(" + role + ")";
                    if (member.is_leader) {
                        line += " §b[L]";
                    }
                    if (member.name != null && selfName != null && member.name.equalsIgnoreCase(selfName)) {
                        line += " §e← You";
                    }
                    CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal(line), leftTextX + 8, lineY, 0xFFFFFFFF);
                }
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    // ── Input handling ───────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, contentHeight() - (height - LIST_TOP - 10));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (verticalAmount * 20)));
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean boolean_arg) {
        if (click.button() == 0) {
            for (HeaderHitbox hitbox : headerHitboxes) {
                if (hitbox.contains(click.x(), click.y())) {
                    if (expandedPartyIds.contains(hitbox.partyId)) {
                        expandedPartyIds.remove(hitbox.partyId);
                    } else {
                        expandedPartyIds.add(hitbox.partyId);
                    }
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, boolean_arg);
    }

    private static final class HeaderHitbox {
        final int partyId;
        final int x;
        final int y;
        final int width;
        final int height;

        HeaderHitbox(int partyId, int x, int y, int width, int height) {
            this.partyId = partyId;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
