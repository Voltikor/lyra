package io.voltikor.lyra.config;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public final class PendingSettingsSave {
   private final LongSupplier clock;
   private boolean retryPending;
   private long retryAt;

   public PendingSettingsSave() { this(System::nanoTime); }
   PendingSettingsSave(LongSupplier clock) { this.clock = clock; }

   public void saveIfDue(LyraSettings settings, BooleanSupplier save, boolean flush) {
      if (!settings.isDirty()) return;
      if (!flush && this.retryPending && this.clock.getAsLong() - this.retryAt < 0) return;
      if (save.getAsBoolean()) {
         settings.markSaved();
         this.retryPending = false;
      } else {
         this.retryPending = true;
         this.retryAt = this.clock.getAsLong() + TimeUnit.SECONDS.toNanos(5);
      }
   }
}
