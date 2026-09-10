package io.voltikor.lyra.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class DeskEditBox extends EditBox {
   private static final int HORIZONTAL_PADDING = 6;
   private static final int VERTICAL_PADDING = 5;
   private final int surfaceX;
   private final int surfaceY;
   private final int surfaceWidth;
   private final int surfaceHeight;

   DeskEditBox(Font font, int x, int y, int width, int height, Component message) {
      super(font, x + HORIZONTAL_PADDING, y + VERTICAL_PADDING,
            width - HORIZONTAL_PADDING * 2, height - VERTICAL_PADDING - 1, message);
      surfaceX = x;
      surfaceY = y;
      surfaceWidth = width;
      surfaceHeight = height;
      setBordered(false);
      setTextColor(DeskTheme.TEXT_INPUT);
   }

   @Override
   public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      graphics.fill(surfaceX, surfaceY, surfaceX + surfaceWidth, surfaceY + surfaceHeight, DeskTheme.INPUT_BACKGROUND);
      graphics.fill(surfaceX, surfaceY + surfaceHeight - 1, surfaceX + surfaceWidth, surfaceY + surfaceHeight,
            DeskTheme.TRACK);
      super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
   }
}
