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

package com.mohistmc.libraries;

import com.mohistmc.LunamuraMCStart;
import com.mohistmc.tools.SHA256;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.SneakyThrows;
import lombok.ToString;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;

@ToString
public class LibrariesDownloadQueue {

    @ToString.Exclude
    public final Set<Libraries> allLibraries = new HashSet<>();
    @ToString.Exclude
    private final Set<Libraries> fail = new HashSet<>();
    @ToString.Exclude
    public InputStream inputStream = null;
    @ToString.Exclude
    public Set<Libraries> need_download = new LinkedHashSet<>();

    public String parentDirectory = "libraries";
    public String systemProperty = null;
    public boolean debug = false;


    public static LibrariesDownloadQueue create() {
        return new LibrariesDownloadQueue();
    }

    private static boolean isTargetFile(JarEntry entry) {
        String name = entry.getName().toLowerCase();
        return name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".txt");
    }

    /**
     * Set the input stream for the list that needs to be downloaded
     *
     * @param inputStream The input stream of the target file
     * @return Returns the current real column
     */
    public LibrariesDownloadQueue inputStream(InputStream inputStream) {
        this.inputStream = inputStream;
        return this;
    }

    /**
     * Set the file download directory
     *
     * @param parentDirectory The path to which the file is downloaded
     * @return Returns the current real column
     */
    public LibrariesDownloadQueue parentDirectory(String parentDirectory) {
        this.parentDirectory = parentDirectory;
        return this;
    }

    /**
     * Construct the final column
     *
     * @return Construct the final column
     */
    @SneakyThrows
    public LibrariesDownloadQueue build() {
        scanFromJar();
        return this;
    }

    /**
     * Download in the form of a progress bar
     */
    public void progressBar() {
        if (needDownload()) {
            ProgressBarBuilder builder = new ProgressBarBuilder()
                    .setTaskName("")
                    .setStyle(ProgressBarStyle.ASCII)
                    .setUpdateIntervalMillis(100)
                    .setInitialMax(need_download.size());
            try (ProgressBar pb = builder.build()) {
                for (Libraries lib : need_download) {
                    File file = new File(parentDirectory, lib.path);
                    file.getParentFile().mkdirs();
                    String url = "META-INF/" + file.getPath().replaceAll("\\\\", "/");
                    if (copyFileFromJar(file, url, lib)) {
                        debug("downloadFile: OK");
                        fail.remove(lib);
                    } else {
                        debug("downloadFile: No " + url);
                        fail.add(lib);
                    }
                    pb.step();
                }
            }
        }
        if (!fail.isEmpty()) {
            progressBar();
        }
    }

    protected boolean copyFileFromJar(File file, String pathInJar, Libraries lib) {
        InputStream is = LunamuraMCStart.class.getClassLoader().getResourceAsStream(pathInJar);
        if (file.exists()) return true;
        if (!SHA256.is(is, lib.getSha256()) || file.length() <= 1) {
            file.getParentFile().mkdirs();
            if (is != null) {
                try {
                    file.createNewFile();
                    Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return true;
                } catch (IOException ignored) {
                }
            } else {
                System.out.println("[Lunamura] The file " + file.getPath()+ " doesn't exists in the Lunamura jar !");
                return false;
            }
        }
        return true;
    }

    public boolean needDownload() {
        for (Libraries libraries : allLibraries) {
            File lib = new File(parentDirectory, libraries.path);
            if (lib.exists() && SHA256.is(lib, libraries.sha256)) {
                continue;
            }
            debug("sha256: %s : %s %s%n".formatted(lib, SHA256.as(lib), libraries.sha256));
            need_download.add(libraries);
        }
        return !need_download.isEmpty();
    }

    public void scanFromJar() throws IOException {
        Enumeration<URL> resources = LibrariesDownloadQueue.class.getClassLoader().getResources("META-INF/libraries");
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            if ("jar".equals(url.getProtocol())) {
                JarURLConnection jarConnection = (JarURLConnection) url.openConnection();
                JarFile jarFile = jarConnection.getJarFile();
                String entryPrefix = jarConnection.getEntryName();

                jarFile.stream()
                        .filter(entry -> !entry.isDirectory())
                        .filter(entry -> entry.getName().startsWith(entryPrefix))
                        .filter(LibrariesDownloadQueue::isTargetFile)
                        .forEach(entry -> {
                            String line = entry.getName().substring(entryPrefix.length());
                            InputStream is = LunamuraMCStart.class.getClassLoader().getResourceAsStream(entry.getName());
                            Libraries libraries = new Libraries(line, SHA256.as(is), entry.getSize());
                            allLibraries.add(libraries);
                            debug("Find the resource: " + libraries);
                        });
            }
        }
    }

    public void debug(String log) {
        if (debug) System.out.println(log + "\n");
    }
}
