package io.voltikor.lyra.config;

import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.TempoQuantization;

import java.util.EnumMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
public final class SongConfig {
   private final TempoQuantization tempoOverride;
   private final TransposeSetting transposeOverride;
   private final OutOfRangeMode outOfRangeModeOverride;
   private final Map<NoteBlockInstrument, NoteBlockInstrument> instrumentMappings;

   SongConfig() {
      this(null, null, null, Map.of());
   }

   SongConfig(TempoQuantization tempoOverride, TransposeSetting transposeOverride,
         OutOfRangeMode outOfRangeModeOverride) {
      this(tempoOverride, transposeOverride, outOfRangeModeOverride, Map.of());
   }

   SongConfig(TempoQuantization tempoOverride, TransposeSetting transposeOverride,
         OutOfRangeMode outOfRangeModeOverride,
         Map<NoteBlockInstrument, NoteBlockInstrument> instrumentMappings) {
      this.tempoOverride = tempoOverride;
      this.transposeOverride = transposeOverride;
      this.outOfRangeModeOverride = outOfRangeModeOverride;
      Map<NoteBlockInstrument, NoteBlockInstrument> mappings = new EnumMap<>(NoteBlockInstrument.class);
      mappings.putAll(instrumentMappings);
      this.instrumentMappings = Collections.unmodifiableMap(mappings);
   }

   public TempoQuantization tempoOverride() {
      return this.tempoOverride;
   }

   public TransposeSetting transposeOverride() { return this.transposeOverride; }

   public OutOfRangeMode outOfRangeModeOverride() {
      return this.outOfRangeModeOverride;
   }

   public NoteBlockInstrument mapInstrument(NoteBlockInstrument source) {
      return this.instrumentMappings.getOrDefault(source, source);
   }

   public Set<NoteBlockInstrument> mappedSources() {
      return this.instrumentMappings.keySet();
   }

   public boolean isEmpty() {
      return this.tempoOverride == null && this.transposeOverride == null
            && this.outOfRangeModeOverride == null && this.instrumentMappings.isEmpty();
   }

   SongConfig withTempoOverride(TempoQuantization value) {
      return new SongConfig(value, this.transposeOverride, this.outOfRangeModeOverride, this.instrumentMappings);
   }

   SongConfig withTransposeOverride(TransposeSetting value) {
      return new SongConfig(this.tempoOverride, value, this.outOfRangeModeOverride, this.instrumentMappings);
   }

   SongConfig withOutOfRangeModeOverride(OutOfRangeMode value) {
      return new SongConfig(this.tempoOverride, this.transposeOverride, value, this.instrumentMappings);
   }

   SongConfig withInstrumentOverride(NoteBlockInstrument source, NoteBlockInstrument targetOrNull) {
      Map<NoteBlockInstrument, NoteBlockInstrument> mappings = new EnumMap<>(NoteBlockInstrument.class);
      mappings.putAll(this.instrumentMappings);
      if (targetOrNull == null) {
         mappings.remove(source);
      } else {
         mappings.put(source, targetOrNull);
      }
      return new SongConfig(this.tempoOverride, this.transposeOverride, this.outOfRangeModeOverride, mappings);
   }

   SongConfig withoutInstrumentOverrides() {
      if (this.instrumentMappings.isEmpty()) return this;
      return new SongConfig(this.tempoOverride, this.transposeOverride, this.outOfRangeModeOverride, Map.of());
   }
}
