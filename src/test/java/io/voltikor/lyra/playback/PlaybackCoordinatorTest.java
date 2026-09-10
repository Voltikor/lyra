package io.voltikor.lyra.playback;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.noteblock.NoteBlockScanner;
import io.voltikor.lyra.noteblock.NoteBlockTuner;
import io.voltikor.lyra.song.Song;
import io.voltikor.lyra.song.SongFileManager;
import io.voltikor.lyra.song.SongLoadCoordinator;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackCoordinatorTest extends MinecraftTestSupport {
   @Test
   void seekingClampsAndPreservesPausedPreviewSession() {
      Fixture fixture = fixture();
      Song song = new Song("seek", "author");
      song.addNote(200, new io.voltikor.lyra.song.Note(null, 12));
      song.finishLoading();
      fixture.session.beginLoading(SongLoadIntent.PREVIEW);
      fixture.session.completeLoad(song, Path.of("seek.nbs"));
      fixture.coordinator.preview(null);
      assertTrue(fixture.coordinator.seek(null, 140));
      assertEquals(140, fixture.coordinator.snapshot().currentTick());
      assertEquals(PlaybackStage.PLAYING, fixture.session.stage());
      fixture.coordinator.pause(null);
      assertTrue(fixture.coordinator.seek(null, -100));
      assertEquals(0, fixture.coordinator.snapshot().currentTick());
      assertTrue(fixture.coordinator.seek(null, Integer.MAX_VALUE));
      assertEquals(200, fixture.coordinator.snapshot().currentTick());
      assertEquals(PlaybackStage.PAUSED, fixture.session.stage());
      fixture.coordinator.seek(null, 40);
      fixture.coordinator.play(null);
      assertEquals(40, fixture.coordinator.snapshot().currentTick());
      assertEquals(PlaybackMode.PREVIEW, fixture.session.mode());
   }

   @Test
   void seekingRejectsLoadingAndTuningAndKeepsTunedAssignments() {
      Fixture fixture = fixture();
      assertFalse(fixture.coordinator.seek(null, 20));
      fixture.session.beginLoading(SongLoadIntent.PLAY);
      assertFalse(fixture.coordinator.seek(null, 20));
      Song song = new Song("test", "author");
      song.finishLoading();
      fixture.session.completeLoad(song, Path.of("song.nbs"));
      fixture.session.beginSetup(false);
      assertFalse(fixture.coordinator.seek(null, 20));
      fixture.session.beginTuning();
      assertFalse(fixture.coordinator.seek(null, 20));
      fixture.session.markReady();
      fixture.tuner.setAnyNoteblockTuned(true);
      var note = new io.voltikor.lyra.song.Note(null, 12);
      fixture.scanner.noteBlockPositions().put(note, net.minecraft.core.BlockPos.ZERO);
      assertTrue(fixture.coordinator.seek(null, 0));
      assertEquals(PlaybackStage.READY, fixture.session.stage());
      assertTrue(fixture.tuner.anyNoteblockTuned());
      assertEquals(PlaybackMode.NOTEBLOCKS, fixture.session.mode());
      assertEquals(net.minecraft.core.BlockPos.ZERO, fixture.scanner.noteBlockPositions().get(note));
   }

   @Test
   void finishedSongRemainsSeekableAndReplayRestartsItsCursor() {
      Fixture fixture = fixture();
      load(fixture.session, SongLoadIntent.PREVIEW);
      fixture.coordinator.preview(null);
      fixture.engine.setCurrentTick(fixture.session.song().lastTick() + 1);
      fixture.coordinator.onSongEnd(null);
      assertEquals(PlaybackStage.PAUSED, fixture.session.stage());
      assertTrue(fixture.coordinator.snapshot().canSeek());
      assertEquals(Path.of("song.nbs"), fixture.coordinator.snapshot().loadedSongPath());
      fixture.coordinator.play(null);
      assertEquals(0, fixture.engine.currentTick());
      assertEquals(PlaybackStage.PLAYING, fixture.session.stage());
   }

   @Test
   void explicitStopMakesTheOldSongUnseekable() {
      Fixture fixture = fixture();
      load(fixture.session, SongLoadIntent.LOAD_ONLY);
      assertTrue(fixture.coordinator.seek(null, 0));
      fixture.coordinator.stop(null);
      assertFalse(fixture.coordinator.seek(null, 0));
   }

   @Test
   void loadedSongFlowsThroughTuningInsteadOfControllerPolicy() {
      Fixture fixture = fixture();
      load(fixture.session, SongLoadIntent.LOAD_ONLY);

      assertTrue(fixture.coordinator.play(null));

      assertEquals(PlaybackStage.SETUP, fixture.session.stage());
      assertEquals(PlaybackMode.NOTEBLOCKS, fixture.session.mode());
      assertTrue(fixture.session.autoPlayAfterTune());
   }

   @Test
   void previewPauseAndResumeUseExplicitStableTransitions() {
      Fixture fixture = fixture();
      load(fixture.session, SongLoadIntent.LOAD_ONLY);

      assertTrue(fixture.coordinator.preview(null));
      assertEquals(PlaybackStage.PLAYING, fixture.session.stage());
      assertEquals(PlaybackMode.PREVIEW, fixture.session.mode());

      assertTrue(fixture.coordinator.pause(null));
      assertEquals(PlaybackStage.PAUSED, fixture.session.stage());

      assertTrue(fixture.coordinator.play(null));
      assertEquals(PlaybackStage.PLAYING, fixture.session.stage());
      assertEquals(PlaybackMode.PREVIEW, fixture.session.mode());
   }

   @Test
   void invalidLifecycleCommandsReturnFalseWithoutBypassingStateMachine() {
      Fixture fixture = fixture();
      load(fixture.session, SongLoadIntent.LOAD_ONLY);
      assertTrue(fixture.coordinator.startTuning(null, false));

      assertFalse(fixture.coordinator.preview(null));
      assertFalse(fixture.coordinator.startTuning(null, false));
      assertEquals(PlaybackStage.SETUP, fixture.session.stage());
   }

   @Test
   void stopAtomicallyClearsPlaybackStateAndRunsExternalResetHook() {
      AtomicInteger resets = new AtomicInteger();
      Fixture fixture = fixture(resets::incrementAndGet);
      load(fixture.session, SongLoadIntent.LOAD_ONLY);
      fixture.engine.setCurrentTick(42);
      fixture.tuner.setAnyNoteblockTuned(true);

      fixture.coordinator.stop(null);

      PlaybackSnapshot snapshot = fixture.coordinator.snapshot();
      assertEquals(PlaybackStage.IDLE, snapshot.stage());
      assertEquals(PlaybackMode.NONE, snapshot.mode());
      assertNull(snapshot.song());
      assertNull(snapshot.loadedSongPath());
      assertEquals(0, snapshot.currentTick());
      assertFalse(fixture.tuner.anyNoteblockTuned());
      assertEquals(1, resets.get());
   }

   @Test
   void toggleActionDependsOnlyOnWorldAvailabilityAndSessionStage() {
      assertEquals(PlaybackCoordinator.ToggleAction.NONE,
            PlaybackCoordinator.toggleAction(PlaybackStage.PLAYING, false));
      assertEquals(PlaybackCoordinator.ToggleAction.PAUSE,
            PlaybackCoordinator.toggleAction(PlaybackStage.PLAYING, true));
      assertEquals(PlaybackCoordinator.ToggleAction.PLAY,
            PlaybackCoordinator.toggleAction(PlaybackStage.PAUSED, true));
      assertEquals(PlaybackCoordinator.ToggleAction.PLAY,
            PlaybackCoordinator.toggleAction(PlaybackStage.LOADED, true));
      assertEquals(PlaybackCoordinator.ToggleAction.PLAY,
            PlaybackCoordinator.toggleAction(PlaybackStage.READY, true));
      assertEquals(PlaybackCoordinator.ToggleAction.PLAY,
            PlaybackCoordinator.toggleAction(PlaybackStage.IDLE, true));
      assertEquals(PlaybackCoordinator.ToggleAction.NONE,
            PlaybackCoordinator.toggleAction(PlaybackStage.LOADING_SONG, true));
      assertEquals(PlaybackCoordinator.ToggleAction.NONE,
            PlaybackCoordinator.toggleAction(PlaybackStage.TUNING, true));
   }

   private static Fixture fixture() {
      return fixture(() -> {});
   }

   private static Fixture fixture(Runnable resetHook) {
      LyraSettings settings = new LyraSettings();
      SongFileManager songs = new SongFileManager();
      SongLoadCoordinator loads = new SongLoadCoordinator();
      PlaybackSession session = new PlaybackSession();
      SongPlaybackEngine engine = new SongPlaybackEngine();
      NoteBlockScanner scanner = new NoteBlockScanner();
      NoteBlockTuner tuner = new NoteBlockTuner();
      PlaybackCoordinator coordinator = new PlaybackCoordinator(settings, songs, loads, session, engine, scanner, tuner,
            resetHook);
      return new Fixture(coordinator, session, engine, scanner, tuner);
   }

   private static void load(PlaybackSession session, SongLoadIntent intent) {
      Song song = new Song("test", "author");
      song.finishLoading();
      session.beginLoading(intent);
      session.completeLoad(song, Path.of("song.nbs"));
   }

   private record Fixture(PlaybackCoordinator coordinator, PlaybackSession session, SongPlaybackEngine engine,
         NoteBlockScanner scanner, NoteBlockTuner tuner) {}
}
