package com.mohistmc.api;

import com.mohistmc.LunamuraConfig;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import org.bukkit.entity.EntityType;

public class EntityAPI {

    public static String entityName(Entity entity) {
        String entityName = ServerAPI.entityTypeMap.get(entity.getType());
        if (entityName == null) {
            entityName = entity.getName().getString();
        }
        return entityName;
    }

    public static EntityType entityType(String entityName) {
        EntityType type = EntityType.fromName(entityName);
        return Objects.requireNonNullElse(type, EntityType.UNKNOWN);
    }

    public static EntityType entityType(String entityName, EntityType defType) {
        EntityType type = EntityType.fromName(entityName);
        if (type != null) {
            return type;
        } else {
            return defType;
        }
    }

    public static net.minecraft.world.entity.EntityType<?> getType(String resourceLocation) {
        return ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(resourceLocation));
    }

    public static String resourceLocation(Entity nmsEntity) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(nmsEntity.getType());
        return key.toString();
    }
}
