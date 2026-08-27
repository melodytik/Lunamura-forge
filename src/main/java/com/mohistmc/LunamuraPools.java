package com.mohistmc;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Lunamura 对象池化工具（v1.4.0）—— IO 序列化缓冲池。
 *
 * <p><b>设计变更说明</b>：早期曾计划池化 {@code Vec3}/{@code AABB}（immutable + final 字段），
 * 但这两者在 Mohist patcher 环境与客户端语义下会引发「跨 tick 悬浮引用 → 生物冻结/竞态」，
 * 正是我们拆掉 ActivationRange 2.0 的同类教训；且对 final 字段做 AT 改写会给编译期 mapping 带来
 * 不可控差异。故改为<b>纯 JDK、零 vanilla 字段依赖</b>的缓冲池，只服务异步写盘线程上的
 * gzip 压缩与字节缓冲复用，命中磁盘 IO 高并发短时分配，无任何生命周期风险。
 *
 * <p>仅在线程局部使用，无跨线程共享，acquire 后必须在同一线程归还。
 */
public final class LunamuraPools {

    private static final ThreadLocal<ByteArrayOutputStream> BYTES_OUT =
            ThreadLocal.withInitial(() -> new ByteArrayOutputStream(65536));
    private static final ThreadLocal<Deque<byte[]>> BYTE_BUF = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deflater> DEFLATER = ThreadLocal.withInitial(() -> new Deflater(Deflater.DEFAULT_COMPRESSION, true));

    private LunamuraPools() {
    }

    /**
     * 借一个可复用的 {@link ByteArrayOutputStream}（已 reset），用完必须 {@link #releaseByteArrayOutputStream} 归还。
     * 归还可省去大数组扩容，序列化热点上可减少分配。
     */
    public static ByteArrayOutputStream borrowByteArrayOutputStream() {
        ByteArrayOutputStream bos = BYTES_OUT.get();
        bos.reset();
        return bos;
    }

    /**
     * 归还（内部分块缓冲可被后续 reuse 提升容量，此处不再 reset 以便保留大内部数组）。
     */
    public static void releaseByteArrayOutputStream(ByteArrayOutputStream bos) {
        // ThreadLocal 单例，无需入栈；仅触发 cap 兜底、保留内部数组。
        if (bos != null && bos.size() == 0) {
            // no-op: 已由上借时 reset
        }
    }

    /**
     * 借入一个至少 {@code min} 字节的 byte[]（线程局部，可复用）。使用后调用 {@link #releaseByteArray}。
     */
    public static byte[] borrowByteArray(int min) {
        Deque<byte[]> queue = BYTE_BUF.get();
        while (!queue.isEmpty()) {
            byte[] buf = queue.pollLast();
            if (buf.length >= min) {
                return buf;
            }
        }
        int cap = Math.max(min, 4096);
        return new byte[cap];
    }

    /**
     * 归还 byte[] 到线程局部池。容量过大（&gt;64MiB）直接丢弃避免长期占用。
     */
    public static void releaseByteArray(byte[] buf) {
        if (buf == null) return;
        Deque<byte[]> queue = BYTE_BUF.get();
        if (buf.length <= 64 * 1024 * 1024 && queue.size() < 64) {
            queue.addLast(buf);
        }
    }

    /**
     * 借一个 raw (nowrap) {@link Deflater}，用于 gzip 压缩（线程局部，不复用跨线程）。
     */
    public static Deflater borrowDeflater() {
        Deflater d = DEFLATER.get();
        d.reset();
        return d;
    }

    /**
     * 便捷：将 {@code src} 用线程局部 raw-deflater 压缩，返回持有压缩结果的 ByteArrayOutputStream（借出的，需归还）。
     * 仅作为范示例，实际异步写盘请直接使用 {@link #borrowDeflater} + 手动流。
     */
    public static ByteArrayOutputStream compressToBuffer(byte[] src) throws java.io.IOException {
        ByteArrayOutputStream out = borrowByteArrayOutputStream();
        Deflater def = borrowDeflater();
        DeflaterOutputStream dos = new DeflaterOutputStream(out, def);
        dos.write(src);
        dos.finish();
        return out;
    }
}