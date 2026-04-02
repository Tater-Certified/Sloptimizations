package com.github.tatercertified.slopium.mixin;

import com.github.tatercertified.slopium.RamChunkStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(SimpleRegionStorage.class)
public class SimpleRegionStorageMixin {
    @Shadow @Final private IOWorker worker;

    @Unique
    private RamChunkStore slopium$ramChunkStore;

    @Unique
    private boolean slopium$ramChunkStoreEnabled;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void slopium$initRamChunkStore(RegionStorageInfo info, Path path, com.mojang.datafixers.DataFixer fixerUpper, boolean sync, net.minecraft.util.datafix.DataFixTypes dataFixType, CallbackInfo ci) {
        this.slopium$ramChunkStoreEnabled = RamChunkStore.isSupported();
        if (this.slopium$ramChunkStoreEnabled) {
            this.slopium$ramChunkStore = RamChunkStore.getOrCreate(info, path);
        }
    }

    /**
     * @author tatercertified
     * @reason Serve chunk reads from RAM on supported platforms.
     */
    @Overwrite
    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos pos) {
        if (!this.slopium$ramChunkStoreEnabled) {
            return this.worker.loadAsync(pos);
        }

        final Optional<CompoundTag> cached = this.slopium$ramChunkStore.getCached(pos);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return this.worker.loadAsync(pos).thenApply(optionalTag -> {
            this.slopium$ramChunkStore.cacheLoaded(pos, optionalTag.orElse(null));
            return optionalTag.map(CompoundTag::copy);
        });
    }

    /**
     * @author tatercertified
     * @reason Keep chunk writes uncompressed in RAM until synchronize/close.
     */
    @Overwrite
    public CompletableFuture<Void> write(ChunkPos pos, CompoundTag tag) {
        return this.write(pos, () -> tag);
    }

    /**
     * @author tatercertified
     * @reason Keep chunk writes uncompressed in RAM until synchronize/close.
     */
    @Overwrite
    public CompletableFuture<Void> write(ChunkPos pos, java.util.function.Supplier<CompoundTag> supplier) {
        if (!this.slopium$ramChunkStoreEnabled) {
            return this.worker.store(pos, supplier);
        }

        this.slopium$ramChunkStore.store(pos, supplier.get());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * @author tatercertified
     * @reason Flush dirty RAM-backed chunks into vanilla region storage when the game asks to sync.
     */
    @Overwrite
    public CompletableFuture<Void> synchronize(boolean flush) {
        if (!this.slopium$ramChunkStoreEnabled) {
            return this.worker.synchronize(flush);
        }

        final List<RamChunkStore.PendingFlush> pending = this.slopium$ramChunkStore.snapshotDirty();
        if (pending.isEmpty()) {
            return this.worker.synchronize(flush);
        }

        final CompletableFuture<?>[] futures = new CompletableFuture[pending.size()];
        for (int i = 0; i < pending.size(); i++) {
            final RamChunkStore.PendingFlush pendingFlush = pending.get(i);
            futures[i] = this.worker.store(pendingFlush.pos(), pendingFlush.tag());
        }

        return CompletableFuture.allOf(futures)
                .thenCompose(ignored -> {
                    this.slopium$ramChunkStore.markFlushed(pending);
                    return this.worker.synchronize(flush);
                });
    }

    /**
     * @author tatercertified
     * @reason Persist RAM-backed chunks before closing the storage.
     */
    @Overwrite
    public void close() throws IOException {
        if (!this.slopium$ramChunkStoreEnabled) {
            this.worker.close();
            return;
        }

        try {
            this.synchronize(true).join();
        } catch (Exception exception) {
            throw new IOException("Failed to flush RAM-backed chunks before close", exception);
        }

        this.worker.close();
    }
}
