package io.voltikor.lyra.command;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class LyraSettingsCommandsTest extends MinecraftTestSupport {
   private final LyraSettingsCommands commands = new LyraSettingsCommands();

   @Test
   void numericCommandsRejectValuesOutsideTheirDocumentedRanges() {
      var settings = new LyraSettings();
      int tickDelay = settings.tickDelay();
      int rescanDelay = settings.checkNoteblocksAgainDelay();

      assertFalse(this.commands.setTickDelay(null, settings, "21"));
      assertFalse(this.commands.setCheckNoteblocksAgainDelay(null, settings, "0"));
      assertEquals(tickDelay, settings.tickDelay());
      assertEquals(rescanDelay, settings.checkNoteblocksAgainDelay());
      assertNull(this.commands.parseConcurrentTuneBlocks("-1"));
      assertNull(this.commands.parseConcurrentTuneBlocks("21"));
   }

   @Test
   void unknownBooleanSettingIsRejected() {
      assertFalse(this.commands.setBooleanFlag(null, new LyraSettings(), "unknown", "on"));
   }
}
