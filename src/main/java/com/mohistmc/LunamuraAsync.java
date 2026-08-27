package com.mohistmc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lunamura 自研多线程模块（2026-08-27）。
 *
 * 设计原则（相比被拆除的 PRTS 移植模块）：
 * 1. 零共享可变状态：只接管"已经构建完成的不可变快照"的异步处理，
 *    决不把活体世界对象（Entity/Chunk/BlockEntity）暴露给工作线程 —— 从根上杜绝竞态。
 * 2. 统一管理：所有后台工作共用一个固定大小线程池（lunamura.async_threads，默认 2），
 *    线程均为 daemon + 低优先级，避免与主循环抢核。
 * 3. 优雅关闭：JVM shutdown hook 阻塞等待队列清空（drain），关服零数据丢失。
 *
 * 当前子任务：
 *  - savePlayerData: 玩家数据压缩写盘 + 原子替换（原 LunamuraAsyncSave 收编）
 *  - runAsyncWrite : 通用一次性 IO 任务
 */
public final class LunamuraAsync {

    private static final Logger LOGGER = LoggerFactory.getLogger("Lunamura");

    private static volatile ExecutorService EXECUTOR;
    private static final AtomicInteger PENDING = new AtomicInteger();
    private static final Object LOCK = new Object();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(LunamuraAsync::flush, "Lunamura-Async-Flush"));
    }

    private LunamuraAsync() {
    }

    /** 懒初始化：首次使用时按配置建池。 */
    private static ExecutorService executor() {
        ExecutorService e = EXECUTOR;
        if (e == null) {
            synchronized (LunamuraAsync.class) {
                e = EXECUTOR;
                if (e == null) {
                    int threads = Math.max(1, com.mohistmc.LunamuraConfig.async_threads);
                    final AtomicInteger counter = new AtomicInteger();
                    e = Executors.newFixedThreadPool(threads, new ThreadFactory() {
                        @Override
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "Lunamura-Async-" + counter.incrementAndGet());
                            t.setDaemon(true);
                            t.setPriority(Thread.NORM_PRIORITY - 1);
                            return t;
                        }
                    });
                    EXECUTOR = e;
                }
            }
        }
        return e;
    }

    /**
     * 在后台线程执行玩家数据的压缩写盘（tag 必须是主线程已序列化好的快照，独立于玩家活数据）。
     */
    public static void savePlayerData(CompoundTag tag, java.io.File dir, String uuid, String name) {
        submit(() -> {
            try {
                java.io.File file1 = java.io.File.createTempFile(uuid + "-", ".dat", dir);
                NbtIo.writeCompressed(tag, file1);
                java.io.File file2 = new java.io.File(dir, uuid + ".dat");
                java.io.File file3 = new java.io.File(dir, uuid + ".dat_old");
                Util.safeReplaceFile(file2, file1, file3);
            } catch (Exception ex) {
                LOGGER.warn("Failed to async save player data for {}", name, ex);
            }
        });
    }

    /**
     * 提交一个通用后台 IO 任务。
     */
    public static void submit(Runnable task) {
        PENDING.incrementAndGet();
        executor().submit(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                LOGGER.warn("Lunamura async task failed", t);
            } finally {
                PENDING.decrementAndGet();
                synchronized (LOCK) {
                    LOCK.notifyAll();
                }
            }
        });
    }

    /**
     * 提交带返回值的任务。
     */
    public static <T> Future<T> submit(java.util.concurrent.Callable<T> task) {
        PENDING.incrementAndGet();
        return executor().submit(() -> {
            try {
                return task.call();
            } finally {
                PENDING.decrementAndGet();
                synchronized (LOCK) {
                    LOCK.notifyAll();
                }
            }
        });
    }

    /**
     * 阻塞直到所有待执行任务完成（关服兜底；shutdown 后允许调用方判断超时）。
     */
    public static void flush() {
        drain(Long.MAX_VALUE);
    }

    /**
     * 带超时的 drain。返回是否在时限内清空。
     */
    public static boolean drain(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (LOCK) {
            while (PENDING.get() > 0) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    return PENDING.get() <= 0;
                }
                try {
                    LOCK.wait(Math.min(left, 500));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return PENDING.get() <= 0;
                }
            }
        }
        return true;
    }

    /**
     * 有序停机（正常 stop 流程调用）：等待既有任务完成并拒绝新任务。
     */
    public static List<Runnable> shutdownGracefully(long timeoutMs) {
        List<Runnable> leftover = new ArrayList<>();
        ExecutorService e = EXECUTOR;
        if (e != null) {
            e.shutdown();
            try {
                if (!e.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                    leftover = e.shutdownNow();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                leftover = e.shutdownNow();
            }
            EXECUTOR = null;
        }
        return leftover;
    }
}
