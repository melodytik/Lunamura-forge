package com.mohistmc.plugins;

import java.text.DecimalFormat;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion exposing the same numbers that {@code /forge tps} prints:
 * <ul>
 *   <li>{@code %forge_mspt%} - average game-tick duration in milliseconds</li>
 *   <li>{@code %forge_tps%} - average ticks per second (clamped to 20)</li>
 * </ul>
 * Registered when the PlaceholderAPI plugin enables (see {@link PluginHooks}).
 */
public class ForgeTPSExpansion extends PlaceholderExpansion {

    private static final DecimalFormat FORMAT = new DecimalFormat("########0.000");

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.equalsIgnoreCase("mspt")) {
            return FORMAT.format(getAverageMspt());
        }
        if (params.equalsIgnoreCase("tps")) {
            return FORMAT.format(getAverageTps());
        }
        return null;
    }

    private static MinecraftServer getServer() {
        if (Bukkit.getServer() instanceof CraftServer craft) {
            return craft.getServer();
        }
        return null;
    }

    /** Average game-tick duration in milliseconds (same value /forge tps prints). */
    private static double getAverageMspt() {
        MinecraftServer server = getServer();
        if (server == null) {
            return 0.0D;
        }
        long[] tickTimes = server.tickTimes;
        if (tickTimes == null || tickTimes.length == 0) {
            return 0.0D;
        }
        long sum = 0L;
        for (long tickTime : tickTimes) {
            sum += tickTime;
        }
        return sum / (double) tickTimes.length * 1.0E-6D; // nanoseconds -> milliseconds
    }

    /** Average TPS, clamped to 20 (same value /forge tps prints). */
    private static double getAverageTps() {
        double mspt = getAverageMspt();
        return Math.min(1000.0D / Math.max(mspt, 0.05D), 20.0D);
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "forge"; // -> %forge_mspt% / %forge_tps%
    }

    @Override
    public @NotNull String getAuthor() {
        return "LunamuraMC";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.3.0";
    }
}
