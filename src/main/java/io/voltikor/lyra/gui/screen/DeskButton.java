package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.gui.DeskTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Flat desk controls retain vanilla input, sounds, tooltips and narration. */
final class DeskButton extends Button {
   private static final int HEIGHT = 20;
   private static final int TEXT_PADDING = 4;
   private static final int TEXT_TOP = 6;
   private boolean selected;

   DeskButton(Component message, int x, int y, int width, OnPress action) {
      super(x, y, width, HEIGHT, message, action, DEFAULT_NARRATION);
   }

   void setSelected(boolean selected) { this.selected = selected; }

   @Override
   protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      boolean highlight = active && (isHovered() || isFocused());
      graphics.fill(getX(), getY(), getRight(), getBottom(),
            backgroundColor(highlight));
      graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(),
            selected ? DeskTheme.ACCENT : highlight ? DeskTheme.BORDER_HIGHLIGHT : DeskTheme.BORDER_SUBTLE);
      if (isFocused()) {
         graphics.fill(getX(), getY(), getRight(), getY() + 1, DeskTheme.ACCENT);
      }
      var font = Minecraft.getInstance().font;
      String text = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, width - 2 * TEXT_PADDING));
      graphics.text(font, text, getX() + (width - font.width(text)) / 2, getY() + TEXT_TOP,
            textColor(), false);
   }

   private int backgroundColor(boolean highlighted) {
      if (selected) return DeskTheme.CONTROL_SELECTED_BACKGROUND;
      if (highlighted) return DeskTheme.CONTROL_HOVER_BACKGROUND;
      return active ? DeskTheme.CONTROL_BACKGROUND : DeskTheme.CONTROL_DISABLED_BACKGROUND;
   }

   private int textColor() {
      if (selected) return DeskTheme.ACCENT;
      return active ? DeskTheme.TEXT_PRIMARY : DeskTheme.TEXT_DISABLED;
   }
}
