package io.voltikor.lyra.config;

import io.voltikor.lyra.hud.HudAnchor;
import io.voltikor.lyra.noteblock.InstrumentDetectMode;
import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import io.voltikor.lyra.noteblock.RotateMode;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.TempoQuantization;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
public final class LyraSettings {
   private int tickDelay = 1;
   private int concurrentTuneBlocks = 1;
   private InstrumentMatchMode mode;
   private InstrumentDetectMode instrumentDetectMode;
   private TempoQuantization tempoQuantization = TempoQuantization.SNAP_NEAREST;
   private boolean polyphonic;
   private boolean autoRotate;
   private RotateMode rotateMode;
   private boolean autoPlay;
   private OutOfRangeMode outOfRangeMode = OutOfRangeMode.REMAP;
   private TransposeSetting transpose = TransposeSetting.AUTO;
   private boolean swingArm;
   private int checkNoteblocksAgainDelay;
   private boolean showHud;
   private HudAnchor hudAnchor;
   private boolean hudAutoHide;
   private String selectedSongPath;
   private final InstrumentMappings instrumentMappings;
   private boolean suppressChangeTracking;

   public LyraSettings() {
      this.mode = InstrumentMatchMode.EXACT_INSTRUMENTS;
      this.instrumentDetectMode = InstrumentDetectMode.BLOCK_STATE;
      this.tempoQuantization = TempoQuantization.SNAP_NEAREST;
      this.polyphonic = true;
      this.autoRotate = true;
      this.rotateMode = RotateMode.VISIBLE_FACE;
      this.autoPlay = false;
      this.outOfRangeMode = OutOfRangeMode.REMAP;
      this.transpose = TransposeSetting.AUTO;
      this.swingArm = true;
      this.checkNoteblocksAgainDelay = 10;
      this.showHud = true;
      this.hudAnchor = HudAnchor.BOTTOM_RIGHT;
      this.hudAutoHide = true;
      this.selectedSongPath = "";
      this.instrumentMappings = new InstrumentMappings();
   }

   public int tickDelay() { return this.tickDelay; }
   public int concurrentTuneBlocks() { return this.concurrentTuneBlocks; }
   public InstrumentMatchMode mode() { return this.mode; }
   public InstrumentDetectMode instrumentDetectMode() { return this.instrumentDetectMode; }
   public TempoQuantization tempoQuantization() { return this.tempoQuantization; }
   public boolean polyphonic() { return this.polyphonic; }
   public boolean autoRotate() { return this.autoRotate; }
   public RotateMode rotateMode() { return this.rotateMode; }
   public boolean autoPlay() { return this.autoPlay; }
   public OutOfRangeMode outOfRangeMode() { return this.outOfRangeMode; }
   public TransposeSetting transpose() { return this.transpose; }
   public boolean swingArm() { return this.swingArm; }
   public int checkNoteblocksAgainDelay() { return this.checkNoteblocksAgainDelay; }
   public boolean showHud() { return this.showHud; }
   public HudAnchor hudAnchor() { return this.hudAnchor; }
   public boolean hudAutoHide() { return this.hudAutoHide; }
   public String selectedSongPath() { return this.selectedSongPath; }

   public NoteBlockInstrument mapInstrument(NoteBlockInstrument source) {
      return this.instrumentMappings.mapInstrument(source);
   }

   public boolean setInstrumentOverride(NoteBlockInstrument source, NoteBlockInstrument targetOrNull) {
      boolean present = this.mappedSources().contains(source);
      if (targetOrNull == null ? !present : present && this.mapInstrument(source) == targetOrNull) return false;
      this.instrumentMappings.setInstrumentOverride(source, targetOrNull);
      this.changed(true);
      return true;
   }

   public Set<NoteBlockInstrument> mappedSources() {
      return this.instrumentMappings.mappedSources();
   }

   public boolean clearInstrumentOverrides() {
      if (this.instrumentMappings.isEmpty()) return false;
      this.instrumentMappings.clearInstrumentOverrides();
      this.changed(true);
      return true;
   }

   /** Replaces values loaded from disk without treating deserialization as a user mutation. */
   void replaceInstrumentOverrides(Map<NoteBlockInstrument, NoteBlockInstrument> replacements) {
      this.instrumentMappings.clearInstrumentOverrides();
      if (replacements != null) {
         replacements.forEach(this.instrumentMappings::setInstrumentOverride);
      }
   }

   /** Applies persisted values through normal setters without treating the load as a user mutation. */
   void applyLoadedValues(Consumer<LyraSettings> loader) {
      boolean previous = this.suppressChangeTracking;
      this.suppressChangeTracking = true;
      try {
         loader.accept(this);
      } finally {
         this.suppressChangeTracking = previous;
      }
      this.markSaved();
   }

