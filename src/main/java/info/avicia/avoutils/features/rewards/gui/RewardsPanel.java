package info.avicia.avoutils.features.rewards.gui;

import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.BAR_GAP;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.BAR_H;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.BORDER;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.BUTTON_H;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.EDITOR_H;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.HEADER_H;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.LINE_H;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.MARGIN;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.PLAY_SIZE;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.ROW_GAP;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.ROW_H;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.SMALL_BUTTON;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.TABLE_HEAD_H;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.TAB_W;
import static info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.TOTALS_H;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.FlatSliderWidget;
import info.avicia.avoutils.core.gui.ScrollViewport;
import info.avicia.avoutils.core.gui.UiIcons;
import info.avicia.avoutils.core.gui.UiStyle;
import info.avicia.avoutils.features.rewards.RewardKind;
import info.avicia.avoutils.features.rewards.RewardsFeature;
import info.avicia.avoutils.features.rewards.RewardsFeature.PlayerEntry;
import info.avicia.avoutils.features.rewards.RewardsFeature.Stepper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * The pending-rewards panel left of the guild menus: an overlay drawn and driven from AbstractContainerScreenMixin,
 * like a widget the screen doesn't know about. It takes every click inside its bounds, so none reaches the menu as a
 * click outside it. Its state lives in {@link RewardsFeature}; the buttons are rebuilt when that changes.
 *
 * <p>A table with a row per player and their LE, tomes and aspects in columns, plus indented sub-rows beneath the
 * player for any freeform item rewards. Clicking a number opens a slider under its row to record a hand-out by hand;
 * ▶ hands out tomes or aspects. Its text uses AvoUtils' dedicated compact Unifont definition ({@code avoutils:compact})
 * so server resource packs cannot override it, and its icons are drawn via {@link UiIcons} at half-GUI-pixel
 * resolution.
 */
