package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.hud.HudAnchor;

final class HudSection implements LyraScreenSection {
   @Override
   public void build(LyraScreen screen) {
      var settings = screen.settings();
      screen.toggle("Show HUD", "Show the song and required note blocks in the game overlay.",
            settings::showHud, settings::setShowHud);
      screen.cycle("Position", "Choose which corner contains the overlay.",
            HudAnchor.values(), settings::hudAnchor, settings::setHudAnchor);
      screen.toggle("Hide when empty", "Hide the overlay when no song is selected or loaded.",
            settings::hudAutoHide, settings::setHudAutoHide);
   }
}
