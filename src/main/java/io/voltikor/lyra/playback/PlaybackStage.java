package io.voltikor.lyra.playback;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum PlaybackStage {
   IDLE,
   LOADED,
   LOADING_SONG,
   SETUP,
   TUNING,
   WAITING_FOR_RECHECK,
   READY,
   PLAYING,
   PAUSED;

   /** Preserve the legacy status text while using clearer internal names. */
   public String displayName() {
      return switch (this) {
         case IDLE -> "NONE";
         case TUNING -> "TUNE";
         case WAITING_FOR_RECHECK -> "WAITING_TO_CHECK_NOTEBLOCKS";
         default -> this.name();
      };
   }
}
