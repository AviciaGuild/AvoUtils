package info.avicia.avoutils.core.gui.config;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ConfigSection;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.config.Setting;
import info.avicia.avoutils.core.config.Settings;
import info.avicia.avoutils.core.config.SliderSetting;
import info.avicia.avoutils.core.config.ToggleSetting;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.FlatButtonWidget;
import info.avicia.avoutils.core.gui.FlatSliderWidget;
import info.avicia.avoutils.core.gui.FlatToggleWidget;
import info.avicia.avoutils.core.gui.ScrollViewport;
import info.avicia.avoutils.core.gui.UiStyle;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.features.updater.UpdateCheckResult;
import info.avicia.avoutils.features.updater.UpdateFeature;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Configuration screen for AvoUtils
 */
public class ConfigScreen extends Screen {

    private final ModConfig config;
    private final Screen parent;
    private static final int SIDE_PADDING = 20;

    private final ScrollViewport viewport = new ScrollViewport();
    private FlatButtonWidget doneButton;
    // Built in init(), so render() draws the layout the widgets were placed for
    private ConfigScreenLayout layout;
    // Each setting's control, so they can all be refreshed after a change
    private final List<SettingRow> rows = new ArrayList<>();
    // Sliders change the config on every step; it's written to disk once on release or close
    private boolean unsavedSliderChanges;

    private record SettingRow(Setting setting, AbstractWidget control) {
    }

    public ConfigScreen() {
        this(null);
    }

