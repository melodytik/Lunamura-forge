/*
 * PRTS - Arclight/Luminara fork
 * Copyright (c) 2024-2026 ElainAwa
 *
 * This file adapts code from:
 *  - HariPlayer by JustHari01 (https://modrinth.com/mod/hp-hariplayer), MIT.
 *  - VMP (Very Many Players) by ishland (https://github.com/RelativityMC/VMP-fabric), MIT.
 *  - Paper by PaperMC/Spottedleaf (https://github.com/PaperMC/Paper), GPL-3.0.
 * Original code Copyright (c) respective authors.
 *
 * Ported from PRTS (Arclight) to Lunamura (Mohist/Forge 1.20.1) under com.mohistmc.
 */

/* Ported from HariPlayer (VMP fork) -> Paper's Delayed8WayDistancePropagator2D. */

package com.mohistmc.optimizations.ticketpropagator;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.bytes.ByteArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.world.level.ChunkPos;

public final class Delayed8WayDistancePropagator2D {

    // this map is considered "stale" unless updates are propagated.
    protected final LevelMap levels = new LevelMap(8192 * 2, 0.6f);

    // this map is never stale
    protected final Long2ByteOpenHashMap sources = new Long2ByteOpenHashMap(4096, 0.6f);

    // Generally updates to positions are made close to other updates, so we link to decrease cache misses when
    protected final LongLinkedOpenHashSet updatedSources = new LongLinkedOpenHashSet();

    @FunctionalInterface
    public static interface LevelChangeCallback {

        /**
         * This can be called for intermediate updates. So do not rely on newLevel being close to or
         * the exact level that is expected after a full propagation has occured.
         */
        public void onLevelUpdate(final long coordinate, final byte oldLevel, final byte newLevel);

    }

    protected final LevelChangeCallback changeCallback;

    public Delayed8WayDistancePropagator2D() {
        this(null);
    }

    public Delayed8WayDistancePropagator2D(final LevelChangeCallback changeCallback) {
        this.changeCallback = changeCallback;
    }

    public int getLevel(final long pos) {
        return this.levels.get(pos);
    }

    public int getLevel(final int x, final int z) {
        return this.levels.get(ChunkPos.asLong(x, z));
    }

    public void setSource(final int x, final int z, final int level) {
        this.setSource(ChunkPos.asLong(x, z), level);
    }

    public void setSource(final long coordinate, final int level) {
        if ((level & 63) != level || level == 0) {
            throw new IllegalArgumentException("Level must be in (0, 63], not " + level);
        }

        final byte byteLevel = (byte) level;
        final byte oldLevel = this.sources.put(coordinate, byteLevel);

        if (oldLevel == byteLevel) {
            return; // nothing to do
        }

        // queue to update later
        this.updatedSources.add(coordinate);
    }

    public void removeSource(final int x, final int z) {
        this.removeSource(ChunkPos.asLong(x, z));
    }

    public void removeSource(final long coordinate) {
        if (this.sources.remove(coordinate) != 0) {
            this.updatedSources.add(coordinate);
        }
    }

    // queues used for BFS propagating levels
    protected final WorkQueue[] levelIncreaseWorkQueues = new WorkQueue[64];
    {
        for (int i = 0; i < this.levelIncreaseWorkQueues.length; ++i) {
            this.levelIncreaseWorkQueues[i] = new WorkQueue();
        }
    }
    protected final WorkQueue[] levelRemoveWorkQueues = new WorkQueue[64];
    {
        for (int i = 0; i < this.levelRemoveWorkQueues.length; ++i) {
            this.levelRemoveWorkQueues[i] = new WorkQueue();
        }
    }
    protected long levelIncreaseWorkQueueBitset;
    protected long levelRemoveWorkQueueBitset;

    protected final void addToIncreaseWorkQueue(final long coordinate, final byte level) {
        final WorkQueue queue = this.levelIncreaseWorkQueues[level];
        queue.queuedCoordinates.enqueue(coordinate);
        queue.queuedLevels.enqueue(level);

        this.levelIncreaseWorkQueueBitset |= (1L << level);
    }

    protected final void addToIncreaseWorkQueue(final long coordinate, final byte index, final byte level) {
        final WorkQueue queue = this.levelIncreaseWorkQueues[index];
        queue.queuedCoordinates.enqueue(coordinate);
        queue.queuedLevels.enqueue(level);

        this.levelIncreaseWorkQueueBitset |= (1L << index);
    }

    protected final void addToRemoveWorkQueue(final long coordinate, final byte level) {
        final WorkQueue queue = this.levelRemoveWorkQueues[level];
        queue.queuedCoordinates.enqueue(coordinate);
        queue.queuedLevels.enqueue(level);

        this.levelRemoveWorkQueueBitset |= (1L << level);
    }

