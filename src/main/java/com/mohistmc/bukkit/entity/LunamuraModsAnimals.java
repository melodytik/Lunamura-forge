package com.mohistmc.bukkit.entity;

import net.minecraft.world.entity.animal.Animal;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftAnimals;

/**
 * Lunamura
 *
 * @author Malcolm - m1lc0lm
 * @Created at 20.02.2022 - 20:46 GMT+1
 * © Copyright 2021 / 2022 - M1lcolm
 */
public class LunamuraModsAnimals extends CraftAnimals {

    public LunamuraModsAnimals(CraftServer server, Animal entity) {
        super(server, entity);
    }

    @Override
    public String toString() {
        return "LunamuraModsAnimals{" + getType() + '}';
    }
}
