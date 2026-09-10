package io.voltikor.lyra.config;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.song.TempoQuantization;
import java.nio.file.Path;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SongConfigManagerTest extends MinecraftTestSupport {
   @TempDir Path directory;

   @Test
   void unsupportedLegacyOffsetFallsBackToGlobalWithoutLosingOtherOverrides() throws Exception {
      java.nio.file.Files.writeString(directory.resolve("song_config.json"), """
            {"configs":{"song.nbs":{"transpose":"5","tempo":"snap_up"},"fixed.nbs":{"transpose":"-18"}}}
            """);
      var manager = new SongConfigManager();
      manager.initialize(directory);
      assertNull(manager.getTransposeOverride("song.nbs"));
      assertEquals(TempoQuantization.SNAP_UP, manager.getTempoOverride("song.nbs"));
      assertEquals(TransposeSetting.DOWN_18, manager.getTransposeOverride("fixed.nbs"));
   }

   @Test
   void returnedConfigsAreImmutableSnapshotsOfManagerState() {
      SongConfigManager manager = new SongConfigManager();
      manager.initialize(directory);
      String key = "song.nbs";
      manager.setTempoOverride(key, TempoQuantization.SNAP_UP);
      manager.setInstrumentOverride(key, NoteBlockInstrument.HARP, NoteBlockInstrument.BASS);

      SongConfig original = manager.getConfig(key);
      long revision = manager.revision(key);

      manager.setTransposeOverride(key, TransposeSetting.OFF);
      manager.setInstrumentOverride(key, NoteBlockInstrument.HARP, NoteBlockInstrument.GUITAR);

      assertEquals(TempoQuantization.SNAP_UP, original.tempoOverride());
      assertNull(original.transposeOverride());
      assertEquals(NoteBlockInstrument.BASS, original.mapInstrument(NoteBlockInstrument.HARP));
      assertThrows(UnsupportedOperationException.class, () -> original.mappedSources().clear());

      SongConfig current = manager.getConfig(key);
      assertEquals(TransposeSetting.OFF, current.transposeOverride());
      assertEquals(NoteBlockInstrument.GUITAR, current.mapInstrument(NoteBlockInstrument.HARP));
      assertEquals(revision + 2, manager.revision(key));
   }

   @Test
   void noOpWritesDoNotAdvanceRevision() {
      SongConfigManager manager = new SongConfigManager();
      manager.initialize(directory);
      String key = "song.nbs";

      manager.setTempoOverride(key, TempoQuantization.SNAP_NEAREST);
      long revision = manager.revision(key);

      assertFalse(manager.setTempoOverride(key, TempoQuantization.SNAP_NEAREST));
      assertFalse(manager.setInstrumentOverride(key, NoteBlockInstrument.HARP, null));
      assertEquals(revision, manager.revision(key));
   }
}
