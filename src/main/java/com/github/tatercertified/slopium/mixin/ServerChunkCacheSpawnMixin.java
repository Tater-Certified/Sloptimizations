package com.github.tatercertified.slopium.mixin;

import com.github.tatercertified.slopium.FastSpawnManager;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheSpawnMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private DistanceManager distanceManager;
    @Shadow private boolean spawnEnemies;

    @Inject(method = "tickSpawningChunk", at = @At("TAIL"))
    private void slopium$fallbackSpawnTick(LevelChunk chunk, long inhabitedTimeDelta, List<MobCategory> categories, NaturalSpawner.SpawnState spawnState, CallbackInfo ci) {
        ChunkPos chunkPos = chunk.getPos();
        if (!this.distanceManager.inEntityTickingRange(chunkPos.pack()) || !this.level.canSpawnEntitiesInChunk(chunkPos)) {
            return;
        }

        if ((this.level.getGameTime() + ChunkPos.hash(chunkPos.x(), chunkPos.z())) % 8L != 0L) {
            return;
        }

        FastSpawnManager.scheduleSpawnTick(this.level, chunkPos, this.spawnEnemies, this.slopium$mainThreadExecutor());
    }

    @Unique
    private java.util.concurrent.Executor slopium$mainThreadExecutor() {
        return this.level.getServer()::execute;
    }
}
