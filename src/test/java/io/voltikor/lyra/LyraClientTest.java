package io.voltikor.lyra;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LyraClientTest extends MinecraftTestSupport {
   @Test
   void queuedKeyPressesProduceOnlyOneToggle() {
      AtomicInteger remaining = new AtomicInteger(4);
      AtomicInteger toggles = new AtomicInteger();

      LyraClient.drainClicks(() -> remaining.getAndDecrement() > 0, toggles::incrementAndGet);

      assertEquals(1, toggles.get());
      assertEquals(-1, remaining.get());
   }

}
