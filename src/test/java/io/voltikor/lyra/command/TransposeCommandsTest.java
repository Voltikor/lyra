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
      assertTrue(global.setTranspose(null, settings, TransposeSetting.UP_18));
      assertFalse(global.setTranspose(null, settings, null));
      assertEquals(TransposeSetting.UP_18, settings.transpose());

      var files = new SongFileManager();
      files.songConfigManager().initialize(directory);
      var song = new SongConfigCommands(files);
      Path path = directory.resolve("song.nbs");
      assertTrue(song.setTransposeOverride(null, path, TransposeSetting.DOWN_24));
      assertFalse(song.setTransposeOverride(null, path, null));
      assertEquals(TransposeSetting.DOWN_24, files.songConfigManager().getTransposeOverride(files.toStoredSongPath(path)));
      assertTrue(song.clearTransposeOverride(null, path));
      assertNull(files.songConfigManager().getTransposeOverride(files.toStoredSongPath(path)));
   }
}
