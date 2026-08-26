package com.mohistmc.optimizations.entitytracking;

/**
 * 原 VMP com.ishland.vmp.common.playerwatching.EntityTrackerEntryExtension 的 mojmap 版。
 * 由 ServerEntity 实现，挂在 ServerEntity 上。
 */
public interface EntityTrackerEntryExtension {

    void vmp$tickAlways();

    void vmp$syncEntityData();

    void vmp$updatePassengers();

}
