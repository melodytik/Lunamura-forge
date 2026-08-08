package com.mohistmc.bukkit.entity;

import net.minecraft.world.entity.animal.AbstractGolem;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftGolem;

/**
 * @author Mgazul by LunamuraMC
 * @date 2023/9/13 3:26:33
 */
public class LunamuraModsGolem extends CraftGolem {

    public LunamuraModsGolem(CraftServer server, AbstractGolem entity) {
        super(server, entity);
    }

    @Override
    public String toString() {
        return "LunamuraModsGolem{" + getType() + '}';
    }
}
