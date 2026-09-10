package io.voltikor.lyra.noteblock;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum RotateMode {
   VISIBLE_FACE,
   CLOSEST_FACE;

   public static RotateMode parse(String input) {
      if (input == null) {
         return null;
      }
      switch (input.trim().toLowerCase(Locale.ROOT)) {
         case "visible_face":
            return VISIBLE_FACE;
         case "closest_face":
            return CLOSEST_FACE;
         default:
            return null;
      }
   }
}
