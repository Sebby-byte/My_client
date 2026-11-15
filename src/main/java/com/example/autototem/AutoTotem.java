package com.example.autototem;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AutoTotem implements ClientModInitializer {

    private static final int MAX_DELAY = 10; // ticks per step (1 tick = 50 ms)

    private enum Stage { IDLE, OPEN, SWAP, CLOSE }

    private static boolean enabled = false;
    private static int delay = 2;
    private static Stage stage = Stage.IDLE;
    private static int wait = 0;
    private static int lastCount = -1;

    private static KeyMapping toggleKey;
    private static KeyMapping settingsKey;
    private static KeyMapping equipKey;

    private static final Path CONFIG =
            FabricLoader.getInstance().getConfigDir().resolve("autototem.properties");

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        stage = Stage.IDLE;
        lastCount = -1;
    }

    public static Screen settingsScreen() {
        return new SettingsScreen();
    }

    @Override
    public void onInitializeClient() {
        System.out.println("[AutoTotem] 1.1.0 loaded");
        loadConfig();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("autototem", "main"));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autototem.toggle", 71, category));   // G
        settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autototem.settings", 72, category)); // H
        equipKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autototem.equip", 86, category)); // V

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            stage = Stage.IDLE;
            lastCount = -1;
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("Auto Totem: " + (enabled ? "ON" : "OFF")));
            }
        }
        while (settingsKey.consumeClick()) {
            if (mc.gui.screen() == null) mc.gui.setScreen(new SettingsScreen());
        }

        while (equipKey.consumeClick()) {
            if (mc.player != null && mc.gameMode != null && mc.gui.screen() == null) {
                equipTotem(mc, mc.player);
            }
        }

        LocalPlayer p = mc.player;
        if (!enabled || p == null || mc.gameMode == null) {
            stage = Stage.IDLE;
            lastCount = -1;
            return;
        }

        if (stage != Stage.IDLE && wait > 0) {
            wait--;
            return;
        }

        switch (stage) {
            case IDLE -> {
                // Don't watch while a screen is open (moving items by hand changes the count)
                if (mc.gui.screen() != null) {
                    lastCount = -1;
                    return;
                }
                int count = countTotems(p);
                boolean popped = lastCount > count && !p.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
                lastCount = count;
                if (popped && count > 0) {
                    stage = Stage.OPEN;
                    wait = delay;
                }
            }
            case OPEN -> {
                mc.gui.setScreen(new InventoryScreen(p));
                stage = Stage.SWAP;
                wait = delay;
            }
            case SWAP -> {
                if (!(mc.gui.screen() instanceof InventoryScreen)) {
                    stage = Stage.IDLE;
                    lastCount = -1;
                    return;
                }
                int slot = findTotem(p);
                if (slot >= 0) {
                    // player inventory menu: 9-35 = main inventory, 36-44 = hotbar
                    int menuSlot = slot < 9 ? 36 + slot : slot;
                    // button 40 = swap with the offhand
                    mc.gameMode.handleContainerInput(
                            p.inventoryMenu.containerId, menuSlot, 40, ContainerInput.SWAP, p);
                }
                stage = Stage.CLOSE;
                wait = delay;
            }
            case CLOSE -> {
                p.closeContainer();
                stage = Stage.IDLE;
                lastCount = countTotems(p);
            }
        }
    }

    private static void equipTotem(Minecraft mc, LocalPlayer p) {
        if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            p.sendSystemMessage(Component.literal("Totem already in offhand"));
            return;
        }
        int slot = findTotem(p);
        if (slot < 0) {
            p.sendSystemMessage(Component.literal("No totem in your inventory"));
            return;
        }
        int menuSlot = slot < 9 ? 36 + slot : slot;
        mc.gameMode.handleContainerInput(
                p.inventoryMenu.containerId, menuSlot, 40, ContainerInput.SWAP, p);
    }

    private static int findTotem(LocalPlayer p) {
        for (int i = 9; i < 36; i++) {
            if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private static int countTotems(LocalPlayer p) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            var s = p.getInventory().getItem(i);
            if (s.is(Items.TOTEM_OF_UNDYING)) n += s.getCount();
        }
        var off = p.getOffhandItem();
        if (off.is(Items.TOTEM_OF_UNDYING)) n += off.getCount();
        return n;
    }

    // ---------- config ----------

    private static void loadConfig() {
        try {
            if (Files.exists(CONFIG)) {
                Properties props = new Properties();
                try (var in = Files.newInputStream(CONFIG)) { props.load(in); }
                int v = Integer.parseInt(props.getProperty("delay", "2").trim());
                delay = Math.max(0, Math.min(MAX_DELAY, v));
            }
        } catch (Exception ignored) { }
    }

    private static void saveConfig() {
        try {
            Properties props = new Properties();
            props.setProperty("delay", Integer.toString(delay));
            try (var out = Files.newOutputStream(CONFIG)) { props.store(out, "Auto Totem"); }
        } catch (Exception ignored) { }
    }

    // ---------- settings screen ----------

    private static class DelaySlider extends AbstractSliderButton {
        DelaySlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), delay / (double) MAX_DELAY);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int t = (int) Math.round(this.value * MAX_DELAY);
            setMessage(Component.literal("Delay per step: " + t + (t == 1 ? " tick" : " ticks")
                    + " (" + (t * 50) + " ms)"));
        }

        @Override
        protected void applyValue() {
            delay = (int) Math.round(this.value * MAX_DELAY);
        }
    }

    private static class SettingsScreen extends Screen {
        SettingsScreen() {
            super(Component.literal("Auto Totem"));
        }

        @Override
        protected void init() {
            addRenderableWidget(new DelaySlider(this.width / 2 - 100, this.height / 2 - 22, 200, 20));
            addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                    .bounds(this.width / 2 - 100, this.height / 2 + 6, 200, 20)
                    .build());
        }

        @Override
        public void onClose() {
            saveConfig();
            super.onClose();
        }
    }
}
