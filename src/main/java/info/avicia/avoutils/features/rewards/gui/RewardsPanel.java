package info.avicia.avoutils.features.rewards.gui;

import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.BUTTON_H;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.HEADER_H;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.LINE_H;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.MARGIN;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.PADDING;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.ROW_GAP;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.TAB_W;

import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.FlatButtonWidget;
import info.avicia.avoutils.core.gui.ScrollViewport;
import info.avicia.avoutils.core.gui.UiStyle;
import info.avicia.avoutils.features.rewards.RewardKind;
import info.avicia.avoutils.features.rewards.RewardsFeature;
import info.avicia.avoutils.features.rewards.RewardsFeature.PlayerEntry;
import info.avicia.avoutils.features.rewards.RewardsFeature.Stepper;
import info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.Position;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The pending-rewards panel left of the guild menus: an overlay drawn and driven from AbstractContainerScreenMixin,
 * like a widget the screen doesn't know about. It takes every click inside its bounds, so none reaches the menu as a
 * click outside it. Its state lives in {@link RewardsFeature}; the buttons are rebuilt when that changes.
 */
public final class RewardsPanel {
    private static final int MAX_STATUS_LINES = 6;
    private static final int SMALL_BUTTON_W = 11;
    private static final int PILL_GAP = 3;
    private static final int NAME_H = 11;
    private static final int PLAYER_GAP = 4;
    private static final int TEXT_GRAY = 0xFFA0A5B5;
    private static final int TEXT_DIM = 0xFF6A6F80;
    private static final String HINT = "Lower the GUI scale to see pending rewards.";

    /** Text in the list at its unscrolled position; a chip is a reward's label in a pill frame. */
    private record Text(int x, int y, int width, FormattedCharSequence text, int color, boolean chip) {}

    private record Line(FormattedCharSequence text, int color) {}

    private final RewardsFeature feature;
    private final Font font = Minecraft.getInstance().font;
    private final ScrollViewport viewport = new ScrollViewport();
    private final List<FlatButtonWidget> headerButtons = new ArrayList<>();
    private final List<FlatButtonWidget> listButtons = new ArrayList<>();
    private final List<Text> listTexts = new ArrayList<>();
    private FlatButtonWidget footerButton;
    private FlatButtonWidget expandButton;
    private RewardsPanelLayout layout;
    private int builtVersion = -1;
    private int contentHeight;
    private String title = "";
    private String summary = "";
    private boolean shiftDown;

    public RewardsPanel(RewardsFeature feature) {
        this.feature = feature;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta, RewardsPanelLayout newLayout) {
        if (!newLayout.equals(layout) || builtVersion != feature.version()) {
            rebuild(newLayout);
        }
        if (layout.showsHint()) {
            drawHint(graphics);
            return;
        }
        if (expandButton != null) {
            expandButton.render(graphics, mouseX, mouseY, delta);
            return;
        }

        List<Line> status = wrapStatus();
        int footerTop = layout.footerTop(status.size());
        viewport.update(layout.listTop(), layout.listBottom(status.size()), contentHeight);
        footerButton.setY(footerTop);

        int x = layout.x();
        int y = layout.y();
        int right = layout.right();
        int bottom = layout.bottom();
        graphics.fill(x, y, right, bottom, UiStyle.MODAL_BACKGROUND);
        graphics.fill(x + 1, y + 1, right - 1, y + HEADER_H, UiStyle.MODAL_HEADER);
        CompatibilityHelper.drawBorder(graphics, x, y, right - x, bottom - y, UiStyle.MODAL_OUTLINE);
        graphics.drawString(font, title, x + PADDING, y + 4, UiStyle.TEXT_WHITE);
        graphics.drawString(font, summary, x + PADDING, layout.summaryY(), TEXT_GRAY);
        graphics.fill(x + 1, layout.listTop() - 1, right - 1, layout.listTop(), UiStyle.MODAL_SEPARATOR);
        graphics.fill(x + 1, footerTop - ROW_GAP, right - 1, footerTop - ROW_GAP + 1, UiStyle.MODAL_SEPARATOR);
        for (FlatButtonWidget button : headerButtons) {
            button.render(graphics, mouseX, mouseY, delta);
        }

        viewport.enableScissor(graphics, x + 1, right - 1);
        for (Text text : listTexts) {
            drawText(graphics, text, viewport.getOffset());
        }
        viewport.renderTracked(graphics, mouseX, mouseY, delta);
        graphics.disableScissor();
        viewport.drawScrollbar(graphics, right - 3);

        footerButton.render(graphics, mouseX, mouseY, delta);
        int lineY = layout.statusTop(status.size());
        for (Line line : status) {
            graphics.drawString(font, line.text(), x + PADDING, lineY, line.color());
            lineY += LINE_H;
        }
    }

