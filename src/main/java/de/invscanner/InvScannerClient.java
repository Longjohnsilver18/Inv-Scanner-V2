package de.invscanner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class InvScannerClient implements ClientModInitializer {
    // Das "Ä" einer deutschen Tastatur sitzt auf der Position von GLFW_KEY_APOSTROPHE.
    public static KeyBinding OPEN_KEY;
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("invscanner", "main"));

    @Override
    public void onInitializeClient() {
        Config.load();
        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.invscanner.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_APOSTROPHE, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ScanEngine.tick(client);
            while (OPEN_KEY.wasPressed()) {
                if (ScanEngine.running()) {
                    ScanEngine.cancel(client);
                } else if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new ScannerScreen());
                }
            }
        });
    }
}
