package com.mohistmc.util;

import java.util.concurrent.ExecutionException;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R1.util.LazyPlayerSet;
import org.bukkit.craftbukkit.v1_20_R1.util.Waitable;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;

public class ChatPatchFix {

    // CraftBukkit start - add method
    public static void chat(ServerGamePacketListenerImpl packetListener, String s, PlayerChatMessage original, boolean async) {
        if (s.isEmpty() || packetListener.player.getChatVisibility() == ChatVisiblity.HIDDEN) {
            return;
        }

        if (!async && s.startsWith("/")) {
            packetListener.handleCommand(s);
        } else if (packetListener.player.getChatVisibility() != ChatVisiblity.SYSTEM) {
            org.bukkit.entity.Player thisPlayer = packetListener.getCraftPlayer();
            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(async, thisPlayer, s, new LazyPlayerSet(packetListener.server));
            String originalFormat = event.getFormat(), originalMessage = event.getMessage();
            Bukkit.getPluginManager().callEvent(event);
            if (PlayerChatEvent.getHandlerList().getRegisteredListeners().length != 0) {
                // Evil plugins still listening to deprecated event
                final PlayerChatEvent queueEvent = new PlayerChatEvent(thisPlayer, event.getMessage(), event.getFormat(), event.getRecipients());
                queueEvent.setCancelled(event.isCancelled());

                class SyncChat extends Waitable<Object> {
                    @Override
                    protected Object evaluate() {
                        Bukkit.getPluginManager().callEvent(queueEvent);
                        if (queueEvent.isCancelled()) {
                            return null;
                        }
                        String message = String.format(queueEvent.getFormat(), queueEvent.getPlayer().getDisplayName(), queueEvent.getMessage());
                        if (((LazyPlayerSet) queueEvent.getRecipients()).isLazy()) {
                            if (!org.spigotmc.SpigotConfig.bungee && originalFormat.equals(queueEvent.getFormat()) && originalMessage.equals(queueEvent.getMessage()) && queueEvent.getPlayer().getName().equalsIgnoreCase(queueEvent.getPlayer().getDisplayName())) {
                                packetListener.server.getPlayerList().broadcastChatMessage(original, packetListener.player, ChatType.bind(ChatType.CHAT, packetListener.player));
                                return null;
                            }

                            for (ServerPlayer recipient : packetListener.server.getPlayerList().players) {
                                recipient.getBukkitEntity().sendMessage(packetListener.player.getUUID(), message);
                            }
                        } else {
                            for (org.bukkit.entity.Player player : queueEvent.getRecipients()) {
                                player.sendMessage(thisPlayer.getUniqueId(), message);
                            }
                        }
                        Bukkit.getConsoleSender().sendMessage(message);
                        return null;
                    }
                }
                Waitable<Object> waitable = new SyncChat();
                if (async) {
                    packetListener.server.processQueue.add(waitable);
                } else {
                    waitable.run();
                }
                try {
                    waitable.get();
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // This is proper habit for java. If we aren't handling it, pass it on!
                    return;
                } catch (ExecutionException e) {
                    throw new RuntimeException("Exception processing chat event", e.getCause());
                }
            }
            if (event.isCancelled()) {
                return;
            }
            s = String.format(event.getFormat(), event.getPlayer().getDisplayName(), event.getMessage());
            if (((LazyPlayerSet) event.getRecipients()).isLazy()) {
                if (!org.spigotmc.SpigotConfig.bungee && originalFormat.equals(event.getFormat()) && originalMessage.equals(event.getMessage()) && event.getPlayer().getName().equalsIgnoreCase(event.getPlayer().getDisplayName())) {
                    packetListener.server.getPlayerList().broadcastChatMessage(original, packetListener.player, ChatType.bind(ChatType.CHAT, packetListener.player));
                    return;
                }
                for (ServerPlayer recipient : packetListener.server.getPlayerList().players) {
                    recipient.getBukkitEntity().sendMessage(packetListener.player.getUUID(), s);
                }
            } else {
                for (org.bukkit.entity.Player recipient : event.getRecipients()) {
                    recipient.sendMessage(packetListener.player.getUUID(), s);
                }
            }
            Bukkit.getConsoleSender().sendMessage(s);
        }
    }
    // CraftBukkit end
}
