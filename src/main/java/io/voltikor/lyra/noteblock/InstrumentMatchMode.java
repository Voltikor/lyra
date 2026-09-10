package io.voltikor.lyra.noteblock;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum InstrumentMatchMode {
   ANY_INSTRUMENT,
   EXACT_INSTRUMENTS;

   public static InstrumentMatchMode parse(String input) {
      if (input == null) {
         return null;
      }

      return switch (input.trim().toLowerCase()) {
         case "any", "anyinstrument", "anyinstruments" -> ANY_INSTRUMENT;
         case "exact", "exactinstrument", "exactinstruments" -> EXACT_INSTRUMENTS;
         default -> null;
      };
   }
}
