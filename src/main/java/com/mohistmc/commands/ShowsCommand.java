package com.mohistmc.commands;

import com.mohistmc.api.ItemAPI;
import com.mohistmc.api.ServerAPI;
import com.mohistmc.api.WorldAPI;
import com.mohistmc.api.gui.DemoGUI;
import com.mohistmc.api.gui.GUIItem;
import com.mohistmc.api.gui.ItemStackFactory;
import com.mohistmc.util.I18n;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mgazul by LunamuraMC
 * @date 2023/8/1 20:00:00
 */
public class ShowsCommand extends Command {

    public ShowsCommand(String name) {
        super(name);
        this.description = "Lunamura shows commands";
        this.usageMessage = "/shows [sound|entitys|blockentitys]";
        this.setPermission("lunamura.command.shows");
    }

    private final List<String> params = List.of("sound", "entitys", "blockentitys");

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1 && (sender.isOp() || testPermission(sender))) {
            for (String param : params) {
                if (param.toLowerCase().startsWith(args[0].toLowerCase())) {
                    list.add(param);
                }
            }
        }

        return list;
    }


    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!testPermission(sender)) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }


        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + I18n.as("error.notplayer"));
            return false;
        }

        switch (args[0].toLowerCase(Locale.ENGLISH)) {
            case "sound" -> {
                DemoGUI wh = new DemoGUI("Sounds");
                wh.getGUI().setItem(47, new GUIItem(new ItemStackFactory(Material.REDSTONE)
                        .setDisplayName(I18n.as("showscommand.sound.stopall"))
                        .toItemStack()) {
                    @Override
                    public void ClickAction(ClickType type, Player u, ItemStack itemStack) {
                        u.stopAllSounds();
                    }
                });
                for (Sound s : Sound.values()) {
                    wh.addItem(new GUIItem(new ItemStackFactory(Material.NOTE_BLOCK)
                            .setDisplayName(s.name())
                            .toItemStack()) {
                        @Override
                        public void ClickAction(ClickType type, Player u, ItemStack itemStack) {
                            player.playSound(player.getLocation(), s, 1f, 1.0f);
                        }
                    });
                }
                wh.openGUI(player);
                return true;
            }
            case "entitys" -> {

                Map<net.minecraft.world.entity.EntityType<?>, Integer> collect =
                        StreamSupport.stream(WorldAPI.getServerLevel(player.getWorld()).getAllEntities().spliterator(), false)
                                .collect(Collectors.toMap(
                                        net.minecraft.world.entity.Entity::getType,
                                        entity -> 1,
                                        Integer::sum
                                ));

                Map<net.minecraft.world.entity.EntityType<?>, Map<String, Integer>> entityChunkCount = new HashMap<>();

                StreamSupport.stream(WorldAPI.getServerLevel(player.getWorld()).getAllEntities().spliterator(), false)
                        .forEach(entity -> {
                            net.minecraft.world.entity.EntityType<?> type = entity.getType();
                            long chunkX = entity.blockPosition().getX() >> 4;
                            long chunkZ = entity.blockPosition().getZ() >> 4;
                            String chunkKey = chunkX + "," + chunkZ;

                            entityChunkCount.computeIfAbsent(type, k -> new HashMap<>())
                                    .merge(chunkKey, 1, Integer::sum);
                        });

                List<Map.Entry<net.minecraft.world.entity.EntityType<?>, Integer>> infoIds = new ArrayList<>(collect.entrySet());
                infoIds.sort((o1, o2) -> {
                    Integer p1 = o1.getValue();
                    Integer p2 = o2.getValue();
                    return p2 - p1;
                });

                LinkedHashMap<net.minecraft.world.entity.EntityType<?>, Integer> newMap = new LinkedHashMap<>();
                AtomicInteger allSize = new AtomicInteger(0);
                for (Map.Entry<net.minecraft.world.entity.EntityType<?>, Integer> entity : infoIds) {
                    newMap.put(entity.getKey(), entity.getValue());
                    allSize.addAndGet(entity.getValue());
                }

                DemoGUI wh = new DemoGUI(I18n.as("shows.entitys.title", allSize.getAndSet(0)));
                for (Map.Entry<net.minecraft.world.entity.EntityType<?>, Integer> s : newMap.entrySet()) {

                    String topChunk = "";
                    int maxCount = 0;
                    if (entityChunkCount.containsKey(s.getKey())) {
                        for (Map.Entry<String, Integer> chunkEntry : entityChunkCount.get(s.getKey()).entrySet()) {
                            if (chunkEntry.getValue() > maxCount) {
                                maxCount = chunkEntry.getValue();
                                topChunk = chunkEntry.getKey();
                            }
                        }
                    }

                    String finalTopChunk = topChunk;
                    int finalMaxCount = maxCount;
                    wh.addItem(new GUIItem(new ItemStackFactory(ItemAPI.getEggMaterial(s.getKey()))
                                       .setLore(List.of(
                                               "§7====================",
                                               I18n.as("shows.entitys.item.name", s.getValue()),
                                               I18n.as("shows.entitys.item.entity", EntityType.getKey(s.getKey())),
                                               I18n.as("shows.entitys.item.chunk", finalTopChunk, finalMaxCount),
                                               "",
                                               I18n.as("shows.entitys.item.click"),
                                               "§7===================="
                                       ))
                                       .toItemStack()) {
                                   @Override
                                   public void ClickAction(ClickType type, Player u, ItemStack itemStack) {
                                       if (!finalTopChunk.isEmpty()) {
                                           String[] coords = finalTopChunk.split(",");
                                           if (coords.length == 2) {
                                               try {
                                                   int chunkX = Integer.parseInt(coords[0]);
                                                   int chunkZ = Integer.parseInt(coords[1]);
                                                   u.teleport(u.getWorld().getHighestBlockAt(chunkX * 16 + 8, chunkZ * 16 + 8).getLocation().add(0.5, 1, 0.5));
                                                   u.sendMessage(I18n.as("shows.entitys.teleport.success", chunkX, chunkZ, finalMaxCount));
                                               } catch (NumberFormatException e) {
                                                   u.sendMessage(I18n.as("shows.entitys.teleport.error"));
                                               }
                                           }
                                       } else {
                                           u.sendMessage(I18n.as("shows.entitys.chunk.notfound"));
                                       }
                                   }
                               }
                    );
                }
                wh.openGUI(player);
                return true;
            }
            case "blockentitys" -> {

                Map<Material, Integer> collect = Arrays.stream(player.getWorld().getLoadedChunks()).flatMap(chunk -> Arrays.stream(chunk.getTileEntities())).map(BlockState::getBlock).collect(Collectors.toMap(Block::getType, block -> 1, Integer::sum));

                List<Map.Entry<Material, Integer>> infoIds = new ArrayList<>(collect.entrySet());
                infoIds.sort((o1, o2) -> {
                    Integer p1 = o1.getValue();
                    Integer p2 = o2.getValue();
                    return p2 - p1;
                });

                LinkedHashMap<Material, Integer> newMap = new LinkedHashMap<>();
                AtomicInteger allSize = new AtomicInteger(0);
                for (Map.Entry<Material, Integer> entity : infoIds) {
                    newMap.put(entity.getKey(), entity.getValue());
                    allSize.addAndGet(entity.getValue());
                }

                DemoGUI wh = new DemoGUI("BlockEntitys: " + allSize.getAndSet(0));
                for (Map.Entry<Material, Integer> s : newMap.entrySet()) {
                    Material material = s.getKey().name().contains("_WALL") ? Material.getMaterial(s.getKey().name().replace("_WALL", "")) : s.getKey();
                    wh.addItem(new GUIItem(new ItemStackFactory(material)
                            .setDisplayName("§6Size: §4" + s.getValue())
                            .setLore(List.of("§7BlockEntity: §2" + s.getKey()))
                            .toItemStack())
                    );
                }
                wh.openGUI(player);
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
                return false;
            }
        }
    }
}
