package com.mohistmc;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lunamura 原创优化：异步玩家数据保存。
 * 主线程只做 NBT 序列化（快照），gzip 压缩 + 写盘 + 原子替换下放到后台单线程。
 * 通过 JVM shutdown hook 兜底 flush，保证关服时待写数据不丢失。
 */
public final class LunamuraAsyncSave {

    private static final Logger LOGGER = LoggerFactory.getLogger("Lunamura");
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Lunamura-AsyncSave-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });
    private static final AtomicInteger pending = new AtomicInteger();
    private static final Object lock = new Object();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(LunamuraAsyncSave::flush, "Lunamura-AsyncSave-Flush"));
    }

    private LunamuraAsyncSave() {
    }

    /**
     * 在后台线程执行玩家数据的压缩写盘（tag 必须是主线程已序列化好的快照，独立于玩家活数据）。
     */
    public static void savePlayerData(CompoundTag tag, File dir, String uuid, String name) {
        pending.incrementAndGet();
        SAVE_EXECUTOR.submit(() -> {
            try {
                File file1 = File.createTempFile(uuid + "-", ".dat", dir);
                NbtIo.writeCompressed(tag, file1);
                File file2 = new File(dir, uuid + ".dat");
                File file3 = new File(dir, uuid + ".dat_old");
                Util.safeReplaceFile(file2, file1, file3);
            } catch (Exception e) {
                LOGGER.warn("Failed to async save player data for {}", name, e);
            } finally {
                pending.decrementAndGet();
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        });
    }

    /**
     * 在后台单线程串行执行一次写盘任务（保证同一文件不会被并发写，关服时由 flush 兜底）。
     */
    public static void runAsyncWrite(Runnable task) {
        pending.incrementAndGet();
        SAVE_EXECUTOR.submit(() -> {
            try {
                task.run();
            } finally {
                pending.decrementAndGet();
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        });
    }

    /**
     * 阻塞直到所有待写数据落盘（关服兜底）。
     */
    public static void flush() {
        synchronized (lock) {
            while (pending.get() > 0) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
