package com.github.tatercertified.slopium.mixin;

import com.github.tatercertified.slopium.FastWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.CompletableFuture;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @Shadow @Final private Holder<NoiseGeneratorSettings> settings;

    /**
     * @author tatercertified
     * @reason Replaces expensive vanilla density filling with a simple deterministic terrain pass.
     */
    @Overwrite
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        final NoiseGeneratorSettings generatorSettings = this.settings.value();
        final NoiseSettings noiseSettings = generatorSettings.noiseSettings();
        final BlockState defaultBlock = generatorSettings.defaultBlock();
        final BlockState defaultFluid = generatorSettings.defaultFluid();
        final BlockState topBlock = FastWorldgen.getTopBlock(defaultBlock);
        final BlockState fillerBlock = FastWorldgen.getFillerBlock(defaultBlock);
        final BlockState beachBlock = FastWorldgen.getBeachBlock(defaultBlock);
        final int minY = noiseSettings.minY();
        final int maxY = minY + noiseSettings.height();
        final int seaLevel = generatorSettings.seaLevel();
        final ChunkPos chunkPos = chunk.getPos();
        final int baseX = chunkPos.getMinBlockX();
        final int baseZ = chunkPos.getMinBlockZ();
        final LevelChunkSection[] sections = chunk.getSections();

        for (int localX = 0; localX < 16; localX++) {
            final int worldX = baseX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                final int worldZ = baseZ + localZ;
                final int terrainY = FastWorldgen.getTerrainHeight(worldX, worldZ, minY, maxY, seaLevel, defaultFluid.is(Blocks.LAVA));
                final int stoneTop = Math.max(minY, terrainY - 4);
                final boolean beach = terrainY <= seaLevel + 2 && terrainY >= seaLevel - 5;

                this.slopium$setBlock(chunk, localX, minY, localZ, Blocks.BEDROCK.defaultBlockState());

                for (int y = minY + 1; y < terrainY; y++) {
                    this.slopium$setBlock(chunk, localX, y, localZ, y >= stoneTop ? fillerBlock : defaultBlock);
                }

                this.slopium$setBlock(chunk, localX, terrainY, localZ, beach ? beachBlock : topBlock);

                for (int y = terrainY + 1; y <= seaLevel && y < maxY; y++) {
                    if (!defaultFluid.isAir()) {
                        this.slopium$setBlock(chunk, localX, y, localZ, defaultFluid);
                    }
                }
            }
        }

        for (LevelChunkSection section : sections) {
            section.recalcBlockCounts();
        }

        Heightmap.primeHeightmaps(chunk, net.minecraft.world.level.chunk.status.ChunkStatus.FINAL_HEIGHTMAPS);
        chunk.initializeLightSources();
        chunk.markUnsaved();
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * @author tatercertified
     * @reason Surface work is already baked into the fast fill pass.
     */
    @Overwrite
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
    }

    /**
     * @author tatercertified
     * @reason Carvers are skipped entirely for fast world generation.
     */
    @Overwrite
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, net.minecraft.world.level.biome.BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
    }

    /**
     * @author tatercertified
     * @reason Mob spawning during generation is skipped for throughput.
     */
    @Overwrite
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    /**
     * @author tatercertified
     * @reason Keeps height queries aligned with the simplified terrain fill.
     */
    @Overwrite
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor accessor, RandomState randomState) {
        final NoiseGeneratorSettings generatorSettings = this.settings.value();
        final NoiseSettings noiseSettings = generatorSettings.noiseSettings();
        final int minY = noiseSettings.minY();
        final int maxY = minY + noiseSettings.height();
        final int seaLevel = generatorSettings.seaLevel();
        final boolean lava = generatorSettings.defaultFluid().is(Blocks.LAVA);
        final int terrainY = FastWorldgen.getTerrainHeight(x, z, minY, maxY, seaLevel, lava);

        return switch (heightmapType) {
            case WORLD_SURFACE, WORLD_SURFACE_WG, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES -> Math.min(maxY, Math.max(terrainY, seaLevel) + 1);
            case OCEAN_FLOOR, OCEAN_FLOOR_WG -> Math.min(maxY, terrainY + 1);
        };
    }

    /**
     * @author tatercertified
     * @reason Keeps block-column queries aligned with the simplified terrain fill.
     */
    @Overwrite
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor accessor, RandomState randomState) {
        final NoiseGeneratorSettings generatorSettings = this.settings.value();
        final NoiseSettings noiseSettings = generatorSettings.noiseSettings();
        final BlockState defaultBlock = generatorSettings.defaultBlock();
        final BlockState defaultFluid = generatorSettings.defaultFluid();
        final BlockState topBlock = FastWorldgen.getTopBlock(defaultBlock);
        final BlockState fillerBlock = FastWorldgen.getFillerBlock(defaultBlock);
        final BlockState beachBlock = FastWorldgen.getBeachBlock(defaultBlock);
        final int minY = noiseSettings.minY();
        final int height = noiseSettings.height();
        final int maxY = minY + height;
        final int seaLevel = generatorSettings.seaLevel();
        final int terrainY = FastWorldgen.getTerrainHeight(x, z, minY, maxY, seaLevel, defaultFluid.is(Blocks.LAVA));
        final int stoneTop = Math.max(minY, terrainY - 4);
        final boolean beach = terrainY <= seaLevel + 2 && terrainY >= seaLevel - 5;
        final BlockState[] states = new BlockState[height];

        for (int y = minY; y < maxY; y++) {
            final int index = y - minY;
            if (y == minY) {
                states[index] = Blocks.BEDROCK.defaultBlockState();
            } else if (y < terrainY) {
                states[index] = y >= stoneTop ? fillerBlock : defaultBlock;
            } else if (y == terrainY) {
                states[index] = beach ? beachBlock : topBlock;
            } else if (y <= seaLevel && !defaultFluid.isAir()) {
                states[index] = defaultFluid;
            } else {
                states[index] = Blocks.AIR.defaultBlockState();
            }
        }

        return new NoiseColumn(minY, states);
    }

    private void slopium$setBlock(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        if (chunk.isOutsideBuildHeight(y) || state.isAir()) {
            return;
        }

        final int sectionIndex = chunk.getSectionIndex(y);
        final LevelChunkSection section = chunk.getSections()[sectionIndex];
        section.setBlockState(x, y & 15, z, state, false);
    }
}
