package io.voltikor.lyra.config;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyraSettingsTest extends MinecraftTestSupport {
   @TempDir Path directory;

   @Test
   void persistedLoadsUseSettersWithoutCreatingNewUserChanges() throws Exception {
      LyraSettings settings = new LyraSettings();
      settings.setMode(InstrumentMatchMode.ANY_INSTRUMENT);
      long revisionBeforeLoad = settings.decodingRevision();
      assertTrue(settings.isDirty());

      Path file = directory.resolve("settings.json");
      Files.writeString(file, "{\"mode\":\"EXACT_INSTRUMENTS\",\"tickDelay\":4}");
      LyraSettingsStorage.load(file, settings);

      assertEquals(InstrumentMatchMode.EXACT_INSTRUMENTS, settings.mode());
      assertEquals(4, settings.tickDelay());
      assertEquals(revisionBeforeLoad, settings.decodingRevision());
      assertFalse(settings.isDirty());
   }

   @Test
   void mappedSourcesCannotMutateSettingsBehindChangeTracking() {
      LyraSettings settings = new LyraSettings();
      settings.setInstrumentOverride(NoteBlockInstrument.HARP, NoteBlockInstrument.BASS);

      assertThrows(UnsupportedOperationException.class, () -> settings.mappedSources().clear());
      assertEquals(NoteBlockInstrument.BASS, settings.mapInstrument(NoteBlockInstrument.HARP));
   }

   @Test
   void successfulStorageSaveMarksSettingsClean() {
      LyraSettings settings = new LyraSettings();
      settings.setTickDelay(3);
      assertTrue(settings.isDirty());

      Path file = directory.resolve("saved.json");
      assertTrue(LyraSettingsStorage.save(file, settings));

      assertFalse(settings.isDirty());
      assertTrue(Files.isRegularFile(file));
   }
}
