package com.github.tatercertified.slopium.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockGetter.class)
public interface BlockGetterMixin {
    /**
     * @author tatercertified
     * @reason Removes the generic lambda-based traversal path from block raycasts and inlines the DDA walk.
     */
    @Overwrite
    default BlockHitResult clip(ClipContext context) {
        final BlockGetter blockGetter = (BlockGetter) this;
        final Vec3 from = context.getFrom();
        final Vec3 to = context.getTo();
        final double dx = to.x - from.x;
        final double dy = to.y - from.y;
        final double dz = to.z - from.z;

        if (dx == 0.0D && dy == 0.0D && dz == 0.0D) {
            BlockPos pos = BlockPos.containing(from);
            return BlockHitResult.miss(to, Direction.getApproximateNearest(dx, dy, dz), pos);
        }

        final double endX = Mth.lerp(-1.0E-7D, to.x, from.x);
        final double endY = Mth.lerp(-1.0E-7D, to.y, from.y);
        final double endZ = Mth.lerp(-1.0E-7D, to.z, from.z);
        final double startX = Mth.lerp(-1.0E-7D, from.x, to.x);
        final double startY = Mth.lerp(-1.0E-7D, from.y, to.y);
        final double startZ = Mth.lerp(-1.0E-7D, from.z, to.z);

        int x = Mth.floor(startX);
        int y = Mth.floor(startY);
        int z = Mth.floor(startZ);
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);

        final Direction missDirection = Direction.getApproximateNearest(dx, dy, dz);
        BlockHitResult immediateHit = this.slopium$clipBlock(context, blockGetter, pos, from, to);
        if (immediateHit != null) {
            return immediateHit;
        }

        final double stepX = endX - startX;
        final double stepY = endY - startY;
        final double stepZ = endZ - startZ;
        final int signX = Mth.sign(stepX);
        final int signY = Mth.sign(stepY);
        final int signZ = Mth.sign(stepZ);
        final double deltaX = signX == 0 ? Double.MAX_VALUE : (double) signX / stepX;
        final double deltaY = signY == 0 ? Double.MAX_VALUE : (double) signY / stepY;
        final double deltaZ = signZ == 0 ? Double.MAX_VALUE : (double) signZ / stepZ;

        double nextX = deltaX * (signX > 0 ? 1.0D - Mth.frac(startX) : Mth.frac(startX));
        double nextY = deltaY * (signY > 0 ? 1.0D - Mth.frac(startY) : Mth.frac(startY));
        double nextZ = deltaZ * (signZ > 0 ? 1.0D - Mth.frac(startZ) : Mth.frac(startZ));

        while (nextX <= 1.0D || nextY <= 1.0D || nextZ <= 1.0D) {
            if (nextX < nextY) {
                if (nextX < nextZ) {
                    x += signX;
                    nextX += deltaX;
                } else {
                    z += signZ;
                    nextZ += deltaZ;
                }
            } else if (nextY < nextZ) {
                y += signY;
                nextY += deltaY;
            } else {
                z += signZ;
                nextZ += deltaZ;
            }

            immediateHit = this.slopium$clipBlock(context, blockGetter, pos.set(x, y, z), from, to);
            if (immediateHit != null) {
                return immediateHit;
            }
        }

        return BlockHitResult.miss(to, missDirection, BlockPos.containing(to));
    }

    private BlockHitResult slopium$clipBlock(ClipContext context, BlockGetter blockGetter, BlockPos pos, Vec3 from, Vec3 to) {
        BlockState blockState = blockGetter.getBlockState(pos);
        FluidState fluidState = blockGetter.getFluidState(pos);

        BlockHitResult blockHit = null;
        if (!blockState.isAir()) {
            VoxelShape blockShape = context.getBlockShape(blockState, blockGetter, pos);
            if (!blockShape.isEmpty()) {
                blockHit = blockGetter.clipWithInteractionOverride(from, to, pos, blockShape, blockState);
            }
        }

        BlockHitResult fluidHit = null;
        if (!fluidState.isEmpty()) {
            VoxelShape fluidShape = context.getFluidShape(fluidState, blockGetter, pos);
            if (!fluidShape.isEmpty()) {
                fluidHit = fluidShape.clip(from, to, pos);
            }
        }

        if (blockHit == null) {
            return fluidHit;
        }

        if (fluidHit == null) {
            return blockHit;
        }

        return fluidHit.getLocation().distanceToSqr(from) < blockHit.getLocation().distanceToSqr(from) ? fluidHit : blockHit;
    }
}
