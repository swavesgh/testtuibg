package com.example.autohit;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public class AutoHitScreen extends Screen {
    // Palette
    private static final int ACCENT = 0x4C8DFF;
    private static final int GREEN = 0x3DDC84;
    private static final int RED = 0xE5484D;
    private static final int MUTED = 0x9AA0A6;
    private static final int TEXT = 0xF1F3F4;

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 236;
    private static final int ROW_H = 32;
    private static final int CTRL_W = 112;

    private static final String[] TABS = {"General", "Combat", "Ghost Hits", "Focus"};
    private static int selectedTab = 0; // remembered between openings

    private final Screen parent;
    private int left, top;

    public AutoHitScreen(Screen parent) {
        super(Component.literal("AutoHit"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AutoHitConfig c = AutoHitConfig.INSTANCE;
        left = (this.width - PANEL_W) / 2;
        top = Math.max(6, (this.height - PANEL_H) / 2);

        // Header
        MutableComponent title = Component.literal("AutoHit")
                .withStyle(s -> s.withColor(ACCENT).withBold(true));
        addRenderableWidget(new StringWidget(left + 12, top + 10, 150, 10, title, this.font).alignLeft());
        addRenderableWidget(new StringWidget(left + PANEL_W - 162, top + 10, 150, 10,
                muted("Minecraft 26.2  ·  Fabric"), this.font).alignRight());

        // Tabs
        int tabW = (PANEL_W - 24 - (TABS.length - 1) * 4) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            boolean active = i == selectedTab;
            Component label = active
                    ? Component.literal(TABS[i]).withStyle(s -> s.withColor(ACCENT).withBold(true))
                    : Component.literal(TABS[i]).withStyle(s -> s.withColor(TEXT));
            addRenderableWidget(Button.builder(label, b -> {
                selectedTab = idx;
                this.rebuildWidgets();
            }).bounds(left + 12 + i * (tabW + 4), top + 28, tabW, 20).build());
        }

        // Page content
        int y = top + 58;
        switch (selectedTab) {
            case 0 -> {
                y = row(y, "Auto Attack", "Master switch. Hit what you're aiming at.",
                        toggle(() -> c.enabled, v -> c.enabled = v));
                y = row(y, "Players Only", "Ignore mobs and other entities.",
                        toggle(() -> c.playersOnly, v -> c.playersOnly = v));
                y = row(y, "Pause While Using Item", "Don't swing while eating or blocking.",
                        toggle(() -> c.pauseWhileUsingItem, v -> c.pauseWhileUsingItem = v));
                y = row(y, "Reach", "Max distance to the point you're aiming at.",
                        slider("Reach", 1.0, 6.0, 0.1, c.reach, v -> c.reach = v, v -> String.format("%.1f blocks", v)));
            }
            case 1 -> {
                y = row(y, "Prefer Crits", "Take a crit over a normal hit when you can.",
                        toggle(() -> c.preferCrit, v -> c.preferCrit = v));
                y = row(y, "Avoid Sweep Hits", "Hold off if a sword sweep would hit others.",
                        toggle(() -> c.avoidSweep, v -> c.avoidSweep = v));
                y = row(y, "Max Extra Delay", "Longest a hit can be held for the above.",
                        slider("Delay", 0, 10, 1, c.maxExtraDelayTicks, v -> c.maxExtraDelayTicks = v,
                                v -> (int) v + " ticks (" + (int) (v * 50) + "ms)"));
            }
            case 2 -> {
                y = row(y, "Prevent Ghost Hits", "Wait for a charged, valid hit. Off = swing every tick.",
                        toggle(() -> c.preventGhostHits, v -> c.preventGhostHits = v));
                y = row(y, "Min Cooldown", "Attack charge needed before swinging.",
                        slider("Charge", 0.0, 1.0, 0.01, c.cooldownThreshold, v -> c.cooldownThreshold = v,
                                v -> (int) Math.round(v * 100) + "%"));
                y = row(y, "Skip Hurt Targets", "Skip players still in their hurt flash.",
                        toggle(() -> c.skipHurtTargets, v -> c.skipHurtTargets = v));
            }
            case 3 -> {
                y = row(y, "Focus Mode", "Only hit the last player you hit.",
                        toggle(() -> c.focusMode, v -> c.focusMode = v));
                y = row(y, "Focus Timeout", "Drop focus after this long without a hit.",
                        slider("Timeout", 1.0, 30.0, 0.5, c.focusTimeoutSeconds, v -> c.focusTimeoutSeconds = v,
                                v -> String.format("%.1f s", v)));
            }
            default -> { }
        }

        // Footer
        int fy = top + PANEL_H - 30;
        addRenderableWidget(Button.builder(Component.literal("Reset Defaults")
                        .withStyle(s -> s.withColor(MUTED)), b -> {
                    AutoHitConfig.INSTANCE = new AutoHitConfig();
                    this.rebuildWidgets();
                }).bounds(left + 12, fy, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done")
                        .withStyle(s -> s.withColor(ACCENT).withBold(true)), b -> onClose())
                .bounds(left + PANEL_W - 12 - 110, fy, 110, 20).build());
    }

    /** Lays out one settings row: label + description on the left, control on the right. */
    private int row(int y, String label, String description, net.minecraft.client.gui.components.AbstractWidget control) {
        addRenderableWidget(new StringWidget(left + 14, y + 4, PANEL_W - CTRL_W - 40, 10,
                Component.literal(label).withStyle(s -> s.withColor(TEXT)), this.font).alignLeft());
        addRenderableWidget(new StringWidget(left + 14, y + 16, PANEL_W - CTRL_W - 40, 10,
                muted(description), this.font).alignLeft());
        control.setX(left + PANEL_W - CTRL_W - 14);
        control.setY(y + (ROW_H - 20) / 2 - 1);
        addRenderableWidget(control);
        return y + ROW_H;
    }

    private static MutableComponent muted(String s) {
        return Component.literal(s).withStyle(st -> st.withColor(MUTED));
    }

    private static MutableComponent state(boolean on) {
        return Component.literal(on ? "● ON" : "○ OFF")
                .withStyle(s -> s.withColor(on ? GREEN : RED).withBold(true));
    }

    private Button toggle(BooleanSupplier get, Consumer<Boolean> set) {
        return Button.builder(state(get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(state(get.getAsBoolean()));
        }).bounds(0, 0, CTRL_W, 20).build();
    }

    private Slider slider(String name, double min, double max, double step, double cur,
                          DoubleConsumer set, DoubleFunction<String> fmt) {
        return new Slider(CTRL_W, 20, min, max, step, cur, set, fmt);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Background + all widgets
        super.extractRenderState(g, mouseX, mouseY, delta);

        int right = left + PANEL_W, bottom = top + PANEL_H;
        int border = 0x55FFFFFF;

        // Accent strip + panel outline
        g.fill(left, top, right, top + 2, 0xFF000000 | ACCENT);
        g.fill(left, top, left + 1, bottom, border);
        g.fill(right - 1, top, right, bottom, border);
        g.fill(left, bottom - 1, right, bottom, border);

        // Active tab underline
        int tabW = (PANEL_W - 24 - (TABS.length - 1) * 4) / TABS.length;
        int tx = left + 12 + selectedTab * (tabW + 4);
        g.fill(tx, top + 49, tx + tabW, top + 51, 0xFF000000 | ACCENT);

        // Row dividers
        int rows = switch (selectedTab) { case 3 -> 2; case 1, 2 -> 3; default -> 4; };
        for (int i = 1; i <= rows; i++) {
            int dy = top + 58 + i * ROW_H - 1;
            g.fill(left + 10, dy, right - 10, dy + 1, 0x22FFFFFF);
        }

        // Footer divider
        g.fill(left + 10, bottom - 36, right - 10, bottom - 35, 0x33FFFFFF);
    }

    @Override
    public void onClose() {
        AutoHitConfig.save();
        this.minecraft.setScreenAndShow(parent);
    }

    private static class Slider extends AbstractSliderButton {
        private final double min, max, step;
        private final DoubleConsumer onChange;
        private final DoubleFunction<String> fmt;
        private boolean ready;

        Slider(int w, int h, double min, double max, double step, double current,
               DoubleConsumer onChange, DoubleFunction<String> fmt) {
            super(0, 0, w, h, Component.empty(), (current - min) / (max - min));
            this.min = min;
            this.max = max;
            this.step = step;
            this.onChange = onChange;
            this.fmt = fmt;
            this.ready = true;
            updateMessage();
        }

        private double real() {
            double v = min + this.value * (max - min);
            if (step > 0) v = Math.round(v / step) * step;
            return Math.max(min, Math.min(max, v));
        }

        @Override
        protected void updateMessage() {
            if (!ready) return;
            setMessage(Component.literal(fmt.apply(real())).withStyle(s -> s.withColor(TEXT)));
        }

        @Override
        protected void applyValue() {
            onChange.accept(real());
        }
    }
}
