package com.mohistmc.util;

import com.mohistmc.LunamuraMCStart;

/**
 * @author Mgazul by LunamuraMC
 * @date 2023/9/23 3:19:38
 */
public class I18n {

    public static String as(String key) {
        return LunamuraMCStart.i18n.as(key);
    }

    public static String as(String key, Object... objects) {
        return LunamuraMCStart.i18n.as(key, objects);
    }
}
