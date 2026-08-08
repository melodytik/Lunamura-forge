package com.mohistmc.bukkit.entity;

import net.minecraft.world.entity.monster.Monster;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftMonster;

/**
 * Lunamura
 *
 * @author Malcolm - m1lc0lm
 * @Created at 20.02.2022 - 21:02 GMT+1
 * © Copyright 2021 / 2022 - M1lcolm
 */
public class LunamuraModsMonster extends CraftMonster {

    public LunamuraModsMonster(CraftServer server, Monster entity) {
        super(server, entity);
    }

    @Override
    public String toString() {
        return "LunamuraModsMonster{" + getType() + '}';
    }
}
