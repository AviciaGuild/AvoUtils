package info.avicia.avoutils.core.gui;

/**
 * Shared color constants for the mod's screens and widgets.
 * All values are ARGB ints for use with {@code DrawContext.fill} / {@code DrawContext.drawText}.
 */
public final class UiStyle {

    // Screen
    public static final int SCREEN_BACKGROUND = 0xD80A0A0F;

    // Cards
    public static final int CARD_SHADOW = 0x3F000000;
    public static final int CARD_BACKGROUND = 0xD5161622;
    public static final int CARD_BACKGROUND_HOVERED = 0xF2222232;
    public static final int CARD_BACKGROUND_ACTIVE = 0xF2253530;
    public static final int ACCENT_BAR = 0x408A9CFE;

    // Accents
    public static final int ACCENT_GREEN = 0xFF00FF66;
    public static final int ACCENT_BLUE = 0xFF8A9CFE;
    public static final int ACCENT_RED = 0xFFFF4D4D;

    // Borders
    public static final int BORDER_FAINT = 0x1A8A9CFE;
    public static final int BORDER_GREEN = 0x6600FF66;

    // Pills
    public static final int PILL_BG_GREEN = 0x2200FF66;
    public static final int PILL_BG_RED = 0x22FF4D4D;
    public static final int PILL_BORDER_GREEN = 0x5500FF66;
    public static final int PILL_BORDER_RED = 0x55FF4D4D;

    // Modals
    public static final int MODAL_OVERLAY = 0x88000000;
    public static final int MODAL_SHADOW = 0x7F000000;
    public static final int MODAL_BACKGROUND = 0xF80D0D12;
    public static final int MODAL_HEADER = 0xFF1A1A26;
    public static final int MODAL_OUTLINE = 0x308A9CFE;
    public static final int MODAL_SEPARATOR = 0x208A9CFE;

    // Status messages
    public static final int STATUS_ERROR = 0xFFFF5555;
    public static final int STATUS_SUCCESS = 0xFF55FF55;
    public static final int STATUS_WARNING = 0xFFAAAA00;

    // Text
    public static final int TEXT_WHITE = 0xFFFFFFFF;

    private UiStyle() {
    }
}
