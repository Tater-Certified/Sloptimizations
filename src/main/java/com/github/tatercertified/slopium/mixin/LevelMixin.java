package com.github.tatercertified.slopium.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelAccessor, AutoCloseable {
    @Shadow public abstract boolean isInWorldBounds(BlockPos pos);


    /**
     * @author tatercertified
     * @reason Mirrors Paper's loaded-chunk helper optimization by avoiding extra helper indirection on a hot path.
     */
    @Overwrite
    public boolean isLoaded(BlockPos pos) {
        return this.isInWorldBounds(pos) && this.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
