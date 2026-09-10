package io.voltikor.lyra.gui;

/**
 * Shared semantic colors for the music desk and in-game HUD.
 * Values use Minecraft's packed ARGB format: 0xAARRGGBB.
 * The alpha byte is intentional: surfaces let the world show through while text stays opaque.
 */
public final class DeskTheme {
   public static final int TEXT_PRIMARY = 0xFFF1EADD;
   public static final int TEXT_SECONDARY = 0xFFBCB7AD;
   public static final int TEXT_DISABLED = 0xFF78847E;
   public static final int TEXT_INPUT = 0xFFE9E3D9;
   public static final int TEXT_ERROR = 0xFFFF9990;
   public static final int ACCENT = 0xFFE6B775;
   public static final int SCREEN_SCRIM = 0x60101513;
   public static final int PANEL_BACKGROUND = 0xB018211E;
   public static final int LIST_BACKGROUND = 0x90131C18;
   public static final int INPUT_BACKGROUND = 0x40101513;
   public static final int CONTROL_BACKGROUND = 0x302D3935;
   public static final int CONTROL_DISABLED_BACKGROUND = 0x182D3935;
   public static final int CONTROL_SELECTED_BACKGROUND = 0x40E6B775;
   public static final int CONTROL_HOVER_BACKGROUND = 0x504E625C;
   public static final int BORDER_SUBTLE = 0x305F746B;
   public static final int BORDER_HIGHLIGHT = 0xFF91A69C;
   public static final int TRACK = 0x506D8177;
   public static final int SCROLLBAR_TRACK = 0x30303A34;
   public static final int SCROLLBAR_THUMB = 0x9091A69C;

   public static final int STATUS_READY = BORDER_HIGHLIGHT;
   public static final int STATUS_MISSING = TEXT_ERROR;

   private DeskTheme() {}
}
