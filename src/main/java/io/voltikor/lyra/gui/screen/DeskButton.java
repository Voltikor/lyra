package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.gui.DeskTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Flat desk controls retain vanilla input, sounds, tooltips and narration. */
final class DeskButton extends Button {
   private static final int HEIGHT = 20;
   private static final int TEXT_PADDING = 4;
   private static final int TEXT_TOP = 6;
   private final OnPress action;
   private boolean selected;
   private boolean pressed;

   DeskButton(Component message, int x, int y, int width, OnPress action) {
      super(x, y, width, HEIGHT, message, action, DEFAULT_NARRATION);
      this.action = action;
   }

   void setSelected(boolean selected) { this.selected = selected; }

   void clearPressed() { pressed = false; }

   @Override
   protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      boolean highlight = active && isHovered() && !pressed;
      graphics.fill(getX(), getY(), getRight(), getBottom(),
            backgroundColor(highlight));
      graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(),
            selected || pressed ? DeskTheme.ACCENT : highlight ? DeskTheme.BORDER_HIGHLIGHT : DeskTheme.BORDER_SUBTLE);
      var font = Minecraft.getInstance().font;
      String text = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, width - 2 * TEXT_PADDING));
      graphics.text(font, text, getX() + (width - font.width(text)) / 2, getY() + TEXT_TOP,
            textColor(), false);
   }

   @Override
   public void onClick(MouseButtonEvent event, boolean doubleClick) {
      pressed = true;
   }

   @Override
   public void onRelease(MouseButtonEvent event) {
      boolean activate = pressed && isMouseOver(event.x(), event.y());
      pressed = false;
      if (activate) action.onPress(this);
   }

   private int backgroundColor(boolean highlighted) {
      if (selected || pressed) return DeskTheme.CONTROL_SELECTED_BACKGROUND;
      if (highlighted) return DeskTheme.CONTROL_HOVER_BACKGROUND;
      return active ? DeskTheme.CONTROL_BACKGROUND : DeskTheme.CONTROL_DISABLED_BACKGROUND;
   }

   private int textColor() {
      if (selected || pressed) return DeskTheme.ACCENT;
      return active ? DeskTheme.TEXT_PRIMARY : DeskTheme.TEXT_DISABLED;
   }
}
