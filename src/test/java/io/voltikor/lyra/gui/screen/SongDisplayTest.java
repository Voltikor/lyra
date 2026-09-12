package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.playback.PlaybackMode;
import io.voltikor.lyra.playback.PlaybackSnapshot;
import io.voltikor.lyra.playback.PlaybackStage;
import io.voltikor.lyra.song.Song;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SongDisplayTest {
   @Test
   void blankMetadataUsesLoadedFilenameInPreviewAndNoteBlockPlayback() {
      for (var mode : new PlaybackMode[]{PlaybackMode.PREVIEW, PlaybackMode.NOTEBLOCKS}) {
         for (String title : new String[]{"", "  \t", null}) {
            var state = new PlaybackSnapshot(PlaybackStage.PLAYING, mode, new Song(title, ""), Path.of("Actual song.nbs"), 10);
            assertEquals("Actual song.nbs", SongDisplay.title(state, Path.of("Different selection.nbs")));
         }
      }
   }

   @Test
   void metadataTitleAndUnloadedSelectionRemainVisible() {
      var loaded = new PlaybackSnapshot(PlaybackStage.PAUSED, PlaybackMode.PREVIEW,
            new Song(" A quiet afternoon ", "author"), Path.of("file.nbs"), 10);
      assertEquals("A quiet afternoon", SongDisplay.title(loaded, null));
      var idle = new PlaybackSnapshot(PlaybackStage.IDLE, PlaybackMode.NONE, null, null, 0);
      assertEquals("Selected.nbs", SongDisplay.title(idle, Path.of("Selected.nbs")));
      assertEquals("What shall we play?", SongDisplay.title(idle, null));
   }
}
