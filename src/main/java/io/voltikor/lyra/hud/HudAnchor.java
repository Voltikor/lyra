package io.voltikor.lyra.hud;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum HudAnchor {
   TOP_LEFT,
   TOP_RIGHT,
   BOTTOM_LEFT,
   BOTTOM_RIGHT;

   public static HudAnchor parse(String input) {
      if (input == null) {
         return null;
      }
      String normalized = input.trim().toUpperCase(Locale.ROOT).replace('-', '_');
      for (HudAnchor anchor : values()) {
         if (anchor.name().equals(normalized)) {
            return anchor;
         }
      }
      return null;
   }
}
