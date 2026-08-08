package com.mohistmc.util;

import com.mohistmc.LunamuraMCStart;
import com.mohistmc.tools.FileUtils;
import com.mohistmc.tools.OSUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DataParser {

    public static final HashMap<String, String> versionMap = new HashMap<>();
    public static final List<String> launchArgs = new ArrayList<>();

    public static void parseVersions() {
        versionMap.put("forge", FileUtils.readFileFromJar(DataParser.class.getClassLoader(), "versions/forge.txt").get(0));
        versionMap.put("minecraft", FileUtils.readFileFromJar(DataParser.class.getClassLoader(), "versions/minecraft.txt").get(0));
        versionMap.put("mcp", FileUtils.readFileFromJar(DataParser.class.getClassLoader(), "versions/mcp.txt").get(0));
        versionMap.put("lunamura", FileUtils.readFileFromJar(DataParser.class.getClassLoader(), "versions/mohist.txt").get(0));

        LunamuraMCStart.MCVERSION = versionMap.get("minecraft");
    }

    public static void parseLaunchArgs() {
        OSUtil.OS os = OSUtil.getOS();
        String osName = (os != null && os.equals(OSUtil.OS.WINDOWS)) ? "win" : "unix";
        launchArgs.addAll(FileUtils.readFileFromJar(DataParser.class.getClassLoader(), "data/" + osName + "_args.txt"));
    }
}
