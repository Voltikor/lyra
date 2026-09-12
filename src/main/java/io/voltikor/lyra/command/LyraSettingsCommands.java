package io.voltikor.lyra.command;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.hud.HudAnchor;
import io.voltikor.lyra.noteblock.InstrumentDetectMode;
import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import io.voltikor.lyra.noteblock.RotateMode;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.TempoQuantization;
import java.util.Comparator;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
public final class LyraSettingsCommands {
   public boolean setMode(Minecraft client, LyraSettings settings, InstrumentMatchMode mode) {
      if (mode == null) return invalid(client, "Mode is required.");
      settings.setMode(mode);
      LyraMessenger.replyFormatted(client, "Mode set to %1$s", mode.name());
      return true;
   }

   public boolean setInstrumentDetectMode(Minecraft client, LyraSettings settings, InstrumentDetectMode mode) {
      if (mode == null) return invalid(client, "Instrument detection mode is required.");
      settings.setInstrumentDetectMode(mode);
      LyraMessenger.replyFormatted(client, "Instrument detect mode set to %1$s", mode.name());
      return true;
   }

   public boolean setTempoQuantization(Minecraft client, LyraSettings settings, TempoQuantization mode) {
      if (mode == null) return invalid(client, "Tempo quantization mode is required.");
      settings.setTempoQuantization(mode);
      LyraMessenger.replyFormatted(client, "Tempo quantization set to %1$s", mode.name());
      return true;
   }

   public boolean setTickDelay(Minecraft client, LyraSettings settings, int ticks) {
      if (ticks < 1 || ticks > 20) return invalid(client, "Tick delay must be between 1 and 20.");
      settings.setTickDelay(ticks);
      LyraMessenger.replyFormatted(client, "tick-delay = %1$s", ticks);
      return true;
   }

   public boolean setCheckNoteblocksAgainDelay(Minecraft client, LyraSettings settings, int ticks) {
      if (ticks < 1 || ticks > 100) return invalid(client, "Recheck delay must be between 1 and 100.");
      settings.setCheckNoteblocksAgainDelay(ticks);
      LyraMessenger.replyFormatted(client, "check-noteblocks-again-delay = %1$s", ticks);
      return true;
   }

   public boolean setConcurrentTuneBlocks(Minecraft client, LyraSettings settings, int count) {
      if (count < 0 || count > 20) return invalid(client, "Concurrent tune blocks must be 0 through 20.");
      settings.setConcurrentTuneBlocks(count);
      LyraMessenger.replyFormatted(client, "concurrent-tune-blocks = %1$s",
            count == 0 ? "UNLIMITED" : Integer.toString(count));
      return true;
   }

   public String concurrentTuneBlocksLabel(LyraSettings settings) {
      return settings.concurrentTuneBlocks() == 0 ? "UNLIMITED" : Integer.toString(settings.concurrentTuneBlocks());
   }

   public boolean toggleHud(Minecraft client, LyraSettings settings) {
      return this.setShowHud(client, settings, !settings.showHud());
   }

   public boolean setShowHud(Minecraft client, LyraSettings settings, boolean enabled) {
      settings.setShowHud(enabled);
      LyraMessenger.replyFormatted(client, "hud = %1$s", enabled);
      return true;
   }

   public boolean setHudAnchor(Minecraft client, LyraSettings settings, HudAnchor anchor) {
      if (anchor == null) return invalid(client, "HUD anchor is required.");
      settings.setHudAnchor(anchor);
      LyraMessenger.replyFormatted(client, "hud anchor = %1$s", anchor.name().toLowerCase(Locale.ROOT));
      return true;
   }

   public boolean setHudAutoHide(Minecraft client, LyraSettings settings, boolean enabled) {
      settings.setHudAutoHide(enabled);
      LyraMessenger.replyFormatted(client, "hud autohide = %1$s", enabled);
      return true;
   }

   public boolean setBooleanFlag(Minecraft client, LyraSettings settings, BooleanSetting setting, boolean enabled) {
      if (setting == null) return invalid(client, "Unknown boolean setting.");
      setting.set(settings, enabled);
      LyraMessenger.replyFormatted(client, "%1$s = %2$s", setting.commandName(), enabled);
      return true;
   }

   public boolean setRotateMode(Minecraft client, LyraSettings settings, RotateMode mode) {
      if (mode == null) return invalid(client, "Rotate mode is required.");
      settings.setRotateMode(mode);
      LyraMessenger.replyFormatted(client, "rotate-mode = %1$s", mode.name());
      return true;
   }

   public boolean setOutOfRangeMode(Minecraft client, LyraSettings settings, OutOfRangeMode mode) {
      if (mode == null) return invalid(client, "Out-of-range mode is required.");
      settings.setOutOfRangeMode(mode);
      LyraMessenger.replyFormatted(client, "out-of-range-mode = %1$s", mode.name().toLowerCase(Locale.ROOT));
      return true;
   }

   public boolean listInstrumentMap(Minecraft client, LyraSettings settings) {
      LyraMessenger.replyFormatted(client, "Instrument mappings: %1$s", mappingsSummary(settings));
      return true;
   }

   public boolean clearInstrumentMap(Minecraft client, LyraSettings settings) {
      settings.clearInstrumentOverrides();
      return this.listInstrumentMap(client, settings);
   }

   public boolean removeInstrumentMap(Minecraft client, LyraSettings settings, NoteBlockInstrument source) {
      if (source == null) return invalid(client, "Unknown source instrument.");
      settings.setInstrumentOverride(source, null);
      LyraMessenger.replyFormatted(client, "Mapping removed for %1$s", LyraMessenger.instrumentLabel(source));
      return true;
   }

   public boolean setInstrumentMap(Minecraft client, LyraSettings settings,
         NoteBlockInstrument source, NoteBlockInstrument target) {
      if (source == null || target == null) {
         return invalid(client, "Instrument mappings require a source and target instrument.");
      }
      settings.setInstrumentOverride(source, target);
      LyraMessenger.replyFormatted(client, "Mapped %1$s -> %2$s",
            LyraMessenger.instrumentLabel(source), LyraMessenger.instrumentLabel(target));
      return true;
   }

   public boolean setTranspose(Minecraft client, LyraSettings settings, TransposeSetting transpose) {
      if (transpose == null) return invalid(client, "Transpose setting is required.");
      settings.setTranspose(transpose);
      LyraMessenger.reply(client, "transpose = " + transpose.serialized());
      return true;
   }

   private static boolean invalid(Minecraft client, String message) {
      LyraMessenger.error(client, message);
      return false;
   }

   private static String mappingsSummary(LyraSettings settings) {
      if (settings.mappedSources().isEmpty()) return "none";
      return settings.mappedSources().stream()
            .sorted(Comparator.comparing(Enum::name))
            .map(source -> LyraSettings.prettyName(source) + "->"
                  + LyraSettings.prettyName(settings.mapInstrument(source)))
            .collect(java.util.stream.Collectors.joining(", "));
   }
}
