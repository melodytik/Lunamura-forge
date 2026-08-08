package com.mohistmc.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class LunamuraStartDoneEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    public LunamuraStartDoneEvent() {
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
