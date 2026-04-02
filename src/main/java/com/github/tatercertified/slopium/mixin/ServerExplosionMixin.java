package com.github.tatercertified.slopium.mixin;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Optional;

@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private Vec3 center;
    @Shadow @Final private float radius;
    @Shadow @Final private ExplosionDamageCalculator damageCalculator;
    @Shadow @Final private Entity source;
    @Shadow @Final private DamageSource damageSource;

    /**
     * @author tatercertified
     * @reason Uses a coarse fixed sample set instead of a dense AABB grid for explosion visibility checks.
     */
    @Overwrite
    public static float getSeenPercent(Vec3 source, Entity entity) {
        AABB box = entity.getBoundingBox();
        double minX = box.minX + 1.0E-3D;
        double minY = box.minY + 1.0E-3D;
        double minZ = box.minZ + 1.0E-3D;
        double maxX = box.maxX - 1.0E-3D;
        double maxY = box.maxY - 1.0E-3D;
        double maxZ = box.maxZ - 1.0E-3D;
        double midX = (minX + maxX) * 0.5D;
        double midY = (minY + maxY) * 0.5D;
        double midZ = (minZ + maxZ) * 0.5D;

        Vec3[] samples = new Vec3[] {
                new Vec3(midX, midY, midZ),
                new Vec3(minX, minY, minZ),
                new Vec3(minX, minY, maxZ),
                new Vec3(minX, maxY, minZ),
                new Vec3(minX, maxY, maxZ),
                new Vec3(maxX, minY, minZ),
                new Vec3(maxX, minY, maxZ),
                new Vec3(maxX, maxY, minZ),
                new Vec3(maxX, maxY, maxZ)
        };

        int visible = 0;
        for (Vec3 sample : samples) {
            BlockHitResult hit = entity.level().clip(new ClipContext(sample, source, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            if (hit.getType() == HitResult.Type.MISS) {
                visible++;
            }
        }

        return visible / 9.0F;
    }

    /**
     * @author tatercertified
     * @reason Uses fewer edge rays, primitive collections, and a per-explosion resistance cache.
     */
    @Overwrite
    private List<BlockPos> calculateExplodedPositions() {
        final int grid = 8;
        final float rayStep = 0.3F;
        final float rayDecay = 0.22500001F;
        final float gridScale = 2.0F / (grid - 1);
        final LongOpenHashSet exploded = new LongOpenHashSet(512);
        final Long2FloatOpenHashMap resistanceCache = new Long2FloatOpenHashMap(512);
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        resistanceCache.defaultReturnValue(Float.NaN);

        final double originX = this.center.x;
        final double originY = this.center.y;
        final double originZ = this.center.z;

        for (int ix = 0; ix < grid; ix++) {
            for (int iy = 0; iy < grid; iy++) {
                for (int iz = 0; iz < grid; iz++) {
                    if (ix != 0 && ix != grid - 1 && iy != 0 && iy != grid - 1 && iz != 0 && iz != grid - 1) {
                        continue;
                    }

                    double dx = ix * gridScale - 1.0D;
                    double dy = iy * gridScale - 1.0D;
                    double dz = iz * gridScale - 1.0D;
                    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (length == 0.0D) {
                        continue;
                    }

                    dx /= length;
                    dy /= length;
                    dz /= length;

                    float power = this.radius * (0.7F + this.level.getRandom().nextFloat() * 0.6F);
                    double x = originX;
                    double y = originY;
                    double z = originZ;

                    while (power > 0.0F) {
                        int bx = net.minecraft.util.Mth.floor(x);
                        int by = net.minecraft.util.Mth.floor(y);
                        int bz = net.minecraft.util.Mth.floor(z);
                        if (!this.level.isInWorldBounds(pos.set(bx, by, bz))) {
                            break;
                        }

                        long key = BlockPos.asLong(bx, by, bz);
                        float resistance = resistanceCache.get(key);
                        BlockState blockState = this.level.getBlockState(pos);

                        if (Float.isNaN(resistance)) {
                            FluidState fluidState = this.level.getFluidState(pos);
                            Optional<Float> blockResistance = this.damageCalculator.getBlockExplosionResistance(
                                    (Explosion) (Object) this,
                                    this.level,
                                    pos,
                                    blockState,
                                    fluidState
                            );
                            resistance = blockResistance.map(value -> (value + 0.3F) * 0.3F).orElse(0.0F);
                            resistanceCache.put(key, resistance);
                        }

                        power -= resistance;
                        if (power > 0.0F && this.damageCalculator.shouldBlockExplode((Explosion) (Object) this, this.level, pos, blockState, power)) {
                            exploded.add(key);
                        }

                        x += dx * rayStep;
                        y += dy * rayStep;
                        z += dz * rayStep;
                        power -= rayDecay;
                    }
                }
            }
        }

        ObjectArrayList<BlockPos> explodedPositions = new ObjectArrayList<>(exploded.size());
        LongIterator iterator = exploded.iterator();
        while (iterator.hasNext()) {
            explodedPositions.add(BlockPos.of(iterator.nextLong()));
        }

        return explodedPositions;
    }
}
