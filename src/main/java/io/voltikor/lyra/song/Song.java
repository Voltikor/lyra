package io.voltikor.lyra.song;

import io.voltikor.lyra.config.TransposeSetting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
public final class Song {
   private final NavigableMap<Integer, List<Note>> notesByTick = new TreeMap<>();
   private final String title;
   private final String author;
   private final Set<Note> requirements = new LinkedHashSet<>();
   private final Map<NoteBlockInstrument, Set<NoteBlockInstrument>> remappedSourcesByTarget = new HashMap<>();
   private boolean finishedLoading;
   private int lastTick;
   private int transposeSemitones;
   private TransposeSetting transposeSetting = TransposeSetting.OFF;

   public Song(String title, String author) {
      this.title = title;
      this.author = author;
   }

   public void recordInstrumentRemap(NoteBlockInstrument source, NoteBlockInstrument target) {
      if (source != null && target != null && source != target) {
         this.remappedSourcesByTarget.computeIfAbsent(target, (k) -> new HashSet<>()).add(source);
      }
   }

   public boolean isSourceRemappedToTarget(NoteBlockInstrument source, NoteBlockInstrument target) {
      Set<NoteBlockInstrument> sources = this.remappedSourcesByTarget.get(target);
      return sources != null && sources.contains(source);
   }

   public void addNote(int tick, Note note) {
      this.notesByTick.computeIfAbsent(tick, (key) -> new ArrayList<Note>()).add(note);
   }

   public int transposeSemitones() {
      return this.transposeSemitones;
   }

   public void setTransposeSemitones(int semitones) {
      this.transposeSemitones = semitones;
   }

   public TransposeSetting transposeSetting() { return this.transposeSetting; }

   public void setTransposeSetting(TransposeSetting setting) {
      this.transposeSetting = setting == null ? TransposeSetting.OFF : setting;
   }

   public NavigableMap<Integer, List<Note>> mutableNotesByTick() {
      return this.notesByTick;
   }

   public void finishLoading() {
      if (this.finishedLoading) {
         throw new IllegalStateException("Song has already finished loading.");
      } else {
         this.requirements.clear();

         for (List<Note> notes : this.notesByTick.values()) {
            this.requirements.addAll(notes);
         }

         this.lastTick = this.notesByTick.isEmpty() ? 0 : this.notesByTick.lastKey();
         this.finishedLoading = true;
      }
   }

   public String title() {
      return this.title;
   }

   public String author() {
      return this.author;
   }

   public int lastTick() {
      if (!this.finishedLoading) {
         throw new IllegalStateException("Song is still loading.");
      } else {
         return this.lastTick;
      }
   }

   public Set<Note> requirements() {
      if (!this.finishedLoading) {
         throw new IllegalStateException("Song is still loading.");
      } else {
         return Collections.unmodifiableSet(this.requirements);
      }
   }

   public Collection<Note> notesAt(int tick) {
      return this.notesByTick.getOrDefault(tick, List.of());
   }

   public Collection<Note> nextNotesAtOrAfter(int tick) {
      Map.Entry<Integer, List<Note>> entry = this.notesByTick.ceilingEntry(tick);
      return entry == null ? List.of() : Collections.unmodifiableList(entry.getValue());
   }

   public Map<Integer, List<Note>> notesByTick() {
      return Collections.unmodifiableMap(this.notesByTick);
   }
}
