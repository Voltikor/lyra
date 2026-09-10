package io.voltikor.lyra.song.decoder;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.song.Song;

import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface SongDecoder {
   Song parse(Path path) throws Exception;

   default Song parse(Path path, LyraSettings settings) throws Exception {
      return this.parse(path);
   }
}
