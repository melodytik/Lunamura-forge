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

package com.mohistmc.config;

import com.mohistmc.LunamuraMCStart;
import com.mohistmc.i18n.i18n;
import com.mohistmc.yaml.file.YamlConfiguration;
import java.io.File;
import java.util.Locale;

public class LunamuraConfigUtil {

    public static final File lunamurayml = new File("lunamura-config", "lunamura.yml");
    public static final YamlConfiguration yml = YamlConfiguration.loadConfiguration(lunamurayml);

    public static void init() {
        try {
            if (!lunamurayml.exists()) {
                lunamurayml.createNewFile();
            }
        } catch (Exception e) {
            System.out.println("File init exception!");
        }
    }

    public static boolean INSTALLATIONFINISHED() {
        return !yml.getBoolean("lunamura.installation-finished", false);
    }

    public static boolean CHECK_LIBRARIES() {
        String key = "lunamura.libraries.check";
        if (yml.get(key) == null) {
            yml.set(key, true);
            save();
        }
        return yml.getBoolean(key, true);
    }

    public static boolean aBoolean(String key, boolean defaultReturn) {
        return yml.getBoolean(key, defaultReturn);
    }

    public static void i18n() {
        LunamuraMCStart.i18n = new i18n(LunamuraMCStart.class.getClassLoader(), LUNAMURALANG());
    }

    public static void save() {
        try {
            yml.save(lunamurayml);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String LUNAMURALANG() {
        String key = "lunamura.lang";
        if (yml.get(key) == null) {
            yml.set(key, Locale.getDefault().toString());
            save();
        }
        return yml.getString(key, Locale.getDefault().toString());
    }
}
