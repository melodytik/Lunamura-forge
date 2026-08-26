package com.mohistmc.optimizations;

import com.mohistmc.optimizations.utils.ChunkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Villager brain offload (源自 Mohist 1.20.1 com.mohistmc.optimizations.OptVillager).
 * 村民被"卡住"（乘客 / 无法移动）时，跳过 Brain.tick，仅每 20 tick 唤醒一次，降低村民 AI 开销。
 * 修复：静态单例（原每次 new 导致 notLobotomizedCount 重置、600 tick 降频永不生效）；
 *       使用 BlockState.blocksMotion() 替代已废弃的 Block.hasCollision。
 */
public class OptVillager {

    private static final OptVillager INSTANCE = new OptVillager();

    public static OptVillager getInstance() {
        return INSTANCE;
    }

    private boolean isLobotomized = false;
    private int notLobotomizedCount = 0;

    public boolean isLobotomized(Villager villager) {
        return !this.checkLobotomize(villager) || villager.tickCount % 20 == 0;
    }

    private boolean checkLobotomize(Villager villager) {
        // 连续 3+ 次检查都"未卡住"则降低检查频率（每 600 tick 一次），减少开销
        if (villager.tickCount % (this.notLobotomizedCount > 3 ? 600 : 300) == 0) {
            this.isLobotomized = villager.isPassenger() || !this.canTravel(BlockPos.containing(villager.getX(), villager.getY() + 0.0625D, villager.getZ()), villager);

            if (this.isLobotomized) {
                this.notLobotomizedCount = 0;
            } else {
                this.notLobotomizedCount++;
            }
        }

        return this.isLobotomized;
    }

    private boolean canTravel(BlockPos center, Villager villager) {
        ChunkAccess chunk = ChunkManager.getChunkNow(villager.level(), center);
        if (chunk == null) {
            return false;
        }

        BlockPos.MutableBlockPos mutable = center.mutable();
        boolean canJump = !this.hasCollisionAt(chunk, mutable.move(Direction.UP, 2));

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (this.canTravelTo(mutable.setWithOffset(center, direction), canJump, villager)) {
                return true;
            }
        }
        return false;
    }

    private boolean canTravelTo(BlockPos.MutableBlockPos mutable, boolean canJump, Villager villager) {
        ChunkAccess chunk = ChunkManager.getChunkNow(villager.level(), mutable);
        if (chunk == null) {
            return false;
        }

        BlockState state = chunk.getBlockState(mutable);
        Block bottom = state.getBlock();
        if (bottom instanceof BedBlock) {
            // 床方块视为可移动，保证铁农场正常
            return true;
        }

        if (this.hasCollisionAt(chunk, mutable.move(Direction.UP))) {
            // 头顶有碰撞则无法进入该格
            return false;
        }

        boolean isTallBlock = bottom instanceof FenceBlock || bottom instanceof FenceGateBlock || bottom instanceof WallBlock;
        return !state.blocksMotion() || (canJump && !isTallBlock && !this.hasCollisionAt(chunk, mutable.move(Direction.UP)));
    }

    private boolean hasCollisionAt(ChunkAccess chunk, BlockPos pos) {
        return chunk.getBlockState(pos).blocksMotion();
    }
}
