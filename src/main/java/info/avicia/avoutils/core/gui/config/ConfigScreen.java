package info.avicia.avoutils.core.gui.config;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.FlatButtonWidget;
import info.avicia.avoutils.core.gui.FlatSliderWidget;
import info.avicia.avoutils.core.gui.FlatToggleWidget;
import info.avicia.avoutils.core.gui.UiStyle;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.features.emojis.EmojiFeature;
import info.avicia.avoutils.features.updater.UpdateCheckResult;
import info.avicia.avoutils.features.updater.UpdateFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Configuration screen for AvoUtils
 */
public class ConfigScreen extends Screen {

    private final ModConfig config;
    private final Screen parent;
    private static final int SIDE_PADDING = 20;
    private static final int CARD_SINGLE_H = 48;
    private static final int CARD_DOUBLE_H = 66;
    private static final int CARD_QUAD_H = 102;
    private static final int CARD_GAP = 8;

    public ConfigScreen() {
        this(null);
    }

    public ConfigScreen(Screen parent) {
        super(Text.literal("AvoUtils Configs"));
        this.config = AvoUtilsMod.getInstance().getConfig();
        this.parent = parent;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    protected void init() {
        int cardRight = width - SIDE_PADDING;
        int y = 38;

        AvoAuthService authService = AvoAuthService.getInstance();
        if (authService.getCachedGuildMember() == null) {
            authService.resolveGuildMembership().thenAccept(ignored -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null) {
                    mc.execute(() -> {
                        if (mc.currentScreen == this) {
                            this.clearAndInit();
                        }
                    });
                }
            });
        }

        // ── Chat Bridge ──────────────────────────────────────────────────
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
        addDrawableChild(bridgeToggle[0]);
        y += CARD_SINGLE_H + CARD_GAP;

        // ── Emojis ──────────────────────────────────────────────────────
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
        addDrawableChild(new FlatToggleWidget(
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
        addDrawableChild(emojiAutocompleteToggle[0]);
        y += CARD_DOUBLE_H + CARD_GAP;

        // ── Party Finder ────────────────────────────────────────────────
        FlatToggleWidget[] pfSoundsToggle = new FlatToggleWidget[1];
        pfSoundsToggle[0] = new FlatToggleWidget(
                cardRight - 40, y + 46, 30, 16,
                config.notificationSoundsEnabled,
                checked -> { config.notificationSoundsEnabled = checked; config.save(); }
        );
        addDrawableChild(new FlatToggleWidget(
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
        addDrawableChild(pfSoundsToggle[0]);
        y += CARD_DOUBLE_H + CARD_GAP;

        // ── Guild Storage ───────────────────────────────────────────────
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
        addDrawableChild(storageToggle[0]);

        storageSoundsToggle[0] = new FlatToggleWidget(
                cardRight - 40, y + 46, 30, 16,
                config.guildStorageNotifSoundsEnabled,
                checked -> { config.guildStorageNotifSoundsEnabled = checked; config.save(); }
        );
        if (!config.guildStorageNotifsEnabled || !canEnableStorage) {
            storageSoundsToggle[0].active = false;
        }
        addDrawableChild(storageSoundsToggle[0]);

        emeraldSlider[0] = new FlatSliderWidget(
                cardRight - 170, y + 67, 120, 10,
                0, 100, config.guildStorageEmeraldThresholdPercent,
                value -> { config.guildStorageEmeraldThresholdPercent = value; config.save(); }
        );
        if (!config.guildStorageNotifsEnabled || !canEnableStorage) {
            emeraldSlider[0].active = false;
        }
        addDrawableChild(emeraldSlider[0]);

        aspectSlider[0] = new FlatSliderWidget(
                cardRight - 170, y + 85, 120, 10,
                0, 100, config.guildStorageAspectThresholdPercent,
                value -> { config.guildStorageAspectThresholdPercent = value; config.save(); }
        );
        if (!config.guildStorageNotifsEnabled || !canEnableStorage) {
            aspectSlider[0].active = false;
        }
        addDrawableChild(aspectSlider[0]);

        y += CARD_QUAD_H + CARD_GAP;

        // ── Updates ─────────────────────────────────────────────────────
        addDrawableChild(new FlatToggleWidget(
                cardRight - 40, y + 28, 30, 16,
                config.updateRemindersEnabled,
                checked -> { config.updateRemindersEnabled = checked; config.save(); }
        ));

        // Check for updates if unchecked or checking so update button appears once check completes
        UpdateFeature updateFeature = AvoUtilsMod.getInstance().getFeature(UpdateFeature.class);
        boolean hasUpdateAction = false;
        if (updateFeature != null) {
            UpdateFeature.UpdateState updateState = updateFeature.getState();
            if (WynncraftServerPolicy.isNetworkingAllowed() && (updateState == UpdateFeature.UpdateState.UNCHECKED || updateState == UpdateFeature.UpdateState.CHECKING)) {
                updateFeature.checkForUpdate().thenAccept(res -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.execute(() -> {
                        if (mc.currentScreen == this) {
                            this.clearAndInit();
                        }
                    });
                });
            }

            hasUpdateAction = hasUpdateAction(updateState);

            // Show update button when update is available, or restart button when ready
            if (updateState == UpdateFeature.UpdateState.UPDATE_AVAILABLE) {
                UpdateCheckResult result = updateFeature.getLastCheckResult();
                String label = result != null ? "Update to v" + result.latestVersion() : "Update";
                addDrawableChild(new FlatButtonWidget(
                        cardRight - 110, y + 44, 100, 18,
                        Text.literal(label),
                        () -> {
                            this.close();
                            updateFeature.downloadAndApplyUpdate();
                        }
                ));
            } else if (updateState == UpdateFeature.UpdateState.READY_TO_RESTART) {
                addDrawableChild(new FlatButtonWidget(
                        cardRight - 110, y + 44, 100, 18,
                        Text.literal("Restart Now"),
                        () -> {
                            this.close();
                            updateFeature.requestRestart();
                        }
                ));
            }
        }

        y += (hasUpdateAction ? CARD_DOUBLE_H : CARD_SINGLE_H) + CARD_GAP;

        // ── Bottom buttons ──────────────────────────────────────────────
        int btnY = y + 16;
        addDrawableChild(new FlatButtonWidget(
                width / 2 - 50, btnY, 100, 20,
                Text.literal("Done"),
                this::close
        ));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiStyle.SCREEN_BACKGROUND);

        CompatibilityHelper.drawScreenTitle(context, textRenderer, width, "\u00a7b\u00a7lAVOUTILS \u00a7f\u00a7lCONFIGS");

        int cardLeft = SIDE_PADDING;
        int cardRight = width - SIDE_PADDING;
        int y = 38;

        // ── Chat Bridge ──────────────────────────────────────────────────
        drawSectionCard(context, y, CARD_SINGLE_H, "Chat Bridge");
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Enabled (requires guild membership)"), cardLeft + 16, y + 32, 0xFFFFFFFF);
        y += CARD_SINGLE_H + CARD_GAP;

        // ── Emojis ──────────────────────────────────────────────────────
        drawSectionCard(context, y, CARD_DOUBLE_H, "Emojis");
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Enabled"), cardLeft + 16, y + 32, 0xFFFFFFFF);
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Emoji Autocomplete"), cardLeft + 16, y + 50, 0xFFFFFFFF);
        y += CARD_DOUBLE_H + CARD_GAP;

        // ── Party Finder ────────────────────────────────────────────────
        drawSectionCard(context, y, CARD_DOUBLE_H, "Party Finder");
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77New Party Notifications"),
                cardLeft + 16, y + 32, 0xFFFFFFFF);
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Notification Sounds"),
                cardLeft + 16, y + 50, 0xFFFFFFFF);
        y += CARD_DOUBLE_H + CARD_GAP;

