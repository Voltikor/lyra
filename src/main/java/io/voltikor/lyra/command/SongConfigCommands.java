package io.voltikor.lyra.command;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.SongConfig;
import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.SongFileManager;
import io.voltikor.lyra.song.TempoQuantization;

import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
public final class SongConfigCommands {
   private final SongFileManager fileManager;

   public SongConfigCommands(SongFileManager fileManager) {
      this.fileManager = fileManager;
   }

   public boolean setTempoOverride(Minecraft client, Path activePath, TempoQuantization mode) {
      if (mode == null) {
         return invalid(client, "Invalid tempo mode. Valid: default, snap_nearest, snap_up, snap_down, reset");
      }
      if (mode == TempoQuantization.DEFAULT) {
         return this.clearTempoOverride(client, activePath);
      }
      String key = this.storedPath(client, activePath);
      if (key == null) {
         return false;
      }
      this.fileManager.songConfigManager().setTempoOverride(key, mode);
      LyraMessenger.reply(client, "Set tempo quantization override for " + activePath.getFileName() + " to "
            + mode.name() + ".");
      return true;
   }

   public boolean clearTempoOverride(Minecraft client, Path activePath) {
      String key = this.storedPath(client, activePath);
      if (key == null) {
         return false;
      }
      this.fileManager.songConfigManager().setTempoOverride(key, null);
      LyraMessenger.reply(client, "Cleared tempo quantization override for " + activePath.getFileName() + ".");
      return true;
   }

   public boolean setTransposeOverride(Minecraft client, Path activePath, TransposeSetting transpose) {
      if (transpose == null) {
         return invalid(client, "Invalid transpose value. Valid: " + TransposeSetting.usage() + "|reset");
      }
      String key = this.storedPath(client, activePath);
      if (key == null) {
         return false;
      }
      this.fileManager.songConfigManager().setTransposeOverride(key, transpose);
      LyraMessenger.reply(client, "Set transpose override for " + activePath.getFileName() + " to "
            + transpose.serialized() + ".");
      return true;
   }

   public boolean clearTransposeOverride(Minecraft client, Path activePath) {
      String key = this.storedPath(client, activePath);
      if (key == null) {
         return false;
      }
      this.fileManager.songConfigManager().setTransposeOverride(key, null);
      LyraMessenger.reply(client, "Cleared auto-transpose override for " + activePath.getFileName() + ".");
      return true;
   }

   public boolean setOutOfRangeModeOverride(Minecraft client, Path activePath, OutOfRangeMode mode) {
      if (mode == null) {
         return invalid(client, "Invalid round mode. Valid: drop, clamp, fold, remap, reset");
      }
      String key = this.storedPath(client, activePath);
      if (key == null) {
         return false;
      }
      this.fileManager.songConfigManager().setOutOfRangeModeOverride(key, mode);
      LyraMessenger.reply(client, "Set round override for " + activePath.getFileName() + " to "
            + mode.name().toLowerCase(Locale.ROOT) + ".");
      return true;
   }

   public boolean clearOutOfRangeModeOverride(Minecraft client, Path activePath) {
      String key = this.storedPath(client, activePath);
      if (key == null) {
         return false;
      }
      this.fileManager.songConfigManager().setOutOfRangeModeOverride(key, null);
      LyraMessenger.reply(client, "Cleared round override for " + activePath.getFileName() + ".");
      return true;
   }

   public boolean listInstrumentOverrides(Minecraft client, Path activePath) {
      String key = this.storedPath(client, activePath);
      if (key == null) {
         return false;
      }
      SongConfig config = this.fileManager.songConfigManager().getConfig(key);
      if (config == null || config.mappedSources().isEmpty()) {
         LyraMessenger.reply(client, "No per-song instrument mappings for " + activePath.getFileName() + ".");
      } else {
         StringBuilder message = new StringBuilder("Per-song instrument mappings for ")
               .append(activePath.getFileName()).append(":\n");
         for (NoteBlockInstrument source : config.mappedSources()) {
            message.append("  ").append(LyraSettings.prettyName(source)).append(" -> ")
                  .append(LyraSettings.prettyName(config.mapInstrument(source))).append("\n");
         }
         LyraMessenger.reply(client, message.toString().trim());
      }
      return true;
   }

