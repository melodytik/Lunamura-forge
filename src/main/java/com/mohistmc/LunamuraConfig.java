package com.mohistmc;

import com.google.common.base.Throwables;
import com.mohistmc.api.ServerAPI;
import com.mohistmc.api.color.ColorsAPI;
import com.mohistmc.commands.BackupWorldCommand;
import com.mohistmc.commands.DumpCommand;
import com.mohistmc.commands.GetPluginListCommand;
import com.mohistmc.commands.LunamuraCommand;
import com.mohistmc.commands.PermissionCommand;
import com.mohistmc.commands.PingCommand;
import com.mohistmc.commands.PluginCommand;
import com.mohistmc.commands.ShowsCommand;
import com.mohistmc.util.YamlUtils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public class LunamuraConfig {

    private static final List<String> HEADER = Arrays.asList("""
            This is the main configuration file for Lunamura.
            As you can see, there's tons to configure. Some options may impact gameplay, so use
            with caution, and make sure you know what each option does before configuring.
            For a reference for any variable inside this file, check out the Lunamura wiki at
            https://wiki.lunamuramc.com/

            If you need help with the configuration or have any questions related to Spigot,
            join us at the Discord or drop by our forums and leave a post.

            Discord: https://discord.gg/lunamuramc
            Forums: https://lunamuramc.com/
            Forums (CN): https://lunamuramc.cn/

            """.split("\\n"));
    /*========================================================================*/
    public static YamlConfiguration config;
    static int version;
    static Map<String, Command> commands;
    private static File CONFIG_FILE;

    public static File lunamurayml = new File("lunamura-config", "lunamura.yml");
    public static YamlConfiguration yml = YamlConfiguration.loadConfiguration(lunamurayml);

    public static void init(File configFile) {
        CONFIG_FILE = configFile;
        config = new YamlConfiguration();
        try {
            config.load(CONFIG_FILE);
        } catch (IOException | InvalidConfigurationException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not load lunamura.yml, please correct your syntax errors", ex);
            Throwables.throwIfUnchecked(ex);
        }

        config.options().setHeader(HEADER);
        config.options().copyDefaults(true);

        commands = new HashMap<>();
        commands.put("lunamura", new LunamuraCommand("lunamura"));
        commands.put("getpluginlist", new GetPluginListCommand("getpluginlist"));
        commands.put("dump", new DumpCommand("dump"));
        commands.put("plugin", new PluginCommand("plugin"));
        commands.put("backupworld", new BackupWorldCommand("backupworld"));
        commands.put("permission", new PermissionCommand("permission"));
        commands.put("shows", new ShowsCommand("shows"));
        commands.put("ping", new PingCommand("ping"));

        version = getInt("config-version", 1);
        set("config-version", 1);
        readConfig();

        try {
            Class.forName("org.sqlite.JDBC");
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Throwable t) {
            throw new RuntimeException("Error initializing Lunamura", t);
        }
    }

    public static void save() {
        YamlUtils.save(lunamurayml, yml);
    }

    public static void registerCommands() {
        for (Map.Entry<String, Command> entry : commands.entrySet()) {
            MinecraftServer.getServer().server.getCommandMap().register(entry.getKey(), "Lunamura", entry.getValue());
        }
    }

    static void readConfig() {
        for (Method method : LunamuraConfig.class.getDeclaredMethods()) {
            if (Modifier.isPrivate(method.getModifiers())) {
                if (method.getParameterTypes().length == 0 && method.getReturnType() == Void.TYPE) {
                    try {
                        method.setAccessible(true);
                        method.invoke(null);
                    } catch (InvocationTargetException ex) {
                        Throwables.throwIfUnchecked(ex.getCause());
                    } catch (Exception ex) {
                        Bukkit.getLogger().log(Level.SEVERE, "Error invoking " + method, ex);
                    }
                }
            }
        }

        try {
            config.save(CONFIG_FILE);
        } catch (IOException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not save " + CONFIG_FILE, ex);
        }
    }

    private static void set(String path, Object val) {
        config.set(path, val);
    }

    private static boolean getBoolean(String path, boolean def) {
        config.addDefault(path, def);
        return config.getBoolean(path, config.getBoolean(path));
    }

    private static int getInt(String path, int def) {
        config.addDefault(path, def);
        return config.getInt(path, config.getInt(path));
    }

    private static <T> List<String> getStringList(String path, T def) {
        config.addDefault(path, def);
        return config.getStringList(path);
    }

    private static String getString(String path, String def) {
        config.addDefault(path, def);
        return config.getString(path, config.getString(path));
    }

    private static double getDouble(String path, double def) {
        config.addDefault(path, def);
        return config.getDouble(path, config.getDouble(path));
    }

    public static String lunamura_lang() {
        return yml.getString("lunamura.lang", Locale.getDefault().toString());
    }

    public static String lunamura_lang;
    public static int maximumRepairCost;
    public static boolean enchantment_fix;
    public static int max_enchantment_level;

    public static boolean player_modlist_blacklist_enable;
    public static List<String> player_modlist_blacklist;

    public static boolean server_modlist_whitelist_enable;
    public static String server_modlist_whitelist;

    // Thread Priority
    public static int server_thread;

    public static boolean bukkitpermissionshandler;
    public static boolean watchdog_spigot;
    public static boolean watchdog_lunamura;
    public static boolean async_save_world;
    public static boolean proxy_protocol;

    // Performance optimizations (ported from Leaf / Pufferfish, configurable in lunamura.yml)
    public static boolean perf_entity_ttl;
    public static int perf_entity_ttl_ticks;
    public static boolean perf_get_biome_fast;
    public static boolean perf_minecart_collision;
    public static int perf_minecart_collision_skip_ticks;
    public static boolean perf_natural_spawn_fast;
    public static boolean perf_game_event_prefilter;
    public static boolean perf_structure_locate_fix;
    public static boolean perf_recipe_manager_fast;
    public static boolean enable_fma;
    public static boolean perf_async_save_json;
    public static int perf_spawn_count_interval;
    public static boolean perf_async_player_save;
    public static int stop_save_timeout_ms;

    // CatServer performance/robustness ports (config key prefix: cat.)
    public static boolean catActivationNullGuard;
    public static boolean catChunkUnloadSafeguard;
    public static boolean catQuietInvalidEntity;
    public static boolean catAsyncEntityAddQueue;
    public static boolean catDrainTasksInChunkTick;
    public static boolean catPluginBytecodeFix;
    public static int catPluginExecutorMaxThreads;

    //Messaes
    public static String message_require_forge;

    public static String server_mod_name;

    public static String ping_status_version;
    public static String library_download_repo;

    private static void lunamura() {
        lunamura_lang = getString("lunamura.lang", Locale.getDefault().toString());
        ping_status_version = getString("lunamura.ping_status_version", "lunamura 1.20.1");
        watchdog_spigot = getBoolean("lunamura.watchdog_spigot", true);
        watchdog_lunamura = getBoolean("lunamura.watchdog_lunamura", false);
        maximumRepairCost = getInt("anvilfix.maximumrepaircost", 40);
        enchantment_fix = getBoolean("anvilfix.enchantment_fix", false);
        max_enchantment_level = getInt("anvilfix.max_enchantment_level", 32767);
        player_modlist_blacklist_enable = getBoolean("player_modlist_blacklist.enable", false);
        player_modlist_blacklist = getStringList("player_modlist_blacklist.list", new ArrayList<>());
        server_modlist_whitelist_enable = getBoolean("server_modlist_whitelist.enable", false);
        server_modlist_whitelist = getString("server_modlist_whitelist.list", ServerAPI.modlists_All.toString().replace(", lunamura", ""));
        server_thread = getInt("threadpriority.server_thread", 8);
        if (server_thread < 1) server_thread = 1;
        else if (server_thread > 10) server_thread = 10;

        bukkitpermissionshandler = getBoolean("forge.bukkitpermissionshandler", true);

        message_require_forge = getString("message.require_forge", "This server has mods that require Forge to be installed on the client. Contact your server admin for more details.");
        server_mod_name = getString("server_mod_name", "lunamura");

        async_save_world = getBoolean("world.async_save", false);
        proxy_protocol = getBoolean("lunamura.proxy_protocol", false);
        library_download_repo = getString("lunamura.library_download_repo", "");
        perf_entity_ttl = getBoolean("lunamura.perf_entity_ttl", false);
        perf_entity_ttl_ticks = getInt("lunamura.perf_entity_ttl_ticks", 12000);
        perf_get_biome_fast = getBoolean("lunamura.perf_get_biome_fast", true);
        perf_minecart_collision = getBoolean("lunamura.perf_minecart_collision", false);
        perf_minecart_collision_skip_ticks = Math.max(1, getInt("lunamura.perf_minecart_collision_skip_ticks", 4));
        perf_natural_spawn_fast = getBoolean("lunamura.perf_natural_spawn_fast", false);
        perf_game_event_prefilter = getBoolean("lunamura.perf_game_event_prefilter", true);
        perf_structure_locate_fix = getBoolean("lunamura.perf_structure_locate_fix", true);
        perf_recipe_manager_fast = getBoolean("lunamura.perf_recipe_manager_fast", true);
        enable_fma = getBoolean("lunamura.enable_fma", false);
        perf_async_save_json = getBoolean("lunamura.perf_async_save_json", true);
        perf_spawn_count_interval = getInt("lunamura.perf_spawn_count_interval", 5);
        perf_async_player_save = getBoolean("lunamura.perf_async_player_save", true);
        stop_save_timeout_ms = getInt("lunamura.stop_save_timeout_ms", 10000);

        // CatServer ports (config key prefix: cat.)
        catActivationNullGuard = getBoolean("cat.activation_null_guard", true);
        catChunkUnloadSafeguard = getBoolean("cat.chunk_unload_safeguard", true);
        catQuietInvalidEntity = getBoolean("cat.quiet_invalid_entity", true);
        catAsyncEntityAddQueue = getBoolean("cat.async_entity_add_queue", true);
        catDrainTasksInChunkTick = getBoolean("cat.drain_tasks_in_chunk_tick", true);
        catPluginBytecodeFix = getBoolean("cat.plugin_bytecode_fix", true);
        catPluginExecutorMaxThreads = getInt("cat.plugin_executor_max_threads", 0);
    }
}
