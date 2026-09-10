package io.voltikor.lyra.config;

import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.TempoQuantization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
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
public final class SongConfigManager {
   private static final Logger LOGGER = LoggerFactory.getLogger("Lyra/SongConfig");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

   private Path configFile;
   private final Map<String, Long> revisions = new LinkedHashMap<>();
   private boolean lastSaveSucceeded = true;

   public synchronized long revision(String key) { return this.revisions.getOrDefault(key, 0L); }
   public synchronized boolean lastSaveSucceeded() { return this.lastSaveSucceeded; }

   private record State(TempoQuantization tempo, TransposeSetting transpose, OutOfRangeMode outOfRangeMode,
         Map<NoteBlockInstrument, NoteBlockInstrument> mappings) {}

   private State state(String key) {
      SongConfig config = this.configs.get(key);
      Map<NoteBlockInstrument, NoteBlockInstrument> mappings = new LinkedHashMap<>();
      if (config != null) for (NoteBlockInstrument source : config.mappedSources()) {
         mappings.put(source, config.mapInstrument(source));
      }
      return new State(config == null ? null : config.tempoOverride(),
            config == null ? null : config.transposeOverride(),
            config == null ? null : config.outOfRangeModeOverride(), mappings);
   }

   private boolean changed(String key, State before) {
      if (before.equals(this.state(key))) {
         this.lastSaveSucceeded = true;
         return false;
      }
      this.revisions.merge(key, 1L, Long::sum);
      this.lastSaveSucceeded = this.save();
      return true;
   }
   private final Map<String, SongConfig> configs = new LinkedHashMap<>();

   public void initialize(Path configDir) {
      this.configFile = configDir.resolve("song_config.json");
      this.load();
   }

   public synchronized void load() {
      this.configs.clear();
      if (this.configFile != null && Files.isRegularFile(this.configFile)) {
         try (Reader reader = Files.newBufferedReader(this.configFile, StandardCharsets.UTF_8)) {
            StoredSongConfigs stored = GSON.fromJson(reader, StoredSongConfigs.class);
            if (stored != null && stored.configs != null) {
               for (Map.Entry<String, StoredSongConfig> entry : stored.configs.entrySet()) {
                  String key = entry.getKey();
                  StoredSongConfig sc = entry.getValue();
                  if (sc != null) {
                     TempoQuantization tempo = null;
                     if (sc.tempo != null && !sc.tempo.isBlank()) {
                        TempoQuantization parsed = TempoQuantization.parse(sc.tempo);
                        if (parsed != TempoQuantization.DEFAULT) {
                           tempo = parsed;
                        }
                     }
                     OutOfRangeMode outOfRangeMode = parseOutOfRangeMode(sc.round != null
                           ? sc.round : sc.outOfRangeMode);
                     Map<NoteBlockInstrument, NoteBlockInstrument> mappings = new LinkedHashMap<>();
                     if (sc.instrumentMappings != null) {
                        for (Map.Entry<String, String> mapEntry : sc.instrumentMappings.entrySet()) {
                           NoteBlockInstrument from = LyraSettings.parseInstrument(mapEntry.getKey());
                           NoteBlockInstrument to = LyraSettings.parseInstrument(mapEntry.getValue());
                           if (from != null && to != null) {
                              mappings.put(from, to);
                           }
                        }
                     }
                     TransposeSetting transpose = TransposeSetting.parse(sc.transpose);
                     SongConfig songConfig = new SongConfig(tempo, transpose, outOfRangeMode, mappings);
                     this.configs.put(key, songConfig);
                  }
               }
            }
         } catch (Exception e) {
            LOGGER.error("Failed to load song config from {}", this.configFile, e);
         }
      }
   }

   public synchronized boolean save() {
      if (this.configFile != null) {
         try {
            Path parent = this.configFile.getParent();
            if (parent != null) {
               Files.createDirectories(parent);
            }
            StoredSongConfigs stored = new StoredSongConfigs();
            for (Map.Entry<String, SongConfig> entry : this.configs.entrySet()) {
               if (!entry.getValue().isEmpty()) {
                  StoredSongConfig sc = new StoredSongConfig();
                  if (entry.getValue().tempoOverride() != null && entry.getValue().tempoOverride() != TempoQuantization.DEFAULT) {
                     sc.tempo = entry.getValue().tempoOverride().name();
                  }
                  sc.transpose = entry.getValue().transposeOverride() == null ? null
                        : entry.getValue().transposeOverride().serialized();
                  if (entry.getValue().outOfRangeModeOverride() != null) {
                     sc.round = entry.getValue().outOfRangeModeOverride().name();
                  }
                  for (NoteBlockInstrument source : entry.getValue().mappedSources()) {
                     NoteBlockInstrument target = entry.getValue().mapInstrument(source);
                     if (target != null) {
                        sc.instrumentMappings.put(source.name().toLowerCase(Locale.ROOT), target.name().toLowerCase(Locale.ROOT));
                     }
                  }
                  stored.configs.put(entry.getKey(), sc);
               }
            }
            AtomicJsonWriter.write(this.configFile, writer -> GSON.toJson(stored, writer));
            return true;
         } catch (Exception e) {
            LOGGER.error("Failed to save song config to {}", this.configFile, e);
         }
      }
      return false;
   }