public final class RewardsPanel {
    /** Longer status text is cut, and shown whole when hovered. */
    private static final int MAX_STATUS_LINES = 3;
    private static final int TOOLTIP_WIDTH = 220;
    private static final Style UI = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath(AvoUtilsMod.MOD_ID, "compact")));
    /** Unifont's capitals are this tall and this far below the top of a line, which is what's centred in a box. */
    private static final int CAP_H = 5;
    private static final int CAP_TOP = 2;
    private static final int TEXT_GRAY = 0xFFA0A5B5;
    private static final int TEXT_DIM = 0xFF6A6F80;
    private static final int TEXT_EMPTY = 0xFF3A3D50;
    private static final int TEXT_OFF = 0xFF55586A;
    private static final int ROW_ODD = 0xFF121219;
    private static final int ROW_HOVER = 0xFF1A1A26;
    private static final int ROW_ACTIVE = 0xFF1C2033;
    private static final int CELL_HOVER = 0xFF25253A;
    private static final int CELL_OUTLINE = 0x598A9CFE;
    private static final int BUTTON_BACKGROUND = 0xFF161622;
    private static final int BUTTON_HOVER = 0xFF25253A;
    private static final int BUTTON_OFF = 0xFF111118;
    private static final int BUTTON_BORDER = 0x248A9CFE;
    private static final int EDITOR_BACKGROUND = 0xFF161622;
    private static final int EDITOR_LINE = 0x1A8A9CFE;
    private static final int TAG_BORDER = 0xFF33364A;
    private static final int STOP_COLOR = 0xFFFF6B6B;
    private static final int CHECK_COLOR = 0xFF55FF55;
    private static final int CUSTOM_COLOR = 0xFFD8B4FE;
    /** Indentation from {@code tableLeft()} for freeform item names under a player row. */
    private static final int SUBROW_INDENT = 8;
    /** The slider's line: the reward's name gets between {@code EDITOR_LABEL_W} and {@code MAX_EDITOR_LABEL_W}. */
    private static final int EDITOR_LABEL_W = 16;
    private static final int MAX_EDITOR_LABEL_W = 42;
    private static final int EDITOR_VALUE_W = 18;
    private static final int EDITOR_GAP = 4;
    private static final List<RewardKind> COLUMNS = List.of(RewardKind.LE, RewardKind.TOME, RewardKind.ASPECT);
    private static final String HINT = "Lower the GUI scale to see pending rewards.";
    private static final String EMERALDS_TOOLTIP =
            "Sends all emeralds in guild storage to the owner.";
    private static final String HAND_OUT_ALL_TOOLTIP =
            "Hands out every tome and aspect on the list. LE and other items must be recorded by hand.";

    /** A player's row at its unscrolled position. */
    private record Row(int y, PlayerEntry player, boolean odd) {}

    /** An indented sub-row under a player for a freeform (non-LE/Tome/Aspect) reward. */
    private record CustomRow(int y, PlayerEntry player, RewardKind kind, long amount, int countW, boolean odd) {}

    /** The slider's line at its unscrolled position: where its reward and its amount go. */
    private record Editor(int y, RewardKind kind, String labelText, long max, int labelX, int valueRight) {}

    private record Line(String text, int color) {}

    /** The status lines that fit, and all of it for a tooltip when some had to be cut (null otherwise). */
    private record Status(List<Line> lines, List<FormattedCharSequence> whole) {}

    private final RewardsFeature feature;
    private final Font font = Minecraft.getInstance().font;
    private final ScrollViewport viewport = new ScrollViewport();
    private final List<AbstractWidget> headerButtons = new ArrayList<>();
    private final List<AbstractWidget> footerButtons = new ArrayList<>();
    private final List<AbstractWidget> listWidgets = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<CustomRow> customRows = new ArrayList<>();
    private final List<FormattedCharSequence> message = new ArrayList<>();
    private final long[] totals = new long[COLUMNS.size()];
    private TextButton expandButton;
    private RewardsPanelLayout layout;
    private int builtVersion = -1;
    private int footerRows = 1;
    private int contentHeight;
    private String title = "";
    private Editor editor;
    /** Kept across rebuilds while the same stepper is open, so a drag isn't cut off by a list update. */
    private FlatSliderWidget slider;
    private Stepper sliderStepper;
    private boolean dragging;

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

        Status status = status();
        double progress = feature.progressRatio();
        boolean bar = progress >= 0;
        int statusTop = layout.statusTop(status.lines().size(), bar, footerRows);
        int listBottom = layout.listBottom(status.lines().size(), bar, footerRows);
        boolean table = !rows.isEmpty();
        viewport.update(table ? layout.rowsTop() : layout.listTop(), listBottom, contentHeight);

        int x = layout.x();
        int y = layout.y();
        int right = layout.right();
        int bottom = layout.bottom();
        graphics.fill(x, y, right, bottom, UiStyle.MODAL_BACKGROUND);
        graphics.fill(x + BORDER, y + BORDER, right - BORDER, y + HEADER_H - 1, UiStyle.MODAL_HEADER);
        graphics.fill(x + BORDER, y + HEADER_H - 1, right - BORDER, y + HEADER_H, UiStyle.MODAL_SEPARATOR);
        CompatibilityHelper.drawBorder(graphics, x, y, right - x, bottom - y, UiStyle.MODAL_OUTLINE);
        text(graphics, title, layout.contentLeft(), centeredY(y + BORDER, HEADER_H - 2), UiStyle.TEXT_WHITE);
        for (AbstractWidget button : headerButtons) {
            button.render(graphics, mouseX, mouseY, delta);
        }
        if (table) {
            drawTableHead(graphics);
        }

        int offset = viewport.getOffset();
        boolean mouseInList = viewport.contains(mouseY) && mouseX >= x && mouseX < right;
        viewport.enableScissor(graphics, x + BORDER, right - BORDER);
        for (Row row : rows) {
            int rowY = row.y() - offset;
            drawRow(graphics, row, rowY, mouseInList && mouseY >= rowY && mouseY < rowY + ROW_H);
        }
        for (CustomRow sub : customRows) {
            int subY = sub.y() - offset;
            drawCustomRow(graphics, sub, subY, mouseInList && mouseY >= subY && mouseY < subY + ROW_H);
        }
        if (editor != null) {
            drawEditor(graphics, editor.y() - offset);
        }
        int messageY = layout.listTop() + 8 - offset;
        for (FormattedCharSequence line : message) {
            int lineWidth = Math.max(0, font.width(line) - 1);
            graphics.drawString(font, line, x + (layout.width() - lineWidth) / 2, centeredY(messageY, LINE_H),
                    TEXT_GRAY, false);
            messageY += LINE_H;
        }
        viewport.renderTracked(graphics, mouseX, mouseY, delta);
        graphics.disableScissor();
        viewport.drawScrollbar(graphics, right - BORDER - 3);
        graphics.fill(x + BORDER, listBottom, right - BORDER, listBottom + 1, UiStyle.MODAL_SEPARATOR);

        int lineY = statusTop;
        for (Line line : status.lines()) {
            text(graphics, line.text(), layout.contentLeft(), centeredY(lineY, LINE_H), line.color());
            lineY += LINE_H;
        }
        if (bar) {
            int barY = lineY + BAR_GAP;
            int left = layout.contentLeft();
            int width = layout.contentWidth();
            graphics.fill(left, barY, left + width, barY + BAR_H, ROW_HOVER);
            graphics.fill(left, barY, left + (int) Math.round(progress * width), barY + BAR_H, UiStyle.ACCENT_BLUE);
        }
        for (AbstractWidget button : footerButtons) {
            button.render(graphics, mouseX, mouseY, delta);
        }
        if (status.whole() != null && mouseX >= x && mouseX < right && mouseY >= statusTop && mouseY < lineY) {
            graphics.setTooltipForNextFrame(font, status.whole(), mouseX, mouseY);
        } else if (mouseInList) {
            showNameTooltip(graphics, mouseX, mouseY, offset);
        }
    }

    /** True if the click was inside the panel, used or not. */
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (layout == null || layout.showsHint()) return false;
        if (expandButton != null) {
            if (!expandButton.isMouseOver(click.x(), click.y())) return false;
            expandButton.mouseClicked(click, doubleClick);
            return true;
        }
        if (!layout.contains(click.x(), click.y())) return false;
        for (AbstractWidget button : headerButtons) {
            if (button.mouseClicked(click, doubleClick)) return true;
        }
        for (AbstractWidget button : footerButtons) {
            if (button.mouseClicked(click, doubleClick)) return true;
        }
        // Widgets scrolled out of the list are clipped there, so they only take clicks inside it
        if (viewport.contains(click.y())) {
            for (AbstractWidget widget : listWidgets) {
                if (widget.mouseClicked(click, doubleClick)) {
                    dragging = widget == slider;
                    return true;
                }
            }
        }
        return true;
    }

    /** Moves the slider while a click that landed on it is held; true if it did. */
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging || slider == null) return false;
        slider.mouseDragged(event, dragX, dragY);
        return true;
    }

    /** Ends a drag of the slider; true if one was going on. */
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!dragging) return false;
        dragging = false;
        if (slider != null) {
            slider.mouseReleased(event);
        }
        return true;
    }

    /** Container screens don't pass the wheel to widgets, so the screen mixin brings it here. */
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (layout == null || layout.showsHint() || expandButton != null || !layout.contains(mouseX, mouseY)) {
            return false;
        }
        if (slider != null && viewport.contains(mouseY) && slider.isMouseOver(mouseX, mouseY)) {
            slider.setValue(slider.getValue() + (amount > 0 ? 1 : -1)); // one at a time, finer than a drag
            return true;
        }
        viewport.scroll(amount);
        return true;
    }

    private void rebuild(RewardsPanelLayout newLayout) {
        layout = newLayout;
        builtVersion = feature.version();
        headerButtons.clear();
        footerButtons.clear();
        listWidgets.clear();
        rows.clear();
        customRows.clear();
        message.clear();
        editor = null;
        viewport.clearTracked();
        expandButton = null;
        if (layout.showsHint()) return;
        if (feature.isCollapsed()) {
            expandButton = new TextButton(layout.right() - TAB_W, layout.y(), TAB_W, BUTTON_H + 2, "Rewards",
                    UiIcons.PLAY, UiStyle.TEXT_WHITE, true, () -> feature.setCollapsed(false));
            return;
        }

        int collapseX = layout.tableRight() - SMALL_BUTTON;
        int buttonY = layout.y() + BORDER + (HEADER_H - 2 - SMALL_BUTTON) / 2;
        IconButton refresh = new IconButton(collapseX - 2 - SMALL_BUTTON, buttonY, SMALL_BUTTON, SMALL_BUTTON,
                UiIcons.REFRESH, UiStyle.TEXT_WHITE, true, feature::refresh);
        refresh.setTooltip(Tooltip.create(Component.literal("Refresh the list")));
        headerButtons.add(refresh);
        IconButton collapse = new IconButton(collapseX, buttonY, SMALL_BUTTON, SMALL_BUTTON, UiIcons.MINUS,
                UiStyle.TEXT_WHITE, !feature.isBusy(), () -> feature.setCollapsed(true));
        collapse.setTooltip(Tooltip.create(Component.literal("Hide the panel")));
        headerButtons.add(collapse);
        int titleRoom = collapseX - 2 - SMALL_BUTTON - 4 - layout.contentLeft();
        title = textWidth("Pending Rewards") <= titleRoom ? "Pending Rewards" : "Rewards";

        buildFooter();
        buildList();
    }

    /** Stop during a hand-out; otherwise the emerald button and "Hand out all", side by side if they fit. */
    private void buildFooter() {
        int left = layout.contentLeft();
        int width = layout.contentWidth();
        if (feature.isBusy()) {
            footerRows = 1;
            footerButtons.add(new TextButton(left, layout.buttonsTop(1), width, BUTTON_H, "Stop", null, STOP_COLOR,
                    true, feature::stopRun));
            return;
        }
        int half = (width - ROW_GAP) / 2;
        boolean sideBySide = textWidth("LE → Owner") + 6 <= half && textWidth("Hand out all") + 6 <= half;
        footerRows = sideBySide ? 1 : 2;
        int top = layout.buttonsTop(footerRows);
        String emeraldLabel = sideBySide ? "LE → Owner"
                : textWidth("All emeralds → owner") + 6 <= width ? "All emeralds → owner" : "Emeralds → owner";
        TextButton emeralds = footerButton(left, top, sideBySide ? half : width, emeraldLabel,
                feature.canStartRun(), feature::startEmeraldRun, EMERALDS_TOOLTIP);
        TextButton handOutAll = sideBySide
                ? footerButton(left + half + ROW_GAP, top, width - half - ROW_GAP, "Hand out all",
                        feature.canHandOutAll(), feature::startHandOutAll, HAND_OUT_ALL_TOOLTIP)
                : footerButton(left, top + BUTTON_H + ROW_GAP, width, "Hand out all",
                        feature.canHandOutAll(), feature::startHandOutAll, HAND_OUT_ALL_TOOLTIP);
        footerButtons.add(emeralds);
        footerButtons.add(handOutAll);
    }

    private TextButton footerButton(int x, int y, int width, String label, boolean active, Runnable action,
                                    String tooltip) {
        TextButton button = new TextButton(x, y, width, BUTTON_H, label, null, UiStyle.TEXT_WHITE, active, action);
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return button;
    }

    private void buildList() {
        List<PlayerEntry> players = feature.players();
        Stepper stepper = feature.stepper();
        if (players.isEmpty()) {
            String text = feature.listError() != null ? feature.listError()
                    : feature.isLoading() ? "Loading…" : "Nobody is owed anything.";
            message.addAll(font.split(ui(text), layout.contentWidth()));
            contentHeight = 8 + message.size() * LINE_H;
            forgetSlider();
            return;
        }
        Arrays.fill(totals, 0);
        int y = layout.rowsTop();
        boolean editing = false;
        for (int i = 0; i < players.size(); i++) {
            PlayerEntry player = players.get(i);
            boolean odd = i % 2 == 1;
            for (int column = 0; column < COLUMNS.size(); column++) {
                totals[column] += player.rewards().getOrDefault(COLUMNS.get(column), 0L);
            }
            rows.add(new Row(y, player, odd));
            addCells(player, y, stepper);
            y += ROW_H;
            if (stepper != null && stepper.uuid().equals(player.uuid()) && !stepper.kind().isCustom()) {
                y = addEditor(stepper, y);
                editing = true;
            }
            for (Map.Entry<RewardKind, Long> entry : player.rewards().entrySet()) {
                RewardKind kind = entry.getKey();
                long amount = entry.getValue();
                if (!kind.isCustom() || amount <= 0) continue;
                boolean open = stepper != null && stepper.uuid().equals(player.uuid()) && stepper.kind().equals(kind);
                int countW = addCustomCell(player, kind, amount, y, open);
                customRows.add(new CustomRow(y, player, kind, amount, countW, odd));
                y += ROW_H;
                if (open) {
                    y = addEditor(stepper, y);
                    editing = true;
                }
            }
        }
        if (!editing) {
            forgetSlider();
        }
        contentHeight = y - layout.rowsTop();
    }

    /** A player's numbers, which open the slider, and a square ▶ beside their tomes and aspects. */
    private void addCells(PlayerEntry player, int y, Stepper stepper) {
        int cellY = y + (ROW_H - PLAY_SIZE) / 2;
        for (int column = 0; column < COLUMNS.size(); column++) {
            RewardKind kind = COLUMNS.get(column);
            long amount = player.rewards().getOrDefault(kind, 0L);
            if (amount <= 0) continue;
            boolean open = stepper != null && stepper.uuid().equals(player.uuid()) && stepper.kind().equals(kind);
            String amountText = String.valueOf(amount);
            int countW = Math.max(PLAY_SIZE, textWidth(amountText) + 4);
            CountButton count = new CountButton(layout.valueRight(column) - countW, cellY, countW, PLAY_SIZE,
                    amountText, player.inGuild() ? color(kind) : TEXT_DIM, open, () -> toggleStepper(player, kind));
            count.setTooltip(Tooltip.create(Component.literal("Record by hand")));
            track(count);
            if (kind.handedOutByMod()) {
                boolean canStart = player.inGuild() && feature.canStartRun();
                String tooltip = !player.inGuild() ? "They left the guild, so the mod can't hand these out."
                        : canStart ? "Hand out " + kind.label(amount) + " now" : "Another hand-out is running.";
                IconButton start = new IconButton(layout.playX(column), cellY, PLAY_SIZE, PLAY_SIZE, UiIcons.PLAY,
                        UiStyle.TEXT_WHITE, canStart, () -> feature.startRun(player, kind));
                start.setTooltip(Tooltip.create(Component.literal(tooltip)));
                track(start);
            }
        }
    }

    /** Right-aligned count pill for an indented freeform item row; returns its pixel width. */
    private int addCustomCell(PlayerEntry player, RewardKind kind, long amount, int y, boolean open) {
        int cellY = y + (ROW_H - PLAY_SIZE) / 2;
        String amountText = String.valueOf(amount);
        int countW = Math.max(PLAY_SIZE, textWidth(amountText) + 4);
        CountButton count = new CountButton(layout.tableRight() - countW, cellY, countW, PLAY_SIZE,
                amountText, player.inGuild() ? CUSTOM_COLOR : TEXT_DIM, open, () -> toggleStepper(player, kind));
        count.setTooltip(Tooltip.create(Component.literal("Record " + kind.displayName() + " by hand")));
        track(count);
        return countW;
    }

    /**
     * The line under a player's row to record a hand-out by hand: the reward, a slider from 1 to what's owed, which
     * starts at all of it, the amount, ✓ and ✕. A slider rather than a text field, as typing E would close the menu.
     */
    private int addEditor(Stepper stepper, int y) {
        int max = (int) Math.min(Integer.MAX_VALUE, stepper.max());
        int buttonY = y + (EDITOR_H - SMALL_BUTTON) / 2;
        int closeX = layout.tableRight() - SMALL_BUTTON;
        int recordX = closeX - 2 - SMALL_BUTTON;
        int valueRight = recordX - EDITOR_GAP;
        int valueLeft = valueRight - Math.max(EDITOR_VALUE_W, textWidth(max + "/" + max));
        int labelX = layout.tableLeft() + 2;
        String labelText = fit(label(stepper.kind()), MAX_EDITOR_LABEL_W);
        int sliderX = labelX + Math.max(EDITOR_LABEL_W, textWidth(labelText)) + EDITOR_GAP;
        int sliderWidth = Math.max(10, valueLeft - EDITOR_GAP - sliderX);
        if (slider == null || !stepper.equals(sliderStepper)) {
            boolean sameReward = sliderStepper != null && slider != null
                    && sliderStepper.uuid().equals(stepper.uuid()) && sliderStepper.kind().equals(stepper.kind());
            slider = new FlatSliderWidget(sliderX, buttonY, sliderWidth, SMALL_BUTTON, 1, max,
                    sameReward ? Math.min(slider.getValue(), max) : max, null);
            dragging = false;
        } else {
            slider.setX(sliderX);
            slider.setY(buttonY);
            slider.setWidth(sliderWidth);
        }
        sliderStepper = stepper;
        editor = new Editor(y, stepper.kind(), labelText, max, labelX, valueRight);
        track(slider);
        IconButton record = new IconButton(recordX, buttonY, SMALL_BUTTON, SMALL_BUTTON, UiIcons.CHECK, CHECK_COLOR,
                true, () -> feature.recordStepper(slider.getValue()));
        record.setTooltip(Tooltip.create(Component.literal("Record as handed out")));
        track(record);
        IconButton close = new IconButton(closeX, buttonY, SMALL_BUTTON, SMALL_BUTTON, UiIcons.CROSS,
                UiStyle.TEXT_WHITE, true, feature::closeStepper);
        close.setTooltip(Tooltip.create(Component.literal("Cancel")));
        track(close);
        return y + EDITOR_H;
    }

    private void forgetSlider() {
        slider = null;
        sliderStepper = null;
        dragging = false;
    }

    /** A number opens the slider for it, or closes it if it's already open. */
    private void toggleStepper(PlayerEntry player, RewardKind kind) {
        Stepper open = feature.stepper();
        if (open != null && open.uuid().equals(player.uuid()) && open.kind().equals(kind)) {
            feature.closeStepper();
        } else {
            feature.openStepper(player, kind);
        }
    }

    private <T extends AbstractWidget> T track(T widget) {
        listWidgets.add(viewport.track(widget));
        return widget;
    }

    /** The column titles over the totals, right-aligned to each column, staying put while the rows scroll. */
    private void drawTableHead(GuiGraphics graphics) {
        int headY = centeredY(layout.headTop(), TABLE_HEAD_H);
        int totalsY = centeredY(layout.totalsTop(), TOTALS_H);
        int count = feature.players().size();
        String players = count + (count == 1 ? " player" : " players");
        if (textWidth(players) > layout.nameRight() - layout.tableLeft()) {
            players = String.valueOf(count); // a narrow panel: just the number, clear of the LE column
        }
        text(graphics, players, layout.tableLeft(), headY, TEXT_GRAY);
        text(graphics, "Total", layout.tableLeft(), totalsY, TEXT_DIM);
        for (int column = 0; column < COLUMNS.size(); column++) {
            RewardKind kind = COLUMNS.get(column);
            String colLabel = label(kind);
            text(graphics, colLabel, layout.columnRight(column) - 2 - textWidth(colLabel), headY, color(kind));
            String total = totals[column] > 0 ? String.valueOf(totals[column]) : "·";
            text(graphics, total, layout.columnRight(column) - 2 - textWidth(total), totalsY,
                    totals[column] > 0 ? TEXT_DIM : TEXT_EMPTY);
        }
        int lineY = layout.totalsSeparatorY();
        graphics.fill(layout.x() + BORDER, lineY, layout.right() - BORDER, lineY + 1, UiStyle.MODAL_SEPARATOR);
    }

    /** A row's background, the active one marked, and its name; the numbers and ▶ are widgets on top. */
    private void drawRow(GuiGraphics graphics, Row row, int y, boolean hovered) {
        int left = layout.x() + BORDER;
        int right = layout.right() - BORDER;
        PlayerEntry player = row.player();
        boolean active = player.uuid().equals(feature.activeUuid());
        int background = active ? ROW_ACTIVE : hovered ? ROW_HOVER : row.odd() ? ROW_ODD : 0;
        if (background != 0) {
            graphics.fill(left, y, right, y + ROW_H, background);
        }
        if (active) {
            graphics.fill(left, y, left + 2, y + ROW_H, UiStyle.ACCENT_BLUE);
        }
        int textY = centeredY(y, ROW_H);
        int nameX = layout.tableLeft();
        int room = layout.nameRight() - nameX;
        String name = player.displayName();
        int nameColor = player.inGuild() ? UiStyle.TEXT_WHITE : TEXT_DIM;
        int tagWidth = textWidth("left") + 6;
        if (!player.inGuild() && textWidth(name) + 3 + tagWidth <= room) {
            text(graphics, name, nameX, textY, nameColor);
            int tagX = nameX + textWidth(name) + 3;
            int tagY = y + (ROW_H - PLAY_SIZE) / 2;
            CompatibilityHelper.drawBorder(graphics, tagX, tagY, tagWidth, PLAY_SIZE, TAG_BORDER);
            text(graphics, "left", tagX + 3, centeredY(tagY, PLAY_SIZE), TEXT_DIM);
        } else {
            text(graphics, fit(name, room), nameX, textY, nameColor);
        }
        for (int column = 0; column < COLUMNS.size(); column++) {
            if (player.rewards().getOrDefault(COLUMNS.get(column), 0L) <= 0) {
                text(graphics, "·", layout.columnRight(column) - 3 - textWidth("·"), textY, TEXT_EMPTY);
            }
        }
    }

    /** An indented sub-row under a player: subtle └ tree branch + item name on the left; count pill is a widget. */
    private void drawCustomRow(GuiGraphics graphics, CustomRow sub, int y, boolean hovered) {
        int left = layout.x() + BORDER;
        int right = layout.right() - BORDER;
        int background = hovered ? ROW_HOVER : sub.odd() ? ROW_ODD : 0;
        if (background != 0) {
            graphics.fill(left, y, right, y + ROW_H, background);
        }
        int branchX = layout.tableLeft() + 2;
        int branchMidY = y + ROW_H / 2;
        graphics.fill(branchX, branchMidY - 3, branchX + 1, branchMidY + 1, TEXT_DIM);
        graphics.fill(branchX, branchMidY, branchX + 4, branchMidY + 1, TEXT_DIM);

        int itemX = layout.tableLeft() + SUBROW_INDENT;
        int room = Math.max(20, layout.tableRight() - sub.countW() - 4 - itemX);
        int itemColor = sub.player().inGuild() ? CUSTOM_COLOR : TEXT_DIM;
        text(graphics, fit(sub.kind().displayName(), room), itemX, centeredY(y, ROW_H), itemColor);
    }

    /** The slider's line: the reward on the left, the amount chosen on the right; the slider and buttons are widgets. */
    private void drawEditor(GuiGraphics graphics, int y) {
        int left = layout.x() + BORDER;
        int right = layout.right() - BORDER;
        graphics.fill(left, y, right, y + EDITOR_H, EDITOR_BACKGROUND);
        graphics.fill(left, y, right, y + 1, EDITOR_LINE);
        graphics.fill(left, y + EDITOR_H - 1, right, y + EDITOR_H, EDITOR_LINE);
        int textY = centeredY(y, EDITOR_H);
        text(graphics, editor.labelText(), editor.labelX(), textY, color(editor.kind()));
        String value = (slider != null ? slider.getValue() : editor.max()) + "/" + editor.max();
        text(graphics, value, editor.valueRight() - textWidth(value), textY, UiStyle.TEXT_WHITE);
    }

    /** Full tooltip for a truncated player name, custom item name, or editor label. */
    private void showNameTooltip(GuiGraphics graphics, int mouseX, int mouseY, int offset) {
        if (mouseX >= layout.tableLeft() && mouseX < layout.nameRight()) {
            for (Row row : rows) {
                int y = row.y() - offset;
                if (mouseY < y || mouseY >= y + ROW_H) continue;
                PlayerEntry player = row.player();
                boolean cut = textWidth(player.displayName()) > layout.nameRight() - layout.tableLeft();
                if (cut || !player.inGuild()) {
                    String text = player.displayName() + (player.inGuild() ? "" : " left the guild.");
                    graphics.setTooltipForNextFrame(font, font.split(Component.literal(text), TOOLTIP_WIDTH), mouseX,
                            mouseY);
                }
                return;
            }
        }
        int itemX = layout.tableLeft() + SUBROW_INDENT;
        for (CustomRow sub : customRows) {
            int y = sub.y() - offset;
            if (mouseY < y || mouseY >= y + ROW_H) continue;
            int itemRight = layout.tableRight() - sub.countW() - 4;
            if (mouseX < layout.tableLeft() || mouseX >= itemRight) return;
            int room = Math.max(20, itemRight - itemX);
            boolean cut = textWidth(sub.kind().displayName()) > room;
            if (cut || !sub.player().inGuild()) {
                String text = sub.kind().displayName()
                        + (sub.player().inGuild() ? "" : " (" + sub.player().displayName() + " left the guild)");
                graphics.setTooltipForNextFrame(font, font.split(Component.literal(text), TOOLTIP_WIDTH), mouseX,
                        mouseY);
            }
            return;
        }
        if (editor != null) {
            int y = editor.y() - offset;
            if (mouseY >= y && mouseY < y + EDITOR_H) {
                String fullLabel = label(editor.kind());
                int labelRight = editor.labelX() + textWidth(editor.labelText());
                if (!fullLabel.equals(editor.labelText()) && mouseX >= editor.labelX() && mouseX <= labelRight) {
                    graphics.setTooltipForNextFrame(font, font.split(Component.literal(fullLabel), TOOLTIP_WIDTH),
                            mouseX, mouseY);
                }
            }
        }
    }

    private void text(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(font, ui(text), x, y, color, false);
    }

    private static Component ui(String text) {
        return Component.literal(text).withStyle(UI);
    }

    /** Text's visible width: the font's width counts a pixel of space after the last character. */
    private int textWidth(String text) {
        return Math.max(0, font.width(ui(text)) - 1);
    }

    /** Where a line of text goes for its capitals to sit in the middle of a box {@code height} high at {@code top}. */
    private int centeredY(int top, int height) {
        return font.width(ui("M")) <= 4 ? top + (height - CAP_H) / 2 - CAP_TOP : top + (height - 7) / 2;
    }

    private String fit(String text, int width) {
        if (textWidth(text) <= width) return text;
        return font.getSplitter().plainHeadByWidth(text, width - font.width(ui("…")), UI).stripTrailing() + "…";
    }

    private static String label(RewardKind kind) {
        if (kind.equals(RewardKind.TOME)) return "Tome";
        if (kind.equals(RewardKind.ASPECT)) return "Asp";
        if (kind.equals(RewardKind.LE)) return "LE";
        return kind.displayName();
    }

    private static int color(RewardKind kind) {
        if (kind.equals(RewardKind.TOME)) return 0xFFFFAA00;
        if (kind.equals(RewardKind.ASPECT)) return 0xFF55FFFF;
        if (kind.equals(RewardKind.LE)) return 0xFF55FF55;
        return CUSTOM_COLOR;
    }

    /** The status wrapped to the panel, up to {@link #MAX_STATUS_LINES} lines; the last one ends in "…" if cut. */
    private Status status() {
        int width = layout.contentWidth();
        List<Line> lines = new ArrayList<>();
        List<FormattedCharSequence> whole = new ArrayList<>();
        boolean cut = false;
        for (RewardsFeature.StatusLine status : feature.statusLines()) {
            for (FormattedText part : font.getSplitter().splitLines(status.text(), width, UI)) {
                if (lines.size() < MAX_STATUS_LINES) {
                    lines.add(new Line(part.getString(), status.color()));
                } else {
                    cut = true;
                }
            }
            whole.addAll(font.split(Component.literal(status.text()), TOOLTIP_WIDTH));
        }
        if (!cut) {
            return new Status(lines, null);
        }
        Line last = lines.getLast();
        String shortened = font.getSplitter().plainHeadByWidth(last.text(), width - font.width(ui("…")), UI)
                .stripTrailing() + "…";
        lines.set(lines.size() - 1, new Line(shortened, last.color()));
        return new Status(lines, whole);
    }

    private void drawHint(GuiGraphics graphics) {
        int width = layout.leftPos() - 2 * MARGIN;
        if (width < 30) return;
        int y = layout.y();
        for (FormattedCharSequence line : font.split(ui(HINT), width)) {
            graphics.drawString(font, line, MARGIN, y, TEXT_GRAY, false);
            y += LINE_H;
        }
    }

    /** A button in the panel's style: a flat box that lightens on hover, and dims when it can't be used. */
    private abstract static class PanelButton extends AbstractWidget {
        protected final int color;
        private final Runnable onPress;

        PanelButton(int x, int y, int width, int height, Component message, int color, boolean active,
                    Runnable onPress) {
            super(x, y, width, height, message);
            this.color = color;
            this.active = active;
            this.onPress = onPress;
        }

        // AbstractWidget.mouseClicked checks it's active, the left button and the bounds, and plays the sound
        @Override
        public void onClick(MouseButtonEvent click, boolean doubleClick) {
            onPress.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int background = !active ? BUTTON_OFF : isHovered() ? BUTTON_HOVER : BUTTON_BACKGROUND;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            CompatibilityHelper.drawBorder(graphics, getX(), getY(), getWidth(), getHeight(), BUTTON_BORDER);
            drawContent(graphics, active ? color : TEXT_OFF);
        }

        protected abstract void drawContent(GuiGraphics graphics, int contentColor);

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            // No-op
        }
    }

    /** A labelled button, its label centred (optionally followed by a trailing half-pixel icon). */
    private final class TextButton extends PanelButton {
        private final UiIcons.Icon trailingIcon;

        TextButton(int x, int y, int width, int height, String label, UiIcons.Icon trailingIcon, int color,
                   boolean active, Runnable onPress) {
            super(x, y, width, height, ui(label), color, active, onPress);
            this.trailingIcon = trailingIcon;
        }

        @Override
        protected void drawContent(GuiGraphics graphics, int contentColor) {
            String label = getMessage().getString();
            int labelW = textWidth(label);
            if (trailingIcon == null) {
                text(graphics, label, getX() + (getWidth() - labelW) / 2, centeredY(getY(), getHeight()),
                        contentColor);
            } else {
                int iconW = trailingIcon.widthPx();
                int gap = 3;
                int startX = getX() + (getWidth() - (labelW + gap + iconW)) / 2;
                text(graphics, label, startX, centeredY(getY(), getHeight()), contentColor);
                trailingIcon.draw(graphics, startX + labelW + gap, getY() + (getHeight() - 9) / 2, iconW, 9,
                        contentColor);
            }
        }
    }

    /** A button showing one of the shared half-pixel icons. */
    private static final class IconButton extends PanelButton {
        private final UiIcons.Icon icon;

        IconButton(int x, int y, int width, int height, UiIcons.Icon icon, int color, boolean active,
                   Runnable onPress) {
            super(x, y, width, height, Component.empty(), color, active, onPress);
            this.icon = icon;
        }

        @Override
        protected void drawContent(GuiGraphics graphics, int contentColor) {
            icon.draw(graphics, getX(), getY(), getWidth(), getHeight(), contentColor);
        }
    }

    /** A reward's number in the table, right-aligned in its value column: click it to record by hand. */
    private final class CountButton extends AbstractWidget {
        private final int color;
        private final boolean open;
        private final Runnable onPress;

        CountButton(int x, int y, int width, int height, String text, int color, boolean open, Runnable onPress) {
            super(x, y, width, height, Component.literal(text));
            this.color = color;
            this.open = open;
            this.onPress = onPress;
        }

        // AbstractWidget.mouseClicked checks it's active, the left button and the bounds, and plays the sound
        @Override
        public void onClick(MouseButtonEvent click, boolean doubleClick) {
            onPress.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (isHovered() || open) {
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), CELL_HOVER);
                CompatibilityHelper.drawBorder(graphics, getX(), getY(), getWidth(), getHeight(), CELL_OUTLINE);
            }
            String text = getMessage().getString();
            text(graphics, text, getX() + (getWidth() - textWidth(text)) / 2, centeredY(getY(), getHeight()), color);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            // No-op
        }
    }
}
