package com.mohistmc;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.NamedThreadFactory;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.spigotmc.TicksPerSecondCommand;

public class WatchLunamura implements Runnable {

    public static ScheduledThreadPoolExecutor WatchLunamura = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("WatchLunamura"));

    private static long Time = 0L;
    private static long WarnTime = 0L;

    public static void start() {
        if (isEnable()) {
            WatchLunamura.scheduleAtFixedRate(new WatchLunamura(), 60000L, 500L, TimeUnit.MILLISECONDS);
        }
    }

    public static void update() {
        if (isEnable()) {
            Time = System.currentTimeMillis();
        }
    }

    public static void stop() {
        if (isEnable()) {
            WatchLunamura.shutdown();
        }
    }

    public static boolean isEnable() {
        return LunamuraConfig.watchdog_lunamura;
    }

    @Override
    public void run() {
        long curTime = System.currentTimeMillis();
        if (Time > 0L && curTime - Time > 2000L && curTime - WarnTime > 60000L) {
            WarnTime = curTime;
            LunamuraMC.LOGGER.warn(LunamuraMC.i18n.as("watchlunamura.1"));

            double[] tps = Bukkit.getTPS();
            String[] tpsAvg = new String[tps.length];
            for (int i = 0; i < tps.length; i++) {
                tpsAvg[i] = TicksPerSecondCommand.format(tps[i]);
            }

            LunamuraMC.LOGGER.warn(LunamuraMC.i18n.as("watchlunamura.2", String.valueOf(curTime - Time), StringUtils.join(tpsAvg, ", ")));
            LunamuraMC.LOGGER.warn(LunamuraMC.i18n.as("watchlunamura.3"));
            LunamuraMC.LOGGER.warn(LunamuraMC.i18n.as("watchlunamura.4"));
            for (StackTraceElement stack : MinecraftServer.getServer().serverThread.getStackTrace()) {
                LunamuraMC.LOGGER.warn("{}{}", LunamuraMC.i18n.as("watchlunamura.5"), stack);
            }
            LunamuraMC.LOGGER.warn(LunamuraMC.i18n.as("watchlunamura.1"));
        }
    }
}