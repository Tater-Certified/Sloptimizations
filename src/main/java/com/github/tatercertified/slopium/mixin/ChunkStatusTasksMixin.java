package com.github.tatercertified.slopium.mixin;

import com.github.tatercertified.slopium.FastWorldgen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksMixin {
    /**
     * @author tatercertified
     * @reason Schedules lightweight structures asynchronously so they don't block chunk generation.
     */
    @Overwrite
    static CompletableFuture<ChunkAccess> generateStructureStarts(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        ServerLevel level = context.level();
        ChunkGenerator generator = context.generator();
        if (generator instanceof NoiseBasedChunkGenerator noiseGenerator) {
            FastWorldgen.scheduleDecoration(level, chunk.getPos(), noiseGenerator.generatorSettings().value(), FastWorldgen.DECORATION_STRUCTURES, context.mainThreadExecutor());
        }
        level.onStructureStartsAvailable(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * @author tatercertified
     * @reason Skips structure references for extremely fast chunk generation.
     */
    @Overwrite
    static CompletableFuture<ChunkAccess> generateStructureReferences(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * @author tatercertified
     * @reason Skips carver stage task work; terrain is already finalized in the fast generator.
     */
    @Overwrite
    static CompletableFuture<ChunkAccess> generateCarvers(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * @author tatercertified
     * @reason Schedules lightweight features asynchronously so they don't block chunk generation.
     */
    @Overwrite
    static CompletableFuture<ChunkAccess> generateFeatures(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        ChunkGenerator generator = context.generator();
        if (generator instanceof NoiseBasedChunkGenerator noiseGenerator) {
            FastWorldgen.scheduleDecoration(context.level(), chunk.getPos(), noiseGenerator.generatorSettings().value(), FastWorldgen.DECORATION_FEATURES, context.mainThreadExecutor());
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * @author tatercertified
     * @reason Skips generation-time mob spawning for throughput.
     */
    @Overwrite
    static CompletableFuture<ChunkAccess> generateSpawn(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }
}
