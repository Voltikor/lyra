package io.voltikor.lyra.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.voltikor.lyra.LyraController;

/** Loaded only by Mod Menu; the rest of Lyra has no runtime dependency on its API. */
public final class LyraModMenu implements ModMenuApi {
   @Override
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      return parent -> LyraController.get().createConfigScreen(parent);
   }
}
