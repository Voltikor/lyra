package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.noteblock.InstrumentDetectMode;
import io.voltikor.lyra.noteblock.RotateMode;

final class TuningSection implements LyraScreenSection {
   @Override
   public void build(LyraScreen screen) {
      var settings = screen.settings();
      screen.number("Tune delay", "Ticks between tuning clicks, from 1 to 20.",
            settings::tickDelay, settings::setTickDelay, 1, 20);
      screen.number("Tune together", "Blocks tuned at once: 1–20. Use 0 for unlimited.",
            settings::concurrentTuneBlocks, settings::setConcurrentTuneBlocks, 0, 20);
      screen.number("Recheck delay", "Wait this many ticks before checking tuning again: 1–100.",
            settings::checkNoteblocksAgainDelay, settings::setCheckNoteblocksAgainDelay, 1, 100);
      screen.cycle("Detect instruments", "Read the note block state, or inspect the block below it.",
            InstrumentDetectMode.values(), settings::instrumentDetectMode, settings::setInstrumentDetectMode);
      screen.toggle("Turn toward notes", "Automatically face the next note block.", settings::autoRotate, settings::setAutoRotate);
      screen.cycle("Aim at", "Choose the visible face or the closest face of each note block.",
            RotateMode.values(), settings::rotateMode, settings::setRotateMode);
      screen.toggle("Swing arm", "Show an arm swing when playing notes.", settings::swingArm, settings::setSwingArm);
   }
}
