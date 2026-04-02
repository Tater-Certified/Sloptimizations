package com.github.tatercertified.slopium.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
    @Shadow protected abstract ChunkHolder getVisibleChunkIfPresent(long pos);
    @Shadow abstract boolean chunkAbsent(ChunkHolder holder, int status);

    /**
     * @author tatercertified
     * @reason Ports Paper's loaded-chunk lookup style by avoiding transient ChunkPos allocation.
     */
    @Overwrite
    public boolean hasChunk(int x, int z) {
        final ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.pack(x, z));
        return !this.chunkAbsent(holder, ChunkLevel.byStatus(ChunkStatus.FULL));
    }
}
