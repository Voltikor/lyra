package io.voltikor.lyra.command;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.song.SongFileManager;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TransposeCommandsTest extends MinecraftTestSupport {
   @TempDir Path directory;

   @Test
   void invalidOffsetsDoNotReplaceGlobalOrSongPresets() {
      var settings = new LyraSettings();
      var global = new LyraSettingsCommands();
      assertTrue(global.setTranspose(null, settings, "+18"));
      assertFalse(global.setTranspose(null, settings, "5"));
      assertEquals(TransposeSetting.UP_18, settings.transpose());

      var files = new SongFileManager();
      files.songConfigManager().initialize(directory);
      var song = new SongConfigCommands(files);
      Path path = directory.resolve("song.nbs");
      assertTrue(song.handleSongCommand(null, "transpose -24", path));
      assertFalse(song.handleSongCommand(null, "transpose -13", path));
      assertEquals(TransposeSetting.DOWN_24, files.songConfigManager().getTransposeOverride(files.toStoredSongPath(path)));
      assertTrue(song.handleSongCommand(null, "transpose reset", path));
      assertNull(files.songConfigManager().getTransposeOverride(files.toStoredSongPath(path)));
   }
}