   public synchronized SongConfig getConfig(String key) {
      if (key == null || key.isBlank()) {
         return null;
      }
      return this.configs.get(key);
   }

   public synchronized TempoQuantization getTempoOverride(String key) {
      SongConfig cfg = this.getConfig(key);
      return cfg != null ? cfg.tempoOverride() : null;
   }

   public synchronized TransposeSetting getTransposeOverride(String key) {
      SongConfig cfg = this.getConfig(key);
      return cfg != null ? cfg.transposeOverride() : null;
   }

   public synchronized OutOfRangeMode getOutOfRangeModeOverride(String key) {
      SongConfig cfg = this.getConfig(key);
      return cfg != null ? cfg.outOfRangeModeOverride() : null;
   }

   public synchronized boolean setTempoOverride(String key, TempoQuantization tempo) {
      if (key == null || key.isBlank()) return false;
      State before = this.state(key);
      TempoQuantization value = tempo == TempoQuantization.DEFAULT ? null : tempo;
      SongConfig config = this.configs.get(key);
      if (config == null && value == null) return this.changed(key, before);
      SongConfig updated = (config == null ? new SongConfig() : config).withTempoOverride(value);
      this.store(key, updated);
      return this.changed(key, before);
   }

   public synchronized boolean setTransposeOverride(String key, TransposeSetting transpose) {
      if (key == null || key.isBlank()) return false;
      State before = this.state(key);
      SongConfig config = this.configs.get(key);
      if (config == null && transpose == null) return this.changed(key, before);
      SongConfig updated = (config == null ? new SongConfig() : config).withTransposeOverride(transpose);
      this.store(key, updated);
      return this.changed(key, before);
   }

   public synchronized boolean setOutOfRangeModeOverride(String key, OutOfRangeMode mode) {
      if (key == null || key.isBlank()) return false;
      State before = this.state(key);
      SongConfig config = this.configs.get(key);
      if (config == null && mode == null) return this.changed(key, before);
      SongConfig updated = (config == null ? new SongConfig() : config).withOutOfRangeModeOverride(mode);
      this.store(key, updated);
      return this.changed(key, before);
   }

   public synchronized boolean setInstrumentOverride(String key, NoteBlockInstrument from, NoteBlockInstrument to) {
      if (key == null || key.isBlank() || from == null) return false;
      State before = this.state(key);
      SongConfig config = this.configs.getOrDefault(key, new SongConfig());
      this.store(key, config.withInstrumentOverride(from, to));
      return this.changed(key, before);
   }

   public synchronized boolean removeInstrumentOverride(String key, NoteBlockInstrument from) {
      return this.setInstrumentOverride(key, from, null);
   }

   public synchronized boolean clearInstrumentOverrides(String key) {
      if (key == null || key.isBlank()) return false;
      State before = this.state(key);
      SongConfig config = this.configs.get(key);
      if (config != null) {
         this.store(key, config.withoutInstrumentOverrides());
      }
      return this.changed(key, before);
   }

   public synchronized boolean clearConfig(String key) {
      if (key == null || key.isBlank()) return false;
      State before = this.state(key);
      this.configs.remove(key);
      return this.changed(key, before);
   }

   private void store(String key, SongConfig config) {
      if (config == null || config.isEmpty()) {
         this.configs.remove(key);
      } else {
         this.configs.put(key, config);
      }
   }

   private static OutOfRangeMode parseOutOfRangeMode(String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }
      return OutOfRangeMode.fromInput(raw);
   }

   @Environment(EnvType.CLIENT)
   private static final class StoredSongConfigs {
      Map<String, StoredSongConfig> configs = new LinkedHashMap<>();
   }

   @Environment(EnvType.CLIENT)
   private static final class StoredSongConfig {
      String tempo;
      String transpose;
      String round;
      String outOfRangeMode;
      Map<String, String> instrumentMappings = new LinkedHashMap<>();
   }
}
