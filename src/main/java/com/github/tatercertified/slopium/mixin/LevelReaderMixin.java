package com.github.tatercertified.slopium.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelReader.class)
public interface LevelReaderMixin {
    @Shadow boolean hasChunk(int x, int z);
    @Shadow ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create);

    /**
     * @author tatercertified
     * @reason Ports Paper-style chunk lookup inlining to avoid repeated helper dispatch.
     */
    @Overwrite
    default ChunkAccess getChunk(BlockPos pos) {
        return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.EMPTY, true);
    }

    /**
     * @author tatercertified
     * @reason Keeps block-to-chunk conversion branch-free and local.
     */
    @Overwrite
    default boolean hasChunkAt(int x, int z) {
        return this.hasChunk(x >> 4, z >> 4);
    }

    /**
     * @author tatercertified
     * @reason Avoids the extra overload hop for a common block-position chunk check.
     */
    @Overwrite
    default boolean hasChunkAt(BlockPos pos) {
        return this.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * @author tatercertified
     * @reason Reduces repeated SectionPos helper calls on box-wide chunk presence checks.
     */
    @Overwrite
    default boolean hasChunksAt(int minX, int minZ, int maxX, int maxZ) {
        final int minChunkX = minX >> 4;
        final int minChunkZ = minZ >> 4;
        final int maxChunkX = maxX >> 4;
        final int maxChunkZ = maxZ >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!this.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }

        return true;
    }
}
