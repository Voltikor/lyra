package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.MinecraftTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;

class ScrollableChoicesTest extends MinecraftTestSupport {
   @Test
   void mouseSelectsScrolledRowAndInheritedNullValue() {
      var chosen = new ArrayList<Integer>();
      var menu = new ScrollableChoices<Integer>(10, 20, 180, 68, "Test", value -> false, chosen::add);
      var options = new ArrayList<ScrollableChoices.Option<Integer>>();
      options.add(new ScrollableChoices.Option<>(null, "Inherit"));
      IntStream.range(0, 20).forEach(i -> options.add(new ScrollableChoices.Option<>(i, "Option " + i)));
      menu.setOptions(options);
      menu.onClick(mouse(30, 30), false);
      assertEquals(1, chosen.size());
      assertNull(chosen.getFirst());
      assertTrue(menu.mouseScrolled(30, 30, 0, -1));
      menu.onClick(mouse(30, 30), false);
      assertEquals(2, chosen.getLast());
   }

   @Test
   void keyboardRevealsAndSelectsLastOption() {
      var chosen = new ArrayList<Integer>();
      var menu = new ScrollableChoices<Integer>(10, 20, 180, 68, "Test", value -> value == 0, chosen::add);
      menu.setOptions(IntStream.range(0, 30).mapToObj(i -> new ScrollableChoices.Option<>(i, "Option " + i)).toList());
      menu.setFocused(true);
      assertTrue(menu.keyPressed(new KeyEvent(GLFW.GLFW_KEY_END, 0, 0)));
      assertTrue(menu.scrollAmount() > 0);
      assertTrue(menu.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)));
      assertEquals(List.of(29), chosen);
   }

   @Test
   void scrollbarDragDoesNotSelectAnOption() {
      var chosen = new ArrayList<Integer>();
      var menu = new ScrollableChoices<Integer>(10, 20, 180, 68, "Test", value -> false, chosen::add);
      menu.setOptions(IntStream.range(0, 30).mapToObj(i -> new ScrollableChoices.Option<>(i, "Option " + i)).toList());
      menu.onClick(mouse(185, 25), false);
      menu.onDrag(mouse(185, 85), 0, 60);
      menu.onRelease(mouse(185, 85));
      assertTrue(menu.scrollAmount() > 0);
      assertTrue(chosen.isEmpty());
   }

   private static MouseButtonEvent mouse(double x, double y) {
      return new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
   }
}
