package com.mohistmc.bukkit.entity;

import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftEntity;

public class LunamuraModsEntity extends CraftEntity {

    public LunamuraModsEntity(CraftServer server, net.minecraft.world.entity.Entity entity) {
        super(server, entity);
    }

    @Override
    public String toString() {
        return "LunamuraModsEntity{" + this.getType() + '}';
    }
}
