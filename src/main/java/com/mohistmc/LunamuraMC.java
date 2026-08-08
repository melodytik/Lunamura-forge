package com.mohistmc;

import com.mohistmc.eventhandler.EventDispatcherRegistry;
import com.mohistmc.i18n.i18n;
import com.mohistmc.plugins.LunamuraProxySelector;
import com.mohistmc.util.VersionInfo;
import java.net.ProxySelector;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;

@Mod("lunamura")
@OnlyIn(Dist.DEDICATED_SERVER)
public class LunamuraMC {
    public static final String NAME = "Lunamura";
    public static Logger LOGGER = LogManager.getLogger();
    public static i18n i18n;
    public static String version = "1.20.1";
    public static String modid = "lunamura";
    public static ClassLoader classLoader;
    public static VersionInfo versionInfo;

    public LunamuraMC() {
        classLoader = LunamuraMC.class.getClassLoader();

        //TODO: do something when mod loading
        LOGGER.info("Lunamura mod loading.....");
        EventDispatcherRegistry.init();
        ProxySelector.setDefault(new LunamuraProxySelector(ProxySelector.getDefault()));
    }

    public static void initVersion() {
        String lunamura_lang = LunamuraConfig.yml.getString("lunamura.lang", Locale.getDefault().toString());
        i18n = new i18n(LunamuraMC.class.getClassLoader(), lunamura_lang);

        Map<String, String> arguments = new HashMap<>();
        String[] cbs = CraftServer.class.getPackage().getImplementationVersion().split("-");
        arguments.put("lunamura", (LunamuraMC.class.getPackage().getImplementationVersion() != null) ? LunamuraMC.class.getPackage().getImplementationVersion() : version);
        arguments.put("bukkit", cbs[0]);
        arguments.put("craftbukkit", cbs[1]);
        arguments.put("spigot", cbs[2]);
        arguments.put("neoforge", cbs[3]);
        arguments.put("forge", ForgeVersion.getVersion());
        versionInfo = new VersionInfo(arguments);
    }
}