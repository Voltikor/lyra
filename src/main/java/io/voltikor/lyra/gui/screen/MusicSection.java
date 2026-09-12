package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.TempoQuantization;
import java.util.Arrays;

final class MusicSection implements LyraScreenSection {
   private boolean songSettings;

   void editSelectedSong() {
      songSettings = true;
   }

   @Override
   public void build(LyraScreen screen) {
      screen.scopeToggle(songSettings, "global music", value -> songSettings = value);
      if (songSettings && screen.songKey() == null) {
         screen.feedback("Choose a song in the player first, or switch to global music.");
         return;
      }

      var settings = screen.settings();
      if (!songSettings) {
         screen.cycle("Instruments",
               "Exact instruments preserves timbre. Any uses the available blocks. Reload the song after changing.",
               InstrumentMatchMode.values(), settings::mode, settings::setMode);
      }
      buildTempoSetting(screen);
      screen.transpose(songSettings);
      buildOutOfRangeSetting(screen);
      if (songSettings) buildSongActions(screen);
   }

   private void buildTempoSetting(LyraScreen screen) {
      if (!songSettings) {
         screen.cycle("Tempo rounding", "Fit song timing to Minecraft ticks. Reload the song after changing.",
               TempoQuantization.values(), screen.settings()::tempoQuantization,
               screen.settings()::setTempoQuantization);
         return;
      }
      screen.cycleNullable("Tempo rounding", "Inherit uses your global music setting. Reload the song to apply.",
            Arrays.stream(TempoQuantization.values()).filter(value -> value != TempoQuantization.DEFAULT).toList(),
            () -> screen.songConfig() == null ? null : screen.songConfig().tempoOverride(),
            value -> screen.songChange(
                  () -> screen.files().songConfigManager().setTempoOverride(screen.songKey(), value)));
   }

   private void buildOutOfRangeSetting(LyraScreen screen) {
      if (!songSettings) {
         screen.cycle("Out-of-range notes",
               "Drop, clamp, fold by octaves, or remap notes outside the playable range. Reload to apply.",
               OutOfRangeMode.values(), screen.settings()::outOfRangeMode, screen.settings()::setOutOfRangeMode);
         return;
      }
      screen.cycleNullable("Out-of-range notes",
            "Override the range policy for this song, or inherit the global setting.",
            Arrays.asList(OutOfRangeMode.values()),
            () -> screen.songConfig() == null ? null : screen.songConfig().outOfRangeModeOverride(),
            value -> screen.songChange(
                  () -> screen.files().songConfigManager().setOutOfRangeModeOverride(screen.songKey(), value)));
   }

   private void buildSongActions(LyraScreen screen) {
      screen.row(y -> screen.button("Edit this song's instrument mappings", screen.left(), y, screen.span(),
            screen::editSongMappings));
      screen.row(y -> screen.button("Reset this song to global settings", screen.left(), y, screen.span(), () -> {
         screen.songChange(() -> screen.files().songConfigManager().clearConfig(screen.songKey()));
         screen.rebuild();
      }));
   }

   @Override
   public String subtitle(LyraScreen screen) {
      if (!songSettings) return "Global music settings; changes apply when you reload a song.";
      return "Song: " + (screen.songKey() == null ? "no song selected" : screen.songKey());
   }
}
