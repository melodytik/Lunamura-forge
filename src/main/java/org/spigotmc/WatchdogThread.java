package org.spigotmc;

import com.mohistmc.LunamuraConfig;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;

import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bukkit.Bukkit.shutdown;

public class WatchdogThread extends Thread {

    private static WatchdogThread instance;
    private long timeoutTime;
    private boolean restart;
    private volatile long lastTick;
    private volatile boolean stopping;

    private WatchdogThread(long timeoutTime, boolean restart) {
        super("Spigot Watchdog Thread");
        this.timeoutTime = timeoutTime;
        this.restart = restart;
    }

    private static long monotonicMillis() {
        return System.nanoTime() / 1000000L;
    }

    public static void doStart(int timeoutTime, boolean restart) {
        if (instance == null) {
            instance = new WatchdogThread(timeoutTime * 1000L, restart);
            instance.start();
        } else {
            instance.timeoutTime = timeoutTime * 1000L;
            instance.restart = restart;
        }
    }

    public static void tick() {
        if (LunamuraConfig.watchdog_spigot) {
            instance.lastTick = monotonicMillis();
        }
    }

    public static void doStop() {
        if (instance != null) {
            instance.stopping = true;
        }
    }

    @Override
    public void run() {
        while (!stopping) {
            //
            if (lastTick != 0 && timeoutTime > 0 && monotonicMillis() > lastTick + timeoutTime) {
                Logger log = Bukkit.getServer().getLogger();
                log.log( Level.SEVERE, "The server has stopped responding!" );
                log.log( Level.SEVERE, "Lunamura version: " + Bukkit.getServer().getVersion() );
                log.log( Level.SEVERE, "Memory using: " + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576) + "MB/" + (Runtime.getRuntime().totalMemory() / 1048576) + "MB/" + (Runtime.getRuntime().maxMemory() / 1048576) + "MB" );
                //
                log.log(Level.SEVERE, "------------------------------");
                log.log(Level.SEVERE, "Server thread dump (Look for plugins here before reporting to Lunamura!):");
                dumpThread(ManagementFactory.getThreadMXBean().getThreadInfo(MinecraftServer.getServer().serverThread.getId(), Integer.MAX_VALUE), log);
                log.log(Level.SEVERE, "------------------------------");
                //
                log.log(Level.SEVERE, "Entire Thread Dump:");
                ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
                for (ThreadInfo thread : threads) {
                    dumpThread(thread, log);
                }
                log.log(Level.SEVERE, "------------------------------");
                shutdown();
                break;
            }

            try {
                sleep(10000);
            } catch (InterruptedException ex) {
                interrupt();
            }
        }
    }

    private static void dumpThread(ThreadInfo thread, Logger log) {
        log.log(Level.SEVERE, "------------------------------");
        //
        log.log(Level.SEVERE, "Current Thread: " + thread.getThreadName());
        log.log(Level.SEVERE, "\tPID: " + thread.getThreadId()
                + " | Suspended: " + thread.isSuspended()
                + " | Native: " + thread.isInNative()
                + " | State: " + thread.getThreadState());
        if (thread.getLockedMonitors().length != 0) {
            log.log(Level.SEVERE, "\tThread is waiting on monitor(s):");
            for (MonitorInfo monitor : thread.getLockedMonitors()) {
                log.log(Level.SEVERE, "\t\tLocked on:" + monitor.getLockedStackFrame());
            }
        }
        log.log(Level.SEVERE, "\tStack:");
        //
        for (StackTraceElement stack : thread.getStackTrace()) {
            log.log(Level.SEVERE, "\t\t" + stack);
        }
    }
}
