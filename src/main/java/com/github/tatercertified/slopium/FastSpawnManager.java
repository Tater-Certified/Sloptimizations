package com.github.tatercertified.slopium;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class FastSpawnManager {
    private static final ExecutorService SPAWN_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1)),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger();

                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "slopium-spawn-" + this.counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            }
    );

    private static final ConcurrentHashMap<Long, Long> SCHEDULED_CHUNKS = new ConcurrentHashMap<>();

    private FastSpawnManager() {
    }

    public static void scheduleSpawnTick(ServerLevel level, ChunkPos chunkPos, boolean spawnEnemies, Executor mainThreadExecutor) {
        long packedChunk = chunkPos.pack();
        long gameTime = level.getGameTime();
        Long previous = SCHEDULED_CHUNKS.putIfAbsent(packedChunk, gameTime);
        if (previous != null && gameTime - previous < 20L) {
            return;
        }

        CompletableFuture
                .supplyAsync(() -> buildPlan(level.getSeed(), chunkPos, gameTime, spawnEnemies), SPAWN_EXECUTOR)
                .thenAcceptAsync(plan -> {
                    try {
                        applyPlan(level, chunkPos, plan);
                    } finally {
                        SCHEDULED_CHUNKS.remove(packedChunk);
                    }
                }, mainThreadExecutor);
    }

    private static SpawnPlan buildPlan(long seed, ChunkPos chunkPos, long gameTime, boolean spawnEnemies) {
        long mixed = mixSeed(seed, chunkPos.x(), chunkPos.z(), gameTime);
        ArrayList<SpawnAttempt> attempts = new ArrayList<>(4);

        if (spawnEnemies) {
            for (int i = 0; i < 3; i++) {
                attempts.add(new SpawnAttempt(
                        MobCategory.MONSTER,
                        chunkPos.getMinBlockX() + nextInt(mixed + i * 31L, 16),
                        chunkPos.getMinBlockZ() + nextInt(mixed + i * 47L, 16)
                ));
            }
        }

        if (nextInt(mixed ^ 0x9E3779B97F4A7C15L, 3) == 0) {
            attempts.add(new SpawnAttempt(
                    MobCategory.CREATURE,
                    chunkPos.getMinBlockX() + nextInt(mixed + 211L, 16),
                    chunkPos.getMinBlockZ() + nextInt(mixed + 307L, 16)
            ));
        }

        return new SpawnPlan(attempts);
    }

    private static void applyPlan(ServerLevel level, ChunkPos chunkPos, SpawnPlan plan) {
        if (!level.getChunkSource().hasChunk(chunkPos.x(), chunkPos.z())) {
            return;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (SpawnAttempt attempt : plan.attempts) {
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, attempt.x, attempt.z);
            pos.set(attempt.x, surfaceY, attempt.z);

            if (!level.isLoaded(pos) || !level.anyPlayerCloseEnoughForSpawning(chunkPos)) {
                continue;
            }

            BlockState floor = level.getBlockState(pos.below());
            if (floor.isAir()) {
                continue;
            }

            NaturalSpawner.spawnCategoryForPosition(attempt.category, level, pos.immutable());
        }
    }

    private static int nextInt(long seed, int bound) {
        long mixed = seed;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return (int) Math.floorMod(mixed, bound);
    }

    private static long mixSeed(long seed, int x, int z, long gameTime) {
        long mixed = seed ^ (x * 341873128712L) ^ (z * 132897987541L) ^ (gameTime * 17L);
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    private record SpawnAttempt(MobCategory category, int x, int z) {
    }

    private record SpawnPlan(List<SpawnAttempt> attempts) {
    }
}
