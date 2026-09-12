package io.voltikor.lyra.command;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.SongConfigManager;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.SongFileManager;
import io.voltikor.lyra.song.TempoQuantization;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SongConfigCommandsTest extends MinecraftTestSupport {
   @TempDir Path directory;

   @Test
   void typedOverridesPersistWithoutChangingActiveSong() {
      var files = new SongFileManager();
      files.songConfigManager().initialize(directory);
      var commands = new SongConfigCommands(files);
      Path selected = directory.resolve("selected.nbs");
      String key = files.toStoredSongPath(selected);
      commands.setTempoOverride(null, selected, TempoQuantization.SNAP_NEAREST);
      assertEquals(TempoQuantization.SNAP_NEAREST, files.songConfigManager().getTempoOverride(key));
      commands.setTransposeOverride(null, selected, io.voltikor.lyra.config.TransposeSetting.OFF);
      assertEquals(io.voltikor.lyra.config.TransposeSetting.OFF, files.songConfigManager().getTransposeOverride(key));
      commands.setOutOfRangeModeOverride(null, selected, OutOfRangeMode.CLAMP);
      assertEquals(OutOfRangeMode.CLAMP, files.songConfigManager().getOutOfRangeModeOverride(key));
      commands.setInstrumentOverride(null, selected, NoteBlockInstrument.HARP, NoteBlockInstrument.BASS);
      assertEquals(NoteBlockInstrument.BASS, files.songConfigManager().getConfig(key).mapInstrument(NoteBlockInstrument.HARP));
      var restored = new SongConfigManager();
      restored.initialize(directory);
      assertEquals(TempoQuantization.SNAP_NEAREST, restored.getTempoOverride(key));
      assertEquals(io.voltikor.lyra.config.TransposeSetting.OFF, restored.getTransposeOverride(key));
      assertEquals(OutOfRangeMode.CLAMP, restored.getOutOfRangeModeOverride(key));
      assertEquals(NoteBlockInstrument.BASS, restored.getConfig(key).mapInstrument(NoteBlockInstrument.HARP));
      commands.clearTempoOverride(null, selected);
      commands.clearTransposeOverride(null, selected);
      commands.clearOutOfRangeModeOverride(null, selected);
      commands.removeInstrumentOverride(null, selected, NoteBlockInstrument.HARP);
      assertNull(files.songConfigManager().getConfig(key));
      Path loaded = directory.resolve("loaded.nbs");
      commands.setTempoOverride(null, loaded, TempoQuantization.SNAP_UP);
      assertNull(files.songConfigManager().getConfig(key));
      assertEquals(TempoQuantization.SNAP_UP,
            files.songConfigManager().getTempoOverride(files.toStoredSongPath(loaded)));
      commands.clearConfig(null, loaded);
      assertNull(files.songConfigManager().getConfig(files.toStoredSongPath(loaded)));
   }}
