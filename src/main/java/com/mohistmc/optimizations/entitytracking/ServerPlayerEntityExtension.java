package com.mohistmc.optimizations.entitytracking;

/**
 * 原 VMP com.ishland.vmp.common.playerwatching.ServerPlayerEntityExtension 的 mojmap 版。
 * 由 ServerPlayer 实现，挂在 ServerPlayer 上。
 */
public interface ServerPlayerEntityExtension {

    boolean vmpTracking$isPositionUpdated();

    void vmpTracking$updatePosition();

    /** 判断玩家本 tick 是否发生了"瞬移/大距离跳变"（如 /tp、waystones、tpmaster、传送门）。 */
    boolean vmpTracking$isTeleport();

}