    public boolean propagateUpdates() {
        if (this.updatedSources.isEmpty()) {
            return false;
        }

        boolean ret = false;

        for (final LongIterator iterator = this.updatedSources.iterator(); iterator.hasNext();) {
            final long coordinate = iterator.nextLong();

            final byte currentLevel = this.levels.get(coordinate);
            final byte updatedSource = this.sources.get(coordinate);

            if (currentLevel == updatedSource) {
                continue;
            }
            ret = true;

            if (updatedSource > currentLevel) {
                // level increase
                this.addToIncreaseWorkQueue(coordinate, updatedSource);
            } else {
                // level decrease
                this.addToRemoveWorkQueue(coordinate, currentLevel);
                // if the current coordinate is a source, then the decrease propagation will detect that and queue
            }
        }

        this.updatedSources.clear();

        // propagate source level increases first for performance reasons (in crowded areas hopefully the additions
        this.propagateIncreases();

        // now we propagate the decreases (which will then re-propagate clobbered sources)
        this.propagateDecreases();

        return ret;
    }

    protected void propagateIncreases() {
        for (int queueIndex = 63 ^ Long.numberOfLeadingZeros(this.levelIncreaseWorkQueueBitset);
             this.levelIncreaseWorkQueueBitset != 0L;
             this.levelIncreaseWorkQueueBitset ^= (1L << queueIndex), queueIndex = 63 ^ Long.numberOfLeadingZeros(this.levelIncreaseWorkQueueBitset)) {

            final WorkQueue queue = this.levelIncreaseWorkQueues[queueIndex];
            while (!queue.queuedLevels.isEmpty()) {
                final long coordinate = queue.queuedCoordinates.removeFirstLong();
                byte level = queue.queuedLevels.removeFirstByte();

                final boolean neighbourCheck = level < 0;

                final byte currentLevel;
                if (neighbourCheck) {
                    level = (byte) -level;
                    currentLevel = this.levels.get(coordinate);
                } else {
                    currentLevel = this.levels.putIfGreater(coordinate, level);
                }

                if (neighbourCheck) {
                    // used when propagating from decrease to indicate that this level needs to check its neighbours

                    if (currentLevel != level) {
                        // something caused the level to change, which means something propagated to it (which means
                        continue;
                    }
                } else if (currentLevel >= level) {
                    // something higher/equal propagated
                    continue;
                }
                if (this.changeCallback != null) {
                    this.changeCallback.onLevelUpdate(coordinate, currentLevel, level);
                }

                if (level == 1) {
                    // can't propagate 0 to neighbours
                    continue;
                }

                // propagate to neighbours
                final byte neighbourLevel = (byte) (level - 1);
                final int x = (int) coordinate;
                final int z = (int) (coordinate >>> 32);

                for (int dx = -1; dx <= 1; ++dx) {
                    for (int dz = -1; dz <= 1; ++dz) {
                        if ((dx | dz) == 0) {
                            // already propagated to coordinate
                            continue;
                        }

                        // sure we can check the neighbour level in the map right now and avoid a propagation,
                        final long neighbourCoordinate = ChunkPos.asLong(x + dx, z + dz);
                        this.addToIncreaseWorkQueue(neighbourCoordinate, neighbourLevel);
                    }
                }
            }
        }
    }

    protected void propagateDecreases() {
        for (int queueIndex = 63 ^ Long.numberOfLeadingZeros(this.levelRemoveWorkQueueBitset);
             this.levelRemoveWorkQueueBitset != 0L;
             this.levelRemoveWorkQueueBitset ^= (1L << queueIndex), queueIndex = 63 ^ Long.numberOfLeadingZeros(this.levelRemoveWorkQueueBitset)) {

            final WorkQueue queue = this.levelRemoveWorkQueues[queueIndex];
            while (!queue.queuedLevels.isEmpty()) {
                final long coordinate = queue.queuedCoordinates.removeFirstLong();
                final byte level = queue.queuedLevels.removeFirstByte();

                final byte currentLevel = this.levels.removeIfGreaterOrEqual(coordinate, level);
                if (currentLevel == 0) {
                    // something else removed
                    continue;
                }

                if (currentLevel > level) {
                    // something higher propagated here or we hit the propagation of another source
                    this.addToIncreaseWorkQueue(coordinate, currentLevel, (byte) -currentLevel); // indicate to the increase code that the level's neighbours need checking
                    continue;
                }

                if (this.changeCallback != null) {
                    this.changeCallback.onLevelUpdate(coordinate, currentLevel, (byte) 0);
                }

                final byte source = this.sources.get(coordinate);
                if (source != 0) {
                    // must re-propagate source later
                    this.addToIncreaseWorkQueue(coordinate, source);
                }

                if (level == 0) {
                    // can't propagate -1 to neighbours
                    continue;
                }

                // propagate to neighbours
                final byte neighbourLevel = (byte) (level - 1);
                final int x = (int) coordinate;
                final int z = (int) (coordinate >>> 32);

                for (int dx = -1; dx <= 1; ++dx) {
                    for (int dz = -1; dz <= 1; ++dz) {
                        if ((dx | dz) == 0) {
                            // already propagated to coordinate
                            continue;
                        }

                        // sure we can check the neighbour level in the map right now and avoid a propagation,
                        final long neighbourCoordinate = ChunkPos.asLong(x + dx, z + dz);
                        this.addToRemoveWorkQueue(neighbourCoordinate, neighbourLevel);
                    }
                }
            }
        }

        // propagate sources we clobbered in the process
        this.propagateIncreases();
    }

