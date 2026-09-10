package io.voltikor.lyra.playback;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.noteblock.NoteBlockScanner;
import io.voltikor.lyra.noteblock.NoteBlockTuner;
import io.voltikor.lyra.song.Song;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuningCoordinatorTest extends MinecraftTestSupport {
   @Test
   void startResetsTuningStateAndBeginsSetup() {
      PlaybackSession session = new PlaybackSession();
      NoteBlockScanner scanner = new NoteBlockScanner();
      NoteBlockTuner tuner = new NoteBlockTuner();
      tuner.setAnyNoteblockTuned(true);
      TuningCoordinator coordinator = new TuningCoordinator(session, scanner, tuner);
      load(session);

      coordinator.start(true);

      assertEquals(PlaybackStage.SETUP, session.stage());
      assertTrue(session.autoPlayAfterTune());
      assertFalse(tuner.anyNoteblockTuned());
      assertTrue(scanner.nearbySnapshot().tuningCandidates().isEmpty());
   }

   @Test
   void emptyTuneQueueBecomesReadyAndRequestsAutoplay() {
      PlaybackSession session = new PlaybackSession();
      NoteBlockScanner scanner = new NoteBlockScanner();
      NoteBlockTuner tuner = new NoteBlockTuner();
      TuningCoordinator coordinator = new TuningCoordinator(session, scanner, tuner);
      LyraSettings settings = new LyraSettings();
      load(session);
      session.beginSetup(true);
      session.beginTuning();

      TuningCoordinator.TickResult result = coordinator.tick(null, null, settings, 0, () -> {});

      assertEquals(TuningCoordinator.TickResult.AUTOPLAY, result);
      assertEquals(PlaybackStage.READY, session.stage());
      assertFalse(session.autoPlayAfterTune());
   }

   @Test
   void tunedQueueSchedulesThenCompletesDelayedRecheck() {
      PlaybackSession session = new PlaybackSession();
      NoteBlockScanner scanner = new NoteBlockScanner();
      NoteBlockTuner tuner = new NoteBlockTuner();
      TuningCoordinator coordinator = new TuningCoordinator(session, scanner, tuner);
      LyraSettings settings = new LyraSettings();
      settings.setCheckNoteblocksAgainDelay(1);
      load(session);
      session.beginSetup(false);
      session.beginTuning();
      tuner.setAnyNoteblockTuned(true);

      assertEquals(TuningCoordinator.TickResult.RECHECK_SCHEDULED,
            coordinator.tick(null, null, settings, 0, () -> {}));
      assertEquals(PlaybackStage.WAITING_FOR_RECHECK, session.stage());

      assertEquals(TuningCoordinator.TickResult.RECHECKED,
            coordinator.tick(null, null, settings, 0, () -> {}));
      assertEquals(PlaybackStage.TUNING, session.stage());
   }

   @Test
   void setupWithoutWorldReportsNoNearbyBlocks() {
      PlaybackSession session = new PlaybackSession();
      TuningCoordinator coordinator = new TuningCoordinator(session, new NoteBlockScanner(), new NoteBlockTuner());
      load(session);
      session.beginSetup(false);

      assertEquals(TuningCoordinator.TickResult.NO_NEARBY_BLOCKS,
            coordinator.tick(null, null, new LyraSettings(), 0, () -> {}));
      assertEquals(PlaybackStage.SETUP, session.stage());
   }

   private static void load(PlaybackSession session) {
      session.beginLoading(SongLoadIntent.LOAD_ONLY);
      session.completeLoad(new Song("test", "author"), Path.of("song.nbs"));
   }
}
