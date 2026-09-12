package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.TempoQuantization;

final class MusicSection implements LyraScreenSection {
   @Override
   public void build(LyraScreen screen) {
      var settings = screen.settings();
      screen.cycle("Instruments", "Exact instruments preserves timbre. Any uses the available blocks. Reload the song after changing.",
            InstrumentMatchMode.values(), settings::mode, settings::setMode);
      screen.cycle("Tempo rounding", "Fit song timing to Minecraft ticks. Reload the song after changing.",
            TempoQuantization.values(), settings::tempoQuantization, settings::setTempoQuantization);
      screen.transpose(false);
      screen.cycle("Out-of-range notes", "Drop, clamp, fold by octaves, or remap notes outside the playable range. Reload to apply.",
            OutOfRangeMode.values(), settings::outOfRangeMode, settings::setOutOfRangeMode);
      screen.toggle("Play chords", "Play simultaneous notes together. Off plays one note at a time.",
            settings::polyphonic, settings::setPolyphonic);
      screen.toggle("Keep music going", "Automatically play a random song when note-block playback finishes.",
            settings::autoPlay, settings::setAutoPlay);
   }

   @Override
   public String subtitle(LyraScreen screen) { return "Music changes apply when you reload a song."; }
}
