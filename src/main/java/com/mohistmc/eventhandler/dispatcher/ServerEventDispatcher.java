package com.mohistmc.eventhandler.dispatcher;

import com.mohistmc.api.event.LunamuraStartDoneEvent;
import com.mohistmc.paper.event.server.ServerTickStartEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.bukkit.Bukkit;

public class ServerEventDispatcher {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        new ServerTickStartEvent(event.getServer().getTickCount() + 1).callEvent(); // Paper
    }

    @SubscribeEvent
    public void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        Bukkit.getPluginManager().callEvent(new LunamuraStartDoneEvent());//Lunamura
    }
}
