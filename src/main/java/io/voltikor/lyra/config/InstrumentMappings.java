package io.voltikor.lyra.config;

import java.util.EnumMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
final class InstrumentMappings {
   private final Map<NoteBlockInstrument, NoteBlockInstrument> instrumentOverrides = new EnumMap<>(NoteBlockInstrument.class);

   NoteBlockInstrument mapInstrument(NoteBlockInstrument source) {
      return this.instrumentOverrides.getOrDefault(source, source);
   }

   void setInstrumentOverride(NoteBlockInstrument source, NoteBlockInstrument targetOrNull) {
      if (targetOrNull == null) {
         this.instrumentOverrides.remove(source);
      } else {
         this.instrumentOverrides.put(source, targetOrNull);
      }
   }

   Set<NoteBlockInstrument> mappedSources() {
      return Collections.unmodifiableSet(this.instrumentOverrides.keySet());
   }

   void clearInstrumentOverrides() {
      this.instrumentOverrides.clear();
   }

   boolean isEmpty() {
      return this.instrumentOverrides.isEmpty();
   }
}
