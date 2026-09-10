package io.voltikor.lyra.song.decoder;

import static org.junit.jupiter.api.Assertions.*;
import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.song.OutOfRangeMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SongDecodersTest extends MinecraftTestSupport {
   @TempDir Path directory;

   @Test
   void decodesAndFinalizesUsingOnlySuppliedSettings() throws Exception {
      Path path = directory.resolve("example.txt");
      Files.writeString(path, "0:12:0\n4:30:0\n");
      var settings = new LyraSettings();
      settings.setTranspose(TransposeSetting.OFF);
      settings.setOutOfRangeMode(OutOfRangeMode.CLAMP);
      settings.setInstrumentOverride(NoteBlockInstrument.HARP, NoteBlockInstrument.BASS);
      var warnings = new ArrayList<String>();
      var song = SongDecoders.parse(path, settings, warnings::add);
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

      var song = SongDecoders.parse(path, settings, warnings::add);

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
      var song = SongDecoders.parse(path, settings, message -> {});

      assertEquals(-12, song.transposeSemitones());
      assertEquals(TransposeSetting.OFF, song.transposeSetting());
      assertEquals(24, song.notesAt(0).iterator().next().noteLevel());
   }

   @Test
   void recordsNoShiftWhenAutoWouldNotChangeTheSong() throws Exception {
      Path path = directory.resolve("in-range.txt");
      Files.writeString(path, "0:12:0\n");
      var song = SongDecoders.parse(path, new LyraSettings(), message -> {});

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
         var song = SongDecoders.parse(path, settings, message -> {});
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
      var song = SongDecoders.parse(path, settings, message -> {});
      assertEquals(-13, song.transposeSemitones());
      assertEquals(12, song.notesAt(0).iterator().next().noteLevel());
      assertEquals(13, song.notesAt(1).iterator().next().noteLevel());
   }
}