   public boolean clearInstrumentOverrides(Minecraft client, Path activePath) {
      String key = this.storedPath(client, activePath);
      if (key == null) {
         return false;
      }
      this.fileManager.songConfigManager().clearInstrumentOverrides(key);
      LyraMessenger.reply(client, "Cleared per-song instrument mappings for " + activePath.getFileName() + ".");
      return true;
   }

   public boolean removeInstrumentOverride(Minecraft client, Path activePath, NoteBlockInstrument from) {
      if (from == null) {
         return invalid(client, "Unknown source instrument.");
      }
      String key = this.storedPath(client, activePath);
      if (key == null) {
         return false;
      }
      this.fileManager.songConfigManager().removeInstrumentOverride(key, from);
      LyraMessenger.reply(client, "Removed per-song instrument mapping for " + LyraSettings.prettyName(from)
            + " on " + activePath.getFileName() + ".");
      return true;
   }

   public boolean setInstrumentOverride(Minecraft client, Path activePath,
         NoteBlockInstrument from, NoteBlockInstrument to) {
      if (from == null || to == null) {
         return invalid(client, "Instrument mappings require a source and target instrument.");
      }
      String key = this.storedPath(client, activePath);
      if (key == null) {
         return false;
      }
      this.fileManager.songConfigManager().setInstrumentOverride(key, from, to);
      LyraMessenger.reply(client, "Set per-song instrument mapping: " + LyraSettings.prettyName(from)
            + " -> " + LyraSettings.prettyName(to) + " for " + activePath.getFileName() + ".");
      return true;
   }

   public boolean showConfig(Minecraft client, Path activePath) {
      String key = this.storedPath(client, activePath);
      if (key == null) {
         return false;
      }
      SongConfig config = this.fileManager.songConfigManager().getConfig(key);
      if (config == null || config.isEmpty()) {
         LyraMessenger.reply(client, "No per-song overrides applied for " + activePath.getFileName() + ".");
      } else {
         LyraMessenger.reply(client, "Per-song configuration for " + activePath.getFileName() + ":");
         if (config.tempoOverride() != null) {
            LyraMessenger.reply(client, " - Tempo override: " + config.tempoOverride().name());
         }
         if (config.transposeOverride() != null) {
            LyraMessenger.reply(client, " - Transpose override: " + config.transposeOverride().serialized());
         }
         if (config.outOfRangeModeOverride() != null) {
            LyraMessenger.reply(client, " - Round override: "
                  + config.outOfRangeModeOverride().name().toLowerCase(Locale.ROOT));
         }
         if (!config.mappedSources().isEmpty()) {
            StringBuilder message = new StringBuilder(" - Instrument mappings: ");
            boolean first = true;
            for (NoteBlockInstrument source : config.mappedSources()) {
               if (!first) {
                  message.append(", ");
               }
               message.append(LyraSettings.prettyName(source)).append("->")
                     .append(LyraSettings.prettyName(config.mapInstrument(source)));
               first = false;
            }
            LyraMessenger.reply(client, message.toString());
         }
      }
      return true;
   }

   public boolean clearConfig(Minecraft client, Path activePath) {
      String key = this.storedPath(client, activePath);
      if (key == null) {
         return false;
      }
      this.fileManager.songConfigManager().clearConfig(key);
      LyraMessenger.reply(client, "Cleared all per-song configuration overrides for " + activePath.getFileName() + ".");
      return true;
   }

   private String storedPath(Minecraft client, Path activePath) {
      if (activePath == null) {
         LyraMessenger.error(client, "No active or selected song.");
         return null;
      }
      return this.fileManager.toStoredSongPath(activePath);
   }

   private static boolean invalid(Minecraft client, String message) {
      LyraMessenger.error(client, message);
      return false;
   }
}
