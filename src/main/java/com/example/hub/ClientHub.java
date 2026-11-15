package com.example.hub;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Adds one key (Right Shift by default) that opens the menu for all the modules. */
public class ClientHub implements ClientModInitializer {

    private static KeyMapping menuKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("myclient", "main"));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.myclient.menu", 344, category)); // Right Shift

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.consumeClick()) {
                if (mc.player != null && mc.gui.screen() == null) {
                    mc.gui.setScreen(new HubScreen());
                }
            }
        });
    }
}
