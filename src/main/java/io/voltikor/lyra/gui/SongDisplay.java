package io.voltikor.lyra.gui;

import io.voltikor.lyra.playback.PlaybackSnapshot;
import java.nio.file.Path;

final class SongDisplay {
   private SongDisplay() {}

   static String title(PlaybackSnapshot state, Path selected) {
      if (state.hasSong() && state.song().title() != null && !state.song().title().isBlank()) return state.song().title().strip();
      Path path = state.hasSong() ? state.loadedSongPath() : selected;
      if (path != null) return path.getFileName().toString();
      return state.hasSong() ? "Untitled song" : "What shall we play?";
   }
}
