package de.invscanner.mixin;

import de.invscanner.ScanEngine;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fängt das Öffnen des /invsee-Fensters ab, solange ein Scan läuft: Es wird dann kein Fenster
 * angezeigt, der Inhalt wird unsichtbar im Hintergrund gelesen.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onOpenScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private void invscanner$onOpenScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
        if (ScanEngine.captureOpen(packet)) ci.cancel();
    }
}
