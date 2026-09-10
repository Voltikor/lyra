package io.voltikor.lyra.mixin;

import io.voltikor.lyra.LyraController;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({Gui.class})
public abstract class InGameHudMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void Lyra$render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      LyraController.get().renderHud(graphics, Minecraft.getInstance());
   }
}
