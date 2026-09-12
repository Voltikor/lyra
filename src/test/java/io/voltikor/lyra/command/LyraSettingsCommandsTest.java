package io.voltikor.lyra.command;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LyraSettingsCommandsTest extends MinecraftTestSupport {
   private final LyraSettingsCommands commands = new LyraSettingsCommands();

   @Test
   void numericCommandsRejectValuesOutsideTheirDocumentedRanges() {
      var settings = new LyraSettings();
      int tickDelay = settings.tickDelay();
      int rescanDelay = settings.checkNoteblocksAgainDelay();

      assertFalse(this.commands.setTickDelay(null, settings, 21));
      assertFalse(this.commands.setCheckNoteblocksAgainDelay(null, settings, 0));
      assertFalse(this.commands.setConcurrentTuneBlocks(null, settings, -1));
      assertFalse(this.commands.setConcurrentTuneBlocks(null, settings, 21));
      assertEquals(tickDelay, settings.tickDelay());
      assertEquals(rescanDelay, settings.checkNoteblocksAgainDelay());
   }

   @Test
   void booleanSettingsUseTypedKeys() {
      var settings = new LyraSettings();
      assertFalse(this.commands.setBooleanFlag(null, settings, null, true));
      this.commands.setBooleanFlag(null, settings, BooleanSetting.POLYPHONIC, false);
      assertFalse(settings.polyphonic());
   }
}
