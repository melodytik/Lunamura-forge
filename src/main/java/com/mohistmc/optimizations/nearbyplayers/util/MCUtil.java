package com.mohistmc.optimizations.nearbyplayers.util;

/** Local re-implementation of the coordinate-packing helpers. */
public class MCUtil {

    public static long getCoordinateKey(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    public static int getCoordinateX(long key) {
        return (int) (key & 0xFFFFFFFFL);
    }

    public static int getCoordinateZ(long key) {
        return (int) (key >>> 32);
    }
}
