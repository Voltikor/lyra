package io.voltikor.lyra.noteblock;

import io.voltikor.lyra.command.LyraMessenger;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public final class PlayerCenterer {
   private boolean isCentering;
   private double centeringTargetX;
   private double centeringTargetZ;
   private int centeringWaitTicks;

   public void centerPlayer(Minecraft client) {
      if (client != null && client.player != null) {
         this.centeringTargetX = Mth.floor(client.player.getX()) + 0.5D;
         this.centeringTargetZ = Mth.floor(client.player.getZ()) + 0.5D;
         this.isCentering = true;
         this.centeringWaitTicks = 0;
      }
   }

   public void tickCentering(Minecraft client) {
      if (!this.isCentering || client == null || client.player == null) {
         return;
      }

      if (this.centeringWaitTicks > 0) {
         --this.centeringWaitTicks;
         return;
      }

      double currentX = client.player.getX();
      double currentY = client.player.getY();
      double currentZ = client.player.getZ();

      double dx = this.centeringTargetX - currentX;
      double dz = this.centeringTargetZ - currentZ;
      double dist = Math.hypot(dx, dz);

      double stepSize = 0.020D;

      if (dist <= stepSize) {
         client.player.setPos(this.centeringTargetX, currentY, this.centeringTargetZ);
         if (client.getConnection() != null) {
            client.getConnection().send(new ServerboundMovePlayerPacket.Pos(client.player.position(), client.player.onGround(), client.player.horizontalCollision));
         }
         this.isCentering = false;
         LyraMessenger.replyFormatted(client, "Moved to block center.");
      } else {
         double nextX = currentX + (dx / dist) * stepSize;
         double nextZ = currentZ + (dz / dist) * stepSize;

         client.player.setPos(nextX, currentY, nextZ);
         if (client.getConnection() != null) {
            client.getConnection().send(new ServerboundMovePlayerPacket.Pos(client.player.position(), client.player.onGround(), client.player.horizontalCollision));
         }
         this.centeringWaitTicks = 10;
      }
   }

   public void reset() {
      this.isCentering = false;
      this.centeringWaitTicks = 0;
   }
}
