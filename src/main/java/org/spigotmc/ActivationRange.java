package org.spigotmc;

import com.mohistmc.LunamuraConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Entity Activation Range (升级到 2.0, 移植自 ServerCore / Paper 系, 作者 Wesley1808 / Aikar, GPL-3.0)。
 * 相比原 Spigot 版，新增：按类型的 tick 间隔（非激活实体不再完全停，而是每隔 tickInterval 才 tick 一次）、
 * 垂直激活范围、定期唤醒、更细的免疫条件（村民恐慌/工作、目标选择器、动物繁殖等）、以及对非免疫实体的 1/4 跳 tick。
 * 配置开关：LunamuraConfig.perf_activation_range2（总开关）/ perf_activation_range2_vertical（垂直范围）。
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

    /** 2.0 激活类型：携带每类型激活半径、tick 间隔、唤醒间隔与垂直延伸。 */
    public static class ActivationType2 {
        public final int activationRange;
        public final int tickInterval;
        public final int wakeupInterval;
        public final boolean extraHeightUp;
        public final boolean extraHeightDown;

        public ActivationType2(int activationRange, int tickInterval, int wakeupInterval, boolean extraHeightUp, boolean extraHeightDown) {
            this.activationRange = activationRange;
            this.tickInterval = tickInterval;
            this.wakeupInterval = wakeupInterval;
            this.extraHeightUp = extraHeightUp;
            this.extraHeightDown = extraHeightDown;
        }
    }

    static AABB maxBB = new AABB(0, 0, 0, 0, 0, 0);
    private static final double MINIMUM_MOVEMENT = 0.001;
    private static final int DEFAULT_TICK_INTERVAL = 20;

    private static int cfgInt(int v, int def) {
        return v > 0 ? v : def;
    }

    /**
     * 实体初始化时分配其所属激活类型（半径取 Spigot 配置，间隔/唤醒取 Lunamura 配置）。
     */
    /**
     * Spigot 原版：分配实体所属激活类型（旧 enum），供 activationType 字段 / TrackingRange 使用。
     * 注意：此方法仅维护 Spigot 兼容，不参与 2.0 行为（2.0 走 activationRangeType）。
     */
    public static ActivationType initializeEntityActivationType(Entity entity) {
        if (entity instanceof Raider) {
            return ActivationType.RAIDER;
        } else if (entity instanceof net.minecraft.world.entity.monster.Monster || entity instanceof net.minecraft.world.entity.monster.Slime) {
            return ActivationType.MONSTER;
        } else if (entity instanceof net.minecraft.world.entity.PathfinderMob || entity instanceof net.minecraft.world.entity.ambient.AmbientCreature) {
            return ActivationType.ANIMAL;
        } else if (entity instanceof net.minecraft.world.entity.animal.WaterAnimal) {
            return ActivationType.WATER;
        } else {
            return ActivationType.MISC;
        }
    }

    /**
     * 实体初始化时分配其所属 2.0 激活类型（半径取 Spigot 配置，间隔/唤醒取 Lunamura 配置）。
     */
    public static ActivationType2 initializeEntityActivationType2(Entity entity) {
        int tickInterval = cfgInt(LunamuraConfig.perf_activation_range2_tick_interval, DEFAULT_TICK_INTERVAL);
        int wakeup = LunamuraConfig.perf_activation_range2_wakeup_interval;
        int animal = 16, monster = 32, raider = 48, misc = 16, water = 16;
        Level level = entity.level();
        if (level instanceof ServerLevel) {
            var sc = ((ServerLevel) level).spigotConfig;
            if (sc != null) {
                animal = sc.animalActivationRange;
                monster = sc.monsterActivationRange;
                raider = sc.raiderActivationRange;
                misc = sc.miscActivationRange;
                water = sc.miscActivationRange;
            }
        }
        if (entity instanceof Raider) {
            return new ActivationType2(raider, tickInterval, wakeup, true, false);
        } else if (entity instanceof net.minecraft.world.entity.monster.Monster
                || entity instanceof net.minecraft.world.entity.monster.Slime) {
            return new ActivationType2(monster, tickInterval, wakeup, true, false);
        } else if (entity instanceof net.minecraft.world.entity.PathfinderMob
                || entity instanceof net.minecraft.world.entity.ambient.AmbientCreature) {
            return new ActivationType2(animal, tickInterval, wakeup, false, false);
        } else if (entity instanceof net.minecraft.world.entity.animal.WaterAnimal) {
            return new ActivationType2(water, tickInterval, wakeup, false, false);
        } else {
            return new ActivationType2(misc, tickInterval, wakeup, false, false);
        }
    }

    /** Spigot 原版：根据 config 计算 defaultActivationState。 */
    public static boolean initializeEntityActivationState(Entity entity, org.spigotmc.SpigotWorldConfig config) {
        return entity.activationType != ActivationType.MISC;
    }

    /** 排除名单内的实体永远正常 tick，不受激活范围影响。 */
    public static boolean isExcluded(Entity entity) {
        ActivationType2 type = entity.activationRangeType;
        int tickInterval = type == null ? DEFAULT_TICK_INTERVAL : type.tickInterval;
        int range = type == null ? 16 : type.activationRange;
        if (tickInterval <= 1 || range <= 0) {
            return true;
        }
        if (entity instanceof Player
                || entity instanceof ThrownTrident
                || entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || entity instanceof net.minecraft.world.entity.boss.EnderDragonPart
                || entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || entity instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile
                || entity instanceof net.minecraft.world.entity.LightningBolt
                || entity instanceof PrimedTnt
                || entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal
                || entity instanceof net.minecraft.world.entity.projectile.FireworkRocketEntity) {
            return true;
        }
        if (!LunamuraConfig.perf_activation_range2_excluded.isEmpty()) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getKey(entity.getType()).toString();
            if (LunamuraConfig.perf_activation_range2_excluded.contains(id)) {
                return true;
            }
        }
        return false;
    }

    public static void activateEntities(Level world) {
        activateEntities((ServerLevel) world, MinecraftServer.currentTick);
    }

    /** 激活该世界中离玩家足够近的实体。 */
    public static void activateEntities(ServerLevel level, int currentTick) {
        if (!LunamuraConfig.perf_activation_range2) {
            return;
        }
        int maxRange = Integer.MIN_VALUE;
        var sc = level.spigotConfig;
        if (sc != null) {
            maxRange = Math.max(sc.animalActivationRange, sc.monsterActivationRange);
            maxRange = Math.max(maxRange, sc.raiderActivationRange);
            maxRange = Math.max(maxRange, sc.miscActivationRange);
        }
        if (maxRange == Integer.MIN_VALUE) {
            maxRange = 48;
        } else {
            maxRange = Math.min((sc.viewDistance << 4) - 8, maxRange);
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            AABB maxBB = player.getBoundingBox().inflate(maxRange, 256, maxRange);
            for (Entity entity : level.getEntities(player, maxBB)) {
                activateEntity(player, entity, currentTick);
            }
        }
    }

    private static void activateEntity(ServerPlayer player, Entity entity, int currentTick) {
        if (currentTick > entity.activationRangeActivatedTick) {
            if (entity.activationRangeExcluded || isWithinRange(player, entity)) {
                entity.activationRangeActivatedTick = currentTick + 19;
            }
        }
    }

    private static boolean isWithinRange(ServerPlayer player, Entity entity) {
        ActivationType2 type = entity.activationRangeType;
        if (type == null) {
            return false;
        }
        int range = type.activationRange;
        int chessboardDistance = Math.max(
                Math.abs(player.getBlockX() - entity.getBlockX()),
                Math.abs(player.getBlockZ() - entity.getBlockZ()));
        if (chessboardDistance > range) {
            return false;
        }
        if (LunamuraConfig.perf_activation_range2_vertical) {
            int deltaY = entity.getBlockY() - player.getBlockY();
            return deltaY <= range && deltaY >= -range
                    || (deltaY > 0 && type.extraHeightUp)
                    || (deltaY < 0 && type.extraHeightDown);
        }
        return true;
    }

    public static boolean checkIfActive(Entity entity) {
        return checkIfActive(entity, MinecraftServer.currentTick);
    }

    /** 判定实体是否处于激活状态；非激活时按 tickInterval 间隔 tick，并每秒检查一次免疫条件。 */
    public static boolean checkIfActive(Entity entity, int currentTick) {
        if (!LunamuraConfig.perf_activation_range2) {
            return true;
        }
        if (entity.activationRangeExcluded) {
            return true;
        }
        if (shouldTick(entity, currentTick)) {
            entity.activationRangeActivatedTick = currentTick;
            return true;
        }
        boolean active = entity.activationRangeActivatedTick >= currentTick;
        if (!active) {
            int inactiveTicks = currentTick - entity.activationRangeActivatedTick - 1;
            if (inactiveTicks % 20 == 0) {
                int immunity = checkEntityImmunities(entity, currentTick);
                if (immunity >= 0) {
                    entity.activationRangeActivatedTick = currentTick + immunity;
                    return true;
                }
            }
            ActivationType2 type = entity.activationRangeType;
            int tickInterval = type == null ? DEFAULT_TICK_INTERVAL : type.tickInterval;
            if (tickInterval > 0 && inactiveTicks % tickInterval == 0) {
                return true;
            }
        } else if (LunamuraConfig.perf_activation_range2_skip_non_immune
                && entity.activationRangeFullTickCount % 4 == 0
                && checkEntityImmunities(entity, currentTick) < 0) {
            return false;
        }
        return active;
    }

    private static boolean shouldTick(Entity entity, int currentTick) {
        if (entity.activationRangeExcluded) {
            return true;
        }
        if (entity.isOnPortalCooldown()) {
            return true;
        }
        ActivationType2 type = entity.activationRangeType;
        if (entity.tickCount < 200 && (type == null || LunamuraConfig.perf_activation_range2_tick_new_entities)) {
            return true;
        }
        if (entity instanceof Mob mob && mob.isLeashed() && mob.getLeashHolder() instanceof Player) {
            return true;
        }
        if (entity instanceof LivingEntity living && living.hurtTime > 0) {
            return true;
        }
        return false;
    }

    private static int checkInactiveWakeup(Entity entity, int currentTick) {
        ActivationType2 type = entity.activationRangeType;
        if (type == null || type.wakeupInterval <= 0) {
            return -1;
        }
        if (currentTick - entity.activationRangeActivatedTick >= type.wakeupInterval * 20L) {
            return 100;
        }
        return -1;
    }

    /** 检查非激活实体的免疫条件，返回应保持免疫的 tick 数（-1 表示无免疫）。 */
    public static int checkEntityImmunities(Entity entity, int currentTick) {
        int wakeup = checkInactiveWakeup(entity, currentTick);
        if (wakeup > -1) {
            return wakeup;
        }
        if (entity.getRemainingFireTicks() > 0) {
            return 2;
        }
        if (entity.activationRangeActivatedImmunityTick >= currentTick) {
            return 1;
        }
        if (!entity.isAlive()) {
            return 40;
        }
        if (entity.isInWater() && entity.isPushedByFluid()
                && !(entity instanceof Animal || entity instanceof Villager
                || entity instanceof net.minecraft.world.entity.vehicle.Boat)) {
            return 100;
        }
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
            var movement = entity.getDeltaMovement();
            if (Math.abs(movement.x) > MINIMUM_MOVEMENT || Math.abs(movement.z) > MINIMUM_MOVEMENT
                    || movement.y > MINIMUM_MOVEMENT) {
                return 20;
            }
        }
        if (!(entity instanceof AbstractArrow projectile)) {
            if (!entity.onGround() && !entity.isInWater()
                    && !(entity instanceof net.minecraft.world.entity.FlyingMob
                    || entity instanceof net.minecraft.world.entity.ambient.Bat)) {
                return 10;
            }
        } else if (!projectile.inGround) {
            return 1;
        }
        if (entity instanceof LivingEntity living) {
            if (!living.getActiveEffects().isEmpty() || living.onClimbable()) {
                return 1;
            }
            if (living instanceof Mob mob) {
                if (mob.getTarget() != null
                        || mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
                    return 20;
                }
                if (living instanceof Villager villager) {
                    Brain<Villager> brain = villager.getBrain();
                    if (LunamuraConfig.perf_activation_range2_villager_tick_panic) {
                        for (Activity activity : new Activity[]{Activity.HIDE, Activity.PRE_RAID,
                                Activity.RAID, Activity.PANIC}) {
                            if (brain.isActive(activity)) {
                                return 100;
                            }
                        }
                    }
                    int after = LunamuraConfig.perf_activation_range2_villager_work_immunity_after;
                    if (after > 0 && (currentTick - entity.activationRangeActivatedTick) >= after) {
                        if (brain.isActive(Activity.WORK)) {
                            return LunamuraConfig.perf_activation_range2_villager_work_immunity_for;
                        }
                    }
                }
                if (living instanceof Animal animal) {
                    if (animal.isBaby() || animal.isInLove()) {
                        return 5;
                    }
                    if (animal instanceof Sheep sheep && sheep.isSheared()) {
                        return 1;
                    }
                }
                if (living instanceof Creeper creeper && creeper.isIgnited()) {
                    return 20;
                }
                if (hasTasks(mob.targetSelector, null)) {
                    return 0;
                }
            }
        }
        return -1;
    }

    public static boolean hasTasks(GoalSelector selector, java.util.function.Predicate<Goal> predicate) {
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (wrapped.isRunning() && (predicate == null || predicate.test(wrapped.getGoal()))) {
                return true;
            }
        }
        return false;
    }
}
