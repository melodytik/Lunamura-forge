package org.spigotmc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 经典 Spigot Entity Activation Range（Aikar, GPL-3.0）。
 * 2026-08-27 回退：2.0 增强版（ServerCore 移植）在 Mohist patcher 环境下引发生物冻结，
 * 恢复为与 spigot-patches/0012 完全一致的语义 —— 范围外实体每 20 tick 无条件获得一轮完整 tick 保底。
 */
public class ActivationRange {

    public enum ActivationType {
        MONSTER,
        ANIMAL,
        RAIDER,
        MISC,
        WATER;

        AABB boundingBox = new AABB(0, 0, 0, 0, 0, 0);
    }

    static AABB maxBB = new AABB(0, 0, 0, 0, 0, 0);

    /**
     * Initializes an entities type on construction to specify what group this
     * entity is in for activation ranges.
     */
    public static ActivationType initializeEntityActivationType(Entity entity) {
        if (entity instanceof Raider) {
            return ActivationType.RAIDER;
        } else if (entity instanceof Monster || entity instanceof Slime) {
            return ActivationType.MONSTER;
        } else if (entity instanceof net.minecraft.world.entity.PathfinderMob || entity instanceof AmbientCreature) {
            // Mirrors vanilla's check for types that don't move or run goal selectors (spider = PathfinderMob)
            return ActivationType.ANIMAL;
        } else if (entity instanceof WaterAnimal) {
            return ActivationType.WATER;
        } else {
            return ActivationType.MISC;
        }
    }

    /**
     * These entities are excluded from Activation range checks.
     */
    public static boolean initializeEntityActivationState(Entity entity, SpigotWorldConfig config) {
        if ((entity.activationType == ActivationType.MISC && config.miscActivationRange == 0)
                || (entity.activationType == ActivationType.RAIDER && config.raiderActivationRange == 0)
                || (entity.activationType == ActivationType.ANIMAL && config.animalActivationRange == 0)
                || (entity.activationType == ActivationType.MONSTER && config.monsterActivationRange == 0)
                || entity instanceof Player
                || entity instanceof ThrownTrident
                || entity instanceof EnderDragon
                || entity instanceof EnderDragonPart
                || entity instanceof WitherBoss
                || entity instanceof AbstractHurtingProjectile
                || entity instanceof LightningBolt
                || entity instanceof PrimedTnt
                || entity instanceof EndCrystal
                || entity instanceof FireworkRocketEntity) {
            return true;
        }

        return false;
    }

    /**
     * Find what entities are in range of the players in the world and set
     * active if in range.
     */
    public static void activateEntities(Level world) {
        int miscActivationRange = world.spigotConfig.miscActivationRange;
        int raiderActivationRange = world.spigotConfig.raiderActivationRange;
        int animalActivationRange = world.spigotConfig.animalActivationRange;
        int monsterActivationRange = world.spigotConfig.monsterActivationRange;
        int waterActivationRange = world.spigotConfig.miscActivationRange;

        int maxRange = Math.max(monsterActivationRange, animalActivationRange);
        maxRange = Math.max(maxRange, raiderActivationRange);
        maxRange = Math.max(maxRange, miscActivationRange);
        maxRange = Math.min((world.spigotConfig.viewDistance << 4) - 8, maxRange);

        for (Player player : world.players()) {
            player.activatedTick = MinecraftServer.currentTick;
            if (world.spigotConfig.ignoreSpectatorActivation && player.isSpectator()) {
                continue;
            }

            maxBB = player.getBoundingBox().inflate(maxRange, 256, maxRange);
            ActivationType.MISC.boundingBox = player.getBoundingBox().inflate(miscActivationRange, 256, miscActivationRange);
            ActivationType.RAIDER.boundingBox = player.getBoundingBox().inflate(raiderActivationRange, 256, raiderActivationRange);
            ActivationType.ANIMAL.boundingBox = player.getBoundingBox().inflate(animalActivationRange, 256, animalActivationRange);
            ActivationType.MONSTER.boundingBox = player.getBoundingBox().inflate(monsterActivationRange, 256, monsterActivationRange);
            ActivationType.WATER.boundingBox = player.getBoundingBox().inflate(waterActivationRange, 256, waterActivationRange);

            world.getEntities().get(maxBB, ActivationRange::activateEntity);
        }
    }

    /**
     * Checks for the activation state of all entities in this chunk.
     */
    private static void activateEntity(Entity entity) {
        if (MinecraftServer.currentTick > entity.activatedTick) {
            if (entity.defaultActivationState) {
                entity.activatedTick = MinecraftServer.currentTick;
                return;
            }
            if (entity.activationType.boundingBox.intersects(entity.getBoundingBox())) {
                entity.activatedTick = MinecraftServer.currentTick;
            }
        }
    }

    /**
     * If an entity is not in range, do some more checks to see if we should
     * give it a shot.
     */
    public static boolean checkEntityImmunities(Entity entity) {
        // quick checks.
        if (entity.wasTouchingWater || entity.getRemainingFireTicks() > 0) {
            return true;
        }
        if (!(entity instanceof AbstractArrow)) {
            if (!entity.onGround() || !entity.passengers.isEmpty() || entity.isPassenger()) {
                return true;
            }
        } else if (!((AbstractArrow) entity).inGround) {
            return true;
        }
        // special cases.
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            if (living.hurtTime > 0 || !living.getActiveEffects().isEmpty()) {
                return true;
            }
            if (entity instanceof Mob && ((Mob) entity).getTarget() != null) {
                return true;
            }
            if (entity instanceof Villager && ((Villager) entity).canBreed()) {
                return true;
            }
            if (entity instanceof Animal) {
                Animal animal = (Animal) entity;
                if (animal.isBaby() || animal.isInLove()) {
                    return true;
                }
                if (entity instanceof Sheep && ((Sheep) entity).isSheared()) {
                    return true;
                }
            }
            if (entity instanceof net.minecraft.world.entity.monster.Creeper && ((net.minecraft.world.entity.monster.Creeper) entity).isIgnited()) { // isExplosive
                return true;
            }
        }
        // SPIGOT-6644: Otherwise the target refresh tick will be missed
        if (entity instanceof ExperienceOrb) {
            return true;
        }
        if (entity instanceof ItemEntity && ((ItemEntity) entity).hasPickUpDelay()) {
            return false; // hasPickUpDelay + moving item check below is redundant
        }
        return false;
    }

    /**
     * Checks if the entity is active for this tick.
     */
    public static boolean checkIfActive(Entity entity) {
        // Never safe to skip fireworks or entities not yet added to chunk
        if (entity instanceof FireworkRocketEntity) {
            return true;
        }

        boolean isActive = entity.activatedTick >= MinecraftServer.currentTick || entity.defaultActivationState;

        // Should this entity tick?
        if (!isActive) {
            if ((MinecraftServer.currentTick - entity.activatedTick - 1) % 20 == 0) {
                // Check immunities every 20 ticks.
                if (checkEntityImmunities(entity)) {
                    // Triggered some sort of immunity, give 20 full ticks before we check again.
                    entity.activatedTick = MinecraftServer.currentTick + 20;
                }
                isActive = true;
            }
            // Add a little performance juice to active entities. Skip 1/4 if not immune.
        } else if (!entity.defaultActivationState && entity.tickCount % 4 == 0 && !checkEntityImmunities(entity)) {
            isActive = false;
        }
        return isActive;
    }
}
