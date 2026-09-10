package io.voltikor.lyra.playback;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.song.Song;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackSessionTest extends MinecraftTestSupport {
   private static final Path SONG_PATH = Path.of("song.nbs");

   @Test
   void startsAndResetsToIdleDefaults() {
      PlaybackSession session = loadedSession(SongLoadIntent.PLAY);
      session.beginSetup(true);
      session.beginTuning();
      session.waitForRecheck(20);

      session.reset();

      assertEquals(PlaybackStage.IDLE, session.stage());
      assertEquals(PlaybackMode.NONE, session.mode());
      assertNull(session.song());
      assertNull(session.loadedSongPath());
      assertNull(session.loadIntent());
      assertEquals(-1, session.waitTicks());
      assertFalse(session.autoPlayAfterTune());
   }

   @Test
   void loadIntentOwnsModeAndLoadedIdentity() {
      PlaybackSession session = new PlaybackSession();
      Song song = new Song("test", "author");

      session.beginLoading(SongLoadIntent.PREVIEW);
      assertEquals(PlaybackStage.LOADING_SONG, session.stage());
      assertEquals(PlaybackMode.PREVIEW, session.mode());
      assertEquals(SongLoadIntent.PREVIEW, session.loadIntent());

      session.completeLoad(song, SONG_PATH);
      assertEquals(PlaybackStage.LOADED, session.stage());
      assertEquals(song, session.song());
      assertEquals(SONG_PATH, session.loadedSongPath());
   }

   @Test
   void normalNoteblockLifecycleUsesExplicitTransitions() {
      PlaybackSession session = loadedSession(SongLoadIntent.PLAY);

      session.beginSetup(true);
      session.beginTuning();
      session.markReady();
      session.startPlayback();
      session.pausePlayback();
      session.resumePlayback();

      assertEquals(PlaybackStage.PLAYING, session.stage());
      assertEquals(PlaybackMode.NOTEBLOCKS, session.mode());
   }

   @Test
   void previewHasAnExplicitTransitionAndCanTakeOverStableLoadedStates() {
      PlaybackSession loaded = loadedSession(SongLoadIntent.LOAD_ONLY);
      loaded.startPreviewPlayback();
      assertEquals(PlaybackStage.PLAYING, loaded.stage());
      assertEquals(PlaybackMode.PREVIEW, loaded.mode());

      PlaybackSession ready = loadedSession(SongLoadIntent.LOAD_ONLY);
      ready.beginSetup(false);
      ready.beginTuning();
      ready.markReady();
      ready.startPreviewPlayback();
      assertEquals(PlaybackMode.PREVIEW, ready.mode());

      PlaybackSession paused = loadedSession(SongLoadIntent.PLAY);
      paused.beginSetup(false);
      paused.beginTuning();
      paused.markReady();
      paused.startPlayback();
      paused.pausePlayback();
      paused.startPreviewPlayback();
      assertEquals(PlaybackMode.PREVIEW, paused.mode());
   }

   @Test
   void invalidTransitionsAreRejected() {
      PlaybackSession session = new PlaybackSession();
      assertThrows(IllegalStateException.class, session::markReady);
      assertThrows(IllegalStateException.class, session::pausePlayback);
      assertThrows(IllegalStateException.class, session::startPlayback);
      assertThrows(IllegalStateException.class, () -> session.beginSetup(false));

      session.beginLoading(SongLoadIntent.PLAY);
      assertThrows(IllegalStateException.class, () -> session.beginLoading(SongLoadIntent.PREVIEW));
      assertThrows(IllegalStateException.class, () -> session.beginSetup(false));
   }

   @Test
   void previewCannotBypassLoadingOrTuning() {
      PlaybackSession loading = new PlaybackSession();
      loading.beginLoading(SongLoadIntent.PREVIEW);
      assertThrows(IllegalStateException.class, loading::startPreviewPlayback);

      PlaybackSession tuning = loadedSession(SongLoadIntent.PLAY);
      tuning.beginSetup(false);
      tuning.beginTuning();
      assertThrows(IllegalStateException.class, tuning::startPreviewPlayback);
   }

   @Test
   void waitCountdownPreservesExistingTimingSemantics() {
      PlaybackSession session = loadedSession(SongLoadIntent.PLAY);
      session.beginSetup(false);
      session.beginTuning();
      session.waitForRecheck(2);

      assertFalse(session.tickRecheckWait());
      assertTrue(session.tickRecheckWait());
      session.beginTuning();
      assertEquals(-1, session.waitTicks());
   }

   @Test
   void autoplayFlagIsConsumedOnlyOnce() {
      PlaybackSession session = loadedSession(SongLoadIntent.PLAY);
      session.beginSetup(true);

      assertTrue(session.consumeAutoPlayAfterTune());
      assertFalse(session.consumeAutoPlayAfterTune());
   }

   @Test
   void clearerInternalStageNamesPreserveExistingStatusLabels() {
      assertEquals("NONE", PlaybackStage.IDLE.displayName());
      assertEquals("TUNE", PlaybackStage.TUNING.displayName());
      assertEquals("WAITING_TO_CHECK_NOTEBLOCKS", PlaybackStage.WAITING_FOR_RECHECK.displayName());
      assertEquals("PLAYING", PlaybackStage.PLAYING.displayName());
   }

   private static PlaybackSession loadedSession(SongLoadIntent intent) {
      PlaybackSession session = new PlaybackSession();
      session.beginLoading(intent);
      session.completeLoad(new Song("test", "author"), SONG_PATH);
      return session;
   }
}
