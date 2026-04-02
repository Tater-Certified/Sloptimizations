package com.github.tatercertified.slopium;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class FastWorldgen {
    public static final int DECORATION_FEATURES = 1;
    public static final int DECORATION_STRUCTURES = 2;
    private static final TerrainBackendSelector.TerrainBackendState TERRAIN_BACKEND = TerrainBackendSelector.state();

    private static final ExecutorService DECORATION_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() - 1)),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger();

                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "slopium-decoration-" + this.counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            }
    );

    private static final ConcurrentHashMap<String, Integer> SCHEDULED_DECORATIONS = new ConcurrentHashMap<>();

    private FastWorldgen() {
    }

    public static int getTerrainHeight(int x, int z, int minY, int maxY, int seaLevel, boolean hellLike) {
        if (TERRAIN_BACKEND.backend() != TerrainBackendSelector.TerrainBackend.CPU && TERRAIN_BACKEND.available()) {
            return getCpuTerrainHeight(x, z, minY, maxY, seaLevel, hellLike);
        }

        return getCpuTerrainHeight(x, z, minY, maxY, seaLevel, hellLike);
    }

    public static TerrainBackendSelector.TerrainBackendState getTerrainBackend() {
        return TERRAIN_BACKEND;
    }

    private static int getCpuTerrainHeight(int x, int z, int minY, int maxY, int seaLevel, boolean hellLike) {
        if (hellLike) {
            double mass = smoothNoise(x, z, 256.0D, 34.0D) + smoothNoise(x, z, 128.0D, 18.0D);
            double ridges = ridgeNoise(x, z, 180.0D, 20.0D);
            double detail = smoothNoise(x, z, 56.0D, 4.5D);
            int raw = seaLevel + 24 + (int) Math.round(mass + ridges + detail);
            return Mth.clamp(raw, minY + 8, maxY - 16);
        }

        double continents = smoothNoise(x, z, 700.0D, 26.0D);
        double basins = -Math.abs(smoothNoise(x, z, 520.0D, 30.0D));
        double mountainMask = Math.max(0.0D, smoothNoise(x, z, 900.0D, 1.0D));
        double mountains = ridgeNoise(x, z, 240.0D, 54.0D) * mountainMask;
        double foothills = smoothNoise(x, z, 140.0D, 14.0D);
        double detail = smoothNoise(x, z, 48.0D, 3.5D);
        int raw = seaLevel + 8 + (int) Math.round(continents + basins + mountains + foothills + detail);
        return Mth.clamp(raw, minY + 8, maxY - 8);
    }

    public static BlockState getTopBlock(BlockState defaultBlock) {
        return defaultBlock.is(Blocks.STONE) ? Blocks.GRASS_BLOCK.defaultBlockState() : defaultBlock;
    }

    public static BlockState getFillerBlock(BlockState defaultBlock) {
        return defaultBlock.is(Blocks.STONE) ? Blocks.DIRT.defaultBlockState() : defaultBlock;
    }

    public static BlockState getBeachBlock(BlockState defaultBlock) {
        return defaultBlock.is(Blocks.STONE) ? Blocks.SAND.defaultBlockState() : defaultBlock;
    }

    public static void scheduleDecoration(ServerLevel level, ChunkPos chunkPos, NoiseGeneratorSettings settings, int decorationMask, java.util.concurrent.Executor mainThreadExecutor) {
        String key = level.dimension().identifier() + ":" + chunkPos.x() + ":" + chunkPos.z();
        int mergedMask = SCHEDULED_DECORATIONS.merge(key, decorationMask, (left, right) -> left | right);
        if ((mergedMask & decorationMask) != decorationMask) {
            return;
        }

        CompletableFuture
                .supplyAsync(() -> computePlacements(level.getSeed(), chunkPos, settings, decorationMask), DECORATION_EXECUTOR)
                .thenAcceptAsync(placements -> {
                    try {
                        applyPlacements(level, chunkPos, placements);
                    } finally {
                        SCHEDULED_DECORATIONS.remove(key);
                    }
                }, mainThreadExecutor);
    }

    private static List<Placement> computePlacements(long seed, ChunkPos chunkPos, NoiseGeneratorSettings settings, int decorationMask) {
        ArrayList<Placement> placements = new ArrayList<>(96);
        long localSeed = mixSeed(seed, chunkPos.x(), chunkPos.z());
        NoiseSettings noiseSettings = settings.noiseSettings();
        int minY = noiseSettings.minY();
        int maxY = minY + noiseSettings.height();
        int seaLevel = settings.seaLevel();
        boolean hellLike = settings.defaultFluid().is(Blocks.LAVA);
        BlockState topBlock = getTopBlock(settings.defaultBlock());

        if ((decorationMask & DECORATION_FEATURES) != 0) {
            for (int i = 0; i < 4; i++) {
                int x = chunkPos.getMinBlockX() + nextInt(localSeed + i * 17L, 16);
                int z = chunkPos.getMinBlockZ() + nextInt(localSeed + i * 29L, 16);
                int y = getTerrainHeight(x, z, minY, maxY, seaLevel, hellLike);
                if (!hellLike && y > seaLevel && nextInt(localSeed + i * 53L, 100) < 55) {
                    addTree(placements, x, y + 1, z);
                }
            }

            for (int i = 0; i < 6; i++) {
                int x = chunkPos.getMinBlockX() + nextInt(localSeed + i * 71L, 16);
                int z = chunkPos.getMinBlockZ() + nextInt(localSeed + i * 83L, 16);
                int y = Mth.clamp(getTerrainHeight(x, z, minY, maxY, seaLevel, hellLike) - 6 - nextInt(localSeed + i * 97L, 20), minY + 2, maxY - 10);
                BlockState ore = y < minY + 32 ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState() : Blocks.COAL_ORE.defaultBlockState();
                addBlob(placements, x, y, z, ore, 2);
            }
        }

        if ((decorationMask & DECORATION_STRUCTURES) != 0 && nextInt(localSeed ^ 0x9E3779B97F4A7C15L, 32) == 0) {
            int centerX = chunkPos.getMinBlockX() + 8;
            int centerZ = chunkPos.getMinBlockZ() + 8;
            int groundY = getTerrainHeight(centerX, centerZ, minY, maxY, seaLevel, hellLike);
            if (!hellLike && groundY > seaLevel - 2) {
                addHouse(placements, centerX - 3, groundY + 1, centerZ - 3);
            }
        }

        for (int i = 0; i < placements.size(); i++) {
            Placement placement = placements.get(i);
            if (Objects.equals(placement.state, topBlock) && placement.pos.getY() <= seaLevel) {
                placements.set(i, new Placement(placement.pos, getBeachBlock(settings.defaultBlock())));
            }
        }

        return placements;
    }

    private static void applyPlacements(ServerLevel level, ChunkPos chunkPos, List<Placement> placements) {
        if (!level.getChunkSource().hasChunk(chunkPos.x(), chunkPos.z())) {
            return;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Placement placement : placements) {
            pos.set(placement.pos.getX(), placement.pos.getY(), placement.pos.getZ());
            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockState existing = level.getBlockState(pos);
            if (!existing.isAir() && !existing.is(Blocks.WATER) && !existing.is(Blocks.TALL_GRASS)) {
                continue;
            }

            level.setBlock(pos, placement.state, 18);
        }
    }

    private static void addTree(List<Placement> placements, int x, int y, int z) {
        for (int i = 0; i < 4; i++) {
            placements.add(new Placement(new BlockPos(x, y + i, z), Blocks.OAK_LOG.defaultBlockState()));
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 2; dy <= 4; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) <= 3) {
                        placements.add(new Placement(new BlockPos(x + dx, y + dy, z + dz), Blocks.OAK_LEAVES.defaultBlockState()));
                    }
                }
            }
        }
    }

    private static void addBlob(List<Placement> placements, int x, int y, int z, BlockState state, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                        placements.add(new Placement(new BlockPos(x + dx, y + dy, z + dz), state));
                    }
                }
            }
        }
    }

    private static void addHouse(List<Placement> placements, int x, int y, int z) {
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                placements.add(new Placement(new BlockPos(x + dx, y - 1, z + dz), Blocks.COBBLESTONE.defaultBlockState()));
            }
        }

        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                for (int dy = 0; dy < 4; dy++) {
                    boolean wall = dx == 0 || dx == 6 || dz == 0 || dz == 6;
                    if (dy == 0 || dy == 3) {
                        placements.add(new Placement(new BlockPos(x + dx, y + dy, z + dz), Blocks.OAK_PLANKS.defaultBlockState()));
                    } else if (wall) {
                        BlockState state = ((dx == 0 || dx == 6) && dz == 3 && dy == 1) ? Blocks.AIR.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                        if (!state.isAir()) {
                            placements.add(new Placement(new BlockPos(x + dx, y + dy, z + dz), state));
                        }
                    }
                }
            }
        }

        for (int dx = -1; dx <= 7; dx++) {
            for (int dz = -1; dz <= 7; dz++) {
                int roofOffset = Math.max(Math.abs(dx - 3), Math.abs(dz - 3));
                placements.add(new Placement(new BlockPos(x + dx, y + 4 + (roofOffset <= 2 ? 1 : 0), z + dz), Blocks.STONE_BRICKS.defaultBlockState()));
            }
        }

        placements.add(new Placement(new BlockPos(x + 2, y + 1, z), Blocks.GLASS.defaultBlockState()));
        placements.add(new Placement(new BlockPos(x + 4, y + 1, z), Blocks.GLASS.defaultBlockState()));
        placements.add(new Placement(new BlockPos(x + 2, y + 1, z + 6), Blocks.GLASS.defaultBlockState()));
        placements.add(new Placement(new BlockPos(x + 4, y + 1, z + 6), Blocks.GLASS.defaultBlockState()));
    }

    private static double ridgeNoise(int x, int z, double scale, double amplitude) {
        return (1.0D - Math.abs(smoothNoise(x, z, scale, 1.0D))) * amplitude - amplitude * 0.5D;
    }

    private static double smoothNoise(int x, int z, double scale, double amplitude) {
        double fx = x / scale;
        double fz = z / scale;
        int x0 = Mth.floor(fx);
        int z0 = Mth.floor(fz);
        double tx = fx - x0;
        double tz = fz - z0;
        double sx = tx * tx * (3.0D - 2.0D * tx);
        double sz = tz * tz * (3.0D - 2.0D * tz);
        double n00 = hashUnit(x0, z0);
        double n10 = hashUnit(x0 + 1, z0);
        double n01 = hashUnit(x0, z0 + 1);
        double n11 = hashUnit(x0 + 1, z0 + 1);
        double nx0 = Mth.lerp(sx, n00, n10);
        double nx1 = Mth.lerp(sx, n01, n11);
        return Mth.lerp(sz, nx0, nx1) * amplitude;
    }

    private static double hashUnit(int x, int z) {
        long value = x * 341873128712L + z * 132897987541L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value & 0xFFFFFFL) / (double) 0xFFFFFF) * 2.0D - 1.0D;
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

    private static long mixSeed(long seed, int x, int z) {
        long mixed = seed ^ (x * 341873128712L) ^ (z * 132897987541L);
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    private record Placement(BlockPos pos, BlockState state) {
    }
}
