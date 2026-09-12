package io.voltikor.lyra.song;

import static org.junit.jupiter.api.Assertions.*;
import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import io.voltikor.lyra.song.decoder.SongDecoders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SongNormalizerTest extends MinecraftTestSupport {
   @TempDir Path directory;

   @Test
   void normalizesAndFinalizesUsingOnlySuppliedSettings() throws Exception {
      Path path = directory.resolve("example.txt");
      Files.writeString(path, "0:12:0\n4:30:0\n");
      var settings = new LyraSettings();
      settings.setTranspose(TransposeSetting.OFF);
      settings.setOutOfRangeMode(OutOfRangeMode.CLAMP);
      settings.setInstrumentOverride(NoteBlockInstrument.HARP, NoteBlockInstrument.BASS);
      var warnings = new ArrayList<String>();
      var song = normalize(path, settings, warnings);
      assertEquals(4, song.lastTick());
      assertEquals(24, song.notesAt(4).iterator().next().noteLevel());
      assertEquals(NoteBlockInstrument.BASS, song.notesAt(0).iterator().next().instrument());
      assertEquals(2, song.requirements().size());
      assertFalse(warnings.isEmpty());
   }

   @Test
   void appliesAutoTranspose() throws Exception {
      Path path = directory.resolve("transpose.txt");
      Files.writeString(path, "0:25:0\n");
      var settings = new LyraSettings();
      var warnings = new ArrayList<String>();

      var song = normalize(path, settings, warnings);

      assertEquals(-12, song.transposeSemitones());
      assertEquals(TransposeSetting.AUTO, song.transposeSetting());
      assertEquals(13, song.notesAt(0).iterator().next().noteLevel());
      assertTrue(warnings.stream().anyMatch(message -> message.startsWith("Transposed song automatically by")));
   }

   @Test
   void recordsSuggestedShiftWhenTransposeIsOff() throws Exception {
      Path path = directory.resolve("transpose-disabled.txt");
      Files.writeString(path, "0:25:0\n");
      var settings = new LyraSettings();
      settings.setTranspose(TransposeSetting.OFF);
      settings.setOutOfRangeMode(OutOfRangeMode.CLAMP);
      var song = normalize(path, settings, new ArrayList<>());

      assertEquals(-12, song.transposeSemitones());
      assertEquals(TransposeSetting.OFF, song.transposeSetting());
      assertEquals(24, song.notesAt(0).iterator().next().noteLevel());
   }

   @Test
   void recordsNoShiftWhenAutoWouldNotChangeTheSong() throws Exception {
      Path path = directory.resolve("in-range.txt");
      Files.writeString(path, "0:12:0\n");
      var song = normalize(path, new LyraSettings(), new ArrayList<>());

      assertEquals(0, song.transposeSemitones());
   }

   @Test
   void appliesEveryFixedSemitonePreset() throws Exception {
      Path path = directory.resolve("preset.txt");
      for (TransposeSetting preset : TransposeSetting.values()) {
         if (preset.semitones() == 0) continue;
         int originalPitch = preset.semitones() > 0 ? 0 : 24;
         Files.writeString(path, "0:" + originalPitch + ":0\n");
         var settings = new LyraSettings();
         settings.setTranspose(preset);
         var song = normalize(path, settings, new ArrayList<>());
         assertEquals(originalPitch + preset.semitones(), song.notesAt(0).iterator().next().noteLevel());
         assertEquals(preset.semitones(), song.transposeSemitones());
      }
   }

   @Test
   void smartTransposeCanMoveByLessThanAnOctave() throws Exception {
      Path path = directory.resolve("smart.txt");
      Files.writeString(path, "0:25:0\n1:26:0\n");
      var settings = new LyraSettings();
      settings.setTranspose(TransposeSetting.SMART);
      var song = normalize(path, settings, new ArrayList<>());
      assertEquals(-13, song.transposeSemitones());
      assertEquals(12, song.notesAt(0).iterator().next().noteLevel());
      assertEquals(13, song.notesAt(1).iterator().next().noteLevel());
   }

   @Test
   void dropsOutOfRangeNotesAndEmptyTicks() {
      var song = songWithNote(-1);
      var settings = settings(OutOfRangeMode.DROP);

      new SongNormalizer().normalize(song, settings, message -> {});

      assertTrue(song.notesByTick().isEmpty());
      assertTrue(song.requirements().isEmpty());
   }

   @Test
   void foldsOutOfRangeNotesByOctaves() {
      var song = songWithNote(-1);

      new SongNormalizer().normalize(song, settings(OutOfRangeMode.FOLD), message -> {});

      assertEquals(11, song.notesAt(0).iterator().next().noteLevel());
   }

   @Test
   void remapsFarAboveRangeNotesToGuitar() {
      var song = songWithNote(37);

      new SongNormalizer().normalize(song, settings(OutOfRangeMode.REMAP), message -> {});

      Note note = song.notesAt(0).iterator().next();
      assertEquals(13, note.noteLevel());
      assertEquals(NoteBlockInstrument.GUITAR, note.instrument());
   }

   @Test
   void removesInstrumentConstraintInAnyInstrumentMode() {
      var song = songWithNote(12);
      var settings = settings(OutOfRangeMode.REMAP);
      settings.setMode(InstrumentMatchMode.ANY_INSTRUMENT);

      new SongNormalizer().normalize(song, settings, message -> {});

      assertNull(song.notesAt(0).iterator().next().instrument());
   }

   private static Song normalize(Path path, LyraSettings settings, ArrayList<String> warnings) throws Exception {
      Song decoded = SongDecoders.decode(path, settings);
      return new SongNormalizer().normalize(decoded, settings, warnings::add);
   }

   private static Song songWithNote(int level) {
      Song song = new Song("test", "test");
      song.addNote(0, new Note(NoteBlockInstrument.HARP, level));
      return song;
   }

   private static LyraSettings settings(OutOfRangeMode mode) {
      LyraSettings settings = new LyraSettings();
      settings.setTranspose(TransposeSetting.OFF);
      settings.setOutOfRangeMode(mode);
      return settings;
   }
}
