package io.voltikor.lyra.mixin;

import io.voltikor.lyra.LyraController;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StringUtil;
import net.minecraft.client.gui.screens.ChatScreen;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({ChatScreen.class})
public abstract class ChatScreenMixin {
   @Inject(
      method = {"handleChatInput"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void Lyra$handleChatInput(String msg, boolean addToRecent, CallbackInfo ci) {
      String normalized = StringUtil.trimChatMessage(StringUtils.normalizeSpace(msg.trim()));
      if (!normalized.isEmpty()) {
         Minecraft client = Minecraft.getInstance();
         if (LyraController.get().handleChatCommand(client, normalized)) {
            if (addToRecent && client != null) {
               client.gui.getChat().addRecentChat(normalized);
            }

            ci.cancel();
         }
      }
   }
}
