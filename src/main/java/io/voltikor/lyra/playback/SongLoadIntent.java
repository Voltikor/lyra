package io.voltikor.lyra.playback;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum SongLoadIntent {
   LOAD_ONLY(PlaybackMode.NOTEBLOCKS),
   PLAY(PlaybackMode.NOTEBLOCKS),
   PREVIEW(PlaybackMode.PREVIEW);

   private final PlaybackMode mode;

   SongLoadIntent(PlaybackMode mode) {
      this.mode = mode;
   }

   public PlaybackMode mode() {
      return this.mode;
   }
}
