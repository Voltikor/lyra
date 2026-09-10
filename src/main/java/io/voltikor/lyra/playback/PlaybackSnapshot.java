package io.voltikor.lyra.playback;

import io.voltikor.lyra.song.Song;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record PlaybackSnapshot(
      PlaybackStage stage,
      PlaybackMode mode,
      Song song,
      Path loadedSongPath,
      int currentTick) {

   public boolean playing() {
      return this.stage == PlaybackStage.PLAYING;
   }

   public boolean hasSong() {
      return this.song != null;
   }

   public boolean canSeek() {
      return this.hasSong() && switch (this.stage) {
         case LOADED, READY, PLAYING, PAUSED -> true;
         default -> false;
      };
   }
}
