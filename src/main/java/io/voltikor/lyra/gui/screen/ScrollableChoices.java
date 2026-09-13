package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.gui.DeskTheme;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

final class ScrollableChoices<T> extends AbstractWidget {
   record Option<T>(T value, String label, ItemStack icon) {
      Option(T value, String label) { this(value, label, ItemStack.EMPTY); }
   }
   private List<Option<T>> options = List.of();
   private final ScrollListModel scroll = new ScrollListModel();
   private final Predicate<T> selected;
   private final Consumer<T> choose;
   private int cursor = -1;
   private boolean draggingThumb;
   private double thumbGrab;

   ScrollableChoices(int x, int y, int width, int height, String name, Predicate<T> selected, Consumer<T> choose) {
      super(x, y, width, height, Component.literal(name));
      this.selected = selected;
      this.choose = choose;
   }

   void setOptions(List<Option<T>> options) {
      this.options = List.copyOf(options);
      cursor = options.isEmpty() ? -1 : 0;
      for (int i = 0; i < options.size(); i++) if (selected.test(options.get(i).value())) { cursor = i; break; }
      scroll.resize(options.size(), height - 2);
      scroll.scrollTo(0);
   }

   void revealSelection() { scroll.reveal(cursor); }
   double scrollAmount() { return scroll.offset(); }
   void scrollTo(double offset) { scroll.scrollTo(offset); }

   @Override
   protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      var font = Minecraft.getInstance().font;
      graphics.fill(getX(), getY(), getRight(), getBottom(), DeskTheme.BORDER_SUBTLE);
      graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, DeskTheme.LIST_BACKGROUND);
      graphics.enableScissor(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1);
      int hovered = isMouseOver(mouseX, mouseY) && mouseX < getRight() - 9 ? scroll.rowAt(mouseY - getY() - 1) : -1;
      for (int i = scroll.first(); i < scroll.end(); i++) {
         int y = getY() + 1 + (int) Math.floor(i * ScrollListModel.ROW_HEIGHT - scroll.offset());
         var option = options.get(i);
         boolean current = selected.test(option.value());
         if (i == hovered) graphics.fill(getX() + 1, y, getRight() - 9, y + 22, DeskTheme.CONTROL_HOVER_BACKGROUND);
         if (current) graphics.fill(getX() + 2, y + 3, getX() + 4, y + 19, DeskTheme.ACCENT);
         int inset = option.icon().isEmpty() ? 8 : 28;
         if (!option.icon().isEmpty()) graphics.item(option.icon(), getX() + 8, y + 3);
         graphics.text(font, font.plainSubstrByWidth(option.label(), width - inset - 16), getX() + inset, y + 7,
               current ? DeskTheme.ACCENT : DeskTheme.TEXT_PRIMARY, false);
      }
      if (options.isEmpty()) graphics.text(font, "No matching songs", getX() + 8, getY() + 8, DeskTheme.TEXT_SECONDARY, false);
      graphics.disableScissor();
      if (scroll.maximum() > 0) {
         int top = thumbTop();
         graphics.fill(getRight() - 7, getY() + 2, getRight() - 2, getBottom() - 2, DeskTheme.SCROLLBAR_TRACK);
         graphics.fill(getRight() - 7, top, getRight() - 2, top + thumbHeight(), DeskTheme.SCROLLBAR_THUMB);
      }
      if (hovered >= 0 && font.width(options.get(hovered).label()) > width - (options.get(hovered).icon().isEmpty() ? 24 : 44)) {
         graphics.setTooltipForNextFrame(font, Component.literal(options.get(hovered).label()), mouseX, mouseY);
      }
   }

   private int trackHeight() { return height - 4; }
   private int thumbHeight() { return Math.min(trackHeight(), Math.max(12, (int) (trackHeight() * (height - 2.0) / (options.size() * 22.0)))); }
   private int thumbTop() { return getY() + 2 + (int) ((trackHeight() - thumbHeight()) * scroll.offset() / Math.max(1, scroll.maximum())); }

   @Override
   public void onClick(MouseButtonEvent event, boolean doubleClick) {
      if (event.x() >= getRight() - 9 && scroll.maximum() > 0) {
         draggingThumb = true;
         thumbGrab = event.y() >= thumbTop() && event.y() < thumbTop() + thumbHeight()
               ? event.y() - thumbTop() : thumbHeight() / 2.0;
         dragThumb(event.y());
      } else {
         cursor = scroll.rowAt(event.y() - getY() - 1);
         if (cursor >= 0) choose.accept(options.get(cursor).value());
      }
   }

   private void dragThumb(double y) {
      scroll.scrollTo((y - getY() - 2 - thumbGrab) * scroll.maximum() / Math.max(1, trackHeight() - thumbHeight()));
   }

   @Override
   protected void onDrag(MouseButtonEvent event, double dx, double dy) { if (draggingThumb) dragThumb(event.y()); }
   @Override
   public void onRelease(MouseButtonEvent event) { draggingThumb = false; }

   @Override
   public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
      if (!isMouseOver(x, y)) return false;
      scroll.scrollBy(-vertical * 22 * 3);
      return true;
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (!isFocused() || options.isEmpty()) return false;
      int next = switch (event.key()) {
         case GLFW.GLFW_KEY_DOWN -> Math.min(options.size() - 1, cursor + 1);
         case GLFW.GLFW_KEY_UP -> Math.max(0, cursor - 1);
         case GLFW.GLFW_KEY_HOME -> 0;
         case GLFW.GLFW_KEY_END -> options.size() - 1;
         case GLFW.GLFW_KEY_PAGE_DOWN -> Math.min(options.size() - 1, cursor + Math.max(1, height / 22));
         case GLFW.GLFW_KEY_PAGE_UP -> Math.max(0, cursor - Math.max(1, height / 22));
         default -> -1;
      };
      if (next >= 0) { cursor = next; scroll.reveal(cursor); return true; }
      if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_SPACE || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
         choose.accept(options.get(cursor).value());
         return true;
      }
      return false;
   }

   @Override
   protected void updateWidgetNarration(NarrationElementOutput output) {
      output.add(NarratedElementType.TITLE, getMessage());
      if (cursor >= 0) output.add(NarratedElementType.POSITION, Component.literal(options.get(cursor).label() + ", " + (cursor + 1) + " of " + options.size()));
      output.add(NarratedElementType.USAGE, Component.literal("Arrow keys to browse, Enter to select. Home and End jump to the first or last item."));
   }
}
