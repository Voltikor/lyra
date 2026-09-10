package io.voltikor.lyra.song.decoder;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import io.voltikor.lyra.song.Note;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.Song;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
public final class SongDecoders {
   private static final Map<String, SongDecoder> DECODERS = new HashMap<>();

   private SongDecoders() {
   }

   public static void registerDecoder(String extension, SongDecoder songDecoder) {
      DECODERS.put(extension.toLowerCase(Locale.ROOT), songDecoder);
   }

   public static boolean hasDecoder(Path songPath) {
      return DECODERS.containsKey(extensionOf(songPath));
   }

   public static Song parse(Path songPath, LyraSettings settings, Consumer<String> warningSink) throws Exception {
      String extension = extensionOf(songPath);
      SongDecoder decoder = DECODERS.get(extension);
      if (decoder == null) {
         throw new IllegalStateException("No decoder available for extension: " + extension);
      }

      Song song = decoder.parse(songPath, settings);
      fixSong(song, settings, warningSink);
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

   private static void fixSong(Song song, LyraSettings settings, Consumer<String> warningSink) {
      TransposeSetting transpose = settings.transpose();
      int bestShift = switch (transpose) {
         case OFF, AUTO -> selectShift(song);
         case SMART -> selectSmartShift(song);
         default -> transpose.semitones();
      };
      song.setTransposeSemitones(bestShift);
      song.setTransposeSetting(transpose);
      if (transpose.enabled() && bestShift != 0) {
         if (transpose == TransposeSetting.AUTO) {
            int octaves = bestShift / 12;
            warningSink.accept("Transposed song automatically by " + (octaves > 0 ? "+" : "") + octaves + " octave(s) ("
                  + (bestShift > 0 ? "+" : "") + bestShift + " semitones).");
         } else {
            warningSink.accept("Transposed song by " + (bestShift > 0 ? "+" : "") + bestShift
                  + " semitone(s) using '" + transpose.serialized() + "'.");
         }
         for (List<Note> notes : song.mutableNotesByTick().values()) {
            for (Note note : notes) {
               note.setNoteLevel(note.noteLevel() + bestShift);
            }
         }
      }

      final int MAX_DETAIL_WARNINGS = 5;
      final OutOfRangeMode activeMode = settings.outOfRangeMode() != null ? settings.outOfRangeMode() : OutOfRangeMode.REMAP;
      final List<String> adjustmentDetails = new ArrayList<>();
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
               int originalLevel = level;
               switch (activeMode) {
                  case DROP:
                     totalAdjusted++;
                     if (adjustmentDetails.size() < MAX_DETAIL_WARNINGS) {
                        adjustmentDetails.add("  Dropped note at tick " + tick
                              + " (level=" + originalLevel + ")");
                     }
                     noteIterator.remove();
                     continue;

                  case CLAMP: {
                     int clamped = level < 0 ? 0 : 24;
                     note.setNoteLevel(clamped);
                     totalAdjusted++;
                     if (adjustmentDetails.size() < MAX_DETAIL_WARNINGS) {
                        adjustmentDetails.add("  Clamped note at tick " + tick
                              + " (level=" + originalLevel + " -> " + clamped + ")");
                     }
                     break;
                  }

                  case FOLD: {
                     while (level < 0)
                        level += 12;
                     while (level > 24)
                        level -= 12;
                     note.setNoteLevel(level);
                     totalAdjusted++;
                     if (adjustmentDetails.size() < MAX_DETAIL_WARNINGS) {
                        adjustmentDetails.add("  Folded note at tick " + tick
                              + " (level=" + originalLevel + " -> " + level + ")");
                     }
                     break;
                  }

                  case REMAP: {
                     String remappedInstrument;
                     if (level < 0) {
                        level += 24;
                        note.setInstrument(NoteBlockInstrument.BASS);
                        remappedInstrument = "bass";
                     } else {
                        level -= 12;
                        note.setInstrument(NoteBlockInstrument.FLUTE);
                        remappedInstrument = "flute";
                        if (level > 24) {
                           level -= 12;
                           note.setInstrument(NoteBlockInstrument.GUITAR);
                           remappedInstrument = "guitar";
                        }
                     }
                     while (level < 0)
                        level += 12;
                     while (level > 24)
                        level -= 12;
                     note.setNoteLevel(level);
                     totalAdjusted++;
                     if (adjustmentDetails.size() < MAX_DETAIL_WARNINGS) {
                        adjustmentDetails.add("  Remapped note at tick " + tick
                              + " (level=" + originalLevel + " -> " + level
                              + ", instrument -> " + remappedInstrument + ")");
                     }
                     break;
                  }
               }
            }

            if (settings.mode() == InstrumentMatchMode.EXACT_INSTRUMENTS) {
               if (note.instrument() != null) {
                  NoteBlockInstrument sourceInst = note.instrument();
                  NoteBlockInstrument targetInst = settings.mapInstrument(sourceInst);
                  if (sourceInst != targetInst) {
                     song.recordInstrumentRemap(sourceInst, targetInst);
                  }
                  note.setInstrument(targetInst);
               }
            } else {
               note.setInstrument(null);
            }
         }

         if (notes.isEmpty()) {
            tickIterator.remove();
         }
      }

      if (totalAdjusted > 0) {
         String modeLabel = activeMode.name().toLowerCase(Locale.ROOT);
         for (String detail : adjustmentDetails) {
            warningSink.accept(detail);
         }
         int silenced = totalAdjusted - adjustmentDetails.size();
         if (silenced > 0) {
            warningSink.accept("  ... and " + silenced + " more out-of-range note(s) (not shown).");
         }
         warningSink.accept("Out-of-range notes: " + totalAdjusted
               + " note(s) processed using mode '" + modeLabel + "'.");
      }

   }

   private static String extensionOf(Path songPath) {
      String fileName = songPath.getFileName().toString();
      int dot = fileName.lastIndexOf(46);
      return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
   }

   static {
      registerDecoder("nbs", new NbsSongDecoder());
      registerDecoder("txt", new TextSongDecoder());
   }
}
