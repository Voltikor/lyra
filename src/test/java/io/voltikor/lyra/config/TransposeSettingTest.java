package io.voltikor.lyra.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransposeSettingTest {
   @Test
   void onlyNamedModesAndRequestedPresetsAreAccepted() {
      assertEquals(List.of("off", "auto", "smart", "-24", "-18", "-12", "-6", "+6", "+12", "+18", "+24"), TransposeSetting.options());
      for (var mode : TransposeSetting.values()) assertEquals(mode, TransposeSetting.parse(mode.serialized()));
      assertEquals(TransposeSetting.UP_6, TransposeSetting.parse("6"));
      assertEquals(TransposeSetting.DOWN_18, TransposeSetting.parse(" -18 "));
      for (String rejected : List.of("0", "5", "+7", "-13", "25", "-36", "manual", "6.5")) {
         assertNull(TransposeSetting.parse(rejected), rejected);
      }
   }
}