        // ── Guild Storage ───────────────────────────────────────────────
        drawSectionCard(context, y, CARD_QUAD_H, "Guild Rewards Storage");
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Notifications (requires guild membership)"),
                cardLeft + 16, y + 32, 0xFFFFFFFF);
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Notification Sounds"),
                cardLeft + 16, y + 50, 0xFFFFFFFF);
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Emerald threshold"),
                cardLeft + 16, y + 68, 0xFFFFFFFF);
        drawRightText(context, config.guildStorageEmeraldThresholdPercent + "%",
                cardRight - 13, y + 68, UiStyle.ACCENT_BLUE);
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Aspect threshold"),
                cardLeft + 16, y + 86, 0xFFFFFFFF);
        drawRightText(context, config.guildStorageAspectThresholdPercent + "%",
                cardRight - 13, y + 86, UiStyle.ACCENT_BLUE);

        y += CARD_QUAD_H + CARD_GAP;

        // ── Updates ─────────────────────────────────────────────────────
        UpdateFeature updateFeature = AvoUtilsMod.getInstance().getFeature(UpdateFeature.class);
        UpdateFeature.UpdateState updateState = updateFeature != null ? updateFeature.getState() : null;
        boolean hasUpdateAction = updateFeature != null && hasUpdateAction(updateState);

        drawSectionCard(context, y, hasUpdateAction ? CARD_DOUBLE_H : CARD_SINGLE_H, "Updates");
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Update Reminders"), cardLeft + 16, y + 32, 0xFFFFFFFF);

        // Show update status text only when an action or status is present
        if (hasUpdateAction) {
            if (updateState == UpdateFeature.UpdateState.UPDATE_AVAILABLE) {
                UpdateCheckResult result = updateFeature.getLastCheckResult();
                if (result != null) {
                    CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                            Text.literal("\u00a7aUpdate available: v" + result.latestVersion()),
                            cardLeft + 16, y + 50, 0xFFFFFFFF);
                }
            } else if (updateState == UpdateFeature.UpdateState.DOWNLOADING) {
                CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                        Text.literal("\u00a77Downloading update..."),
                        cardLeft + 16, y + 50, 0xFFFFFFFF);
            } else if (updateState == UpdateFeature.UpdateState.READY_TO_RESTART) {
                CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                        Text.literal("\u00a7aUpdate ready — restart to apply"),
                        cardLeft + 16, y + 50, 0xFFFFFFFF);
            } else if (updateState == UpdateFeature.UpdateState.ERROR) {
                CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                        Text.literal("\u00a7cUpdate check failed"),
                        cardLeft + 16, y + 50, 0xFFFFFFFF);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (keyInput.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyInput);
    }

    private void drawRightText(DrawContext context, String text, int rightX, int y, int color) {
        int width = textRenderer.getWidth(Text.literal(text));
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal(text), rightX - width, y, color);
    }

    private void drawSectionCard(DrawContext context, int y, int cardH, String label) {
        int cardLeft = SIDE_PADDING;
        int cardRight = width - SIDE_PADDING;
        int cardY = y + 2;

        context.fill(cardLeft, cardY, cardRight, cardY + cardH, UiStyle.CARD_BACKGROUND);

        CompatibilityHelper.drawBorder(context, cardLeft, cardY,
                cardRight - cardLeft, cardH, UiStyle.BORDER_FAINT);

        context.fill(cardLeft, cardY + 1, cardLeft + 2, cardY + cardH - 1, UiStyle.ACCENT_BAR);

        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a7b\u00a7l" + label), cardLeft + 16, cardY + 11, 0xFFFFFFFF);
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
