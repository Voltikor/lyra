package io.voltikor.lyra.song.decoder;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.song.Note;
import io.voltikor.lyra.song.Song;
import io.voltikor.lyra.song.TempoQuantization;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class NbsSongDecoder implements SongDecoder {
   private static final Logger LOGGER = LoggerFactory.getLogger("Lyra/NbsDecoder");

   public Song parse(Path path) throws IOException {
      return this.parse(path, null);
   }

   @Override
   public Song parse(Path path, LyraSettings settings) throws IOException {
      TempoQuantization quant = settings != null && settings.tempoQuantization() != null
            ? settings.tempoQuantization()
            : TempoQuantization.SNAP_NEAREST;
      try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
         return this.parseStream(in, quant);
      }
   }

   private Song parseStream(DataInputStream in, TempoQuantization quant) throws IOException {
      short length = readLittleEndianShort(in);
      int nbsVersion = 0;
      if (length == 0) {
         nbsVersion = in.readUnsignedByte();
         in.readUnsignedByte();
         if (nbsVersion >= 3) {
            length = readLittleEndianShort(in);
         }
      }

      readLittleEndianShort(in);
      String title = readString(in);
      String author = readString(in);
      readString(in);
      readString(in);
      double rawSpeed = (double) readLittleEndianShort(in) / 100.0;
      if (rawSpeed <= 0.0) {
         rawSpeed = 10.0;
      }
      double speed = quant != null ? quant.calculateEffectiveSpeed(rawSpeed) : rawSpeed;

      if (quant == null || quant == TempoQuantization.DEFAULT) {
         int mcTicksRaw = (int) Math.round(20.0 / rawSpeed);
         LOGGER.debug("[TempoQuantization] mode=DEFAULT  rawSpeed={} NBS-TPS  ({} MC-ticks/NBS-tick)",
               String.format("%.4f", rawSpeed), mcTicksRaw);
      } else if (Double.compare(speed, rawSpeed) != 0) {
         int mcTicks = (int) Math.round(20.0 / speed);
         LOGGER.info(
               "[TempoQuantization] mode={}  rawSpeed={} NBS-TPS -> effectiveSpeed={} NBS-TPS  ({} MC-ticks/NBS-tick, delta={} NBS-TPS)",
               quant,
               String.format("%.4f", rawSpeed),
               String.format("%.4f", speed),
               mcTicks,
               String.format("%+.4f", speed - rawSpeed));
      } else {
         int mcTicks = (int) Math.round(20.0 / speed);
         LOGGER.debug("[TempoQuantization] mode={}  rawSpeed={} NBS-TPS already grid-aligned  ({} MC-ticks/NBS-tick)",
               quant,
               String.format("%.4f", rawSpeed),
               mcTicks);
      }

      in.readBoolean();
      in.readUnsignedByte();
      in.readUnsignedByte();
      readLittleEndianInt(in);
      readLittleEndianInt(in);
      readLittleEndianInt(in);
      readLittleEndianInt(in);
      readLittleEndianInt(in);
      readString(in);
      if (nbsVersion >= 4) {
         in.readUnsignedByte();
         in.readUnsignedByte();
         readLittleEndianShort(in);
      }

      Song song = new Song(title, author);
      double accumulatedNbsTicks = 0.0D;

      while (true) {
         short jumpTicks = readLittleEndianShort(in);
         if (jumpTicks == 0) {
            return song;
         }

         accumulatedNbsTicks += jumpTicks;
         double currentMcTick = (accumulatedNbsTicks - 1.0D) * (20.0D / speed);

         while (true) {
            short jumpLayers = readLittleEndianShort(in);
            if (jumpLayers == 0) {
               break;
            }

            int instrumentId = in.readUnsignedByte();
            int key = in.readUnsignedByte();
            if (nbsVersion >= 4) {
               in.readUnsignedByte();
               in.readUnsignedByte();
               readLittleEndianShort(in);
            }

            NoteBlockInstrument instrument = fromNbsInstrument(instrumentId);
            if (instrument != null) {
               int tickRounded = (int) Math.round(currentMcTick);
               song.addNote(tickRounded, new Note(instrument, key - 33));
            }
         }
      }
   }

   private static short readLittleEndianShort(DataInputStream in) throws IOException {
      int b1 = in.readUnsignedByte();
      int b2 = in.readUnsignedByte();
      return (short) (b1 + (b2 << 8));
   }

   private static int readLittleEndianInt(DataInputStream in) throws IOException {
      int b1 = in.readUnsignedByte();
      int b2 = in.readUnsignedByte();
      int b3 = in.readUnsignedByte();
      int b4 = in.readUnsignedByte();
      return b1 + (b2 << 8) + (b3 << 16) + (b4 << 24);
   }

   private static String readString(DataInputStream in) throws IOException {
      int length = readLittleEndianInt(in);
      if (length < 0) {
         throw new EOFException("String length cannot be negative.");
      } else if (length > in.available()) {
         throw new EOFException("String length exceeds remaining bytes.");
      } else {
         StringBuilder builder = new StringBuilder(length);

         for (int i = 0; i < length; ++i) {
            char c = (char) in.readByte();
            if (c == '\r') {
               c = ' ';
            }

            builder.append(c);
         }

         return builder.toString();
      }
   }

   private static NoteBlockInstrument fromNbsInstrument(int id) {
      return switch (id) {
         case 0 -> NoteBlockInstrument.HARP;
         case 1 -> NoteBlockInstrument.BASS;
         case 2 -> NoteBlockInstrument.BASEDRUM;
         case 3 -> NoteBlockInstrument.SNARE;
         case 4 -> NoteBlockInstrument.HAT;
         case 5 -> NoteBlockInstrument.GUITAR;
         case 6 -> NoteBlockInstrument.FLUTE;
         case 7 -> NoteBlockInstrument.BELL;
         case 8 -> NoteBlockInstrument.CHIME;
         case 9 -> NoteBlockInstrument.XYLOPHONE;
         case 10 -> NoteBlockInstrument.IRON_XYLOPHONE;
         case 11 -> NoteBlockInstrument.COW_BELL;
         case 12 -> NoteBlockInstrument.DIDGERIDOO;
         case 13 -> NoteBlockInstrument.BIT;
         case 14 -> NoteBlockInstrument.BANJO;
         case 15 -> NoteBlockInstrument.PLING;
         default -> null;
      };
   }
}
