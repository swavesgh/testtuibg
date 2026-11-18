package com.example.autohit;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public class AutoHitClient implements ClientModInitializer {
    private static KeyMapping toggleKey;
    private static KeyMapping menuKey;

    /** The last player we hit (focus mode target). */
    private static Entity focusTarget;
    private static long lastHitGameTime;

    /** Ticks we've been holding a ready hit while waiting for a crit / non-sweep moment. */
    private static int waitTicks;

    @Override
    public void onInitializeClient() {
        AutoHitConfig.load();

        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("autohit", "main"));
        toggleKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.autohit.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
        menuKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.autohit.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));

        // Also remember players you hit manually, so focus mode follows your own hits too.
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide() && entity instanceof Player) {
                setFocus(entity, level.getGameTime());
            }
            return InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(AutoHitClient::tick);
    }

    private static void setFocus(Entity e, long gameTime) {
        focusTarget = e;
        lastHitGameTime = gameTime;
    }

    private static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) {
            focusTarget = null;
            waitTicks = 0;
            return;
        }
        AutoHitConfig cfg = AutoHitConfig.INSTANCE;

        while (menuKey.consumeClick()) {
            mc.gui.setScreen(new AutoHitScreen(mc.gui.screen()));
        }
        while (toggleKey.consumeClick()) {
            cfg.enabled = !cfg.enabled;
            AutoHitConfig.save();
        }

        // Drop stale focus
        if (focusTarget != null) {
            long age = mc.level.getGameTime() - lastHitGameTime;
            if (!focusTarget.isAlive() || focusTarget.isRemoved()
                    || age > cfg.focusTimeoutSeconds * 20.0
                    || p.distanceTo(focusTarget) > 16.0f) {
                focusTarget = null;
            }
        }

        // process() returns true only while we are deliberately holding a ready hit.
        if (!process(mc, p, cfg)) {
            waitTicks = 0;
        }
    }

    /** @return true if a hit was ready but is being held for a better one. */
    private static boolean process(Minecraft mc, LocalPlayer p, AutoHitConfig cfg) {
        if (!cfg.enabled || mc.gui.screen() != null || p.isSpectator()) return false;
        if (cfg.pauseWhileUsingItem && p.isUsingItem()) return false;

        // hitResult is what your crosshair actually touches, so a wall/block in front
        // of the player means this is a BlockHitResult and we never attack through it.
        HitResult hr = mc.hitResult;
        if (!(hr instanceof EntityHitResult ehr)) return false;

        Entity target = ehr.getEntity();
        if (target == p || !target.isAlive() || target.isSpectator()) return false;
        if (!(target instanceof LivingEntity living)) return false;
        if (cfg.playersOnly && !(target instanceof Player)) return false;

        if (p.getEyePosition().distanceTo(ehr.getLocation()) > cfg.reach) return false;

        // Focus mode: while a focus target exists, ignore everyone else.
        if (cfg.focusMode && focusTarget != null && target != focusTarget) return false;

        float charge = p.getAttackStrengthScale(0.0f);
        if (cfg.preventGhostHits) {
            if (charge < cfg.cooldownThreshold) return false;
            if (cfg.skipHurtTargets && living.hurtTime > 0) return false;
        }

        // --- Hit quality: prefer crits, avoid sweeps (bounded delay) ---
        if (charge >= 0.9f && !canCrit(p)) {
            boolean hold = (cfg.preferCrit && critComing(p))
                    || (cfg.avoidSweep && sweepWouldHit(mc, p, target));
            if (hold && waitTicks < Math.round(cfg.maxExtraDelayTicks)) {
                waitTicks++;
                return true;
            }
        }

        waitTicks = 0;
        mc.gameMode.attack(p, target);
        p.swing(InteractionHand.MAIN_HAND);

        if (target instanceof Player) {
            setFocus(target, mc.level.getGameTime());
        }
        return false;
    }

    private static boolean baseCritConditions(LocalPlayer p) {
        return !p.onGround()
                && !p.onClimbable()
                && !p.isInWater()
                && !p.hasEffect(MobEffects.BLINDNESS)
                && !p.isPassenger()
                && !p.isSprinting()
                && !p.getAbilities().flying;
    }

    /** Hitting right now would be a critical hit (airborne and falling). */
    private static boolean canCrit(LocalPlayer p) {
        return baseCritConditions(p) && p.getDeltaMovement().y < 0.0;
    }

    /** Airborne and still rising: a crit becomes available in a few ticks. */
    private static boolean critComing(LocalPlayer p) {
        return baseCritConditions(p) && p.getDeltaMovement().y > 0.0;
    }

    /** A normal ground hit with a sword would also sweep-hit someone else near the target. */
    private static boolean sweepWouldHit(Minecraft mc, LocalPlayer p, Entity target) {
        if (!p.onGround() || p.isSprinting()) return false;
        if (!p.getMainHandItem().is(ItemTags.SWORDS)) return false;
        return !mc.level.getEntitiesOfClass(
                LivingEntity.class,
                target.getBoundingBox().inflate(1.0, 0.25, 1.0),
                e -> e != p && e != target && e.isAlive() && !e.isSpectator()
        ).isEmpty();
    }
}