   public String mappingsSummary() {
      if (this.instrumentMappings.isEmpty()) {
         return "none";
      }
      return this.mappedSources().stream().map((source) -> {
         return prettyName(source) + "->" + prettyName(this.mapInstrument(source));
      }).collect(Collectors.joining(", "));
   }

   public static NoteBlockInstrument parseInstrument(String input) {
      if (input == null) {
         return null;
      } else {
         String normalized = input.trim().toUpperCase(Locale.ROOT).replace('-', '_');

         for (NoteBlockInstrument instrument : NoteBlockInstrument.values()) {
            if (instrument.name().equals(normalized)) {
               return instrument;
            }
         }

         return null;
      }
   }

   public static String prettyName(NoteBlockInstrument instrument) {
      return instrument.name().toLowerCase(Locale.ROOT);
   }
   private boolean dirty;
   private long decodingRevision;

   boolean isDirty() { return this.dirty; }
   void markSaved() { this.dirty = false; }
   public long decodingRevision() { return this.decodingRevision; }

   private void changed(boolean decoding) {
      if (this.suppressChangeTracking) return;
      this.dirty = true;
      if (decoding) this.decodingRevision++;
   }

   public boolean setTickDelay(int value) {
      if (java.util.Objects.equals(this.tickDelay, value)) return false;
      this.tickDelay = value;
      this.changed(false);
      return true;
   }

   public boolean setConcurrentTuneBlocks(int value) {
      if (java.util.Objects.equals(this.concurrentTuneBlocks, value)) return false;
      this.concurrentTuneBlocks = value;
      this.changed(false);
      return true;
   }

   public boolean setMode(InstrumentMatchMode value) {
      if (java.util.Objects.equals(this.mode, value)) return false;
      this.mode = value;
      this.changed(true);
      return true;
   }

   public boolean setInstrumentDetectMode(InstrumentDetectMode value) {
      if (java.util.Objects.equals(this.instrumentDetectMode, value)) return false;
      this.instrumentDetectMode = value;
      this.changed(false);
      return true;
   }

   public boolean setTempoQuantization(TempoQuantization value) {
      if (java.util.Objects.equals(this.tempoQuantization, value)) return false;
      this.tempoQuantization = value;
      this.changed(true);
      return true;
   }

   public boolean setPolyphonic(boolean value) {
      if (java.util.Objects.equals(this.polyphonic, value)) return false;
      this.polyphonic = value;
      this.changed(false);
      return true;
   }

   public boolean setAutoRotate(boolean value) {
      if (java.util.Objects.equals(this.autoRotate, value)) return false;
      this.autoRotate = value;
      this.changed(false);
      return true;
   }

   public boolean setRotateMode(RotateMode value) {
      if (java.util.Objects.equals(this.rotateMode, value)) return false;
      this.rotateMode = value;
      this.changed(false);
      return true;
   }

   public boolean setAutoPlay(boolean value) {
      if (java.util.Objects.equals(this.autoPlay, value)) return false;
      this.autoPlay = value;
      this.changed(false);
      return true;
   }

   public boolean setOutOfRangeMode(OutOfRangeMode value) {
      if (java.util.Objects.equals(this.outOfRangeMode, value)) return false;
      this.outOfRangeMode = value;
      this.changed(true);
      return true;
   }

   public boolean setTranspose(TransposeSetting value) {
      if (value == null || java.util.Objects.equals(this.transpose, value)) return false;
      this.transpose = value;
      this.changed(true);
      return true;
   }

   public boolean setSwingArm(boolean value) {
      if (java.util.Objects.equals(this.swingArm, value)) return false;
      this.swingArm = value;
      this.changed(false);
      return true;
   }

   public boolean setCheckNoteblocksAgainDelay(int value) {
      if (java.util.Objects.equals(this.checkNoteblocksAgainDelay, value)) return false;
      this.checkNoteblocksAgainDelay = value;
      this.changed(false);
      return true;
   }

   public boolean setShowHud(boolean value) {
      if (java.util.Objects.equals(this.showHud, value)) return false;
      this.showHud = value;
      this.changed(false);
      return true;
   }

   public boolean setHudAnchor(HudAnchor value) {
      if (java.util.Objects.equals(this.hudAnchor, value)) return false;
      this.hudAnchor = value;
      this.changed(false);
      return true;
   }

   public boolean setHudAutoHide(boolean value) {
      if (java.util.Objects.equals(this.hudAutoHide, value)) return false;
      this.hudAutoHide = value;
      this.changed(false);
      return true;
   }

   public boolean setSelectedSongPath(String value) {
      if (java.util.Objects.equals(this.selectedSongPath, value)) return false;
      this.selectedSongPath = value;
      this.changed(false);
      return true;
   }

}
