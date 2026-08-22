/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

public class ImmediateWindowHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private static ImmediateWindowProvider provider;

    private static ProgressMeter earlyProgress;
    public static void load(final String launchTarget, final String[] arguments) {
        final var serviceLayer = Launcher.INSTANCE.findLayerManager()
            .flatMap(manager -> manager.getLayer(IModuleLayerManager.Layer.SERVICE))
            .orElse(null);
        final var providerName = FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER);

        if (!List.of("forgeclient", "forgeclientuserdev", "forgeclientdev").contains(launchTarget)) {
            LOGGER.info("ImmediateWindowProvider not loading because launch target is {}", launchTarget);
        } else if (!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) {
            LOGGER.info("ImmediateWindowProvider not loading because splash screen is disabled");
        } else if (providerName == null) {
            LOGGER.info("ImmediateWindowProvider not loading because splash screen provider is null");
        } else if (serviceLayer == null) {
            LOGGER.error("Failed to find service layer for ImmediateWindowProvider");
        } else {
            LOGGER.info("Loading ImmediateWindowProvider {}", providerName);

            for (var itr = ServiceLoader.load(serviceLayer, ImmediateWindowProvider.class).iterator(); itr.hasNext(); ) {
                try {
                    var srvc = itr.next();
                    if (providerName.equals(srvc.name())) {
                        provider = new Wrapper(srvc);
                        break;
                    }
                } catch (ServiceConfigurationError e) {
                    LOGGER.error("Failed to initalize ImmediateWindowProvider Service", e);
                }
            }

            if (provider == null)
                LOGGER.info("Failed to find ImmediateWindowProvider {}, disabling", providerName);
        }

        // Only update config if the provider isn't the dummy provider
        if (provider != null)
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, provider.name());
        else
            provider = new DummyProvider();

        FMLLoader.progressWindowTick = provider.initialize(arguments);
        earlyProgress = StartupNotificationManager.addProgressBar("EARLY", 0);
        earlyProgress.label("Bootstrapping Minecraft");
    }

    public static long setupMinecraftWindow(final IntSupplier width, final IntSupplier height, final Supplier<String> title, final LongSupplier monitor) {
        return provider.setupMinecraftWindow(width, height, title, monitor);
    }

    public static boolean positionWindow(Optional<Object> monitor,IntConsumer widthSetter, IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
        return provider.positionWindow(monitor, widthSetter, heightSetter, xSetter, ySetter);
    }

    public static void updateFBSize(IntConsumer width, IntConsumer height) {
        provider.updateFramebufferSize(width, height);
    }

    public static <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
        earlyProgress.complete();
        return provider.loadingOverlay(mc, ri, ex, fade);
    }

    public static void acceptGameLayer(final ModuleLayer layer) {
        provider.updateModuleReads(layer);
    }

    public static void renderTick() {
        provider.periodicTick();
    }

    public static String getGLVersion() {
        return provider.getGLVersion();
    }

    public static void updateProgress(final String message) {
        earlyProgress.label(message);
    }

    private record DummyProvider() implements ImmediateWindowProvider {
        private static Method NV_HANDOFF;
        private static Method NV_POSITION;
        private static Method NV_OVERLAY;
        private static Method NV_VERSION;

        @Override
        public String name() {
            return "dummyprovider";
        }

        @Override
        public Runnable initialize(String[] args) {
            return () -> {};
        }

        @Override
        public void updateFramebufferSize(final IntConsumer width, final IntConsumer height) {
        }

        @Override
        public long setupMinecraftWindow(final IntSupplier width, final IntSupplier height, final Supplier<String> title, final LongSupplier monitor) {
            try {
                var longsupplier = (LongSupplier)NV_HANDOFF.invoke(null, width, height, title, monitor);
                return longsupplier.getAsLong();
            } catch (Throwable e) {
                throw new IllegalStateException("How did you get here?", e);
            }
        }

        public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
            try {
                return (boolean)NV_POSITION.invoke(null, monitor, widthSetter, heightSetter, xSetter, ySetter);
            } catch (Throwable e) {
                throw new IllegalStateException("How did you get here?", e);
            }
        }
        @SuppressWarnings("unchecked")
        public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
            try {
                return (Supplier<T>) NV_OVERLAY.invoke(null, mc, ri, ex, fade);
            } catch (Throwable e) {
                throw new IllegalStateException("How did you get here?", e);
            }
        }

        @Override
        public String getGLVersion() {
            try {
                return (String) NV_VERSION.invoke(null);
            } catch (Throwable e) {
                return "3.2"; // Vanilla sets 3.2 in com.mojang.blaze3d.platform.Window
            }
        }

        @Override
        public void updateModuleReads(final ModuleLayer layer) {
            var fm = layer.findModule("forge");
            if (fm.isPresent()) {
                getClass().getModule().addReads(fm.get());
                var clz = fm.map(l -> Class.forName(l, "net.minecraftforge.client.loading.NoVizFallback")).orElseThrow();
                var methods = Arrays.stream(clz.getMethods()).filter(m -> Modifier.isStatic(m.getModifiers())).collect(Collectors.toMap(Method::getName, Function.identity()));
                NV_HANDOFF = methods.get("windowHandoff");
                NV_OVERLAY = methods.get("loadingOverlay");
                NV_POSITION = methods.get("windowPositioning");
                NV_VERSION = methods.get("glVersion");
            }
        }

        @Override
        public void periodicTick() {
            // NOOP
        }
    }

    // We create a wrapper because we want to log errors to the log files, which not all paths of ModLauncher do. Little dirty, but works for now.
    private static class Wrapper implements ImmediateWindowProvider {
        private final ImmediateWindowProvider delegate;

        private Wrapper(ImmediateWindowProvider delegate) {
            this.delegate = delegate;
        }

        public String name() {
            try {
                return delegate.name();
            } catch (Throwable t) {
                LOGGER.error("Failed to call provider.name", t);
                throw t;
            }
        }

        public Runnable initialize(String[] arguments) {
            try {
                return delegate.initialize(arguments);
            } catch (Throwable t) {
                LOGGER.error("Failed to call provider.initialize", t);
                throw t;
            }
        }

        public void updateFramebufferSize(IntConsumer width, IntConsumer height) {
            try {
                delegate.updateFramebufferSize(width, height);
            } catch (Throwable t) {
                LOGGER.error("Failed to call provider.updateFramebufferSize", t);
                throw t;
            }
        }

        public long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
            try {
                return delegate.setupMinecraftWindow(width, height, title, monitor);
            } catch (Throwable t) {
                LOGGER.error("Failed to call provider.setupMinecraftWindow", t);
                throw t;
            }
        }

        public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
            try {
                return delegate.positionWindow(monitor, widthSetter, heightSetter, xSetter, ySetter);
            } catch (Throwable t) {
                LOGGER.error("Failed to call provider.positionWindow", t);
                throw t;
            }
        }

        public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
            try {
                return delegate.loadingOverlay(mc, ri, ex, fade);
            } catch (Throwable t) {
                LOGGER.error("Failed to call provider.loadingOverlay", t);
                throw t;
            }
        }

        public void updateModuleReads(ModuleLayer layer) {
            try {
                delegate.updateModuleReads(layer);
            } catch (Throwable t) {
                LOGGER.error("Failed to call provider.updateModuleReads", t);
                throw t;
            }
        }

        public void periodicTick() {
            try {
                delegate.periodicTick();
            } catch (Throwable t) {
                LOGGER.error("Failed to call provider.periodicTick", t);
                throw t;
            }
        }

        public String getGLVersion() {
            try {
                return delegate.getGLVersion();
            } catch (Throwable t) {
                LOGGER.error("Failed to call provider.getGLVersion", t);
                throw t;
            }
        }
    }
}