    protected static final class LevelMap extends Long2ByteOpenHashMap {
        public LevelMap() {
            super();
        }

        public LevelMap(final int expected, final float loadFactor) {
            super(expected, loadFactor);
        }

        // copied from superclass
        private int find(final long k) {
            if (k == 0L) {
                return this.containsNullKey ? this.n : -(this.n + 1);
            } else {
                final long[] key = this.key;
                long curr;
                int pos;
                if ((curr = key[pos = (int) HashCommon.mix(k) & this.mask]) == 0L) {
                    return -(pos + 1);
                } else if (k == curr) {
                    return pos;
                } else {
                    while ((curr = key[pos = pos + 1 & this.mask]) != 0L) {
                        if (k == curr) {
                            return pos;
                        }
                    }

                    return -(pos + 1);
                }
            }
        }

        // copied from superclass
        private void insert(final int pos, final long k, final byte v) {
            if (pos == this.n) {
                this.containsNullKey = true;
            }

            this.key[pos] = k;
            this.value[pos] = v;
            if (this.size++ >= this.maxFill) {
                this.rehash(HashCommon.arraySize(this.size + 1, this.f));
            }
        }

        // copied from superclass
        public byte putIfGreater(final long key, final byte value) {
            final int pos = this.find(key);
            if (pos < 0) {
                if (this.defRetValue < value) {
                    this.insert(-pos - 1, key, value);
                }
                return this.defRetValue;
            } else {
                final byte curr = this.value[pos];
                if (value > curr) {
                    this.value[pos] = value;
                    return curr;
                }
                return curr;
            }
        }

        // copied from superclass
        private void removeEntry(final int pos) {
            --this.size;
            this.shiftKeys(pos);
            if (this.n > this.minN && this.size < this.maxFill / 4 && this.n > 16) {
                this.rehash(this.n / 2);
            }
        }

        // copied from superclass
        private void removeNullEntry() {
            this.containsNullKey = false;
            --this.size;
            if (this.n > this.minN && this.size < this.maxFill / 4 && this.n > 16) {
                this.rehash(this.n / 2);
            }
        }

        // copied from superclass
        public byte removeIfGreaterOrEqual(final long key, final byte value) {
            if (key == 0L) {
                if (!this.containsNullKey) {
                    return this.defRetValue;
                }
                final byte current = this.value[this.n];
                if (value >= current) {
                    this.removeNullEntry();
                    return current;
                }
                return current;
            } else {
                long[] keys = this.key;
                byte[] values = this.value;
                long curr;
                int pos;
                if ((curr = keys[pos = (int) HashCommon.mix(key) & this.mask]) == 0L) {
                    return this.defRetValue;
                } else if (key == curr) {
                    final byte current = values[pos];
                    if (value >= current) {
                        this.removeEntry(pos);
                        return current;
                    }
                    return current;
                } else {
                    while ((curr = keys[pos = pos + 1 & this.mask]) != 0L) {
                        if (key == curr) {
                            final byte current = values[pos];
                            if (value >= current) {
                                this.removeEntry(pos);
                                return current;
                            }
                            return current;
                        }
                    }

                    return this.defRetValue;
                }
            }
        }
    }

    protected static final class WorkQueue {

        public final NoResizeLongArrayFIFODeque queuedCoordinates = new NoResizeLongArrayFIFODeque();
        public final NoResizeByteArrayFIFODeque queuedLevels = new NoResizeByteArrayFIFODeque();

    }

    protected static final class NoResizeLongArrayFIFODeque extends LongArrayFIFOQueue {

        /**
         * Assumes non-empty. If empty, undefined behaviour.
         */
        public long removeFirstLong() {
            // copied from superclass
            long t = this.array[this.start];
            if (++this.start == this.length) {
                this.start = 0;
            }

            return t;
        }
    }

    protected static final class NoResizeByteArrayFIFODeque extends ByteArrayFIFOQueue {

        /**
         * Assumes non-empty. If empty, undefined behaviour.
         */
        public byte removeFirstByte() {
            // copied from superclass
            byte t = this.array[this.start];
            if (++this.start == this.length) {
                this.start = 0;
            }

            return t;
        }
    }
}
