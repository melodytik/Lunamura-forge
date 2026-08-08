package com.mohistmc.plugins;

import com.mohistmc.plugins.pluginmanager.Control;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

/**
 * @author Mgazul by LunamuraMC
 * @date 2023/6/14 14:46:34
 */
public class LunamuraPlugin {

    public static Plugin plugin;

    public static Logger LOGGER = LogManager.getLogger("LunamuraPlugin");

    public static void init(Server server) {
        File out = new File("libraries/com/mohistmc/cache", "libPath.txt");
        if (out.exists()) {
            String data;
            try {
                data = Files.readString(out.toPath());
            } catch (IOException e) {
                data = "libraries";
            }
            File file = new File(data, "com/mohistmc/mohistplugins/mohistplugins-1.20.1.jar");
            if (file.exists()) {
                plugin = Control.loadPlugin(file);
                if (plugin != null) {
                    server.getPluginManager().enablePlugin(plugin);
                } else {
                    LOGGER.error("Failed to load mohistplugins.jar");
                }
            }
        }
    }

    public static void registerListener(Event event) {
        if (event instanceof PrepareAnvilEvent prepareAnvilEvent) {
            EnchantmentFix.anvilListener(prepareAnvilEvent);
        }
        if (event instanceof PluginEnableEvent event1) {
            PluginHooks.register(event1);
        }
    }

}
