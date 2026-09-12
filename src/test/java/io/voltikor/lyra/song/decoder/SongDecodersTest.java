package io.voltikor.lyra.song.decoder;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.song.Note;
import io.voltikor.lyra.song.Song;
import java.nio.file.Path;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SongDecodersTest extends MinecraftTestSupport {
   @Test
   void selectsDecoderWithoutNormalizingOrFinalizingItsResult() throws Exception {
      Song decoded = new Song("decoded", "test");
      decoded.addNote(3, new Note(NoteBlockInstrument.HARP, 30));
      SongDecoders.registerDecoder("decode_only_test", path -> decoded);

      Song result = SongDecoders.decode(Path.of("song.decode_only_test"), new LyraSettings());

      assertSame(decoded, result);
      assertEquals(30, result.mutableNotesByTick().get(3).get(0).noteLevel());
      assertThrows(IllegalStateException.class, result::lastTick);
   }
}
