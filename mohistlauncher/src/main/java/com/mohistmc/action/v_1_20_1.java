package com.mohistmc.action;

import com.mohistmc.LunamuraMCStart;
import com.mohistmc.config.LunamuraConfigUtil;
import com.mohistmc.tools.FileUtils;
import com.mohistmc.tools.JarTool;
import com.mohistmc.tools.SHA256;
import com.mohistmc.util.I18n;
import com.mohistmc.util.LunamuraModuleManager;
import java.io.File;
import java.io.FileWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class v_1_20_1 {

    public static void run() {
        try {
            new Install();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class Install extends Action {

        public static ArrayList<String> launchArgs = new ArrayList<>(Arrays.asList("java", "-jar"));
        public final File fmlloader;
        public final File fmlcore;
        public final File javafmllanguage;
        public final File mclanguage;
        public final File lowcodelanguage;
        public final File lunamuraplugin;
        public final File mojmap;
        public final File mc_unpacked;
        public final File mergedMapping;

        protected Install() throws Exception {
            super();
            this.fmlloader = new File(libPath, "net/minecraftforge/fmlloader/" + mcVer + "-" + forgeVer + "/fmlloader-" + mcVer + "-" + forgeVer + ".jar");
            this.fmlcore = new File(libPath, "net/minecraftforge/fmlcore/" + mcVer + "-" + forgeVer + "/fmlcore-" + mcVer + "-" + forgeVer + ".jar");
            this.javafmllanguage = new File(libPath, "net/minecraftforge/javafmllanguage/" + mcVer + "-" + forgeVer + "/javafmllanguage-" + mcVer + "-" + forgeVer + ".jar");
            this.mclanguage = new File(libPath, "net/minecraftforge/mclanguage/" + mcVer + "-" + forgeVer + "/mclanguage-" + mcVer + "-" + forgeVer + ".jar");
            this.lowcodelanguage = new File(libPath, "net/minecraftforge/lowcodelanguage/" + mcVer + "-" + forgeVer + "/lowcodelanguage-" + mcVer + "-" + forgeVer + ".jar");
            this.lunamuraplugin = new File(libPath, "com/mohistmc/mohistplugins/mohistplugins-" + mcVer + ".jar");
            this.mojmap = new File(libPath, otherStart + "-mappings.txt");
            this.mc_unpacked = new File(libPath, otherStart + "-unpacked.jar");
            this.mergedMapping = new File(libPath, mcpStart + "-mappings-merged.txt");
            libPath();
            install();
        }

        private void install() throws Exception {
            launchArgs.add(new File(URLDecoder.decode(LunamuraModuleManager.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath(), StandardCharsets.UTF_8)).getAbsolutePath());
            launchArgs.addAll(LunamuraMCStart.mainArgs);
            copyFileFromJar(lzma, "data/server.lzma");
            copyFileFromJar(fmlloader, "data/fmlloader-" + mcVer + "-" + forgeVer + ".jar");
            copyFileFromJar(fmlcore, "data/fmlcore-" + mcVer + "-" + forgeVer + ".jar");
            copyFileFromJar(javafmllanguage, "data/javafmllanguage-" + mcVer + "-" + forgeVer + ".jar");
            copyFileFromJar(mclanguage, "data/mclanguage-" + mcVer + "-" + forgeVer + ".jar");
            copyFileFromJar(lowcodelanguage, "data/lowcodelanguage-" + mcVer + "-" + forgeVer + ".jar");
            copyFileFromJar(lunamuraplugin, "data/mohistplugins-" + mcVer + ".jar");

            if (!needsInstall()) return;
            System.out.println(I18n.as("installation.start"));

            copyFileFromJar(universalJar, "data/forge-" + mcVer + "-" + forgeVer + "-universal.jar");

            if (lunamuraVer == null || mcpVer == null) {
                System.out.println("[Lunamura] There is an error with the installation, the forge / mcp version is not set.");
                System.exit(0);
            }

            if (minecraft_server.exists()) {
                mute();
                run("net.minecraftforge.installertools.ConsoleTool",
                        new String[]{"--task", "BUNDLER_EXTRACT", "--input", minecraft_server.getPath(), "--output", libPath, "--libraries"});
                unmute();
                if (!mc_unpacked.exists()) {
                    mute();
                    run("net.minecraftforge.installertools.ConsoleTool",
                            new String[]{"--task", "BUNDLER_EXTRACT", "--input", minecraft_server.getPath(), "--output", mc_unpacked.getPath(), "--jar-only"});
                    unmute();
                }
            } else {
                System.out.println(I18n.as("installation.minecraftserver"));
            }

            if (mcpZip.exists()) {
                if (!mcpTxt.exists()) {

                    // MAKE THE MAPPINGS TXT FILE

                    System.out.println(I18n.as("installation.mcp"));
                    mute();
                    run("net.minecraftforge.installertools.ConsoleTool",
                            new String[]{"--task", "MCP_DATA", "--input", mcpZip.getPath(), "--output", mcpTxt.getPath(), "--key", "mappings"});
                    unmute();
                }
            } else {
                System.out.println(I18n.as("installation.mcpfilemissing"));
                System.exit(0);
            }

            if (JarTool.isCorrupted(extra)) {
                extra.delete();
            }
            if (JarTool.isCorrupted(slim)) {
                slim.delete();
            }
            if (JarTool.isCorrupted(srg)) {
                srg.delete();
            }

            if (!mergedMapping.exists()) {
                mute();
                run("net.minecraftforge.installertools.ConsoleTool",
                        new String[]{"--task", "MERGE_MAPPING", "--left", mcpTxt.getPath(), "--right", mojmap.getPath(), "--output", mergedMapping.getAbsolutePath(), "--classes", "--reverse-right"});
                unmute();
            }

            if (!slim.exists() || !extra.exists()) {
                mute();
                run("net.minecraftforge.jarsplitter.ConsoleTool",
                        new String[]{"--input", minecraft_server.getPath(), "--slim", slim.getPath(), "--extra", extra.getPath(), "--srg", mergedMapping.getAbsolutePath()});
                run("net.minecraftforge.jarsplitter.ConsoleTool",
                        new String[]{"--input", mc_unpacked.getPath(), "--slim", slim.getPath(), "--extra", extra.getPath(), "--srg", mergedMapping.getAbsolutePath()});
                unmute();
            }

            if (!srg.exists()) {
                mute();
                run("net.minecraftforge.fart.Main",
                        new String[]{"--input", slim.getPath(), "--output", srg.getPath(), "--names", mergedMapping.getPath(), "--ann-fix", "--ids-fix", "--src-fix", "--record-fix"});
                unmute();
            }

            String storedServerSHA256 = null;
            String storedLunamuraSHA256 = null;
            String serverSHA256 = SHA256.as(serverJar);
            String lunamuraSHA256 = SHA256.as(LunamuraMCStart.jarTool.getFile());

            if (installInfo.exists()) {
                List<String> infoLines = Files.readAllLines(installInfo.toPath());
                if (!infoLines.isEmpty()) {
                    storedServerSHA256 = infoLines.get(0);
                }
                if (infoLines.size() > 1) {
                    storedLunamuraSHA256 = infoLines.get(1);
                }
            }

            if (!serverJar.exists()
                    || storedServerSHA256 == null
                    || storedLunamuraSHA256 == null
                    || !storedServerSHA256.equals(serverSHA256)
                    || !storedLunamuraSHA256.equals(lunamuraSHA256)) {
                mute();
                run("net.minecraftforge.binarypatcher.ConsoleTool",
                        new String[]{"--clean", srg.getPath(), "--output", serverJar.getPath(), "--apply", lzma.getPath()});
                unmute();
                serverSHA256 = SHA256.as(serverJar);
            }

            FileWriter fw = new FileWriter(installInfo);
            fw.write(serverSHA256 + "\n");
            fw.write(lunamuraSHA256);
            fw.close();

            System.out.println(I18n.as("installation.finished"));
            LunamuraConfigUtil.yml.set("lunamura.installation-finished", true);
            LunamuraConfigUtil.save();
            JarTool.restartServer(launchArgs, true);
        }

        protected void libPath() throws Exception {
            File out = new File(libPath, "com/mohistmc/cache/libPath.txt");
            if (!out.exists()) {
                out.getParentFile().mkdirs();
                out.createNewFile();
            }
            FileUtils.fileWriterMethod(out.getPath(), libPath);
        }
    }
}
