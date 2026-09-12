package io.voltikor.lyra.song.decoder;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.song.Song;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SongDecoders {
   private static final Map<String, SongDecoder> DECODERS = new HashMap<>();

   private SongDecoders() {
   }

   public static void registerDecoder(String extension, SongDecoder songDecoder) {
      DECODERS.put(extension.toLowerCase(Locale.ROOT), songDecoder);
   }

   public static boolean hasDecoder(Path songPath) {
      return DECODERS.containsKey(extensionOf(songPath));
   }

   public static Song decode(Path songPath, LyraSettings settings) throws Exception {
      String extension = extensionOf(songPath);
      SongDecoder decoder = DECODERS.get(extension);
      if (decoder == null) {
         throw new IllegalStateException("No decoder available for extension: " + extension);
      }

      return decoder.parse(songPath, settings);
   }

   private static String extensionOf(Path songPath) {
      String fileName = songPath.getFileName().toString();
      int dot = fileName.lastIndexOf(46);
      return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
   }

   static {
      registerDecoder("nbs", new NbsSongDecoder());
      registerDecoder("txt", new TextSongDecoder());
   }
}
