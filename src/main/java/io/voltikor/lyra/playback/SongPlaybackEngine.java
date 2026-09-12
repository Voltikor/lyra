package io.voltikor.lyra.playback;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import io.voltikor.lyra.noteblock.NoteBlockScanner;
import io.voltikor.lyra.noteblock.NoteBlockTuner;
import io.voltikor.lyra.song.Note;
import io.voltikor.lyra.song.Song;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

@Environment(EnvType.CLIENT)
public final class SongPlaybackEngine {

   private int ticks;
   private int currentTick;

   public int ticks() {
      return this.ticks;
   }

   public void setTicks(int ticks) {
      this.ticks = ticks;
   }

   public void incrementTicks() {
      ++this.ticks;
   }

   public int currentTick() {
      return this.currentTick;
   }

   void setCurrentTick(int currentTick) {
      this.currentTick = currentTick;
   }

   private void incrementCurrentTick() {
      ++this.currentTick;
   }

   public void reset() {
      this.currentTick = 0;
      this.ticks = 0;
   }

   public void prepareForPlayback(Song song) {
      if (song != null && this.currentTick > song.lastTick()) {
         this.currentTick = 0;
      }
   }

   /** Moves the next note cursor without triggering skipped notes or clearing block mappings. */
   public void seek(Song song, int tick) {
      this.currentTick = Math.clamp((long) tick, 0, song.lastTick());
   }

   public TickResult tickPlayback(Minecraft client, Song song, PlaybackMode mode, LyraSettings settings,
         NoteBlockScanner scanner, NoteBlockTuner tuner) {
      if (song == null || client == null || client.player == null || client.level == null) {
         return TickResult.NONE;
      }
      if (this.currentTick > song.lastTick()) {
         return TickResult.SONG_ENDED;
      }
      if (mode == PlaybackMode.NOTEBLOCKS && client.player.hasInfiniteMaterials()) {
         return TickResult.SURVIVAL_REQUIRED;
      }

      Collection<Note> notes = song.notesAt(this.currentTick);
      if (!notes.isEmpty()) {
         if (mode == PlaybackMode.PREVIEW) {
            this.playPreviewNotes(client, notes, settings);
         } else if (mode == PlaybackMode.NOTEBLOCKS) {
            this.playNoteblockNotes(client, notes, settings, scanner, tuner);
         }
      }

      this.incrementCurrentTick();
      this.rotatePreemptivelyToNextNote(client, song, mode, settings, scanner, tuner);
      return TickResult.ADVANCED;
   }

   public enum TickResult { NONE, ADVANCED, SONG_ENDED, SURVIVAL_REQUIRED }

   private void playPreviewNotes(Minecraft client, Collection<Note> notes, LyraSettings settings) {
      if (client.player == null) return;
      record Key(SoundEvent event, int noteLevel) {}
      Set<Key> played = new HashSet<>();
      for (Note note : notes) {
         if (!settings.polyphonic() && !played.isEmpty()) break;
         SoundEvent event = settings.mode() == InstrumentMatchMode.EXACT_INSTRUMENTS && note.instrument() != null
               ? note.instrument().getSoundEvent().value()
               : SoundEvents.NOTE_BLOCK_HARP.value();

         if (played.add(new Key(event, note.noteLevel()))) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(event, NoteBlockTuner.pitchForNoteLevel(note.noteLevel()), 1.0F));
         }
      }
   }

   private void playNoteblockNotes(Minecraft client, Collection<Note> notes, LyraSettings settings,
         NoteBlockScanner scanner, NoteBlockTuner tuner) {
      if (client.player != null && client.getConnection() != null) {
         if (settings.autoRotate()) {
            Optional<Note> first = notes.stream().findFirst();
            if (first.isPresent()) {
               BlockPos firstPos = scanner.noteBlockPositions().get(first.get());
               if (firstPos != null && settings.autoRotate()) {
                  tuner.rotateTo(client, tuner.resolveTarget(client, firstPos, settings, scanner));
               }
            }
         }

         if (settings.swingArm()) {
            client.player.swing(InteractionHand.MAIN_HAND);
         }

         int clickBudget = Integer.MAX_VALUE;
         if (clickBudget > 0) {
            if (!settings.polyphonic()) {
               Optional<Note> first = notes.stream().findFirst();
               if (first.isPresent()) {
                  BlockPos pos = scanner.noteBlockPositions().get(first.get());
                  if (pos != null && tuner.triggerNoteBlock(client, pos, scanner)) {
                  }
               }
            } else {
               LinkedHashSet<BlockPos> uniquePositions = new LinkedHashSet<>();

               for (Note note : notes) {
                  BlockPos pos = scanner.noteBlockPositions().get(note);
                  if (pos != null) {
                     uniquePositions.add(pos);
                  }
               }

               int sent = 0;

               for (BlockPos pos : uniquePositions) {
                  if (tuner.triggerNoteBlock(client, pos, scanner)) {
                     ++sent;
                  }

                  if (sent >= clickBudget) {
                     break;
                  }
               }
            }
         }
      }
   }

   public void rotatePreemptivelyToNextNote(Minecraft client, Song song, PlaybackMode mode, LyraSettings settings,
         NoteBlockScanner scanner, NoteBlockTuner tuner) {
      if (settings.autoRotate() && mode == PlaybackMode.NOTEBLOCKS && song != null) {
         Collection<Note> nextNotes = song.nextNotesAtOrAfter(this.currentTick);
         Optional<Note> first = nextNotes.stream().findFirst();
         if (first.isPresent()) {
            BlockPos nextPos = scanner.noteBlockPositions().get(first.get());
            if (nextPos != null) {
               tuner.rotateTo(client, tuner.resolveTarget(client, nextPos, settings, scanner));
            }
         }
      }
   }

}
