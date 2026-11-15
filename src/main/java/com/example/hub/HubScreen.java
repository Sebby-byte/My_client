package com.example.hub;

import com.example.anchormacro.AnchorMacro;
import com.example.autoattack.AutoAttackClient;
import com.example.autoattack.AutoAttackSettingsScreen;
import com.example.bevision.BlockFinderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class HubScreen extends Screen {

    private static final String BE_VISION = "com.example.bevision.BEVisionClient";
    private static final String HITBOXES = "com.example.invisiblehitboxes.InvisibleHitboxesClient";

    private record Module(String name, BooleanSupplier on, Runnable toggle, Supplier<Screen> settings) { }

    private final List<Module> modules = new ArrayList<>();
    private final List<Button> toggleButtons = new ArrayList<>();

    public HubScreen() {
        super(Component.literal("My Client"));

        modules.add(new Module("Anchor Macro",
                AnchorMacro::isEnabled,
                () -> AnchorMacro.setEnabled(!AnchorMacro.isEnabled()),
                AnchorMacro::settingsScreen));

        modules.add(new Module("Auto Click",
                AutoAttackClient::isAutoClick,
                () -> AutoAttackClient.toggleClick(Minecraft.getInstance()),
                AutoAttackSettingsScreen::new));

        modules.add(new Module("Auto Rotate",
                AutoAttackClient::isAutoRotate,
                () -> AutoAttackClient.toggleRotate(Minecraft.getInstance()),
                AutoAttackSettingsScreen::new));

        modules.add(new Module("Block Vision",
                () -> getFlag(BE_VISION),
                () -> setFlag(BE_VISION, !getFlag(BE_VISION)),
                BlockFinderScreen::new));

        modules.add(new Module("Invisible Hitboxes",
                () -> getFlag(HITBOXES),
                () -> setFlag(HITBOXES, !getFlag(HITBOXES)),
                null));
    }

    @Override
    protected void init() {
        toggleButtons.clear();

        int rowW = 190;
        int setW = 66;
        int gap = 4;
        int x = this.width / 2 - (rowW + gap + setW) / 2;
        int y = this.height / 2 - (modules.size() * 24) / 2;

        for (Module m : modules) {
            Button toggle = Button.builder(label(m), b -> {
                m.toggle().run();
                refresh();
            }).bounds(x, y, rowW, 20).build();
            addRenderableWidget(toggle);
            toggleButtons.add(toggle);

            if (m.settings() != null) {
                addRenderableWidget(Button.builder(Component.literal("Settings"),
                        b -> this.minecraft.gui.setScreen(m.settings().get()))
                        .bounds(x + rowW + gap, y, setW, 20).build());
            }
            y += 24;
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 60, y + 8, 120, 20).build());
    }

    private static Component label(Module m) {
        return Component.literal(m.name() + ": " + (m.on().getAsBoolean() ? "ON" : "OFF"));
    }

    private void refresh() {
        for (int i = 0; i < toggleButtons.size(); i++) {
            toggleButtons.get(i).setMessage(label(modules.get(i)));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        MutableComponent title = Component.literal("My Client");
        int ty = this.height / 2 - (modules.size() * 24) / 2 - 24;
        graphics.text(this.font, (Component) title,
                this.width / 2 - this.font.width((FormattedText) title) / 2, ty, -1, true);
    }

    // BE Vision and Invisible Hitboxes keep their on/off state in a private field, so read it directly.
    private static boolean getFlag(String className) {
        try {
            Field f = Class.forName(className).getDeclaredField("enabled");
            f.setAccessible(true);
            return f.getBoolean(null);
        } catch (Exception e) {
            return false;
        }
    }

    private static void setFlag(String className, boolean value) {
        try {
            Field f = Class.forName(className).getDeclaredField("enabled");
            f.setAccessible(true);
            f.setBoolean(null, value);
        } catch (Exception ignored) { }
    }
}
