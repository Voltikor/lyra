package io.voltikor.lyra.noteblock;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class NoteBlockTargetResolverTest extends MinecraftTestSupport {
   private final NoteBlockTargetResolver resolver = new NoteBlockTargetResolver();

   @Test
   void reachableFacesUseTheirCenters() {
      for (Direction face : Direction.values()) {
         Vec3 point = resolver.optimalTarget(BlockPos.ZERO, face, .5, .5, .5, 4.5);
         assertEquals(.5 + face.getStepX() * .5, point.x, 1e-12);
         assertEquals(.5 + face.getStepY() * .5, point.y, 1e-12);
         assertEquals(.5 + face.getStepZ() * .5, point.z, 1e-12);
      }
   }

   @Test
   void nearReachLimitMovesFromCenterTowardClosestPoint() {
      Vec3 point = resolver.optimalTarget(BlockPos.ZERO, Direction.UP, 4.8, 1, .5, 4.0);
      assertEquals(1, point.y);
      assertEquals(.5, point.z);
      assertTrue(point.x > .5 && point.x < 1);
      assertTrue(new Vec3(4.8, 1, .5).distanceTo(point) <= 3.95);
      assertEquals(.85009765625, point.x, 1e-12);
   }

   @Test
   void outOfReachFallsBackToClosestPoint() {
      assertEquals(new Vec3(1, 1, .5), resolver.optimalTarget(BlockPos.ZERO, Direction.UP, 8, 1, .5, 4));
   }

   @Test
   void closestFaceAndAboveBlockOverrideArePreserved() {
      var east = resolver.closestFaceTarget(BlockPos.ZERO, 3, .5, .5, 0, 4.5);
      assertEquals(Direction.EAST, east.face());
      assertEquals(new Vec3(1, .5, .5), east.point());
      var above = resolver.closestFaceTarget(BlockPos.ZERO, 3, 3.6, .5, 2, 4.5);
      assertEquals(Direction.UP, above.face());
      assertEquals(new Vec3(.5, 1, .5), above.point());
   }

   @Test
   void tuningWrapsAtTwentyFiveAndBudgetsRemainBounded() {
      for (int from = 0; from < 25; from++) {
         for (int to = 0; to < 25; to++) {
            int hits = NoteBlockTuner.calcHits(from, to);
            assertTrue(hits >= 0 && hits < 25);
            assertEquals(to, (from + hits) % 25);
         }
      }
      assertEquals(.5f, NoteBlockTuner.pitchForNoteLevel(0));
      assertEquals(1f, NoteBlockTuner.pitchForNoteLevel(12));
      assertEquals(2f, NoteBlockTuner.pitchForNoteLevel(24));
      var settings = new LyraSettings();
      var tuner = new NoteBlockTuner();
      settings.setConcurrentTuneBlocks(0);
      assertEquals(Integer.MAX_VALUE, tuner.packetClickBudget(settings));
      settings.setConcurrentTuneBlocks(-1);
      assertEquals(1, tuner.packetClickBudget(settings));
      settings.setConcurrentTuneBlocks(30);
      assertEquals(20, tuner.packetClickBudget(settings));
   }
}
