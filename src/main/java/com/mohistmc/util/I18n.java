package com.mohistmc.util;

import com.mohistmc.LunamuraMC;

/**
 * @author Mgazul by LunamuraMC
 * @date 2023/9/23 6:15:26
 */
public class I18n {

    public static String as(String key) {
        return LunamuraMC.i18n.as(key);
    }

    public static String as(String key, Object... objects) {
        return LunamuraMC.i18n.as(key, objects);
    }
}
