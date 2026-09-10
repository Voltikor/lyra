package io.voltikor.lyra.command;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.SongConfig;
import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.SongFileManager;
import io.voltikor.lyra.song.TempoQuantization;

import java.nio.file.Path;
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

   public boolean handleSongCommand(Minecraft client, String args, Path activePath) {
      if (activePath == null) {
         LyraMessenger.error(client, "No active or selected song."); return false;
      }
      String storedPath = this.fileManager.toStoredSongPath(activePath);
      String[] split = args.trim().split("\\s+", 2);
      String sub = split[0].toLowerCase(java.util.Locale.ROOT);
      String value = split.length == 2 ? split[1].trim() : "";

      switch (sub) {
         case "tempo" -> {
            if (value.isEmpty()) {
               LyraMessenger.error(client, "Usage: /lyra song tempo <mode|reset>"); return false;
            }
            if (value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("default")) {
               this.fileManager.songConfigManager().setTempoOverride(storedPath, null);
               LyraMessenger.reply(client,
                     "Cleared tempo quantization override for " + activePath.getFileName() + ".");
            } else {
               TempoQuantization mode = TempoQuantization.parse(value);
               if (mode != null && mode != TempoQuantization.DEFAULT) {
                  this.fileManager.songConfigManager().setTempoOverride(storedPath, mode);
                  LyraMessenger.reply(client,
                        "Set tempo quantization override for " + activePath.getFileName() + " to " + mode.name() + ".");
               } else {
                  LyraMessenger.error(client,
                        "Invalid tempo mode. Valid: default, snap_nearest, snap_up, snap_down, reset"); return false;
               }
            }
         }
         case "transpose" -> {
            if (value.isEmpty()) {
               LyraMessenger.error(client, "Usage: /lyra song transpose <" + TransposeSetting.usage() + "|reset>"); return false;
            }
            if (value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("default")) {
               this.fileManager.songConfigManager().setTransposeOverride(storedPath, null);
               LyraMessenger.reply(client,
                     "Cleared auto-transpose override for " + activePath.getFileName() + ".");
            } else {
               TransposeSetting transpose = TransposeSetting.parse(value);
               if (transpose == null) {
                  LyraMessenger.error(client, "Invalid transpose value. Valid: " + TransposeSetting.usage() + "|reset"); return false;
               }
               this.fileManager.songConfigManager().setTransposeOverride(storedPath, transpose);
               LyraMessenger.reply(client, "Set transpose override for " + activePath.getFileName()
                     + " to " + transpose.serialized() + ".");
            }
         }
         case "round" -> {
            if (value.isEmpty()) {
               LyraMessenger.error(client, "Usage: /lyra song round <drop|clamp|fold|remap|reset>"); return false;
            }
            if (value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("default")) {
               this.fileManager.songConfigManager().setOutOfRangeModeOverride(storedPath, null);
               LyraMessenger.reply(client, "Cleared round override for " + activePath.getFileName() + ".");
            } else {
               OutOfRangeMode mode = OutOfRangeMode.fromInput(value);
               if (mode == null) {
                  LyraMessenger.error(client,
                        "Invalid round mode. Valid: drop, clamp, fold, remap, reset"); return false;
               }
               this.fileManager.songConfigManager().setOutOfRangeModeOverride(storedPath, mode);
               LyraMessenger.reply(client, "Set round override for " + activePath.getFileName()
                     + " to " + mode.name().toLowerCase(java.util.Locale.ROOT) + ".");
            }
         }
         case "map" -> { return this.handleSongMapCommand(client, activePath, storedPath, value); }
         case "config" -> {
            SongConfig cfg = this.fileManager.songConfigManager().getConfig(storedPath);
            if (cfg == null || cfg.isEmpty()) {
               LyraMessenger.reply(client, "No per-song overrides applied for " + activePath.getFileName() + ".");
            } else {
               LyraMessenger.reply(client, "Per-song configuration for " + activePath.getFileName() + ":");
               if (cfg.tempoOverride() != null) {
                  LyraMessenger.reply(client, " - Tempo override: " + cfg.tempoOverride().name());
               }
               if (cfg.transposeOverride() != null) {
                  LyraMessenger.reply(client, " - Transpose override: " + cfg.transposeOverride().serialized());
               }
               if (cfg.outOfRangeModeOverride() != null) {
                  LyraMessenger.reply(client, " - Round override: "
                        + cfg.outOfRangeModeOverride().name().toLowerCase(java.util.Locale.ROOT));
               }
               if (!cfg.mappedSources().isEmpty()) {
                  StringBuilder sb = new StringBuilder(" - Instrument mappings: ");
                  boolean first = true;
                  for (NoteBlockInstrument source : cfg.mappedSources()) {
                     if (!first)
                        sb.append(", ");
                     sb.append(LyraSettings.prettyName(source)).append("->")
                           .append(LyraSettings.prettyName(cfg.mapInstrument(source)));
                     first = false;
                  }
                  LyraMessenger.reply(client, sb.toString());
               }
            }
         }
         case "clear" -> {
            this.fileManager.songConfigManager().clearConfig(storedPath);
            LyraMessenger.reply(client,
                  "Cleared all per-song configuration overrides for " + activePath.getFileName() + ".");
         }
         default -> {
            LyraMessenger.error(client,
                  "Usage: /lyra song <transpose|tempo|round|map|config|clear> [args]"); return false;
         }
      }
      return true;
   }

   private boolean handleSongMapCommand(Minecraft client, Path activePath, String storedPath, String args) {
      if (args == null || args.trim().isEmpty() || args.trim().equalsIgnoreCase("list")) {
         SongConfig cfg = this.fileManager.songConfigManager().getConfig(storedPath);
         if (cfg == null || cfg.mappedSources().isEmpty()) {
            LyraMessenger.reply(client, "No per-song instrument mappings for " + activePath.getFileName() + ".");
         } else {
            StringBuilder sb = new StringBuilder("Per-song instrument mappings for ").append(activePath.getFileName())
                  .append(":\n");
            for (NoteBlockInstrument source : cfg.mappedSources()) {
               sb.append("  ").append(LyraSettings.prettyName(source)).append(" -> ")
                     .append(LyraSettings.prettyName(cfg.mapInstrument(source))).append("\n");
            }
            LyraMessenger.reply(client, sb.toString().trim());
         }
         return true;
      }

      String trimmed = args.trim();
      if (trimmed.equalsIgnoreCase("clear")) {
         this.fileManager.songConfigManager().clearInstrumentOverrides(storedPath);
         LyraMessenger.reply(client, "Cleared per-song instrument mappings for " + activePath.getFileName() + ".");
         return true;
      }

      String[] parts = trimmed.split("\\s+");
      if (parts[0].equalsIgnoreCase("remove")) {
         if (parts.length < 2) {
            LyraMessenger.error(client, "Usage: /lyra song map remove <from>"); return false;
         }
         NoteBlockInstrument from = LyraSettings.parseInstrument(parts[1]);
         if (from == null) {
            LyraMessenger.error(client, "Unknown source instrument: " + parts[1]); return false;
         } else {
            this.fileManager.songConfigManager().removeInstrumentOverride(storedPath, from);
            LyraMessenger.reply(client, "Removed per-song instrument mapping for " + LyraSettings.prettyName(from)
                  + " on " + activePath.getFileName() + ".");
         }
         return true;
      }

      if (parts[0].equalsIgnoreCase("add")) {
         if (parts.length < 3) {
            LyraMessenger.error(client, "Usage: /lyra song map add <from> <to>"); return false;
         }
         NoteBlockInstrument from = LyraSettings.parseInstrument(parts[1]);
         if (from == null) {
            LyraMessenger.error(client, "Unknown source instrument: " + parts[1]); return false;
         }
         NoteBlockInstrument to = LyraSettings.parseInstrument(parts[2]);
         if (to == null) {
            LyraMessenger.error(client, "Unknown target instrument: " + parts[2]); return false;
         } else {
            this.fileManager.songConfigManager().setInstrumentOverride(storedPath, from, to);
            LyraMessenger.reply(client, "Set per-song instrument mapping: " + LyraSettings.prettyName(from)
                  + " -> " + LyraSettings.prettyName(to) + " for " + activePath.getFileName() + ".");
         }
         return true;
      }

      LyraMessenger.error(client, "Usage: /lyra song map [add|remove|clear|list]"); return false;
   }

   private static Boolean parseBoolean(String value) {
      if (value == null) {
         return null;
      }
      return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
         case "on", "true", "1", "yes" -> true;
         case "off", "false", "0", "no" -> false;
         default -> null;
      };
   }

}
