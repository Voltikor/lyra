package io.voltikor.lyra;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class LyraClient implements ClientModInitializer {
   private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("lyra", "general"));
   public static final KeyMapping TOGGLE_PLAY_PAUSE_KEYBIND = new KeyMapping("Play/Pause", InputConstants.Type.KEYSYM, 297, CATEGORY);
   public static final KeyMapping OPEN_MENU_KEYBIND = new KeyMapping("key.lyra.open_menu", InputConstants.Type.KEYSYM,
         org.lwjgl.glfw.GLFW.GLFW_KEY_F9, CATEGORY);

   public void onInitializeClient() {
      KeyMappingHelper.registerKeyMapping(TOGGLE_PLAY_PAUSE_KEYBIND);
      KeyMappingHelper.registerKeyMapping(OPEN_MENU_KEYBIND);
      LyraController controller = LyraController.get();
      controller.initialize();
      ClientTickEvents.START_CLIENT_TICK.register(controller::onTick);
      ClientTickEvents.END_CLIENT_TICK.register(client -> drainClicks(TOGGLE_PLAY_PAUSE_KEYBIND::consumeClick,
            () -> controller.onTogglePlayPauseKeybind(client)));
      ClientTickEvents.END_CLIENT_TICK.register(client -> drainClicks(OPEN_MENU_KEYBIND::consumeClick, () -> {
         if (client.screen == null && client.player != null && client.level != null) {
            client.setScreen(controller.createConfigScreen(null));
         }
      }));
      ClientLifecycleEvents.CLIENT_STOPPING.register(client -> controller.flushSettings());
      ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> controller.registerCommands(dispatcher));
   }

   static void drainClicks(BooleanSupplier consume, Runnable toggle) {
      boolean clicked = false;
      while (consume.getAsBoolean()) clicked = true;
      if (clicked) toggle.run();
   }
}
