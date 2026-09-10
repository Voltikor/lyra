package io.voltikor.lyra.song;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
public final class Note {
   private NoteBlockInstrument instrument;
   private int noteLevel;

   public Note(NoteBlockInstrument instrument, int noteLevel) {
      this.instrument = instrument;
      this.noteLevel = noteLevel;
   }

   public NoteBlockInstrument instrument() {
      return this.instrument;
   }

   public void setInstrument(NoteBlockInstrument instrument) {
      this.instrument = instrument;
   }

   public int noteLevel() {
      return this.noteLevel;
   }

   public void setNoteLevel(int noteLevel) {
      this.noteLevel = noteLevel;
   }

   public boolean equals(Object object) {
      if (this == object) {
         return true;
      } else if (object != null && object.getClass() == Note.class) {
         Note other = (Note)object;
         return this.instrument == other.instrument && this.noteLevel == other.noteLevel;
      } else {
         return false;
      }
   }

   public int hashCode() {
      return Objects.hash(new Object[]{this.instrument, this.noteLevel});
   }
}
