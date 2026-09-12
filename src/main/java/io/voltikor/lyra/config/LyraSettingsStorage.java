package io.voltikor.lyra.config;

import io.voltikor.lyra.hud.HudAnchor;
import io.voltikor.lyra.noteblock.InstrumentDetectMode;
import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import io.voltikor.lyra.noteblock.RotateMode;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.TempoQuantization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class LyraSettingsStorage {
   private static final Logger LOGGER = LoggerFactory.getLogger("Lyra/Settings");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

   private LyraSettingsStorage() {
   }

   public static String serialize(LyraSettings settings) {
      return GSON.toJson(fromSettings(settings));
   }

   public static void load(Path file, LyraSettings settings) {
      if (file != null && settings != null && Files.isRegularFile(file)) {
         try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            StoredSettings stored = GSON.fromJson(reader, StoredSettings.class);
            if (stored != null) {
               apply(stored, settings);
            }
         } catch (Exception exception) {
            LOGGER.error("Failed to load settings from {}", file, exception);
         }
      }
   }

   public static boolean save(Path file, LyraSettings settings) {
      if (file != null && settings != null) {
         try {
            AtomicJsonWriter.write(file, writer -> GSON.toJson(fromSettings(settings), writer));
            settings.markSaved();
            return true;
         } catch (Exception exception) {
            LOGGER.error("Failed to save settings to {}", file, exception);
         }
      }
      return false;
   }

   private static StoredSettings fromSettings(LyraSettings settings) {
      StoredSettings out = new StoredSettings();
      out.tickDelay = settings.tickDelay();
      out.concurrentTuneBlocks = settings.concurrentTuneBlocks();
      out.mode = settings.mode();
      out.instrumentDetectMode = settings.instrumentDetectMode();
      out.tempoQuantization = settings.tempoQuantization();
      out.polyphonic = settings.polyphonic();
      out.autoRotate = settings.autoRotate();
      out.rotateMode = settings.rotateMode();
      out.autoPlay = settings.autoPlay();
      out.roundOutOfRange = false;
      out.outOfRangeMode = settings.outOfRangeMode() != null ? settings.outOfRangeMode().name()
            : OutOfRangeMode.REMAP.name();
      out.transpose = settings.transpose().serialized();
      out.swingArm = settings.swingArm();
      out.checkNoteblocksAgainDelay = settings.checkNoteblocksAgainDelay();
      out.showHud = settings.showHud();
      out.hudAnchor = settings.hudAnchor() != null ? settings.hudAnchor().name() : HudAnchor.BOTTOM_RIGHT.name();
      out.hudAutoHide = settings.hudAutoHide();
      out.selectedSongPath = settings.selectedSongPath() == null ? "" : settings.selectedSongPath();

      for (NoteBlockInstrument source : settings.mappedSources()) {
         NoteBlockInstrument target = settings.mapInstrument(source);
         if (target != null) {
            out.instrumentMappings.put(source.name().toLowerCase(Locale.ROOT), target.name().toLowerCase(Locale.ROOT));
         }
      }

      return out;
   }

   private static void apply(StoredSettings stored, LyraSettings settings) {
      HudAnchor parsedAnchor = HudAnchor.parse(stored.hudAnchor);
      OutOfRangeMode loadedOutOfRangeMode;
      // Migrate legacy 'roundOutOfRange' boolean to the new OutOfRangeMode enum.
      if (stored.outOfRangeMode != null) {
         OutOfRangeMode parsed = OutOfRangeMode.fromInput(stored.outOfRangeMode);
         loadedOutOfRangeMode = parsed != null ? parsed : OutOfRangeMode.REMAP;
      } else if (stored.roundOutOfRange != null) {
         loadedOutOfRangeMode = stored.roundOutOfRange ? OutOfRangeMode.CLAMP : OutOfRangeMode.DROP;
      } else {
         loadedOutOfRangeMode = OutOfRangeMode.REMAP;
      }
      Map<NoteBlockInstrument, NoteBlockInstrument> loadedMappings = new LinkedHashMap<>();
      if (stored.instrumentMappings != null) {
         for (Map.Entry<String, String> entry : stored.instrumentMappings.entrySet()) {
            NoteBlockInstrument from = LyraSettings.parseInstrument(entry.getKey());
            NoteBlockInstrument to = LyraSettings.parseInstrument(entry.getValue());
            if (from != null && to != null) {
               loadedMappings.put(from, to);
            }
         }

      }
      settings.applyLoadedValues(loaded -> {
         loaded.setTickDelay(Math.clamp(stored.tickDelay, 1, 20));
         loaded.setConcurrentTuneBlocks(stored.concurrentTuneBlocks == 0 ? 0
               : Math.clamp(stored.concurrentTuneBlocks, 1, 20));
         if (stored.mode != null) loaded.setMode(stored.mode);
         if (stored.instrumentDetectMode != null) loaded.setInstrumentDetectMode(stored.instrumentDetectMode);
         if (stored.tempoQuantization != null) loaded.setTempoQuantization(stored.tempoQuantization);
         loaded.setPolyphonic(stored.polyphonic);
         loaded.setAutoRotate(stored.autoRotate);
         loaded.setRotateMode(stored.rotateMode == null ? RotateMode.VISIBLE_FACE : stored.rotateMode);
         loaded.setAutoPlay(stored.autoPlay);
         loaded.setOutOfRangeMode(loadedOutOfRangeMode);
         TransposeSetting transpose = TransposeSetting.parse(stored.transpose);
         loaded.setTranspose(transpose == null ? TransposeSetting.AUTO : transpose);
         loaded.setSwingArm(stored.swingArm);
         loaded.setCheckNoteblocksAgainDelay(Math.clamp(stored.checkNoteblocksAgainDelay, 1, 100));
         loaded.setShowHud(stored.showHud);
         loaded.setHudAnchor(parsedAnchor != null ? parsedAnchor : HudAnchor.BOTTOM_RIGHT);
         loaded.setHudAutoHide(stored.hudAutoHide);
         loaded.setSelectedSongPath(stored.selectedSongPath == null ? "" : stored.selectedSongPath);
         loaded.replaceInstrumentOverrides(loadedMappings);
      });
   }

   @Environment(EnvType.CLIENT)
   private static final class StoredSettings {
      int tickDelay = 1;
      int concurrentTuneBlocks = 1;
      InstrumentMatchMode mode;
      InstrumentDetectMode instrumentDetectMode;
      TempoQuantization tempoQuantization = TempoQuantization.SNAP_NEAREST;
      boolean polyphonic;
      boolean autoRotate;
      RotateMode rotateMode;
      boolean autoPlay;
      Boolean roundOutOfRange; // legacy field kept for migration; prefer outOfRangeMode
      String outOfRangeMode;
      String transpose;
      boolean swingArm;
      int checkNoteblocksAgainDelay;
      boolean showHud;
      String hudAnchor;
      boolean hudAutoHide = true;
      String selectedSongPath;
      Map<String, String> instrumentMappings;

      private StoredSettings() {
         this.mode = InstrumentMatchMode.EXACT_INSTRUMENTS;
         this.instrumentDetectMode = InstrumentDetectMode.BLOCK_STATE;
         this.tempoQuantization = TempoQuantization.SNAP_NEAREST;
         this.polyphonic = true;
         this.autoRotate = true;
         this.rotateMode = RotateMode.VISIBLE_FACE;
         this.autoPlay = false;
         this.roundOutOfRange = null;
         this.outOfRangeMode = null; // null signals "use legacy roundOutOfRange for migration"
         this.transpose = TransposeSetting.AUTO.serialized();
         this.swingArm = true;
         this.checkNoteblocksAgainDelay = 10;
         this.showHud = true;
         this.hudAnchor = HudAnchor.BOTTOM_RIGHT.name();
         this.hudAutoHide = true;
         this.selectedSongPath = "";
         this.instrumentMappings = new LinkedHashMap<>();
      }
   }
}
