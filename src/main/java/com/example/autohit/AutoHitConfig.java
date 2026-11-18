package com.example.autohit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoHitConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("autohit.json");

    public static AutoHitConfig INSTANCE = new AutoHitConfig();

    // --- General ---
    public boolean enabled = true;
    public boolean playersOnly = true;
    public boolean pauseWhileUsingItem = true;
    /** Max distance from your eyes to the point you're aiming at on the target. */
    public double reach = 3.0;

    // --- Hit quality ---
    /** Take a critical hit over a normal hit when a crit opportunity exists. */
    public boolean preferCrit = true;
    /** Avoid sword sweep hits that would also hit bystanders. */
    public boolean avoidSweep = false;
    /** Max ticks (20 = 1s) the mod may hold a ready hit while waiting for a better one. */
    public double maxExtraDelayTicks = 6.0;

    // --- Ghost hit prevention ---
    /** ON: waits for cooldown + skips targets in damage immunity. OFF: swings every tick. */
    public boolean preventGhostHits = true;
    /** Attack cooldown charge required (0.0 - 1.0). */
    public double cooldownThreshold = 0.95;
    public boolean skipHurtTargets = true;

    // --- Focus mode ---
    public boolean focusMode = false;
    /** Seconds without hitting the focus target before focus is dropped. */
    public double focusTimeoutSeconds = 8.0;

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    AutoHitConfig loaded = GSON.fromJson(r, AutoHitConfig.class);
                    if (loaded != null) INSTANCE = loaded;
                }
            }
        } catch (Exception e) {
            System.err.println("[AutoHit] Failed to load config: " + e);
        }
        save();
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            System.err.println("[AutoHit] Failed to save config: " + e);
        }
    }
}
