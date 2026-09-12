package io.voltikor.lyra.song;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.noteblock.InstrumentMatchMode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
public final class SongNormalizer {
   private static final int MAX_DETAIL_WARNINGS = 5;

   public Song normalize(Song song, LyraSettings settings, Consumer<String> warningSink) {
      this.transpose(song, settings, warningSink);
      this.normalizeNotes(song, settings, warningSink);
      song.finishLoading();
      return song;
   }

   static int selectShift(Song song) {
      int bestShift = 0;
      int bestCount = validNotes(song, 0);
      for (int shift : new int[] {-12, 12, -24, 24}) {
         int count = validNotes(song, shift);
         if (count > bestCount) {
            bestCount = count;
            bestShift = shift;
         }
      }
      return bestShift;
   }

   static int selectSmartShift(Song song) {
      if (song.mutableNotesByTick().isEmpty()) return 0;
      int minimum = Integer.MAX_VALUE;
      int maximum = Integer.MIN_VALUE;
      for (List<Note> notes : song.mutableNotesByTick().values()) for (Note note : notes) {
         minimum = Math.min(minimum, note.noteLevel());
         maximum = Math.max(maximum, note.noteLevel());
      }
      int bestShift = 0;
      int bestCount = -1;
      long bestCenterDistance = Long.MAX_VALUE;
      for (int shift = -maximum; shift <= 24 - minimum; shift++) {
         int count = validNotes(song, shift);
         long centerDistance = Math.abs((long) minimum + maximum + 2L * shift - 24L);
         if (count > bestCount || count == bestCount && (centerDistance < bestCenterDistance
               || centerDistance == bestCenterDistance && Math.abs(shift) < Math.abs(bestShift))) {
            bestCount = count;
            bestCenterDistance = centerDistance;
            bestShift = shift;
         }
      }
      return bestShift;
   }

   private static int validNotes(Song song, int shift) {
      int count = 0;
      for (List<Note> notes : song.mutableNotesByTick().values()) {
         for (Note note : notes) {
            long shifted = (long) note.noteLevel() + shift;
            if (shifted >= 0 && shifted <= 24) count++;
         }
      }
      return count;
   }

   private void transpose(Song song, LyraSettings settings, Consumer<String> warningSink) {
      TransposeSetting transpose = settings.transpose();
      int bestShift = switch (transpose) {
         case OFF, AUTO -> selectShift(song);
         case SMART -> selectSmartShift(song);
         default -> transpose.semitones();
      };
      song.setTransposeSemitones(bestShift);
      song.setTransposeSetting(transpose);
      if (!transpose.enabled() || bestShift == 0) return;

      if (transpose == TransposeSetting.AUTO) {
         int octaves = bestShift / 12;
         warningSink.accept("Transposed song automatically by " + signed(octaves) + " octave(s) ("
               + signed(bestShift) + " semitones).");
      } else {
         warningSink.accept("Transposed song by " + signed(bestShift)
               + " semitone(s) using '" + transpose.serialized() + "'.");
      }
      for (List<Note> notes : song.mutableNotesByTick().values()) {
         for (Note note : notes) {
            note.setNoteLevel(note.noteLevel() + bestShift);
         }
      }
   }

   private void normalizeNotes(Song song, LyraSettings settings, Consumer<String> warningSink) {
      OutOfRangeMode activeMode = settings.outOfRangeMode() != null
            ? settings.outOfRangeMode() : OutOfRangeMode.REMAP;
      List<String> adjustmentDetails = new ArrayList<>();
      int totalAdjusted = 0;
      Iterator<Entry<Integer, List<Note>>> tickIterator = song.mutableNotesByTick().entrySet().iterator();

      while (tickIterator.hasNext()) {
         Entry<Integer, List<Note>> tickEntry = tickIterator.next();
         int tick = tickEntry.getKey();
         List<Note> notes = tickEntry.getValue();
         Iterator<Note> noteIterator = notes.iterator();

         while (noteIterator.hasNext()) {
            Note note = noteIterator.next();
            int level = note.noteLevel();
            if (level < 0 || level > 24) {
               totalAdjusted++;
               String detail = adjustOutOfRangeNote(note, noteIterator, activeMode, tick, level);
               if (adjustmentDetails.size() < MAX_DETAIL_WARNINGS) adjustmentDetails.add(detail);
               if (activeMode == OutOfRangeMode.DROP) continue;
            }
            remapInstrument(song, note, settings);
         }

         if (notes.isEmpty()) tickIterator.remove();
      }

      warnAboutAdjustments(activeMode, adjustmentDetails, totalAdjusted, warningSink);
   }

   private static String adjustOutOfRangeNote(Note note, Iterator<Note> noteIterator,
         OutOfRangeMode mode, int tick, int originalLevel) {
      return switch (mode) {
         case DROP -> {
            noteIterator.remove();
            yield "  Dropped note at tick " + tick + " (level=" + originalLevel + ")";
         }
         case CLAMP -> {
            int level = originalLevel < 0 ? 0 : 24;
            note.setNoteLevel(level);
            yield "  Clamped note at tick " + tick + " (level=" + originalLevel + " -> " + level + ")";
         }
         case FOLD -> {
            int level = foldIntoRange(originalLevel);
            note.setNoteLevel(level);
            yield "  Folded note at tick " + tick + " (level=" + originalLevel + " -> " + level + ")";
         }
         case REMAP -> remapOutOfRangeNote(note, tick, originalLevel);
      };
   }

   private static String remapOutOfRangeNote(Note note, int tick, int originalLevel) {
      int level = originalLevel;
      String instrument;
      if (level < 0) {
         level += 24;
         note.setInstrument(NoteBlockInstrument.BASS);
         instrument = "bass";
      } else {
         level -= 12;
         note.setInstrument(NoteBlockInstrument.FLUTE);
         instrument = "flute";
         if (level > 24) {
            level -= 12;
            note.setInstrument(NoteBlockInstrument.GUITAR);
            instrument = "guitar";
         }
      }
      level = foldIntoRange(level);
      note.setNoteLevel(level);
      return "  Remapped note at tick " + tick + " (level=" + originalLevel + " -> " + level
            + ", instrument -> " + instrument + ")";
   }

   private static int foldIntoRange(int level) {
      while (level < 0) level += 12;
      while (level > 24) level -= 12;
      return level;
   }

   private static void remapInstrument(Song song, Note note, LyraSettings settings) {
      if (settings.mode() != InstrumentMatchMode.EXACT_INSTRUMENTS) {
         note.setInstrument(null);
         return;
      }
      if (note.instrument() == null) return;

      NoteBlockInstrument source = note.instrument();
      NoteBlockInstrument target = settings.mapInstrument(source);
      song.recordInstrumentRemap(source, target);
      note.setInstrument(target);
   }

   private static void warnAboutAdjustments(OutOfRangeMode mode, List<String> details,
         int totalAdjusted, Consumer<String> warningSink) {
      if (totalAdjusted == 0) return;
      details.forEach(warningSink);
      int silenced = totalAdjusted - details.size();
      if (silenced > 0) {
         warningSink.accept("  ... and " + silenced + " more out-of-range note(s) (not shown).");
      }
      warningSink.accept("Out-of-range notes: " + totalAdjusted
            + " note(s) processed using mode '" + mode.name().toLowerCase(Locale.ROOT) + "'.");
   }

   private static String signed(int value) {
      return (value > 0 ? "+" : "") + value;
   }
}
