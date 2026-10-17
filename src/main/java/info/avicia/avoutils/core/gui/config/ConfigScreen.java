package info.avicia.avoutils.core.gui.config;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.FlatButtonWidget;
import info.avicia.avoutils.core.gui.FlatSliderWidget;
import info.avicia.avoutils.core.gui.FlatToggleWidget;
import info.avicia.avoutils.core.gui.ScrollViewport;
import info.avicia.avoutils.core.gui.UiStyle;
import info.avicia.avoutils.core.gui.config.ConfigScreenLayout.Card;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.features.emojis.EmojiFeature;
import info.avicia.avoutils.features.updater.UpdateCheckResult;
import info.avicia.avoutils.features.updater.UpdateFeature;
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
    // Sliders change the config on every step; it's written to disk once on release or close
    private boolean unsavedSliderChanges;

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

        // ── Chat Bridge ──────────────────────────────────────────────────
        int y = layout.top(Card.CHAT_BRIDGE);
        boolean guildAuthPending = authService.getCachedGuildMember() == null;
        boolean canEnableBridge = authService.isGuildMember() || guildAuthPending;

        FlatToggleWidget[] bridgeToggle = new FlatToggleWidget[1];
        bridgeToggle[0] = new FlatToggleWidget(
                cardRight - 40, y + 28, 30, 16,
                config.chatBridgeEnabled,
                checked -> {
                    if (checked) {
                        authService.runIfGuildMember(() -> {
                            config.chatBridgeEnabled = true;
                            config.save();
                            bridgeToggle[0].setChecked(true);
                        }, () -> {
                            bridgeToggle[0].setChecked(false);
                            bridgeToggle[0].active = false;
                        });
                    } else {
                        config.chatBridgeEnabled = false;
                        config.save();
                    }
                }
        );
        if (!config.chatBridgeEnabled && !canEnableBridge) {
            bridgeToggle[0].active = false;
        }
        addScrolling(bridgeToggle[0]);

        // ── Emojis ──────────────────────────────────────────────────────
        y = layout.top(Card.EMOJIS);
        EmojiFeature emojiFeature = AvoUtilsMod.getInstance().getFeature(EmojiFeature.class);
        FlatToggleWidget[] emojiAutocompleteToggle = new FlatToggleWidget[1];
        emojiAutocompleteToggle[0] = new FlatToggleWidget(
                cardRight - 40, y + 46, 30, 16,
                config.emojiAutocompleteEnabled,
                checked -> {
                    config.emojiAutocompleteEnabled = checked;
                    config.save();
                }
        );
        addScrolling(new FlatToggleWidget(
                cardRight - 40, y + 28, 30, 16,
                config.emojiEnabled,
                checked -> {
                    config.emojiEnabled = checked;
                    config.save();
                    emojiAutocompleteToggle[0].active = checked;
                    if (checked && emojiFeature != null) {
                        emojiFeature.ensurePacksLoaded();
                    }
                }
        ));
        if (!config.emojiEnabled) {
            emojiAutocompleteToggle[0].active = false;
        }
        addScrolling(emojiAutocompleteToggle[0]);

        // ── Party Finder ────────────────────────────────────────────────
        y = layout.top(Card.PARTY_FINDER);
        FlatToggleWidget[] pfSoundsToggle = new FlatToggleWidget[1];
        pfSoundsToggle[0] = new FlatToggleWidget(
                cardRight - 40, y + 46, 30, 16,
                config.notificationSoundsEnabled,
                checked -> { config.notificationSoundsEnabled = checked; config.save(); }
        );
        addScrolling(new FlatToggleWidget(
                cardRight - 40, y + 28, 30, 16,
                config.newPartyNotifsEnabled,
                checked -> {
                    config.newPartyNotifsEnabled = checked;
                    config.save();
                    pfSoundsToggle[0].active = checked;
                }
        ));
        if (!config.newPartyNotifsEnabled) {
            pfSoundsToggle[0].active = false;
        }
        addScrolling(pfSoundsToggle[0]);

        // ── Guild Storage ───────────────────────────────────────────────
        y = layout.top(Card.GUILD_STORAGE);
        boolean canEnableStorage = authService.isGuildMember() || guildAuthPending;

        FlatToggleWidget[] storageSoundsToggle = new FlatToggleWidget[1];
        FlatSliderWidget[] emeraldSlider = new FlatSliderWidget[1];
        FlatSliderWidget[] aspectSlider = new FlatSliderWidget[1];

        FlatToggleWidget[] storageToggle = new FlatToggleWidget[1];
        storageToggle[0] = new FlatToggleWidget(
                cardRight - 40, y + 28, 30, 16,
                config.guildStorageNotifsEnabled,
                checked -> {
                    if (checked) {
                        authService.runIfGuildMember(() -> {
                            config.guildStorageNotifsEnabled = true;
                            config.save();
                            storageToggle[0].setChecked(true);
                            setStorageControlsActive(true, storageSoundsToggle[0], emeraldSlider[0], aspectSlider[0]);
                        }, () -> {
                            storageToggle[0].setChecked(false);
                            storageToggle[0].active = false;
                            setStorageControlsActive(false, storageSoundsToggle[0], emeraldSlider[0], aspectSlider[0]);
                        });
                    } else {
                        config.guildStorageNotifsEnabled = false;
                        config.save();
                        setStorageControlsActive(false, storageSoundsToggle[0], emeraldSlider[0], aspectSlider[0]);
                    }
                }
        );
        if (!config.guildStorageNotifsEnabled && !canEnableStorage) {
            storageToggle[0].active = false;
        }
        addScrolling(storageToggle[0]);

        storageSoundsToggle[0] = new FlatToggleWidget(
                cardRight - 40, y + 46, 30, 16,
                config.guildStorageNotifSoundsEnabled,
                checked -> { config.guildStorageNotifSoundsEnabled = checked; config.save(); }
        );
        if (!config.guildStorageNotifsEnabled || !canEnableStorage) {
            storageSoundsToggle[0].active = false;
        }
        addScrolling(storageSoundsToggle[0]);

        emeraldSlider[0] = new FlatSliderWidget(
                cardRight - 170, y + 67, 120, 10,
                0, 100, config.guildStorageEmeraldThresholdPercent,
                value -> { config.guildStorageEmeraldThresholdPercent = value; unsavedSliderChanges = true; }
        );
        if (!config.guildStorageNotifsEnabled || !canEnableStorage) {
            emeraldSlider[0].active = false;
        }
        addScrolling(emeraldSlider[0]);

        aspectSlider[0] = new FlatSliderWidget(
                cardRight - 170, y + 85, 120, 10,
                0, 100, config.guildStorageAspectThresholdPercent,
                value -> { config.guildStorageAspectThresholdPercent = value; unsavedSliderChanges = true; }
        );
        if (!config.guildStorageNotifsEnabled || !canEnableStorage) {
            aspectSlider[0].active = false;
        }
        addScrolling(aspectSlider[0]);

        // ── Updates ─────────────────────────────────────────────────────
        y = layout.top(Card.UPDATES);
        addScrolling(new FlatToggleWidget(
                cardRight - 40, y + 28, 30, 16,
                config.updateRemindersEnabled,
                checked -> { config.updateRemindersEnabled = checked; config.save(); }
        ));

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
                        cardRight - 110, y + 44, 100, 18,
                        Component.literal(label),
                        () -> {
                            this.onClose();
                            updateFeature.downloadAndApplyUpdate();
                        }
                ));
            } else if (updateState == UpdateFeature.UpdateState.READY_TO_RESTART) {
                addScrolling(new FlatButtonWidget(
                        cardRight - 110, y + 44, 100, 18,
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

        // ── Chat Bridge ──────────────────────────────────────────────────
        int y = drawSectionCard(context, Card.CHAT_BRIDGE, "Chat Bridge");
        CompatibilityHelper.drawTextWithShadow(context, font,
                Component.literal("\u00a77Enabled (requires guild membership)"), cardLeft + 16, y + 32, 0xFFFFFFFF);

        // ── Emojis ──────────────────────────────────────────────────────
        y = drawSectionCard(context, Card.EMOJIS, "Emojis");
        CompatibilityHelper.drawTextWithShadow(context, font,
                Component.literal("\u00a77Enabled"), cardLeft + 16, y + 32, 0xFFFFFFFF);
        CompatibilityHelper.drawTextWithShadow(context, font,
                Component.literal("\u00a77Emoji Autocomplete"), cardLeft + 16, y + 50, 0xFFFFFFFF);

        // ── Party Finder ────────────────────────────────────────────────
        y = drawSectionCard(context, Card.PARTY_FINDER, "Party Finder");
        CompatibilityHelper.drawTextWithShadow(context, font,
                Component.literal("\u00a77New Party Notifications"),
                cardLeft + 16, y + 32, 0xFFFFFFFF);
        CompatibilityHelper.drawTextWithShadow(context, font,
                Component.literal("\u00a77Notification Sounds"),
                cardLeft + 16, y + 50, 0xFFFFFFFF);

        // ── Guild Storage ───────────────────────────────────────────────
        y = drawSectionCard(context, Card.GUILD_STORAGE, "Guild Rewards Storage");
        CompatibilityHelper.drawTextWithShadow(context, font,
                Component.literal("\u00a77Notifications (requires guild membership)"),
                cardLeft + 16, y + 32, 0xFFFFFFFF);
        CompatibilityHelper.drawTextWithShadow(context, font,
                Component.literal("\u00a77Notification Sounds"),
                cardLeft + 16, y + 50, 0xFFFFFFFF);
        CompatibilityHelper.drawTextWithShadow(context, font,
                Component.literal("\u00a77Emerald threshold"),
                cardLeft + 16, y + 68, 0xFFFFFFFF);
        drawRightText(context, config.guildStorageEmeraldThresholdPercent + "%",
                cardRight - 13, y + 68, UiStyle.ACCENT_BLUE);
        CompatibilityHelper.drawTextWithShadow(context, font,
                Component.literal("\u00a77Aspect threshold"),
                cardLeft + 16, y + 86, 0xFFFFFFFF);
        drawRightText(context, config.guildStorageAspectThresholdPercent + "%",
                cardRight - 13, y + 86, UiStyle.ACCENT_BLUE);

        // ── Updates ─────────────────────────────────────────────────────
        UpdateFeature updateFeature = AvoUtilsMod.getInstance().getFeature(UpdateFeature.class);
        UpdateFeature.UpdateState updateState = updateFeature != null ? updateFeature.getState() : null;

        y = drawSectionCard(context, Card.UPDATES, "Updates");
        CompatibilityHelper.drawTextWithShadow(context, font,
                Component.literal("\u00a77Update Reminders"), cardLeft + 16, y + 32, 0xFFFFFFFF);

        // Show update status text only when an action or status is present
        if (layout.updateCardHasAction() && updateFeature != null) {
            if (updateState == UpdateFeature.UpdateState.UPDATE_AVAILABLE) {
                UpdateCheckResult result = updateFeature.getLastCheckResult();
                if (result != null) {
                    CompatibilityHelper.drawTextWithShadow(context, font,
                            Component.literal("\u00a7aUpdate available: v" + result.latestVersion()),
                            cardLeft + 16, y + 50, 0xFFFFFFFF);
                }
            } else if (updateState == UpdateFeature.UpdateState.DOWNLOADING) {
                CompatibilityHelper.drawTextWithShadow(context, font,
                        Component.literal("\u00a77Downloading update..."),
                        cardLeft + 16, y + 50, 0xFFFFFFFF);
            } else if (updateState == UpdateFeature.UpdateState.READY_TO_RESTART) {
                CompatibilityHelper.drawTextWithShadow(context, font,
                        Component.literal("\u00a7aUpdate ready — restart to apply"),
                        cardLeft + 16, y + 50, 0xFFFFFFFF);
            } else if (updateState == UpdateFeature.UpdateState.ERROR) {
                CompatibilityHelper.drawTextWithShadow(context, font,
                        Component.literal("\u00a7cUpdate check failed"),
                        cardLeft + 16, y + 50, 0xFFFFFFFF);
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
     * Draws a card at its scrolled position and returns that Y, which the card's rows are placed from.
     */
    private int drawSectionCard(GuiGraphics context, Card card, String label) {
        int y = layout.top(card) - viewport.getOffset();
        int cardH = layout.height(card);
        int cardLeft = SIDE_PADDING;
        int cardRight = width - SIDE_PADDING;
        int cardY = y + 2;

        context.fill(cardLeft, cardY, cardRight, cardY + cardH, UiStyle.CARD_BACKGROUND);

        CompatibilityHelper.drawBorder(context, cardLeft, cardY,
                cardRight - cardLeft, cardH, UiStyle.BORDER_FAINT);

        context.fill(cardLeft, cardY + 1, cardLeft + 2, cardY + cardH - 1, UiStyle.ACCENT_BAR);

        CompatibilityHelper.drawTextWithShadow(context, font,
                Component.literal("\u00a7b\u00a7l" + label), cardLeft + 16, cardY + 11, 0xFFFFFFFF);
        return y;
    }

    private static boolean hasUpdateAction(UpdateFeature.UpdateState state) {
        return state == UpdateFeature.UpdateState.UPDATE_AVAILABLE
                || state == UpdateFeature.UpdateState.READY_TO_RESTART
                || state == UpdateFeature.UpdateState.DOWNLOADING
                || state == UpdateFeature.UpdateState.ERROR;
    }

    private static void setStorageControlsActive(boolean active, FlatToggleWidget sounds, FlatSliderWidget emerald, FlatSliderWidget aspect) {
        if (sounds != null) sounds.active = active;
        if (emerald != null) emerald.active = active;
        if (aspect != null) aspect.active = active;
    }
}
