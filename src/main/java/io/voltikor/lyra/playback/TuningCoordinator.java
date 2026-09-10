package io.voltikor.lyra.playback;

import io.voltikor.lyra.command.LyraMessenger;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.noteblock.NoteBlockScanner;
import io.voltikor.lyra.noteblock.NoteBlockTuner;
import io.voltikor.lyra.song.Song;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class TuningCoordinator {
   private final PlaybackSession session;
   private final NoteBlockScanner scanner;
   private final NoteBlockTuner tuner;

   public TuningCoordinator(PlaybackSession session, NoteBlockScanner scanner, NoteBlockTuner tuner) {
      this.session = session;
      this.scanner = scanner;
      this.tuner = tuner;
   }

   public void start(boolean autoPlayAfterTune) {
      this.session.beginSetup(autoPlayAfterTune);
      this.scanner.reset();
      this.tuner.reset();
   }

   public boolean handlesCurrentStage() {
      return switch (this.session.stage()) {
         case SETUP, TUNING, WAITING_FOR_RECHECK -> true;
         default -> false;
      };
   }

   public TickResult tick(Minecraft client, Song song, LyraSettings settings, int ticks, Runnable resetTicks) {
      return switch (this.session.stage()) {
         case WAITING_FOR_RECHECK -> this.tickRecheckWait(client);
         case SETUP -> this.tickSetup(client, song, settings);
         case TUNING -> this.tickTuning(client, settings, ticks, resetTicks);
         default -> TickResult.NONE;
      };
   }

   private TickResult tickRecheckWait(Minecraft client) {
      if (!this.session.tickRecheckWait()) {
         return TickResult.NONE;
      }
      LyraMessenger.replyFormatted(client, "Checking noteblocks again...");
      this.scanner.setupTuneHitsMap(client);
      this.scanner.cacheNoteBlockFaces(client);
      this.session.beginTuning();
      return TickResult.RECHECKED;
   }

   private TickResult tickSetup(Minecraft client, Song song, LyraSettings settings) {
      this.scanner.scanNearbyNoteblocks(client, settings);
      if (this.scanner.nearbySnapshot().tuningCandidates().isEmpty()) {
         return TickResult.NO_NEARBY_BLOCKS;
      }

      this.scanner.setupNoteblocksMap(client, song, settings);
      if (this.scanner.noteBlockPositions().isEmpty()) {
         return TickResult.NO_MAPPED_BLOCKS;
      }

      this.scanner.setupTuneHitsMap(client);
      this.scanner.cacheNoteBlockFaces(client);
      this.session.beginTuning();
      return TickResult.SETUP_COMPLETE;
   }

   private TickResult tickTuning(Minecraft client, LyraSettings settings, int ticks, Runnable resetTicks) {
      NoteBlockTuner.TuneOutcome outcome = this.tuner.tune(client, settings, this.scanner, ticks, resetTicks);
      return switch (outcome) {
         case IN_PROGRESS -> TickResult.NONE;
         case RECHECK_REQUIRED -> {
            this.session.waitForRecheck(settings.checkNoteblocksAgainDelay());
            yield TickResult.RECHECK_SCHEDULED;
         }
         case READY -> {
            this.session.markReady();
            yield this.session.consumeAutoPlayAfterTune() ? TickResult.AUTOPLAY : TickResult.READY;
         }
      };
   }

   public enum TickResult {
      NONE,
      SETUP_COMPLETE,
      RECHECK_SCHEDULED,
      RECHECKED,
      READY,
      AUTOPLAY,
      NO_NEARBY_BLOCKS,
      NO_MAPPED_BLOCKS
   }
}
