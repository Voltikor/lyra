package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.TempoQuantization;
import java.util.Arrays;

final class SongSection implements LyraScreenSection {
   @Override
   public void build(LyraScreen screen) {
      if (screen.songKey() == null) {
         screen.feedback("Choose a song in the player first.");
         return;
      }
      screen.cycleNullable("Tempo rounding", "Inherit uses your global music setting. Reload the song to apply.",
            Arrays.stream(TempoQuantization.values()).filter(value -> value != TempoQuantization.DEFAULT).toList(),
            () -> screen.songConfig() == null ? null : screen.songConfig().tempoOverride(),
            value -> screen.songChange(() -> screen.files().songConfigManager().setTempoOverride(screen.songKey(), value)));
      screen.transpose(true);
      screen.cycleNullable("Out-of-range notes", "Override the range policy for this song, or inherit the global setting.",
            Arrays.asList(OutOfRangeMode.values()),
            () -> screen.songConfig() == null ? null : screen.songConfig().outOfRangeModeOverride(),
            value -> screen.songChange(() -> screen.files().songConfigManager().setOutOfRangeModeOverride(screen.songKey(), value)));
      screen.row(y -> screen.button("Edit this song's instrument mappings", screen.left(), y, screen.span(),
            screen::editSongMappings));
      screen.row(y -> screen.button("Reset this song to global settings", screen.left(), y, screen.span(), () -> {
         screen.songChange(() -> screen.files().songConfigManager().clearConfig(screen.songKey()));
         screen.rebuild();
      }));
   }

   @Override
   public String subtitle(LyraScreen screen) {
      return "For: " + (screen.songKey() == null ? "no song selected" : screen.songKey());
   }
}
