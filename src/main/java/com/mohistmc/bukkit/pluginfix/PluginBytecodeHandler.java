package com.mohistmc.bukkit.pluginfix;

import com.mohistmc.LunamuraConfig;
import com.mohistmc.LunamuraMC;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Ported from CatServer.
 *
 * <p>Rewrites a few call sites inside plugin bytecode so that plugins behave correctly on a
 * hybrid Forge server:</p>
 *
 * <ul>
 *   <li>{@code CompletableFuture.runAsync(Runnable)} is redirected to a dedicated ForkJoinPool
 *       whose workers inherit the TransformingClassLoader as their context class loader.
 *       The JDK common pool uses the AppClassLoader, which makes Forge's
 *       {@code EventSubclassTransformer#getClassLoader()} blow up when a plugin loads libraries
 *       asynchronously (TrChat and friends).</li>
 *   <li>Java 21 {@code List#getFirst()} / {@code List#getLast()} are backported to
 *       {@code get(0)} / {@code get(size() - 1)} when running on an older JVM, so plugins
 *       compiled against Java 21 still load (QuickShop-Hikari 6.2.0.10 and friends).</li>
 * </ul>
 */
public final class PluginBytecodeHandler {

    private PluginBytecodeHandler() {
    }

    private static final String SELF = "com/mohistmc/bukkit/pluginfix/PluginBytecodeHandler";

    private static final boolean LOWER_THAN_JAVA_21;

    static {
        boolean lower;
        try {
            lower = Runtime.version().version().get(0) < 21;
        } catch (Throwable e) {
            lower = true;
        }
        LOWER_THAN_JAVA_21 = lower;
    }

    private static final class Holder {
        static final ForkJoinPool EXECUTOR = new ForkJoinPool(
                LunamuraConfig.catPluginExecutorMaxThreads <= 0
                        ? Runtime.getRuntime().availableProcessors()
                        : LunamuraConfig.catPluginExecutorMaxThreads,
                PluginBytecodeHandler::newForkJoinWorkerThread,
                PluginBytecodeHandler::onPluginThreadException,
                false);
    }

    private static void onPluginThreadException(Thread thread, Throwable e) {
        LunamuraMC.LOGGER.error(String.format(Locale.ROOT, "Caught exception in thread %s", thread), e);
    }

    private static ForkJoinWorkerThread newForkJoinWorkerThread(ForkJoinPool pool) {
        ForkJoinWorkerThread thread = new ForkJoinWorkerThread(pool) {
            @Override
            protected void onTermination(Throwable throwable) {
                if (throwable != null) {
                    LunamuraMC.LOGGER.warn("{} died", this.getName(), throwable);
                } else {
                    LunamuraMC.LOGGER.debug("{} shutdown", this.getName());
                }
                super.onTermination(throwable);
            }
        };
        thread.setName("lunamura-plugin-worker-" + thread.getPoolIndex());
        // Should be the TransformingClassLoader at the time the first plugin class is transformed.
        thread.setContextClassLoader(Thread.currentThread().getContextClassLoader());
        return thread;
    }

    /** Invoked from rewritten plugin bytecode. */
    public static CompletableFuture<Void> lunamura$CompletableFuture$runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, Holder.EXECUTOR);
    }

    /** Invoked from rewritten plugin bytecode. */
    public static Object lunamura$List$getFirst(List<?> list) {
        return list.get(0);
    }

    /** Invoked from rewritten plugin bytecode. */
    public static Object lunamura$List$getLast(List<?> list) {
        return list.get(list.size() - 1);
    }

    /**
     * Entry point, called from {@code CraftMagicNumbers#processClass}.
     */
    public static byte[] processPluginClass(String path, byte[] clazz) {
        if (!LunamuraConfig.catPluginBytecodeFix) {
            return clazz;
        }
        try {
            return transform(clazz);
        } catch (Throwable ex) {
            LunamuraMC.LOGGER.error("Fatal error trying to convert " + path, ex);
            return clazz;
        }
    }

    private static byte[] transform(byte[] b) {
        ClassReader cr = new ClassReader(b);
        ClassWriter cw = new ClassWriter(cr, 0);

        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(this.api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "java/util/concurrent/CompletableFuture".equals(owner)
                                && "runAsync".equals(mName)
                                && "(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;".equals(mDesc)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, SELF, "lunamura$CompletableFuture$runAsync", mDesc, false);
                        } else if (LOWER_THAN_JAVA_21 && opcode == Opcodes.INVOKEINTERFACE
                                && "java/util/List".equals(owner)
                                && "getFirst".equals(mName)
                                && "()Ljava/lang/Object;".equals(mDesc)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, SELF, "lunamura$List$getFirst", "(Ljava/util/List;)Ljava/lang/Object;", false);
                        } else if (LOWER_THAN_JAVA_21 && opcode == Opcodes.INVOKEINTERFACE
                                && "java/util/List".equals(owner)
                                && "getLast".equals(mName)
                                && "()Ljava/lang/Object;".equals(mDesc)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, SELF, "lunamura$List$getLast", "(Ljava/util/List;)Ljava/lang/Object;", false);
                        } else {
                            super.visitMethodInsn(opcode, owner, mName, mDesc, isInterface);
                        }
                    }
                };
            }
        }, 0);

        return cw.toByteArray();
    }
}
