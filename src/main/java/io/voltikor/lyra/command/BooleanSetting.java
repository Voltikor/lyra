package io.voltikor.lyra.command;

import io.voltikor.lyra.config.LyraSettings;
import java.util.function.BiFunction;

enum BooleanSetting {
   POLYPHONIC("polyphonic", LyraSettings::setPolyphonic),
   ROTATE("rotate", LyraSettings::setAutoRotate),
   AUTOPLAY("autoplay", LyraSettings::setAutoPlay),
   SWING("swing", LyraSettings::setSwingArm);

   private final String commandName;
   private final BiFunction<LyraSettings, Boolean, Boolean> setter;

   BooleanSetting(String commandName, BiFunction<LyraSettings, Boolean, Boolean> setter) {
      this.commandName = commandName;
      this.setter = setter;
   }

   public String commandName() {
      return this.commandName;
   }

   public boolean set(LyraSettings settings, boolean enabled) {
      return this.setter.apply(settings, enabled);
   }
}