    /** True if the click was inside the panel, used or not. */
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (layout == null || layout.showsHint()) return false;
        shiftDown = click.hasShiftDown();
        if (expandButton != null) {
            if (!expandButton.isMouseOver(click.x(), click.y())) return false;
            expandButton.mouseClicked(click, doubleClick);
            return true;
        }
        if (!layout.contains(click.x(), click.y())) return false;
        for (FlatButtonWidget button : headerButtons) {
            if (button.mouseClicked(click, doubleClick)) return true;
        }
        if (footerButton.mouseClicked(click, doubleClick)) return true;
        // Buttons scrolled out of the list are clipped there, so they only take clicks inside it
        if (viewport.contains(click.y())) {
            for (FlatButtonWidget button : listButtons) {
                if (button.mouseClicked(click, doubleClick)) return true;
            }
        }
        return true;
    }

    /** Container screens don't pass the wheel to widgets, so the screen mixin brings it here. */
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (layout == null || layout.showsHint() || expandButton != null || !layout.contains(mouseX, mouseY)) {
            return false;
        }
        viewport.scroll(amount);
        return true;
    }

    private void rebuild(RewardsPanelLayout newLayout) {
        layout = newLayout;
        builtVersion = feature.version();
        headerButtons.clear();
        listButtons.clear();
        listTexts.clear();
        viewport.clearTracked();
        footerButton = null;
        expandButton = null;
        if (layout.showsHint()) return;
        if (feature.isCollapsed()) {
            expandButton = new FlatButtonWidget(layout.right() - TAB_W, layout.y(), TAB_W, BUTTON_H + 2,
                    Component.literal("Rewards ▸"), () -> feature.setCollapsed(false));
            return;
        }

        int collapseX = layout.right() - PADDING - SMALL_BUTTON_W;
        int buttonY = layout.y() + 2;
        headerButtons.add(smallButton(collapseX - SMALL_BUTTON_W - 2, buttonY, "↻", feature::refresh,
                "Refresh the list"));
        FlatButtonWidget collapse = smallButton(collapseX, buttonY, feature.isRunActive() ? "§8–" : "–",
                () -> feature.setCollapsed(true), "Hide the panel");
        collapse.active = !feature.isRunActive();
        headerButtons.add(collapse);
        int titleRoom = layout.contentWidth() - 2 * SMALL_BUTTON_W - 6;
        title = font.width("Pending Rewards") <= titleRoom ? "Pending Rewards" : "Rewards";
        summary = font.plainSubstrByWidth(summaryText(), layout.contentWidth());

        if (feature.isRunActive()) {
            footerButton = new FlatButtonWidget(layout.x() + PADDING, 0, layout.contentWidth(), BUTTON_H,
                    Component.literal("Stop"), feature::stopRun);
            footerButton.setDanger(true);
        } else {
            boolean canStart = feature.canStartRun();
            String label = font.width("All emeralds → owner") <= layout.contentWidth() - 6
                    ? "All emeralds → owner" : "Emeralds → owner";
            footerButton = new FlatButtonWidget(layout.x() + PADDING, 0, layout.contentWidth(), BUTTON_H,
                    Component.literal((canStart ? "" : "§8") + label), feature::startEmeraldRun);
            footerButton.active = canStart;
            footerButton.setTooltip(Tooltip.create(Component.literal(
                    "Sends every emerald in guild storage to the first member of the list, the owner.")));
        }
        buildList();
    }

    private void buildList() {
        int left = layout.x() + PADDING;
        int width = layout.contentWidth() - 4; // room for the scrollbar
        int y = layout.listTop() + 2;
        List<PlayerEntry> players = feature.players();
        if (players.isEmpty()) {
            String message = feature.listError() != null ? feature.listError()
                    : feature.isLoading() ? "Loading…" : "Nobody is owed anything.";
            for (FormattedCharSequence line : font.split(Component.literal(message), width)) {
                listTexts.add(new Text(left, y, width, line, TEXT_GRAY, false));
                y += LINE_H;
            }
        }
        Stepper stepper = feature.stepper();
        for (PlayerEntry player : players) {
            String name = player.displayName() + (player.inGuild() ? "" : " · left");
            listTexts.add(new Text(left, y + 1, width, text(font.plainSubstrByWidth(name, width)),
                    player.inGuild() ? UiStyle.TEXT_WHITE : TEXT_DIM, false));
            y = addPills(player, left, y + NAME_H, width);
            if (stepper != null && stepper.uuid().equals(player.uuid())) {
                y = addStepper(stepper, left, y, width);
            }
            y += PLAYER_GAP;
        }
        contentHeight = y - layout.listTop();
    }

    /** A pill per reward: its label, ▶ to hand it out if the mod can, ✓ to record it by hand. */
    private int addPills(PlayerEntry player, int left, int y, int width) {
        List<RewardKind> kinds = List.copyOf(player.rewards().keySet());
        List<Integer> widths = new ArrayList<>();
        for (RewardKind kind : kinds) {
            widths.add(font.width(kind.label(player.rewards().get(kind))) + 6 + 1
                    + (kind.handedOutByMod() ? SMALL_BUTTON_W + 1 : 0) + SMALL_BUTTON_W);
        }
        List<Position> positions = RewardsPanelLayout.flow(widths, width, PILL_GAP);
        int rows = 0;
        for (int i = 0; i < kinds.size(); i++) {
            RewardKind kind = kinds.get(i);
            long amount = player.rewards().get(kind);
            int x = left + positions.get(i).x();
            int pillY = y + positions.get(i).row() * (BUTTON_H + ROW_GAP);
            String label = kind.label(amount);
            int chipWidth = font.width(label) + 6;
            listTexts.add(new Text(x, pillY, chipWidth, text(label),
                    player.inGuild() ? UiStyle.TEXT_WHITE : TEXT_DIM, true));
            x += chipWidth + 1;
            if (kind.handedOutByMod()) {
                boolean canStart = player.inGuild() && feature.canStartRun();
                String tooltip = !player.inGuild() ? "They left the guild, so the mod can't hand these out."
                        : canStart ? "Hand out " + label + " now" : "Another hand-out is running.";
                FlatButtonWidget start = track(smallButton(x, pillY, canStart ? "▶" : "§8▶",
                        () -> feature.startRun(player, kind), tooltip));
                start.active = canStart;
                x += SMALL_BUTTON_W + 1;
            }
            track(smallButton(x, pillY, "✓", () -> feature.openStepper(player, kind), "Record a hand-out by hand"));
            rows = Math.max(rows, positions.get(i).row() + 1);
        }
        return y + rows * (BUTTON_H + ROW_GAP);
    }

    /** [−] amount [+], then [Record] [Cancel]. There's no text field, since typing E would close the menu. */
    private int addStepper(Stepper stepper, int left, int y, int width) {
        String amount = stepper.kind().label(stepper.amount());
        int amountWidth = font.width(amount);
        track(smallButton(left, y, "−", () -> feature.stepBy(-step()), "Shift: 10 at a time"));
        listTexts.add(new Text(left + SMALL_BUTTON_W + 4, y + 2, amountWidth, text(amount), UiStyle.TEXT_WHITE,
                false));
        track(smallButton(left + SMALL_BUTTON_W + 8 + amountWidth, y, "+", () -> feature.stepBy(step()),
                "Shift: 10 at a time"));
        y += BUTTON_H + ROW_GAP;
        int half = (width - 2) / 2;
        track(new FlatButtonWidget(left, y, half, BUTTON_H, Component.literal("§aRecord"), feature::recordStepper));
        track(new FlatButtonWidget(left + half + 2, y, half, BUTTON_H, Component.literal("Cancel"),
                feature::closeStepper));
        return y + BUTTON_H + ROW_GAP;
    }

    private int step() {
        return shiftDown ? 10 : 1;
    }

    private FlatButtonWidget track(FlatButtonWidget button) {
        listButtons.add(viewport.track(button));
        return button;
    }

    private static FlatButtonWidget smallButton(int x, int y, String label, Runnable action, String tooltip) {
        FlatButtonWidget button = new FlatButtonWidget(x, y, SMALL_BUTTON_W, BUTTON_H, Component.literal(label),
                action);
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return button;
    }

    private String summaryText() {
        List<PlayerEntry> players = feature.players();
        if (players.isEmpty()) return "";
        long[] totals = new long[RewardKind.values().length];
        for (PlayerEntry player : players) {
            player.rewards().forEach((kind, amount) -> totals[kind.ordinal()] += amount);
        }
        StringBuilder text = new StringBuilder(players.size() + (players.size() == 1 ? " player" : " players"));
        for (RewardKind kind : RewardKind.values()) {
            if (totals[kind.ordinal()] > 0) {
                text.append(" · ").append(totals[kind.ordinal()]).append(' ').append(kind.plural());
            }
        }
        return text.toString();
    }

    private List<Line> wrapStatus() {
        List<Line> lines = new ArrayList<>();
        for (RewardsFeature.StatusLine status : feature.statusLines()) {
            for (FormattedCharSequence part : font.split(Component.literal(status.text()), layout.contentWidth())) {
                if (lines.size() == MAX_STATUS_LINES) return lines;
                lines.add(new Line(part, status.color()));
            }
        }
        return lines;
    }

    private void drawText(GuiGraphics graphics, Text text, int offset) {
        int y = text.y() - offset;
        if (text.chip()) {
            graphics.fill(text.x(), y, text.x() + text.width(), y + BUTTON_H, UiStyle.CARD_BACKGROUND);
            CompatibilityHelper.drawBorder(graphics, text.x(), y, text.width(), BUTTON_H, UiStyle.BORDER_FAINT);
            graphics.drawString(font, text.text(), text.x() + 3, y + 2, text.color());
        } else {
            graphics.drawString(font, text.text(), text.x(), y, text.color());
        }
    }

    private void drawHint(GuiGraphics graphics) {
        int width = layout.leftPos() - 2 * MARGIN;
        if (width < 30) return;
        int y = layout.y();
        for (FormattedCharSequence line : font.split(Component.literal(HINT), width)) {
            graphics.drawString(font, line, MARGIN, y, TEXT_GRAY);
            y += LINE_H;
        }
    }

    private static FormattedCharSequence text(String text) {
        return Component.literal(text).getVisualOrderText();
    }
}
