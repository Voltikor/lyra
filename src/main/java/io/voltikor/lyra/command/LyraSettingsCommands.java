package io.voltikor.lyra.command;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.hud.HudAnchor;
import io.voltikor.lyra.noteblock.InstrumentDetectMode;
import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import io.voltikor.lyra.noteblock.RotateMode;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.TempoQuantization;

import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class LyraSettingsCommands {
   public boolean setMode(Minecraft client, LyraSettings settings, String args) {
      InstrumentMatchMode mode = InstrumentMatchMode.parse(args);
      if (mode == null) {
         LyraMessenger.errorFormatted(client, "Usage: /lyra mode <exact|any>"); return false;
      } else {
         settings.setMode(mode);
         LyraMessenger.replyFormatted(client, "Mode set to %1$s", settings.mode().name());
      }
      return true;
   }

   public boolean setInstrumentDetectMode(Minecraft client, LyraSettings settings, String args) {
      InstrumentDetectMode mode = InstrumentDetectMode.parse(args);
      if (mode == null) {
         LyraMessenger.errorFormatted(client, "Usage: /lyra detect <blockstate|below>"); return false;
      } else {
         settings.setInstrumentDetectMode(mode);
         LyraMessenger.replyFormatted(client, "Instrument detect mode set to %1$s", settings.instrumentDetectMode().name());
      }
      return true;
   }

   public boolean setTempoQuantization(Minecraft client, LyraSettings settings, String args) {
      TempoQuantization quant = TempoQuantization.parse(args);
      if (quant == null) {
         LyraMessenger.errorFormatted(client, "Usage: /lyra tempo <default|snap_nearest|snap_up|snap_down>"); return false;
      } else {
         settings.setTempoQuantization(quant);
         LyraMessenger.replyFormatted(client, "Tempo quantization set to %1$s", settings.tempoQuantization().name());
      }
      return true;
   }

   public boolean setTickDelay(Minecraft client, LyraSettings settings, String args) {
      try {
         int value = Integer.parseInt(args);
         this.setTickDelayValue(settings, value);
         LyraMessenger.replyFormatted(client, "tick-delay = %1$s", settings.tickDelay());
      } catch (NumberFormatException ignored) {
         LyraMessenger.errorFormatted(client, "Usage: /lyra delay <1-20>"); return false;
      }
      return true;
   }

   public boolean setCheckNoteblocksAgainDelay(Minecraft client, LyraSettings settings, String args) {
      try {
         int value = Integer.parseInt(args);
         this.setCheckNoteblocksAgainDelayValue(settings, value);
         LyraMessenger.replyFormatted(client, "check-noteblocks-again-delay = %1$s", settings.checkNoteblocksAgainDelay());
      } catch (NumberFormatException ignored) {
         LyraMessenger.errorFormatted(client, "Usage: /lyra checknoteblocksagaindelay <1-100>"); return false;
      }
      return true;
   }

   public boolean setConcurrentTuneBlocks(Minecraft client, LyraSettings settings, String args) {
      Integer value = this.parseConcurrentTuneBlocks(args);
      if (value == null) {
         LyraMessenger.errorFormatted(client, "Usage: /lyra concurrent <1-20|unlimited>"); return false;
      } else {
         this.setConcurrentTuneBlocksValue(settings, value);
         LyraMessenger.replyFormatted(client, "concurrent-tune-blocks = %1$s", this.concurrentTuneBlocksLabel(settings));
      }
      return true;
   }

   public void setCheckNoteblocksAgainDelayValue(LyraSettings settings, int value) {
      settings.setCheckNoteblocksAgainDelay(Math.max(1, Math.min(100, value)));
   }

   public void setTickDelayValue(LyraSettings settings, int value) {
      settings.setTickDelay(Math.clamp((long) value, 1, 20));
   }

   public void setConcurrentTuneBlocksValue(LyraSettings settings, int value) {
      if (value != 0 && value <= 20) {
         settings.setConcurrentTuneBlocks(Math.max(1, Math.min(20, value)));
      } else {
         settings.setConcurrentTuneBlocks(0);
      }
   }

   public void cycleConcurrentTuneBlocksValue(LyraSettings settings, int delta) {
      if (delta != 0) {
         if (delta > 0) {
            if (settings.concurrentTuneBlocks() == 0) {
               settings.setConcurrentTuneBlocks(1);
            } else {
               int next = settings.concurrentTuneBlocks() + delta;
               settings.setConcurrentTuneBlocks(next > 20 ? 0 : next);
            }
         } else if (settings.concurrentTuneBlocks() == 0) {
            settings.setConcurrentTuneBlocks(20);
         } else {
            settings.setConcurrentTuneBlocks(Math.max(1, settings.concurrentTuneBlocks() + delta));
         }
      }
   }

   public String concurrentTuneBlocksLabel(LyraSettings settings) {
      return settings.concurrentTuneBlocks() == 0 ? LyraMessenger.literalComponent("UNLIMITED").getString() : Integer.toString(settings.concurrentTuneBlocks());
   }

   public Integer parseConcurrentTuneBlocks(String input) {
      if (input == null) {
         return null;
      } else {
         String normalized = input.trim().toLowerCase(Locale.ROOT);
         if (normalized.isEmpty()) {
            return null;
         } else if (!normalized.equals("unlimited") && !normalized.equals("max") && !normalized.equals("inf") && !normalized.equals("infinite") && !normalized.equals("無制限")) {
            try {
               int parsed = Integer.parseInt(normalized);
               return parsed > 20 ? 0 : parsed;
            } catch (NumberFormatException ignored) {
               return null;
            }
         } else {
            return 0;
         }
      }
   }

   public boolean handleHudCommand(Minecraft client, LyraSettings settings, String args) {
      if (args == null || args.trim().isEmpty()) {
         settings.setShowHud(!settings.showHud());
         LyraMessenger.replyFormatted(client, "%1$s = %2$s", "hud", settings.showHud());
         return true;
      }
      String[] split = args.trim().split("\\s+", 2);
      String sub = split[0].toLowerCase(Locale.ROOT);
      String subArgs = split.length == 2 ? split[1].trim() : "";
      switch (sub) {
         case "anchor" -> { return this.setHudAnchor(client, settings, subArgs); }
         case "autohide" -> { return this.setHudAutoHide(client, settings, subArgs); }
         default -> {
            Boolean parsed = this.parseBoolean(args);
            if (parsed != null) {
               settings.setShowHud(parsed);
               LyraMessenger.replyFormatted(client, "%1$s = %2$s", "hud", settings.showHud());
            } else {
               LyraMessenger.errorFormatted(client, "Usage: /lyra hud <on|off|anchor <top_left|top_right|bottom_left|bottom_right>|autohide <on|off>>"); return false;
            }
         }
      }
      return true;
   }

   public boolean setHudAnchor(Minecraft client, LyraSettings settings, String args) {
      HudAnchor anchor = HudAnchor.parse(args);
      if (anchor == null) {
         LyraMessenger.errorFormatted(client, "Usage: /lyra hud anchor <top_left|top_right|bottom_left|bottom_right>"); return false;
      } else {
         settings.setHudAnchor(anchor);
         LyraMessenger.replyFormatted(client, "hud anchor = %1$s", anchor.name().toLowerCase(Locale.ROOT));
      }
      return true;
   }

   public boolean setHudAutoHide(Minecraft client, LyraSettings settings, String args) {
      Boolean parsed = this.parseBoolean(args);
      if (parsed == null) {
         LyraMessenger.errorFormatted(client, "Usage: /lyra hud autohide <on|off>"); return false;
      } else {
         settings.setHudAutoHide(parsed);
         LyraMessenger.replyFormatted(client, "hud autohide = %1$s", parsed);
      }
      return true;
   }

   public boolean setBooleanFlag(Minecraft client, LyraSettings settings, String key, String value) {
      Boolean parsed = this.parseBoolean(value);
      if (parsed == null) {
         LyraMessenger.errorFormatted(client, "Usage: /lyra %1$s <on|off>", key); return false;
      } else {
         switch (key) {
            case "polyphonic" -> settings.setPolyphonic(parsed);
            case "rotate" -> settings.setAutoRotate(parsed);
            case "autoplay" -> settings.setAutoPlay(parsed);
            case "swing" -> settings.setSwingArm(parsed);
            case "hud" -> settings.setShowHud(parsed);
            default -> {
               return true;
            }
         }

         LyraMessenger.replyFormatted(client, "%1$s = %2$s", key, parsed);
      }
      return true;
   }

   public boolean setRotateMode(Minecraft client, LyraSettings settings, String args) {
      RotateMode mode = RotateMode.parse(args);
      if (mode == null) {
         LyraMessenger.errorFormatted(client, "Usage: /lyra rotatemode <visible_face|closest_face>"); return false;
      } else {
         settings.setRotateMode(mode);
         LyraMessenger.replyFormatted(client, "rotate-mode = %1$s", settings.rotateMode().name());
      }
      return true;
   }

   public boolean setOutOfRangeMode(Minecraft client, LyraSettings settings, String args) {
      OutOfRangeMode mode = OutOfRangeMode.fromInput(args);
      if (mode == null) {
         LyraMessenger.errorFormatted(client, "Usage: /lyra round <drop|clamp|fold|remap>"); return false;
      } else {
         settings.setOutOfRangeMode(mode);
         LyraMessenger.replyFormatted(client, "out-of-range-mode = %1$s", mode.name().toLowerCase(Locale.ROOT));
      }
      return true;
   }

   public boolean setInstrumentMap(Minecraft client, LyraSettings settings, String args) {
      if (args == null || args.trim().isEmpty() || args.trim().equalsIgnoreCase("list")) {
         LyraMessenger.replyFormatted(client, "Instrument mappings: %1$s", localizedMappingsSummary(settings));
         return true;
      }

      String trimmed = args.trim();
      if (trimmed.equalsIgnoreCase("clear")) {
         settings.clearInstrumentOverrides();
         LyraMessenger.replyFormatted(client, "Instrument mappings: %1$s", localizedMappingsSummary(settings));
         return true;
      }

      String[] parts = trimmed.split("\\s+");
      if (parts[0].equalsIgnoreCase("remove")) {
         if (parts.length < 2) {
            LyraMessenger.errorFormatted(client, "Usage: /lyra map [add <from> <to> | remove <from> | clear | list]"); return false;
         }
         NoteBlockInstrument from = LyraSettings.parseInstrument(parts[1]);
         if (from == null) {
            LyraMessenger.errorFormatted(client, "Unknown source instrument: %1$s", parts[1]); return false;
         } else {
            settings.setInstrumentOverride(from, null);
            LyraMessenger.replyFormatted(client, "Mapping removed for %1$s", LyraMessenger.instrumentLabel(from));
         }
         return true;
      }

      if (parts[0].equalsIgnoreCase("add")) {
         if (parts.length < 3) {
            LyraMessenger.errorFormatted(client, "Usage: /lyra map [add <from> <to> | remove <from> | clear | list]"); return false;
         }
         NoteBlockInstrument from = LyraSettings.parseInstrument(parts[1]);
         if (from == null) {
            LyraMessenger.errorFormatted(client, "Unknown source instrument: %1$s", parts[1]); return false;
         }
         NoteBlockInstrument to = LyraSettings.parseInstrument(parts[2]);
         if (to == null) {
            LyraMessenger.errorFormatted(client, "Unknown target instrument: %1$s", parts[2]); return false;
         } else {
            settings.setInstrumentOverride(from, to);
            LyraMessenger.replyFormatted(client, "Mapped %1$s -> %2$s", LyraMessenger.instrumentLabel(from), LyraMessenger.instrumentLabel(to));
         }
         return true;
      }

      LyraMessenger.errorFormatted(client, "Usage: /lyra map [add <from> <to> | remove <from> | clear | list]"); return false;
   }

   public boolean setTranspose(Minecraft client, LyraSettings settings, String value) {
      TransposeSetting transpose = TransposeSetting.parse(value);
      if (transpose == null) {
         LyraMessenger.error(client, "Usage: /lyra transpose <" + TransposeSetting.usage() + ">");
         return false;
      }
      settings.setTranspose(transpose);
      LyraMessenger.reply(client, "transpose = " + transpose.serialized());
      return true;
   }

   public Boolean parseBoolean(String value) {
      if (value == null) {
         return null;
      }

      return switch (value.trim().toLowerCase(Locale.ROOT)) {
         case "on", "true", "1", "yes" -> true;
         case "off", "false", "0", "no" -> false;
         default -> null;
      };
   }
   private static String localizedMappingsSummary(LyraSettings settings) {
      if (settings.mappedSources().isEmpty()) {
         return LyraMessenger.literalComponent("none").getString();
      } else {
         List<NoteBlockInstrument> sources = new ArrayList<>(settings.mappedSources());
         sources.sort(Comparator.comparing(Enum::name));
         List<String> mappings = new ArrayList<>();

         for (NoteBlockInstrument source : sources) {
            NoteBlockInstrument target = settings.mapInstrument(source);
            String sourceLabel = LyraMessenger.instrumentLabel(source).getString();
            mappings.add(sourceLabel + "->" + LyraMessenger.instrumentLabel(target).getString());
         }

         return String.join(", ", mappings);
      }
   }
}
