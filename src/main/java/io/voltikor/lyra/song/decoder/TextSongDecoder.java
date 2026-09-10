package io.voltikor.lyra.song.decoder;

import io.voltikor.lyra.song.Note;
import io.voltikor.lyra.song.Song;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
public final class TextSongDecoder implements SongDecoder {
   public Song parse(Path path) throws Exception {
      List<String> lines = Files.readAllLines(path);
      String fileName = path.getFileName().toString();
      int dot = fileName.lastIndexOf(46);
      String title = dot > 0 ? fileName.substring(0, dot) : fileName;
      String author = "Unknown";

      for (String lineRaw : lines) {
         String line = lineRaw.trim();
         if (line.startsWith("// Name: ")) {
            title = line.substring("// Name: ".length()).trim();
         } else if (line.startsWith("// Author: ")) {
            author = line.substring("// Author: ".length()).trim();
         }
      }

      Song song = new Song(title, author);

      for (String lineRaw : lines) {
         String line = lineRaw.trim();
         if (!line.startsWith("//") && !line.isEmpty()) {
            String[] parts = line.split(":");
            if (parts.length >= 2) {
               int instrumentType = 0;

               int tick;
               int noteLevel;
               try {
                  tick = Integer.parseInt(parts[0].trim());
                  noteLevel = Integer.parseInt(parts[1].trim());
                  if (parts.length >= 3) {
                     instrumentType = Integer.parseInt(parts[2].trim());
                  }
               } catch (NumberFormatException ignored) {
                  continue;
               }

               NoteBlockInstrument[] instruments = NoteBlockInstrument.values();
               if (instrumentType < 0 || instrumentType >= instruments.length) {
                  instrumentType = 0;
               }

               song.addNote(tick, new Note(instruments[instrumentType], noteLevel));
            }
         }
      }

      return song;
   }
}
