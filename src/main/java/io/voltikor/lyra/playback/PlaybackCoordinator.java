package io.voltikor.lyra.playback;

import io.voltikor.lyra.command.LyraMessenger;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.noteblock.NoteBlockScanner;
import io.voltikor.lyra.noteblock.NoteBlockTuner;
import io.voltikor.lyra.song.Song;
import io.voltikor.lyra.song.SongFileManager;
import io.voltikor.lyra.song.SongLoadCoordinator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class PlaybackCoordinator {
   private static final Logger LOGGER = LoggerFactory.getLogger("Lyra");
   private static final long SONG_LOAD_TIMEOUT_SECONDS = 60L;

   private final LyraSettings settings;
   private final SongFileManager songs;
   private final SongLoadCoordinator loads;
   private final PlaybackSession session;
   private final SongPlaybackEngine engine;
   private final NoteBlockScanner scanner;
   private final NoteBlockTuner tuner;
   private final TuningCoordinator tuning;
   private final Runnable resetHook;
   private long loadGeneration;

   public PlaybackCoordinator(LyraSettings settings, SongFileManager songs, SongLoadCoordinator loads,
         PlaybackSession session, SongPlaybackEngine engine, NoteBlockScanner scanner, NoteBlockTuner tuner) {
      this(settings, songs, loads, session, engine, scanner, tuner, () -> {});
   }

   public PlaybackCoordinator(LyraSettings settings, SongFileManager songs, SongLoadCoordinator loads,
         PlaybackSession session, SongPlaybackEngine engine, NoteBlockScanner scanner, NoteBlockTuner tuner,
         Runnable resetHook) {
      this.settings = settings;
      this.songs = songs;
      this.loads = loads;
      this.session = session;
      this.engine = engine;
      this.scanner = scanner;
      this.tuner = tuner;
      this.tuning = new TuningCoordinator(session, scanner, tuner);
      this.resetHook = resetHook == null ? () -> {} : resetHook;
   }

   public PlaybackSnapshot snapshot() {
      return new PlaybackSnapshot(this.session.stage(), this.session.mode(), this.session.song(),
            this.session.loadedSongPath(), this.engine.currentTick());
   }

   public Path selectedSongPath() {
      return this.songs.selectedSongPath(this.settings);
   }

   public int nearbyPlayableBlocks() {
      return this.scanner.nearbySnapshot().playableTotal();
   }

   public int refreshNearbyPlayableBlocks(Minecraft client) {
      this.scanner.scanNearbyNoteblocks(client, this.settings);
      return this.nearbyPlayableBlocks();
   }

   public void setSelectedSongPath(Path path) {
      this.songs.setSelectedSongPath(this.settings, path, null);
   }

   public boolean load(Minecraft client, Path path, SongLoadIntent intent) {
      if (path == null) {
         LyraMessenger.errorFormatted(client, "Song not found.");
         return false;
      }

      this.setSelectedSongPath(path);
      long requestGeneration = ++this.loadGeneration;
      this.resetRuntimeState();
      this.session.beginLoading(intent);
      LyraMessenger.replyFormatted(client, "Loading song \"%1$s\"...", path.getFileName());

      long started = System.nanoTime();
      CompletableFuture<Song> future = this.songs.loadSongAsync(
            path, this.settings, warning -> LOGGER.warn("[SongAdjust] {}", warning));
      this.loads.track(future, SONG_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((result, ignored) -> {
         Minecraft mc = client == null ? Minecraft.getInstance() : client;
         if (mc == null) {
            return;
         }
         mc.execute(() -> this.completeLoad(mc, path, intent, result, started, requestGeneration));
      });
      return true;
   }

   private void completeLoad(Minecraft client, Path path, SongLoadIntent intent,
         SongLoadCoordinator.LoadResult result, long started, long requestGeneration) {
      if (requestGeneration != this.loadGeneration) {
         return;
      }
      if (result.status() == SongLoadCoordinator.Status.STALE) {
         return;
      }
      if (result.status() != SongLoadCoordinator.Status.SUCCESS) {
         LyraMessenger.errorFormatted(client, SongLoadCoordinator.failureMessage(result));
         if (result.status() == SongLoadCoordinator.Status.FAILED) {
            LOGGER.error("Failed to load song {}", path, result.error());
         }
         this.stop(client);
         return;
      }

      Song song = result.song();
      this.session.completeLoad(song, path);
      long tookMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
      LyraMessenger.replyFormatted(client, "Loaded \"%1$s\" by %2$s in %3$s ms.",
            song.title(), song.author(), tookMs);
      this.announceTranspose(client, path, song);

      switch (intent) {
         case PREVIEW -> this.startPreview(client);
         case LOAD_ONLY -> {
            if (client.player != null && client.level != null) {
               this.scanner.scanNearbyNoteblocks(client, this.settings);
            }
         }
         case PLAY -> this.startTuning(client, true);
      }
   }

   public boolean play(Minecraft client) {
      if (!this.session.hasSong()) {
         Path selected = this.selectedSongPath();
         if (selected == null) {
            LyraMessenger.errorFormatted(client, "No song loaded.");
            return false;
         }
         return this.load(client, selected, SongLoadIntent.PLAY);
      }

      return switch (this.session.stage()) {
         case LOADED -> this.startTuning(client, true);
         case READY -> this.startNoteblockPlayback(client);
         case PAUSED -> this.resumePlayback(client);
         case PLAYING -> true;
         default -> {
            LyraMessenger.errorFormatted(client, "Song is not ready yet. Current stage: %1$s", this.session.stage().displayName());
            yield false;
         }
      };
   }

   public boolean preview(Minecraft client) {
      if (!this.session.hasSong()) {
         LyraMessenger.errorFormatted(client, "No song loaded.");
         return false;
      }
      try {
         return this.startPreview(client);
      } catch (IllegalStateException error) {
         LyraMessenger.errorFormatted(client, "Song cannot be previewed while stage is %1$s.", this.session.stage().displayName());
         return false;
      }
   }

   private boolean startPreview(Minecraft client) {
      this.engine.prepareForPlayback(this.session.song());
      this.session.startPreviewPlayback();
      LyraMessenger.replyFormatted(client, "Playing.");
      return true;
   }

   public boolean startTuning(Minecraft client, boolean autoPlay) {
      if (!this.session.hasSong()) {
         LyraMessenger.errorFormatted(client, "No song loaded.");
         return false;
      }
      try {
         this.tuning.start(autoPlay);
      } catch (IllegalStateException error) {
         LyraMessenger.errorFormatted(client, "Song cannot be tuned while stage is %1$s.", this.session.stage().displayName());
         return false;
      }
      LyraMessenger.replyFormatted(client, "Starting tuning.");
      return true;
   }

   private boolean startNoteblockPlayback(Minecraft client) {
      if (requiresSurvival(client)) {
         LyraMessenger.errorFormatted(client, "Noteblock play mode requires survival mode.");
         return false;
      }
      this.engine.prepareForPlayback(this.session.song());
      this.session.startPlayback();
      LyraMessenger.replyFormatted(client, "Playing.");
      this.engine.rotatePreemptivelyToNextNote(client, this.session.song(), this.session.mode(), this.settings,
            this.scanner, this.tuner);
      return true;
   }

   private boolean resumePlayback(Minecraft client) {
      if (this.session.mode() == PlaybackMode.NOTEBLOCKS && requiresSurvival(client)) {
         LyraMessenger.errorFormatted(client, "Noteblock play mode requires survival mode.");
         return false;
      }
      this.engine.prepareForPlayback(this.session.song());
      this.session.resumePlayback();
      LyraMessenger.replyFormatted(client, "Playing.");
      this.engine.rotatePreemptivelyToNextNote(client, this.session.song(), this.session.mode(), this.settings,
            this.scanner, this.tuner);
      return true;
   }

   public boolean pause(Minecraft client) {
      if (this.session.stage() != PlaybackStage.PLAYING) {
         LyraMessenger.errorFormatted(client, "Nothing to pause/resume.");
         return false;
      }
      this.session.pausePlayback();
      LyraMessenger.replyFormatted(client, "Paused.");
      return true;
   }

   public boolean seek(Minecraft client, int tick) {
      if (!this.snapshot().canSeek()) return false;
      this.engine.seek(this.session.song(), tick);
      if (this.session.stage() == PlaybackStage.PLAYING && client != null && client.player != null) {
         this.engine.rotatePreemptivelyToNextNote(client, this.session.song(), this.session.mode(),
               this.settings, this.scanner, this.tuner);
      }
      return true;
   }

   public void stop(Minecraft client) {
      if (this.session.stage() != PlaybackStage.IDLE || this.session.hasSong() || this.loads.activeFuture() != null) {
         LyraMessenger.replyFormatted(client, "Stopping.");
      }
      ++this.loadGeneration;
      this.resetRuntimeState();
   }

   public void tick(Minecraft client) {
      this.engine.incrementTicks();
      if (this.tuning.handlesCurrentStage()) {
         this.tickTuning(client);
      } else if (this.session.stage() == PlaybackStage.PLAYING) {
         this.tickPlaying(client);
      }
   }

   private void tickTuning(Minecraft client) {
      TuningCoordinator.TickResult result = this.tuning.tick(client, this.session.song(), this.settings,
            this.engine.ticks(), () -> this.engine.setTicks(0));
      switch (result) {
         case NO_NEARBY_BLOCKS -> {
            LyraMessenger.errorFormatted(client, "Can't find any nearby noteblock.");
            this.stop(client);
         }
         case NO_MAPPED_BLOCKS -> {
            LyraMessenger.errorFormatted(client, "Can't map song notes to nearby noteblocks.");
            this.stop(client);
         }
         case AUTOPLAY -> this.startNoteblockPlayback(client);
         default -> { }
      }
   }

   private void tickPlaying(Minecraft client) {
      SongPlaybackEngine.TickResult result = this.engine.tickPlayback(client, this.session.song(), this.session.mode(),
            this.settings, this.scanner, this.tuner);
      switch (result) {
         case SONG_ENDED -> this.onSongEnd(client);
         case SURVIVAL_REQUIRED -> {
            LyraMessenger.errorFormatted(client, "Noteblock play mode requires survival mode.");
            this.stop(client);
         }
         default -> { }
      }
   }

   void onSongEnd(Minecraft client) {
      if (this.settings.autoPlay() && this.session.mode() != PlaybackMode.PREVIEW) {
         if (!this.playRandom(client)) {
            this.stop(client);
         }
      } else {
         // Keep the timeline and tuned block assignments available for replay and seeking.
         this.session.pausePlayback();
      }
   }

   public boolean playRandom(Minecraft client) {
      List<Path> available = this.songs.availableSongs();
      if (available.isEmpty()) {
         Path songsDir = this.songs.songsDir();
         LyraMessenger.replyFormatted(client, "No playable song found in %1$s",
               songsDir == null ? "the song folder" : songsDir.toAbsolutePath());
         return false;
      }
      Path random = available.get(ThreadLocalRandom.current().nextInt(available.size()));
      return this.load(client, random, SongLoadIntent.PLAY);
   }

   public void togglePlayPause(Minecraft client) {
      boolean inWorld = client != null && client.level != null && client.player != null;
      switch (toggleAction(this.session.stage(), inWorld)) {
         case PAUSE -> this.pause(client);
         case PLAY -> {
            if (this.session.hasSong() || this.selectedSongPath() != null) {
               this.play(client);
            }
         }
         case NONE -> { }
      }
   }

   public static ToggleAction toggleAction(PlaybackStage stage, boolean inWorld) {
      if (!inWorld) {
         return ToggleAction.NONE;
      }
      return switch (stage) {
         case PLAYING -> ToggleAction.PAUSE;
         case PAUSED, IDLE, LOADED, READY -> ToggleAction.PLAY;
         default -> ToggleAction.NONE;
      };
   }

   public enum ToggleAction { PAUSE, PLAY, NONE }

   private void resetRuntimeState() {
      this.loads.cancel();
      this.engine.reset();
      this.scanner.reset();
      this.tuner.reset();
      this.session.reset();
      this.resetHook.run();
   }

   private static boolean requiresSurvival(Minecraft client) {
      return client != null && client.player != null && client.player.hasInfiniteMaterials();
   }

   private void announceTranspose(Minecraft client, Path songPath, Song song) {
      int semitones = song.transposeSemitones();
      if (semitones == 0) {
         return;
      }
      String setting = song.transposeSetting().enabled()
            ? song.transposeSetting().serialized()
            : "off (use 'transpose auto' to apply the suggested shift)";
      LyraMessenger.infoFormatted(client,
            "Transpose changes \"%1$s\" by %2$s semitone(s); setting: %3$s.",
            songPath.getFileName(), signedNumber(semitones), setting);
   }

   private static String signedNumber(int value) {
      return value > 0 ? "+" + value : Integer.toString(value);
   }
}
