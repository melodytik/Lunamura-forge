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

package com.mohistmc;

import com.mohistmc.action.v_1_20_1;
import com.mohistmc.config.LunamuraConfigUtil;
import com.mohistmc.feature.AutoDeleteMods;
import com.mohistmc.feature.CustomLibraries;
import com.mohistmc.feature.DefaultLibraries;
import com.mohistmc.feature.ExceptionHandler;
import com.mohistmc.i18n.i18n;
import com.mohistmc.tools.JarTool;
import com.mohistmc.tools.MojangEulaUtil;
import com.mohistmc.tools.ZipUtil;
import com.mohistmc.util.DataParser;
import com.mohistmc.util.LunamuraModuleManager;
import cpw.mods.bootstraplauncher.BootstrapLauncher;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

public class LunamuraMCStart {

    public static final List<String> mainArgs = new ArrayList<>();
    public static String MCVERSION;
    public static i18n i18n;
    public static JarTool jarTool;

    public static String getVersion() {
        return (LunamuraMCStart.class.getPackage().getImplementationVersion() != null) ? LunamuraMCStart.class.getPackage().getImplementationVersion() : "unknown";
    }

    public static void main(String[] args) throws Exception {
        mainArgs.addAll(List.of(args));
        jarTool = new JarTool(LunamuraMCStart.class);
        DataParser.parseVersions();
        DataParser.parseLaunchArgs();
        LunamuraConfigUtil.init();
        LunamuraConfigUtil.i18n();
        if (i18n.isCN()) {
            Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());
        }
        if (LunamuraConfigUtil.INSTALLATIONFINISHED() && LunamuraConfigUtil.aBoolean("lunamura.show_logo", true)) {
            System.out.printf("%s - %s, Java(%s) %s PID: %s%n",
                    i18n.as("lunamura.launch.welcomemessage"),
                    getVersion(),
                    System.getProperty("java.class.version"),
                    System.getProperty("java.version"),
                    ManagementFactory.getRuntimeMXBean().getName().split("@")[0]
            );
            if (i18n.isCN()) {
                System.out.println("+------------------------------------------------------+");
                System.out.println("|                                                      |");
                System.out.println("| 版本修改：OoOooo0518(nyamura）qq：3063276667         |");
                System.out.println("| 爱发电：https://ifdian.net/a/melodytik               |");
                System.out.println("|                                                      |");
                System.out.println("+------------------------------------------------------+");
            }
        }

        if (System.getProperty("log4j.configurationFile") == null) {
            System.setProperty("log4j.configurationFile", "log4j2_lunamura.xml");
        }

        ZipUtil.getFileContent(LunamuraMCStart.class.getClassLoader().getResourceAsStream("META-INF/libraries"));
        if (LunamuraConfigUtil.INSTALLATIONFINISHED() && LunamuraConfigUtil.CHECK_LIBRARIES()) {
            DefaultLibraries.run();
        }

        CustomLibraries.loadCustomLibs();
        if (LunamuraConfigUtil.INSTALLATIONFINISHED()) {
            v_1_20_1.run();
        }

        AutoDeleteMods.jar();

        List<String> forgeArgs = new ArrayList<>();
        for (String arg : DataParser.launchArgs.stream().filter(s ->
                        s.startsWith("--launchTarget")
                                || s.startsWith("--fml.forgeVersion")
                                || s.startsWith("--fml.mcVersion")
                                || s.startsWith("--fml.forgeGroup")
                                || s.startsWith("--fml.mcpVersion"))
                .toList()) {
            forgeArgs.add(arg.split(" ")[0]);
            forgeArgs.add(arg.split(" ")[1]);
        }
        new LunamuraModuleManager(DataParser.launchArgs);

        if (!MojangEulaUtil.hasAcceptedEULA()) {
            System.out.println(i18n.as("eula"));
            while (!"true".equals(new Scanner(System.in).nextLine().trim())) {
                System.out.println(i18n.as("eula_notrue"));
            }
            MojangEulaUtil.writeInfos(i18n.as("eula.text", "https://account.mojang.com/documents/minecraft_eula") + "\n" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "\neula=true");
        }
        String[] args_ = Stream.concat(forgeArgs.stream(), mainArgs.stream()).toArray(String[]::new);
        BootstrapLauncher.main(args_);
    }
}
