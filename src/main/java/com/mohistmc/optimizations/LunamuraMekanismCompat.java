package com.mohistmc.optimizations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lunamura compatibility layer for Mekanism / Mekanism:More Machine tier-installer upgrades.
 *
 * <p>Root cause of "the factory disappears when upgraded" (confirmed against Mekanism 10.4.16.80
 * {@code BlockBounding.onRemove} and server logs):
 * <ol>
 *   <li>{@code LevelChunk.setBlockState} writes the upgraded block into the section first.</li>
 *   <li>It then calls {@code oldState.onRemove}. For a bounding machine that removes the
 *       {@code mekanism:bounding_block} at {@code pos.above()}.</li>
 *   <li>{@code BlockBounding.onRemove} then sees that the main block is no longer air
 *       (it is already the upgraded factory) and calls {@code world.removeBlock(mainPos)} —
 *       destroying the just-upgraded machine.</li>
 *   <li>The subsequent "is the section still the new block?" check fails, so any hook placed
 *       after {@code onRemove} never runs. Logs therefore show {@code air <- advanced} with
 *       no {@code basic -> advanced} transition.</li>
 * </ol>
 *
 * <p>Fix: before {@code onRemove}, disarm existing bounding tiles ({@code setMainLocation(null)})
 * so they no longer destroy the main block. After {@code onRemove} has cleared the old bounding
 * blocks, place the new machine's bounding blocks (emulating {@code setPlacedBy}, which
 * {@code setBlockAndUpdate} never calls).
 *
 * <p>All Mekanism access is reflective. Failures degrade to a no-op and never break world logic.
 */
public final class LunamuraMekanismCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("LunamuraMekanismCompat");

    /** Mekanism and add-ons that reuse its attribute / bounding framework. */
    private static final Set<String> MEK_NAMESPACES = Set.of("mekanism", "mekmm", "mekanism_extras");

    private static final String CLASS_ATTRIBUTE = "mekanism.common.block.attribute.Attribute";
    private static final String CLASS_ATTR_HAS_BOUNDING = "mekanism.common.block.attribute.AttributeHasBounding";

    private static volatile boolean reflectionBroken = false;

    private LunamuraMekanismCompat() {
    }

    /**
     * Called from {@code LevelChunk.setBlockState} <em>before</em> {@code oldState.onRemove}.
     * Unbinds old bounding tiles so they will not destroy the already-written upgraded block.
     */
    public static void prepareUpgrade(Level level, BlockPos pos, BlockState newState, BlockState oldState) {
        if (!shouldHandle(level, pos, newState, oldState)) {
            return;
        }
        try {
            Object oldBounding = getAttribute(oldState.getBlock());
            if (oldBounding == null) {
                return;
            }
            List<BlockPos> positions = getBoundingPositions(oldBounding, pos, oldState);
            int disarmed = 0;
            for (BlockPos boundingPos : positions) {
                if (boundingPos.equals(pos)) {
                    continue;
                }
                if (disarmBoundingTile(level, boundingPos)) {
                    disarmed++;
                }
            }
            if (disarmed > 0) {
                LOGGER.debug("[mek-compat] disarmed {} bounding tile(s) at {} before upgrade", disarmed, pos);
            }
        } catch (Throwable throwable) {
            failClosed(throwable);
        }
    }

    /**
     * Called from {@code LevelChunk.setBlockState} <em>after</em> {@code oldState.onRemove}
     * (which has now safely removed the old bounding blocks) and before {@code newState.onPlace}.
     */
    public static void placeUpgradeBoundingBlocks(Level level, BlockPos pos, BlockState newState, BlockState oldState) {
        if (!shouldHandle(level, pos, newState, oldState)) {
            return;
        }
        try {
            Object hasBounding = getAttribute(newState.getBlock());
            if (hasBounding == null) {
                return;
            }
            List<BlockPos> boundingPositions = getBoundingPositions(hasBounding, pos, newState);
            if (boundingPositions.isEmpty()) {
                return;
            }
            for (BlockPos boundingPos : boundingPositions) {
                if (boundingPos.equals(pos)) {
                    continue;
                }
                BlockState occupying = level.getBlockState(boundingPos);
                if (!occupying.isAir()) {
                    LOGGER.debug("[mek-compat] skip placing bounding at {}: occupied by {}", boundingPos, occupying);
                    return;
                }
            }
            placeBoundingBlocks(hasBounding, level, pos, newState);
            LOGGER.info("[mek-compat] placed bounding blocks for upgraded {} at {}", newState.getBlock(), pos);
        } catch (Throwable throwable) {
            failClosed(throwable);
        }
    }

    private static boolean shouldHandle(Level level, BlockPos pos, BlockState newState, BlockState oldState) {
        if (level.isClientSide() || reflectionBroken || pos == null || newState == null || oldState == null) {
            return false;
        }
        Block newBlock = newState.getBlock();
        Block oldBlock = oldState.getBlock();
        if (newBlock == oldBlock) {
            return false;
        }
        if (!newState.hasBlockEntity() || !oldState.hasBlockEntity()) {
            return false;
        }
        return isMekBlock(newBlock) && isMekBlock(oldBlock);
    }

    private static boolean isMekBlock(Block block) {
        var key = ForgeRegistries.BLOCKS.getKey(block);
        return key != null && MEK_NAMESPACES.contains(key.getNamespace());
    }

    /**
     * Clears {@code TileEntityBoundingBlock.mainPos} so {@code BlockBounding.onRemove} will
     * not proxy-destroy the main machine. Returns {@code true} if a bounding tile was found
     * and disarmed.
     */
    private static boolean disarmBoundingTile(Level level, BlockPos boundingPos) {
        BlockEntity tile = level.getBlockEntity(boundingPos);
        if (tile == null) {
            return false;
        }
        try {
            Method setMainLocation = findMethod(tile.getClass(), "setMainLocation", BlockPos.class);
            if (setMainLocation == null) {
                return false;
            }
            setMainLocation.invoke(tile, new Object[]{null});
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            try {
                Method method = cursor.getDeclaredMethod(name, params);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    private static Object getAttribute(Block block) throws Exception {
        Class<?> attributeHolder = Class.forName(CLASS_ATTRIBUTE);
        Class<?> attributeClass = Class.forName(CLASS_ATTR_HAS_BOUNDING);
        Method get = attributeHolder.getMethod("get", Block.class, Class.class);
        return get.invoke(null, block, attributeClass);
    }

    private static List<BlockPos> getBoundingPositions(Object attribute, BlockPos pos, BlockState state) throws Exception {
        Method positions = attribute.getClass().getMethod("getPositions", BlockPos.class, BlockState.class);
        Object result = positions.invoke(attribute, pos, state);
        if (result instanceof Stream<?> stream) {
            List<BlockPos> list = new ArrayList<>();
            stream.forEach(element -> {
                if (element instanceof BlockPos blockPos) {
                    list.add(blockPos);
                }
            });
            return list;
        }
        return List.of();
    }

    private static void placeBoundingBlocks(Object attribute, Level level, BlockPos pos, BlockState state) throws Exception {
        Method place = attribute.getClass().getMethod("placeBoundingBlocks", Level.class, BlockPos.class, BlockState.class);
        place.invoke(attribute, level, pos, state);
    }

    private static void failClosed(Throwable throwable) {
        reflectionBroken = true;
        LOGGER.warn("Lunamura: Mekanism upgrade compatibility disabled after an error "
                + "(upgrading factories with tier installers may still lose the machine)", throwable);
    }
}
