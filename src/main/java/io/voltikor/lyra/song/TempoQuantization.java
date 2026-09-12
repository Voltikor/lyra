package io.voltikor.lyra.song;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public enum TempoQuantization {
   DEFAULT,
   SNAP_NEAREST,
   SNAP_UP,
   SNAP_DOWN;

   private static final Logger LOGGER = LoggerFactory.getLogger("Lyra/Tempo");

   // Avoid moving an aligned tempo across its grid boundary.
   private static final double GRID_EPSILON = 1e-6;

   public static TempoQuantization parse(String raw) {
      if (raw == null) {
         return null;
      }
      String lower = raw.trim().toLowerCase(Locale.ROOT);
      return switch (lower) {
         case "default", "legacy", "original" -> DEFAULT;
         case "snap_nearest", "snap", "nearest" -> SNAP_NEAREST;
         case "snap_up", "up", "faster" -> SNAP_UP;
         case "snap_down", "down", "slower" -> SNAP_DOWN;
         default -> null;
      };
   }

   public double calculateEffectiveSpeed(double originalSpeed) {
      if (originalSpeed <= 0.0) {
         return 10.0;
      }
      if (this == DEFAULT) {
         return originalSpeed;
      }

      double exactMcTicksPerNbsTick = 20.0 / originalSpeed;

      int mcTicks;
      switch (this) {
         case SNAP_NEAREST -> {
            mcTicks = (int)Math.round(exactMcTicksPerNbsTick);
         }
         case SNAP_UP -> {
            mcTicks = (int)Math.floor(exactMcTicksPerNbsTick + GRID_EPSILON);
         }
         case SNAP_DOWN -> {
            mcTicks = (int)Math.ceil(exactMcTicksPerNbsTick - GRID_EPSILON);
         }
         default -> throw new IllegalStateException(
               "Unhandled TempoQuantization value: " + this +
               ". Add a case to calculateEffectiveSpeed() for new enum members.");
      }

      if (mcTicks < 1) {
         if (this == SNAP_UP) {
            LOGGER.warn(
                  "[TempoQuantization] SNAP_UP: rawSpeed={} NBS-TPS exceeds the 20 TPS grid ceiling; clamping to 20 TPS",
                  String.format("%.4f", originalSpeed));
         }
         mcTicks = 1;
      }

      return 20.0 / mcTicks;
   }
}
