package com.github.tatercertified.slopium;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RamChunkStore {
    private static final ConcurrentHashMap<String, RamChunkStore> STORES = new ConcurrentHashMap<>();
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase();
    private static final boolean SUPPORTED = (OS_NAME.contains("linux") || OS_NAME.contains("windows")) && !OS_NAME.contains("mac");

    private final ConcurrentHashMap<Long, CachedChunk> chunks = new ConcurrentHashMap<>();

    private RamChunkStore() {
    }

    public static boolean isSupported() {
        return SUPPORTED;
    }

    public static RamChunkStore getOrCreate(RegionStorageInfo info, Path path) {
        final String key = info.toString() + "|" + path.toAbsolutePath().normalize();
        return STORES.computeIfAbsent(key, ignored -> new RamChunkStore());
    }

    public Optional<CompoundTag> getCached(ChunkPos pos) {
        final CachedChunk cachedChunk = this.chunks.get(pos.pack());
        if (cachedChunk == null) {
            return null;
        }

        return Optional.ofNullable(copy(cachedChunk.tag));
    }

    public void cacheLoaded(ChunkPos pos, CompoundTag tag) {
        this.chunks.compute(pos.pack(), (ignored, cachedChunk) -> {
            if (cachedChunk != null && cachedChunk.dirty) {
                return cachedChunk;
            }

            return new CachedChunk(copy(tag), cachedChunk == null ? 0L : cachedChunk.version, false);
        });
    }

    public void store(ChunkPos pos, CompoundTag tag) {
        this.chunks.compute(pos.pack(), (ignored, cachedChunk) -> {
            final long version = cachedChunk == null ? 1L : cachedChunk.version + 1L;
            return new CachedChunk(copy(tag), version, true);
        });
    }

    public List<PendingFlush> snapshotDirty() {
        final List<PendingFlush> pending = new ArrayList<>();
        this.chunks.forEach((packedPos, cachedChunk) -> {
            if (cachedChunk.dirty) {
                pending.add(new PendingFlush(ChunkPos.unpack(packedPos), copy(cachedChunk.tag), cachedChunk.version));
            }
        });
        return pending;
    }

    public void markFlushed(List<PendingFlush> flushed) {
        for (PendingFlush pendingFlush : flushed) {
            this.chunks.computeIfPresent(pendingFlush.pos.pack(), (ignored, cachedChunk) -> {
                if (cachedChunk.version != pendingFlush.version) {
                    return cachedChunk;
                }

                return new CachedChunk(cachedChunk.tag, cachedChunk.version, false);
            });
        }
    }

    private static CompoundTag copy(CompoundTag tag) {
        return tag == null ? null : tag.copy();
    }

    private record CachedChunk(CompoundTag tag, long version, boolean dirty) {
    }

    public record PendingFlush(ChunkPos pos, CompoundTag tag, long version) {
    }
}
