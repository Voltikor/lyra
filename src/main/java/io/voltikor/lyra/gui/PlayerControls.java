package io.voltikor.lyra.gui;

import io.voltikor.lyra.playback.PlaybackMode;
import io.voltikor.lyra.playback.PlaybackSnapshot;
import io.voltikor.lyra.playback.PlaybackStage;

final class PlayerControls {
   private PlayerControls() {}

   static boolean needsNoteBlocks(PlaybackSnapshot state, boolean selectedIsLoaded) {
      return !state.playing() && !(selectedIsLoaded && state.stage() == PlaybackStage.PAUSED && state.mode() == PlaybackMode.PREVIEW);
   }

   static boolean canUseTransport(PlaybackSnapshot state, boolean inWorld, boolean hasSelection,
         boolean selectedIsLoaded, int nearbyBlocks) {
      return inWorld && (state.stage() == PlaybackStage.IDLE || state.canSeek())
            && (state.hasSong() || hasSelection)
            && (!needsNoteBlocks(state, selectedIsLoaded) || nearbyBlocks > 0);
   }
}
