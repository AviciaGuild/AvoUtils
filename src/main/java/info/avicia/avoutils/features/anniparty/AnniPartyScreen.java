package info.avicia.avoutils.features.anniparty;

import com.google.gson.JsonObject;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.FlatButtonWidget;
import info.avicia.avoutils.core.gui.ScrollableListScreen;
import info.avicia.avoutils.core.gui.UiStyle;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import info.avicia.avoutils.core.party.InviteHandler;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Displays the live Anni party roster from the backend. The roster is held by
 * {@link AnniPartyFeature}; this screen subscribes to roster changes and renders read-only,
 * collapsible party cards (members, role build strings, in-party status, server, leader). When no
 * event is active, no parties are rendered and an empty-state message is shown.
 */
public class AnniPartyScreen extends ScrollableListScreen {

    private static final String REQ_ANNI_ROSTER = "req_anni_roster";

    private final AnniPartyFeature feature;
    private final InviteHandler inviteHandler;
    private final Consumer<AnniRoster> rosterListener = this::onRosterUpdated;

    private static final int LIST_TOP = 42;
    private static final int MAX_ANNI_PARTY_SIZE = 10;
    private static final int SIDE_PADDING = 14;
    private static final int HEADER_HEIGHT = 26;
    private static final int MEMBER_ROW_HEIGHT = 14;
    private static final int PARTY_GAP = 4;

    private final Set<Long> expandedPartyIds = new HashSet<>();
    private final List<HeaderHitbox> headerHitboxes = new ArrayList<>();

    public AnniPartyScreen(AnniPartyFeature feature, InviteHandler inviteHandler) {
        super(Text.literal("Anni Parties"));
        this.feature = feature;
        this.inviteHandler = inviteHandler;
    }

    @Override
    protected void init() {
        feature.addRosterListener(rosterListener);
        AvoWebSocketManager.getInstance().sendEvent(REQ_ANNI_ROSTER, new JsonObject());
        rebuildWidgets();
    }

    @Override
    public void close() {
        feature.removeRosterListener(rosterListener);
        super.close();
    }

    private AnniRoster roster() {
        return feature.getRoster();
    }

