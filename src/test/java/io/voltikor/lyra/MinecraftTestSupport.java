package io.voltikor.lyra;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;

/** Initializes vanilla registries before any instrument or item is referenced. */
public abstract class MinecraftTestSupport {
   @BeforeAll
   static void initializeMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }
}
