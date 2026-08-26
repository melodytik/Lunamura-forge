package com.mohistmc.optimizations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lunamura compatibility layer for Mekanism / Mekanism:More Machine tier-installer upgrades.
 *
 * <p>Background: Mekanism "large" machines (e.g. advanced-tier centrifuging factories from
 * Mekanism:More Machine) are implemented with invisible <em>bounding blocks</em>
 * ({@code mekanism:bounding_block}) surrounding the main block. Mekanism only places those
 * bounding blocks inside {@code Block#setPlacedBy}, which is invoked by {@code BlockItem.place}
 * (player placement). Tier-installer upgrades however go through {@code Level#setBlockAndUpdate},
 * which never triggers {@code setPlacedBy} - so the bounding blocks are never created. When the
 * new machine's structure validation (onAdded / tick) then finds its bounding blocks missing, it
 * silently removes the machine: the factory "disappears".
 *
 * <p>This class emulates {@code setPlacedBy} for the exact in-place replacement pattern that
 * tier-installer upgrades perform (both the old and the new block are Mekanism-family blocks with
 * block entities and different classes), so the bounding blocks get placed <em>before</em> the
 * new machine runs its structure check. Everything is done reflectively - there is no
 * compile-time dependency on Mekanism - and every failure path degrades to a no-op so this layer
 * can never break world logic.
 */
public final class LunamuraMekanismCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("LunamuraMekanismCompat");

    /** Mekanism and its known add-ons that reuse the Mekanism attribute framework. */
    private static final Set<String> MEK_NAMESPACES = Set.of("mekanism", "mekmm");

    private static final String CLASS_ATTRIBUTE = "mekanism.common.block.attribute.Attribute";
    private static final String CLASS_ATTR_HAS_BOUNDING = "mekanism.common.block.attribute.AttributeHasBounding";

    /** Set once reflection fails; later calls become no-ops (mod classes cannot change at runtime). */
    private static volatile boolean reflectionBroken = false;

    private LunamuraMekanismCompat() {
    }

    /**
     * Called from {@link net.minecraft.world.level.Level#setBlockState} right before the new
     * block state is written into the chunk, only for the in-place upgrade pattern.
     *
     * @param level    the server level
     * @param pos      position of the machine being upgraded
     * @param newState the upgraded block state (target of the upgrade)
     * @param oldState the previous block state (source of the upgrade)
     */
    public static void placeUpgradeBoundingBlocks(Level level, BlockPos pos, BlockState newState, BlockState oldState) {
        if (level.isClientSide() || reflectionBroken) {
            return;
        }
        Block newBlock = newState.getBlock();
        Block oldBlock = oldState.getBlock();
        // Upgrade pattern: different block classes, both block-entity blocks, both Mekanism-family.
        // This also naturally excludes vanilla/other-mod setBlock calls.
        if (newBlock == oldBlock || newBlock.getClass() == oldBlock.getClass()) {
            return;
        }
        if (!newState.hasBlockEntity() || !oldState.hasBlockEntity()) {
            return;
        }
        if (!isMekBlock(newBlock) || !isMekBlock(oldBlock)) {
            return;
        }
        try {
            Object hasBounding = getAttribute(newBlock);
            if (hasBounding == null) {
                return; // not a bounding machine (e.g. vanilla Mekanism multiblock factories); nothing to do
            }
            List<BlockPos> boundingPositions = getBoundingPositions(hasBounding, pos, newState);
            if (boundingPositions.isEmpty()) {
                return;
            }
            // Safety: only proceed when every bounding slot is empty so we never clobber blocks
            // the player may have placed there.
            for (BlockPos boundingPos : boundingPositions) {
                if (boundingPos.equals(pos)) {
                    continue;
                }
                if (!level.getBlockState(boundingPos).isAir()) {
                    return;
                }
            }
            placeBoundingBlocks(hasBounding, level, pos, newState);
        } catch (Throwable throwable) {
            // Never let compatibility code break the world; disable once and report.
            reflectionBroken = true;
            LOGGER.warn("Lunamura: Mekanism upgrade compatibility disabled after an error "
                    + "(upgrading factories with tier installers may still lose the machine)", throwable);
        }
    }

    private static boolean isMekBlock(Block block) {
        var key = ForgeRegistries.BLOCKS.getKey(block);
        return key != null && MEK_NAMESPACES.contains(key.getNamespace());
    }

    /** Returns the AttributeHasBounding instance attached to the block, or {@code null} if absent. */
    private static Object getAttribute(Block block) throws Exception {
        Class<?> attributeHolder = Class.forName(CLASS_ATTRIBUTE);
        Class<?> attributeClass = Class.forName(CLASS_ATTR_HAS_BOUNDING);
        Method get = attributeHolder.getMethod("get", Block.class, Class.class);
        return get.invoke(null, block, attributeClass);
    }

    @SuppressWarnings("unchecked")
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
}
