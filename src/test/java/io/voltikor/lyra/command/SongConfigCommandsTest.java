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
   void commandsParseOverridesAndPersistWithoutChangingActiveSong() {
      var files = new SongFileManager();
      files.songConfigManager().initialize(directory);
      var commands = new SongConfigCommands(files);
      Path selected = directory.resolve("selected.nbs");
      String key = files.toStoredSongPath(selected);
      commands.handleSongCommand(null, " TeMpO snap_nearest ", selected);
      assertEquals(TempoQuantization.SNAP_NEAREST, files.songConfigManager().getTempoOverride(key));
      commands.handleSongCommand(null, "transpose off", selected);
      assertEquals(io.voltikor.lyra.config.TransposeSetting.OFF, files.songConfigManager().getTransposeOverride(key));
      commands.handleSongCommand(null, "round clamp", selected);
      assertEquals(OutOfRangeMode.CLAMP, files.songConfigManager().getOutOfRangeModeOverride(key));
      commands.handleSongCommand(null, "map add harp bass", selected);
      assertEquals(NoteBlockInstrument.BASS, files.songConfigManager().getConfig(key).mapInstrument(NoteBlockInstrument.HARP));
      commands.handleSongCommand(null, "map add invalid bass", selected);
      commands.handleSongCommand(null, "map remove", selected);
      commands.handleSongCommand(null, "tempo invalid", selected);
      var restored = new SongConfigManager();
      restored.initialize(directory);
      assertEquals(TempoQuantization.SNAP_NEAREST, restored.getTempoOverride(key));
      assertEquals(io.voltikor.lyra.config.TransposeSetting.OFF, restored.getTransposeOverride(key));
      assertEquals(OutOfRangeMode.CLAMP, restored.getOutOfRangeModeOverride(key));
      assertEquals(NoteBlockInstrument.BASS, restored.getConfig(key).mapInstrument(NoteBlockInstrument.HARP));
      commands.handleSongCommand(null, "tempo reset", selected);
      commands.handleSongCommand(null, "transpose reset", selected);
      commands.handleSongCommand(null, "round reset", selected);
      commands.handleSongCommand(null, "map remove harp", selected);
      assertNull(files.songConfigManager().getConfig(key));
      Path loaded = directory.resolve("loaded.nbs");
      commands.handleSongCommand(null, "tempo snap_up", loaded);
      assertNull(files.songConfigManager().getConfig(key));
      assertEquals(TempoQuantization.SNAP_UP,
            files.songConfigManager().getTempoOverride(files.toStoredSongPath(loaded)));
      commands.handleSongCommand(null, "clear", loaded);
      assertNull(files.songConfigManager().getConfig(files.toStoredSongPath(loaded)));
   }}
