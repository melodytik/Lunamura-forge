/*
 * Lunamura - LunamuraMC
 * Copyright (C) 2018-2024.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.mohistmc.commands;

import com.mohistmc.LunamuraMC;
import com.mohistmc.api.PlayerAPI;
import com.mohistmc.api.ServerAPI;
import com.mohistmc.util.I18n;
import com.mohistmc.util.MemoryUtils;
import com.mohistmc.util.LunamuraThreadCost;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spigotmc.SpigotConfig;

public class LunamuraCommand extends Command {

    private final List<String> params = Arrays.asList("mods", "playermods", "reload", "version", "channels_incom", "channels_outgo", "speed", "printthreadcost", "cleardropitem", "memoryfix");

    public LunamuraCommand(String name) {
        super(name);
        this.description = "Lunamura related commands";
        this.usageMessage = "/lunamura [mods|playermods|reload|version|channels_incom|channels_outgo|speed|cleardropitem|memoryfix]";
        this.setPermission("lunamura.command.lunamura");
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if ((sender.isOp() || testPermission(sender))) {
            if (args.length == 1) {
                for (String param : params) {
                    if (param.toLowerCase().startsWith(args[0].toLowerCase())) {
                        list.add(param);
                    }
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("playermods")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("cleardropitem")) {
                return Bukkit.getWorldsByName().stream().toList();
            }
        }


        return list;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        switch (args[0].toLowerCase(Locale.ENGLISH)) {
            case "mods" -> {
                // Not recommended for use in games, only test output
                sender.sendMessage(ChatColor.GREEN + I18n.as("lunamuracmd.insidemods") + ServerAPI.modlists_Inside.size() + ") -> " + ServerAPI.modlists_Inside);
                sender.sendMessage(ChatColor.GREEN + I18n.as("lunamuracmd.clientOnlymods")+ ServerAPI.modlists_Client.size() + ") -> " + ServerAPI.modlists_Client);
                sender.sendMessage(ChatColor.GREEN + I18n.as("lunamuracmd.serverOnlymods") + ServerAPI.modlists_Server.size() + ") -> " + ServerAPI.modlists_Server);
                sender.sendMessage(ChatColor.GREEN + I18n.as("lunamuracmd.allMods") + ServerAPI.modlists_All.size() + ") -> " + ServerAPI.modlists_All);
                return true;
            }
            case "playermods" -> {
                // Not recommended for use in games, only test output
                if (args.length == 1) {
                    sender.sendMessage(ChatColor.RED + "Usage: /lunamura playermods <playername>");
                    return false;
                }
                Player player = Bukkit.getPlayer(args[1]);
                if (player != null) {
                    sender.sendMessage(ChatColor.GREEN + String.valueOf(PlayerAPI.getModSize(player)) + " " + PlayerAPI.getModlist(player).toString());
                } else {
                    sender.sendMessage(ChatColor.RED + I18n.as("lunamuracmd.playermods.playernotOnline", args[1]));
                }
                return true;
            }
            case "reload" -> {
                MinecraftServer console = MinecraftServer.getServer();
                com.mohistmc.LunamuraConfig.init((File) console.options.valueOf("lunamura-settings"));
                ((CraftServer)Bukkit.getServer()).initConfig();
                ((CraftServer)Bukkit.getServer()).loadCustomPermissions();
                SpigotConfig.init((File) console.options.valueOf("spigot-settings"));
                for (ServerLevel world : console.getAllLevels()) {
                    world.spigotConfig.init();
                }

                console.server.reloadCount++;
                sender.sendMessage(ChatColor.GREEN + I18n.as("lunamuracmd.reload.complete"));
                return true;
            }
            case "version" -> {
                sender.sendMessage("Lunamura: " + LunamuraMC.versionInfo.lunamura());
                sender.sendMessage("Forge: " + LunamuraMC.versionInfo.forge());
                sender.sendMessage("NeoForge: " + LunamuraMC.versionInfo.neoforge());
                sender.sendMessage("Bukkit: " + LunamuraMC.versionInfo.bukkit());
                sender.sendMessage("CraftBukkit: " + LunamuraMC.versionInfo.craftbukkit());
                sender.sendMessage("Spigot: " + LunamuraMC.versionInfo.spigot());
                return true;
            }
            case "channels_incom" -> sender.sendMessage(ServerAPI.channels_Incoming().toString());
            case "printthreadcost" -> LunamuraThreadCost.dumpThreadCpuTime();
            case "channels_outgo" -> sender.sendMessage(ServerAPI.channels_Outgoing().toString());
            case "speed" -> {
                if (sender instanceof Player p) {
                    if (args.length == 2) {
                        if (this.isFloat(args[1])) {
                            if (p.isFlying()) {
                                float speed = Float.parseFloat(args[1]);
                                if (speed >= 0.0f && speed < 11.0f) {
                                    p.setFlySpeed(speed / 10.0f);
                                    p.sendMessage(I18n.as("lunamuracmd.playerflightspeedSet") + speed);
                                }
                            } else {
                                float speed = Float.parseFloat(args[1]);
                                if (speed >= 0.0f && speed < 11.0f) {
                                    p.setWalkSpeed(speed / 10.0f);
                                    p.sendMessage(I18n.as("lunamuracmd.playerwalkspeedset") + speed);
                                }
                            }
                        }
                        if (args[0].equalsIgnoreCase("reset")) {
                            p.setFlySpeed(0.1f);
                            p.setWalkSpeed(0.2f);
                            p.sendMessage(I18n.as("lunamuracmd.flightAndWalkspeedRestore"));
                        }
                        return true;
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + I18n.as("error.notplayer"));
                    return false;
                }
            }
            case "cleardropitem" -> {
                if (args.length == 2) {
                    World world = Bukkit.getWorld(args[1]);
                    if (world == null) {
                        sender.sendMessage(ChatColor.RED + " World not found!");
                        return false;
                    } else {
                        AtomicInteger size = new AtomicInteger(0);
                        for (org.bukkit.entity.Entity entity : world.getEntities().stream().toList()) {
                            if (entity.getType() == EntityType.DROPPED_ITEM) {
                                ItemStack item = ((org.bukkit.entity.Item) entity).getItemStack();
                                entity.remove();
                                size.addAndGet(item.getAmount());
                            }
                        }
                        sender.sendMessage(I18n.as("worldcommands.world.cleardropitem", size.get(), world.getName()));
                        return true;
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /lunamura cleardropitem <worldname>");
                    return false;
                }
            }
            case "memoryfix" -> {
                sender.sendMessage(ChatColor.GREEN + MemoryUtils.setProcessWorkingSetSize(50, 100));
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
                return false;
            }
        }

        return true;
    }

    private boolean isFloat(String input) {
        try {
            Float.parseFloat(input);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
