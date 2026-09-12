package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.gui.DeskTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

interface LyraScreenSection {
   void build(LyraScreen screen);

   default void tick(LyraScreen screen) {}

   default void renderHeader(LyraScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      String subtitle = subtitle(screen);
      graphics.text(screen.font(), screen.font().plainSubstrByWidth(subtitle, screen.span()),
            screen.left(), 62, DeskTheme.TEXT_SECONDARY, false);
   }

   default void renderOverlay(LyraScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}

   default String subtitle(LyraScreen screen) { return ""; }
}
