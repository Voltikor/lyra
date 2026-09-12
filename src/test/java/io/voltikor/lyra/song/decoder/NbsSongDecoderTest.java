package io.voltikor.lyra.song.decoder;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.song.Note;
import io.voltikor.lyra.song.Song;
import io.voltikor.lyra.song.TempoQuantization;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NbsSongDecoderTest extends MinecraftTestSupport {
   @TempDir Path directory;

   @Test
   void decodesLegacyHeaderAndNotes() throws Exception {
      Path path = directory.resolve("legacy.nbs");
      writeNbs(path, 0, 1000,
            new NbsNote(1, 0, 45),
            new NbsNote(2, 1, 50));

      Song song = parse(path, TempoQuantization.DEFAULT);

      assertEquals("Legacy Song", song.title());
      assertEquals("Test Author", song.author());
      assertEquals(4, song.lastTick());
      assertEquals(new Note(NoteBlockInstrument.HARP, 12), song.notesAt(0).iterator().next());
      assertEquals(new Note(NoteBlockInstrument.BASS, 17), song.notesAt(4).iterator().next());
   }

   @Test
   void decodesVersionFourNoteMetadataWithoutChangingNoteSemantics() throws Exception {
      Path path = directory.resolve("modern.nbs");
      writeNbs(path, 4, 1000, new NbsNote(1, 7, 46));

      Song song = parse(path, TempoQuantization.DEFAULT);

      assertEquals("Modern Song", song.title());
      assertEquals(0, song.lastTick());
      assertEquals(new Note(NoteBlockInstrument.BELL, 13), song.notesAt(0).iterator().next());
   }

   @Test
   void appliesConfiguredTempoQuantizationToMinecraftTickPlacement() throws Exception {
      Path path = directory.resolve("tempo.nbs");
      writeNbs(path, 4, 800,
            new NbsNote(1, 0, 45),
            new NbsNote(2, 0, 46));

      Song unquantized = parse(path, TempoQuantization.DEFAULT);
      Song quantized = parse(path, TempoQuantization.SNAP_NEAREST);

      assertEquals(5, unquantized.lastTick());
      assertEquals(6, quantized.lastTick());
   }

   @Test
   void truncatedNbsFailsInsteadOfReturningPartialSong() throws Exception {
      Path path = directory.resolve("truncated.nbs");
      Files.write(path, new byte[] {1});

      Exception error = assertThrows(Exception.class,
            () -> SongDecoders.decode(path, new LyraSettings()));

      assertInstanceOf(EOFException.class, error);
   }

   private Song parse(Path path, TempoQuantization quantization) throws Exception {
      LyraSettings settings = new LyraSettings();
      settings.setTempoQuantization(quantization);
      settings.setTranspose(io.voltikor.lyra.config.TransposeSetting.OFF);
      Song song = SongDecoders.decode(path, settings);
      song.finishLoading();
      return song;
   }

   private static void writeNbs(Path path, int version, int tempoHundredths, NbsNote... notes) throws IOException {
      try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes)) {
         if (version == 0) {
            writeLittleEndianShort(out, 4);
         } else {
            writeLittleEndianShort(out, 0);
            out.writeByte(version);
            out.writeByte(16);
            writeLittleEndianShort(out, 4);
         }

         writeLittleEndianShort(out, 1);
         writeString(out, version == 0 ? "Legacy Song" : "Modern Song");
         writeString(out, "Test Author");
         writeString(out, "Original Author");
         writeString(out, "Fixture");
         writeLittleEndianShort(out, tempoHundredths);
         out.writeBoolean(false);
         out.writeByte(0);
         out.writeByte(4);
         for (int i = 0; i < 5; i++) {
            writeLittleEndianInt(out, 0);
         }
         writeString(out, "");
         if (version >= 4) {
            out.writeByte(0);
            out.writeByte(0);
            writeLittleEndianShort(out, 0);
         }

         for (NbsNote note : notes) {
            writeLittleEndianShort(out, note.jumpTicks());
            writeLittleEndianShort(out, 1);
            out.writeByte(note.instrumentId());
            out.writeByte(note.key());
            if (version >= 4) {
               out.writeByte(100);
               out.writeByte(100);
               writeLittleEndianShort(out, 0);
            }
            writeLittleEndianShort(out, 0);
         }
         writeLittleEndianShort(out, 0);

         out.flush();
         Files.write(path, bytes.toByteArray());
      }
   }

   private static void writeString(DataOutputStream out, String value) throws IOException {
      byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
      writeLittleEndianInt(out, bytes.length);
      out.write(bytes);
   }

   private static void writeLittleEndianShort(DataOutputStream out, int value) throws IOException {
      out.writeByte(value & 0xFF);
      out.writeByte(value >>> 8 & 0xFF);
   }

   private static void writeLittleEndianInt(DataOutputStream out, int value) throws IOException {
      out.writeByte(value & 0xFF);
      out.writeByte(value >>> 8 & 0xFF);
      out.writeByte(value >>> 16 & 0xFF);
      out.writeByte(value >>> 24 & 0xFF);
   }

   private record NbsNote(int jumpTicks, int instrumentId, int key) {
   }
}
