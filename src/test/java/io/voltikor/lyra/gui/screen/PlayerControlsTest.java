package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.playback.PlaybackMode;
import io.voltikor.lyra.playback.PlaybackSnapshot;
import io.voltikor.lyra.playback.PlaybackStage;
import io.voltikor.lyra.song.Song;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerControlsTest {
   @Test
   void noteBlockStartAndResumeRequireNearbyBlocks() {
      for (var stage : new PlaybackStage[]{PlaybackStage.LOADED, PlaybackStage.READY, PlaybackStage.PAUSED}) {
         var state = state(stage, PlaybackMode.NOTEBLOCKS);
         assertFalse(PlayerControls.canUseTransport(state, true, true, true, 0));
         assertTrue(PlayerControls.canUseTransport(state, true, true, true, 1));
      }
   }

   @Test
   void pauseAndPreviewResumeStayUsableWithoutBlocks() {
      assertTrue(PlayerControls.canUseTransport(state(PlaybackStage.PLAYING, PlaybackMode.NOTEBLOCKS), true, true, true, 0));
      assertTrue(PlayerControls.canUseTransport(state(PlaybackStage.PAUSED, PlaybackMode.PREVIEW), true, true, true, 0));
      assertFalse(PlayerControls.canUseTransport(state(PlaybackStage.PAUSED, PlaybackMode.PREVIEW), true, true, false, 0));
   }

   @Test
   void blocksDoNotBypassWorldSongOrTuningRequirements() {
      assertFalse(PlayerControls.canUseTransport(state(PlaybackStage.READY, PlaybackMode.NOTEBLOCKS), false, true, true, 1));
      assertFalse(PlayerControls.canUseTransport(state(PlaybackStage.TUNING, PlaybackMode.NOTEBLOCKS), true, true, true, 1));
      var idle = new PlaybackSnapshot(PlaybackStage.IDLE, PlaybackMode.NONE, null, null, 0);
      assertFalse(PlayerControls.canUseTransport(idle, true, false, false, 1));
      assertFalse(PlayerControls.canUseTransport(idle, true, true, false, 0));
      assertTrue(PlayerControls.canUseTransport(idle, true, true, false, 1));
   }

   private static PlaybackSnapshot state(PlaybackStage stage, PlaybackMode mode) {
      return new PlaybackSnapshot(stage, mode, new Song("test", ""), Path.of("test.nbs"), 0);
   }
}