    public ConfigScreen(Screen parent) {
        super(Component.literal("AvoUtils Configs"));
        this.config = AvoUtilsMod.getInstance().getConfig();
        this.parent = parent;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    protected void init() {
        viewport.clearTracked();
        rows.clear();
        int cardRight = width - SIDE_PADDING;

        AvoAuthService authService = AvoAuthService.getInstance();
        if (authService.getCachedGuildMember() == null) {
            authService.resolveGuildMembership().thenAccept(ignored -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> {
                        if (mc.screen == this) {
                            this.rebuildWidgets();
                        }
                    });
                }
            });
        }

        // The Updates card is taller when it shows a button or status line, which moves Done down
        UpdateFeature updateFeature = AvoUtilsMod.getInstance().getFeature(UpdateFeature.class);
        UpdateFeature.UpdateState updateState = updateFeature != null ? updateFeature.getState() : null;
        layout = new ConfigScreenLayout(height, hasUpdateAction(updateState));

        // ── Settings: a card per section, a row per setting ─────────────
        for (ConfigSection section : ConfigSection.values()) {
            List<Setting> settings = Settings.inSection(section);
            for (int row = 0; row < settings.size(); row++) {
                Setting setting = settings.get(row);
                AbstractWidget control = createControl(setting, cardRight, layout.rowY(section, row));
                rows.add(new SettingRow(setting, addScrolling(control)));
            }
        }
        updateControls();

        // ── Updates ─────────────────────────────────────────────────────
        // The update/restart button is 18px tall, so it starts 2px above its row's line
        int actionY = layout.updateActionRowY() - 2;

        // Check for updates if unchecked or checking so update button appears once check completes
        if (updateFeature != null) {
            if (WynncraftServerPolicy.isNetworkingAllowed() && (updateState == UpdateFeature.UpdateState.UNCHECKED || updateState == UpdateFeature.UpdateState.CHECKING)) {
                updateFeature.checkForUpdate().thenAccept(res -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (mc.screen == this) {
                            this.rebuildWidgets();
                        }
                    });
                });
            }

            // Show update button when update is available, or restart button when ready
            if (updateState == UpdateFeature.UpdateState.UPDATE_AVAILABLE) {
                UpdateCheckResult result = updateFeature.getLastCheckResult();
                String label = result != null ? "Update to v" + result.latestVersion() : "Update";
                addScrolling(new FlatButtonWidget(
                        cardRight - 110, actionY, 100, 18,
                        Component.literal(label),
                        () -> {
                            this.onClose();
                            updateFeature.downloadAndApplyUpdate();
                        }
                ));
            } else if (updateState == UpdateFeature.UpdateState.READY_TO_RESTART) {
                addScrolling(new FlatButtonWidget(
                        cardRight - 110, actionY, 100, 18,
                        Component.literal("Restart Now"),
                        () -> {
                            this.onClose();
                            updateFeature.requestRestart();
                        }
                ));
            }
        }

        // ── Bottom buttons ──────────────────────────────────────────────
        doneButton = addRenderableWidget(new FlatButtonWidget(
                width / 2 - 50, layout.doneY(), 100, ConfigScreenLayout.DONE_BUTTON_H,
                Component.literal("Done"),
                this::onClose
        ));

        viewport.update(layout.viewportTop(), layout.viewportBottom(), layout.contentHeight());
    }

    /**
     * Adds a card control that scrolls with the viewport. These aren't drawable children because
     * {@link #render} draws them clipped to the viewport.
     */
    private <T extends AbstractWidget> T addScrolling(T widget) {
        return addWidget(viewport.track(widget));
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiStyle.SCREEN_BACKGROUND);

        CompatibilityHelper.drawScreenTitle(context, font, width, "\u00a7b\u00a7lAVOUTILS \u00a7f\u00a7lCONFIGS");

        int cardLeft = SIDE_PADDING;
        int cardRight = width - SIDE_PADDING;

        viewport.enableScissor(context, 0, width);

        // ── Settings: a card per section, a label per row ───────────────
        for (ConfigSection section : ConfigSection.values()) {
            int y = drawSectionCard(context, section);
            List<Setting> settings = Settings.inSection(section);
            for (int row = 0; row < settings.size(); row++) {
                Setting setting = settings.get(row);
                // 8px text, centred on the 16px row
                int textY = y + ConfigScreenLayout.rowOffset(row) + 4;
                CompatibilityHelper.drawTextWithShadow(context, font,
                        Component.literal("\u00a77" + setting.label()), cardLeft + 16, textY, 0xFFFFFFFF);
                if (setting instanceof SliderSetting slider) {
                    drawRightText(context, slider.format(config), cardRight - 13, textY, UiStyle.ACCENT_BLUE);
                }
            }
        }

        // ── Updates ─────────────────────────────────────────────────────
        UpdateFeature updateFeature = AvoUtilsMod.getInstance().getFeature(UpdateFeature.class);
        UpdateFeature.UpdateState updateState = updateFeature != null ? updateFeature.getState() : null;
        int statusY = layout.updateActionRowY() - viewport.getOffset() + 4;

        // Show update status text only when an action or status is present
        if (layout.updateCardHasAction() && updateFeature != null) {
            if (updateState == UpdateFeature.UpdateState.UPDATE_AVAILABLE) {
                UpdateCheckResult result = updateFeature.getLastCheckResult();
                if (result != null) {
                    CompatibilityHelper.drawTextWithShadow(context, font,
                            Component.literal("\u00a7aUpdate available: v" + result.latestVersion()),
                            cardLeft + 16, statusY, 0xFFFFFFFF);
                }
            } else if (updateState == UpdateFeature.UpdateState.DOWNLOADING) {
                CompatibilityHelper.drawTextWithShadow(context, font,
                        Component.literal("\u00a77Downloading update..."),
                        cardLeft + 16, statusY, 0xFFFFFFFF);
            } else if (updateState == UpdateFeature.UpdateState.READY_TO_RESTART) {
                CompatibilityHelper.drawTextWithShadow(context, font,
                        Component.literal("\u00a7aUpdate ready — restart to apply"),
                        cardLeft + 16, statusY, 0xFFFFFFFF);
            } else if (updateState == UpdateFeature.UpdateState.ERROR) {
                CompatibilityHelper.drawTextWithShadow(context, font,
                        Component.literal("\u00a7cUpdate check failed"),
                        cardLeft + 16, statusY, 0xFFFFFFFF);
            }
        }

        viewport.renderTracked(context, mouseX, mouseY, delta);
        context.disableScissor();
        viewport.drawScrollbar(context, cardRight + 6);

        // Fixed widgets (Done)
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        // Controls scrolled under the title or Done button are clipped there, so they must not take
        // the click; outside the viewport only Done is interactive
        if (!viewport.contains(click.y())) {
            if (doneButton != null && doneButton.mouseClicked(click, doubleClick)) {
                setFocused(doneButton);
                return true;
            }
            return false;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return viewport.scroll(verticalAmount) || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        boolean handled = super.mouseReleased(click);
        saveSliderChanges();
        return handled;
    }

    @Override
    public void removed() {
        saveSliderChanges();
        super.removed();
    }

    private void saveSliderChanges() {
        if (unsavedSliderChanges) {
            unsavedSliderChanges = false;
            config.save();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        if (keyInput.isEscape()) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyInput);
    }

    private void drawRightText(GuiGraphics context, String text, int rightX, int y, int color) {
        int width = font.width(Component.literal(text));
        CompatibilityHelper.drawTextWithShadow(context, font,
                Component.literal(text), rightX - width, y, color);
    }

    /**
     * Draws a section's card at its scrolled position and returns that Y, which the card's rows are
     * placed from.
     */
    private int drawSectionCard(GuiGraphics context, ConfigSection section) {
        int y = layout.top(section) - viewport.getOffset();
        int cardH = layout.height(section);
        int cardLeft = SIDE_PADDING;
        int cardRight = width - SIDE_PADDING;
        int cardY = y + 2;

        context.fill(cardLeft, cardY, cardRight, cardY + cardH, UiStyle.CARD_BACKGROUND);

        CompatibilityHelper.drawBorder(context, cardLeft, cardY,
                cardRight - cardLeft, cardH, UiStyle.BORDER_FAINT);

        context.fill(cardLeft, cardY + 1, cardLeft + 2, cardY + cardH - 1, UiStyle.ACCENT_BAR);

        CompatibilityHelper.drawTextWithShadow(context, font,
                Component.literal("\u00a7b\u00a7l" + section.title()), cardLeft + 16, cardY + 11, 0xFFFFFFFF);
        return y;
    }

    private static boolean hasUpdateAction(UpdateFeature.UpdateState state) {
        return state == UpdateFeature.UpdateState.UPDATE_AVAILABLE
                || state == UpdateFeature.UpdateState.READY_TO_RESTART
                || state == UpdateFeature.UpdateState.DOWNLOADING
                || state == UpdateFeature.UpdateState.ERROR;
    }

    /**
     * Creates the control for a setting's row: a toggle on the row's line, or a slider centred on it.
     */
    private AbstractWidget createControl(Setting setting, int cardRight, int rowY) {
        return switch (setting) {
            case ToggleSetting toggle -> new FlatToggleWidget(cardRight - 40, rowY, 30, 16, toggle.get(config),
                    checked -> toggle.change(config, checked, this::updateControls, this::updateControls));
            case SliderSetting slider -> new FlatSliderWidget(cardRight - 170, rowY + 3, 120, 10,
                    slider.min(), slider.max(), slider.get(config),
                    value -> { slider.set(config, value); unsavedSliderChanges = true; });
        };
    }

    /**
     * Matches the controls to the config: toggles show the stored value (a guild-only toggle a
     * non-member tried to turn on goes back off), and controls whose setting isn't editable are
     * disabled.
     */
    private void updateControls() {
        AvoAuthService authService = AvoAuthService.getInstance();
        boolean guildAccess = authService.isGuildMember() || authService.getCachedGuildMember() == null;
        for (SettingRow row : rows) {
            if (row.setting() instanceof ToggleSetting toggle && row.control() instanceof FlatToggleWidget control) {
                control.setChecked(toggle.get(config));
            }
            row.control().active = row.setting().isEditable(config, guildAccess);
        }
    }
}
