package info.avicia.avoutils.core.gui.config;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.FlatButtonWidget;
import info.avicia.avoutils.core.gui.FlatSliderWidget;
import info.avicia.avoutils.core.gui.FlatToggleWidget;
import info.avicia.avoutils.features.chatbridge.ChatBridgeFeature;
import info.avicia.avoutils.features.guildstorage.GuildStorageNotifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Configuration screen for AvoUtils
 */
public class ConfigScreen extends Screen {

    private final ModConfig config;
    private static final int SIDE_PADDING = 20;
    private static final int CARD_SINGLE_H = 48;
    private static final int CARD_DOUBLE_H = 66;
    private static final int CARD_TRIPLE_H = 84;
    private static final int CARD_GAP = 8;

    public ConfigScreen() {
        super(Text.literal("AvoUtils Configs"));
        this.config = AvoUtilsMod.getInstance().getConfig();
    }

    @Override
    protected void init() {
        int cardRight = width - SIDE_PADDING;
        int y = 38;

        // ── Chat Bridge ──────────────────────────────────────────────────
        ChatBridgeFeature bridgeFeature = AvoUtilsMod.getInstance().getFeature(ChatBridgeFeature.class);
        boolean canEnableBridge = bridgeFeature != null && bridgeFeature.isGuildMember();

        FlatToggleWidget[] bridgeToggle = new FlatToggleWidget[1];
        bridgeToggle[0] = new FlatToggleWidget(
                cardRight - 40, y + 28, 30, 16,
                config.chatBridgeEnabled,
                checked -> {
                    if (checked && !canEnableBridge) {
                        bridgeToggle[0].setChecked(false);
                        return;
                    }
                    config.chatBridgeEnabled = checked;
                    config.save();
                }
        );
        if (!config.chatBridgeEnabled && !canEnableBridge) {
            bridgeToggle[0].active = false;
        }
        addDrawableChild(bridgeToggle[0]);
        y += CARD_SINGLE_H + CARD_GAP;

        // ── Emojis ──────────────────────────────────────────────────────
        addDrawableChild(new FlatToggleWidget(
                cardRight - 40, y + 28, 30, 16,
                config.emojiEnabled,
                checked -> { config.emojiEnabled = checked; config.save(); }
        ));
        y += CARD_SINGLE_H + CARD_GAP;

        // ── Party Finder ────────────────────────────────────────────────
        addDrawableChild(new FlatToggleWidget(
                cardRight - 40, y + 28, 30, 16,
                config.newPartyNotifsEnabled,
                checked -> { config.newPartyNotifsEnabled = checked; config.save(); }
        ));
        addDrawableChild(new FlatToggleWidget(
                cardRight - 40, y + 46, 30, 16,
                config.notificationSoundsEnabled,
                checked -> { config.notificationSoundsEnabled = checked; config.save(); }
        ));
        y += CARD_DOUBLE_H + CARD_GAP;

        // ── Guild Storage ───────────────────────────────────────────────
        GuildStorageNotifier storageFeature = AvoUtilsMod.getInstance().getFeature(GuildStorageNotifier.class);
        boolean canEnableStorage = storageFeature != null && storageFeature.isGuildMember();

        FlatToggleWidget[] storageToggle = new FlatToggleWidget[1];
        storageToggle[0] = new FlatToggleWidget(
                cardRight - 40, y + 28, 30, 16,
                config.guildStorageNotifsEnabled,
                checked -> {
                    if (checked && !canEnableStorage) {
                        storageToggle[0].setChecked(false);
                        return;
                    }
                    config.guildStorageNotifsEnabled = checked;
                    config.save();
                }
        );
        if (!config.guildStorageNotifsEnabled && !canEnableStorage) {
            storageToggle[0].active = false;
        }
        addDrawableChild(storageToggle[0]);

        addDrawableChild(new FlatSliderWidget(
                cardRight - 130, y + 46, 120, 12,
                0, 100, config.guildStorageEmeraldThresholdPercent,
                value -> { config.guildStorageEmeraldThresholdPercent = value; config.save(); }
        ));
        addDrawableChild(new FlatSliderWidget(
                cardRight - 130, y + 64, 120, 12,
                0, 100, config.guildStorageAspectThresholdPercent,
                value -> { config.guildStorageAspectThresholdPercent = value; config.save(); }
        ));
        y += CARD_TRIPLE_H + CARD_GAP;

        // ── Bottom buttons ──────────────────────────────────────────────
        int btnY = y + 16;
        addDrawableChild(new FlatButtonWidget(
                width / 2 - 100, btnY, 90, 20,
                Text.literal("⟳ Reload Packs"),
                () -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null) client.reloadResources();
                }
        ));
        addDrawableChild(new FlatButtonWidget(
                width / 2 + 10, btnY, 90, 20,
                Text.literal("Done"),
                this::close
        ));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xD80A0A0F);

        // Title
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("\u00a7b\u00a7lAVOUTILS \u00a7f\u00a7lCONFIGS"),
                width / 2, 12, 0xFFFFFFFF);

        int cardLeft = SIDE_PADDING;
        int y = 38;

        // ── Chat Bridge ──────────────────────────────────────────────────
        drawSectionCard(context, y, CARD_SINGLE_H, "Chat Bridge");
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Enabled (requires guild membership)"), cardLeft + 16, y + 32, 0xFFFFFFFF);
        y += CARD_SINGLE_H + CARD_GAP;

        // ── Emojis ──────────────────────────────────────────────────────
        drawSectionCard(context, y, CARD_SINGLE_H, "Emojis");
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Enabled"), cardLeft + 16, y + 32, 0xFFFFFFFF);
        y += CARD_SINGLE_H + CARD_GAP;

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
        drawSectionCard(context, y, CARD_TRIPLE_H, "Guild Rewards Storage");
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Notifications (requires guild membership)"),
                cardLeft + 16, y + 32, 0xFFFFFFFF);
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Emerald threshold: " + config.guildStorageEmeraldThresholdPercent + "%"),
                cardLeft + 16, y + 50, 0xFFFFFFFF);
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a77Aspect threshold: " + config.guildStorageAspectThresholdPercent + "%"),
                cardLeft + 16, y + 68, 0xFFFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        if (keyInput.key() == 256) { // GLFW_KEY_ESCAPE
            this.close();
            return true;
        }
        return super.keyPressed(keyInput);
    }

    private void drawSectionCard(DrawContext context, int y, int cardH, String label) {
        int cardLeft = SIDE_PADDING;
        int cardRight = width - SIDE_PADDING;
        int cardY = y + 2;

        // Card background
        context.fill(cardLeft, cardY, cardRight, cardY + cardH, 0xD5161622);

        // Card border
        CompatibilityHelper.drawBorder(context, cardLeft, cardY,
                cardRight - cardLeft, cardH, 0x1A8A9CFE);

        // Left accent bar
        context.fill(cardLeft, cardY + 1, cardLeft + 2, cardY + cardH - 1, 0x408A9CFE);

        // Section header (cyan bold)
        CompatibilityHelper.drawTextWithShadow(context, textRenderer,
                Text.literal("\u00a7b\u00a7l" + label), cardLeft + 16, cardY + 11, 0xFFFFFFFF);
    }
}