    private List<AnniPartyData> activeParties() {
        List<AnniPartyData> result = new ArrayList<>();
        AnniRoster roster = roster();
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

    private void onRosterUpdated(AnniRoster updatedRoster) {
        rebuildWidgets();
    }

    private boolean isExpanded(AnniPartyData party) {
        return expandedPartyIds.contains(party.partyId);
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

        addDrawableChild(new FlatButtonWidget(SIDE_PADDING, 8, 80, 20, Text.literal("⟳ Refresh"), this::requestRoster));

        if (feature.getLedParty() != null) {
            addDrawableChild(new FlatButtonWidget(width - SIDE_PADDING - 80, 8, 80, 20, Text.literal("Invite All"), this::inviteAll));
        }

        List<AnniPartyData> parties = activeParties();
        if (parties.isEmpty()) {
            expandedPartyIds.clear();
            return;
        }

        // Drop expand state for parties that no longer exist
        Set<Long> validIds = new HashSet<>();
        for (AnniPartyData p : parties) {
            validIds.add(p.partyId);
        }
        expandedPartyIds.retainAll(validIds);

        int listBottom = height - 10;
        for (int i = 0; i < parties.size(); i++) {
            AnniPartyData party = parties.get(i);
            int top = partyTop(i) - scrollOffset;
            int headerBottom = top + HEADER_HEIGHT;
            if (headerBottom > LIST_TOP && top < listBottom) {
                headerHitboxes.add(new HeaderHitbox(party.partyId, SIDE_PADDING, top, width - 2 * SIDE_PADDING, HEADER_HEIGHT));
            }
        }
    }

    private void requestRoster() {
        AvoWebSocketManager.getInstance().sendEvent(REQ_ANNI_ROSTER, new JsonObject());
    }

    private void inviteAll() {
        AnniPartyData ledParty = feature.getLedParty();
        if (ledParty == null) {
            return;
        }
        String selfName = PlayerUtil.selfName();
        List<String> names = new ArrayList<>();
        for (AnniMemberData member : ledParty.getMembers()) {
            if (member.name == null || member.name.isEmpty()) {
                continue;
            }
            if (member.inParty) {
                continue; // already in the in-game party
            }
            if (selfName != null && member.name.equalsIgnoreCase(selfName)) {
                continue; // skip the leader (self)
            }
            names.add(member.name);
        }
        inviteHandler.queueInvites(names);
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiStyle.SCREEN_BACKGROUND);
        CompatibilityHelper.drawScreenTitle(context, textRenderer, width, "§b§lAVICIA §f§lANNI PARTIES");

        AnniRoster roster = roster();
        boolean active = roster != null && roster.active;
        List<AnniPartyData> parties = activeParties();

        if (parties.isEmpty()) {
            String msg = active ? "§7No parties assigned yet..." : "§7No active Anni event. Parties will appear once generated.";
            CompatibilityHelper.drawCenteredMessage(context, textRenderer, width, height, msg);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        String selfName = PlayerUtil.selfName();
        AnniPartyData myParty = roster.findPartyContaining(selfName);
        int listBottom = height - 10;

        for (int i = 0; i < parties.size(); i++) {
            AnniPartyData party = parties.get(i);
            int top = partyTop(i) - scrollOffset;
            int headerBottom = top + HEADER_HEIGHT;
            if (headerBottom < LIST_TOP || top > listBottom) {
                continue;
            }

            boolean isMine = myParty != null && myParty.partyId == party.partyId;
            boolean expanded = isExpanded(party);

            // Header card
            context.fill(SIDE_PADDING + 1, top + 1, width - SIDE_PADDING + 1, headerBottom - 3, UiStyle.CARD_SHADOW);
            context.fill(SIDE_PADDING, top, width - SIDE_PADDING, headerBottom - 2, isMine ? UiStyle.CARD_BACKGROUND_ACTIVE : UiStyle.CARD_BACKGROUND);
            context.fill(SIDE_PADDING + 1, top + 1, SIDE_PADDING + 4, headerBottom - 3, isMine ? UiStyle.ACCENT_GREEN : UiStyle.ACCENT_BLUE);
            CompatibilityHelper.drawBorder(context, SIDE_PADDING, top, width - 2 * SIDE_PADDING, HEADER_HEIGHT - 2, isMine ? UiStyle.BORDER_GREEN : UiStyle.BORDER_FAINT);

            int leftTextX = SIDE_PADDING + 12;
            String indicator = expanded ? "§b▼" : "§b▶";
            String header = indicator + " §b§lParty " + party.partyId + " §7(" + party.getMembers().size() + "/" + MAX_ANNI_PARTY_SIZE + ")  §7World: §e" + party.getServerDisplay();
            if (isMine) {
                header += " §7← You";
            }
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, Text.literal(header), leftTextX, top + 9, 0xFFFFFFFF);

            // Leader name
            String leaderName = "?";
            for (AnniMemberData m : party.getMembers()) {
                if (m.isLeader) {
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
                    String check = member.inParty ? "§a✔" : "§c✕";
                    String role = member.role != null && !member.role.isBlank() ? member.role : "No role";
                    String line = check + " §f" + member.getDisplayName() + " §7(" + role + ")";
                    if (member.isLeader) {
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
        applyScroll(contentHeight(), LIST_TOP, verticalAmount);
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
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
        return super.mouseClicked(click, doubleClick);
    }

    private static final class HeaderHitbox {
        final long partyId;
        final int x;
        final int y;
        final int width;
        final int height;

        HeaderHitbox(long partyId, int x, int y, int width, int height) {
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
