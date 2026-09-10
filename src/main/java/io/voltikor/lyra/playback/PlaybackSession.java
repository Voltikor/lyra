package io.voltikor.lyra.playback;

import io.voltikor.lyra.song.Song;
import java.nio.file.Path;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class PlaybackSession {
   private PlaybackStage stage = PlaybackStage.IDLE;
   private PlaybackMode mode = PlaybackMode.NONE;
   private Song song;
   private Path loadedSongPath;
   private SongLoadIntent loadIntent;
   private int waitTicks = -1;
   private boolean autoPlayAfterTune;

   public PlaybackStage stage() {
      return this.stage;
   }

   public PlaybackMode mode() {
      return this.mode;
   }

   public Song song() {
      return this.song;
   }

   public Path loadedSongPath() {
      return this.loadedSongPath;
   }

   public SongLoadIntent loadIntent() {
      return this.loadIntent;
   }

   public boolean hasSong() {
      return this.song != null;
   }

   public void beginLoading(SongLoadIntent intent) {
      this.requireStage(PlaybackStage.IDLE);
      this.loadIntent = Objects.requireNonNull(intent, "intent");
      this.mode = intent.mode();
      this.stage = PlaybackStage.LOADING_SONG;
   }

   public void completeLoad(Song song, Path path) {
      this.requireStage(PlaybackStage.LOADING_SONG);
      this.song = Objects.requireNonNull(song, "song");
      this.loadedSongPath = Objects.requireNonNull(path, "path");
      this.stage = PlaybackStage.LOADED;
   }

   public void beginSetup(boolean autoPlayAfterTune) {
      this.requireSong();
      this.requireStage(PlaybackStage.LOADED, PlaybackStage.READY, PlaybackStage.PAUSED);
      this.mode = PlaybackMode.NOTEBLOCKS;
      this.autoPlayAfterTune = autoPlayAfterTune;
      this.waitTicks = -1;
      this.stage = PlaybackStage.SETUP;
   }

   public void beginTuning() {
      this.requireStage(PlaybackStage.SETUP, PlaybackStage.WAITING_FOR_RECHECK);
      this.waitTicks = -1;
      this.stage = PlaybackStage.TUNING;
   }

   public void waitForRecheck(int waitTicks) {
      this.requireStage(PlaybackStage.TUNING);
      this.waitTicks = waitTicks;
      this.stage = PlaybackStage.WAITING_FOR_RECHECK;
   }

   public boolean tickRecheckWait() {
      this.requireStage(PlaybackStage.WAITING_FOR_RECHECK);
      return --this.waitTicks <= 0;
   }

   public void markReady() {
      this.requireStage(PlaybackStage.TUNING);
      this.waitTicks = -1;
      this.stage = PlaybackStage.READY;
   }

   public void startPreviewPlayback() {
      this.requireSong();
      this.requireStage(PlaybackStage.LOADED, PlaybackStage.READY, PlaybackStage.PAUSED);
      this.mode = PlaybackMode.PREVIEW;
      this.stage = PlaybackStage.PLAYING;
   }

   public void startPlayback() {
      this.requireSong();
      this.requireStage(PlaybackStage.READY);
      if (this.mode != PlaybackMode.NOTEBLOCKS) {
         throw new IllegalStateException("Note-block playback requires NOTEBLOCKS mode");
      }
      this.stage = PlaybackStage.PLAYING;
   }

   public void resumePlayback() {
      this.requireSong();
      this.requireStage(PlaybackStage.PAUSED);
      this.stage = PlaybackStage.PLAYING;
   }

   public void pausePlayback() {
      this.requireStage(PlaybackStage.PLAYING);
      this.stage = PlaybackStage.PAUSED;
   }

   public int waitTicks() {
      return this.waitTicks;
   }

   public boolean autoPlayAfterTune() {
      return this.autoPlayAfterTune;
   }

   public boolean consumeAutoPlayAfterTune() {
      boolean result = this.autoPlayAfterTune;
      this.autoPlayAfterTune = false;
      return result;
   }

   public void reset() {
      this.stage = PlaybackStage.IDLE;
      this.mode = PlaybackMode.NONE;
      this.song = null;
      this.loadedSongPath = null;
      this.loadIntent = null;
      this.waitTicks = -1;
      this.autoPlayAfterTune = false;
   }

   private void requireSong() {
      if (this.song == null) {
         throw new IllegalStateException("Playback session has no loaded song");
      }
   }

   private void requireStage(PlaybackStage... allowed) {
      for (PlaybackStage candidate : allowed) {
         if (this.stage == candidate) {
            return;
         }
      }
      throw new IllegalStateException("Invalid playback transition from " + this.stage);
   }
}
