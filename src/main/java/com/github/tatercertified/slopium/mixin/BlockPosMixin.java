package com.github.tatercertified.slopium.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockPos.class)
public abstract class BlockPosMixin extends Vec3i {
    @Shadow public static BlockPos ZERO;

    public BlockPosMixin(int x, int y, int z) {
        super(x, y, z);
    }

    @Overwrite
    public static long offset(long packed, Direction direction) {
        return BlockPos.offset(packed, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    @Overwrite
    public static long offset(long packed, int x, int y, int z) {
        return BlockPos.asLong(BlockPos.getX(packed) + x, BlockPos.getY(packed) + y, BlockPos.getZ(packed) + z);
    }

    @Overwrite
    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    @Overwrite
    public static BlockPos containing(Position position) {
        return containing(position.x(), position.y(), position.z());
    }

    @Overwrite
    public static BlockPos min(BlockPos left, BlockPos right) {
        return new BlockPos(
                Math.min(left.getX(), right.getX()),
                Math.min(left.getY(), right.getY()),
                Math.min(left.getZ(), right.getZ())
        );
    }

    @Overwrite
    public static BlockPos max(BlockPos left, BlockPos right) {
        return new BlockPos(
                Math.max(left.getX(), right.getX()),
                Math.max(left.getY(), right.getY()),
                Math.max(left.getZ(), right.getZ())
        );
    }

    @Overwrite
    public long asLong() {
        return BlockPos.asLong(this.getX(), this.getY(), this.getZ());
    }

    @Overwrite
    public BlockPos offset(int x, int y, int z) {
        if ((x | y | z) == 0) {
            return (BlockPos) (Object) this;
        }
        return new BlockPos(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    @Overwrite
    public Vec3 getCenter() {
        return new Vec3(this.getX() + 0.5D, this.getY() + 0.5D, this.getZ() + 0.5D);
    }

    @Overwrite
    public Vec3 getBottomCenter() {
        return new Vec3(this.getX() + 0.5D, this.getY(), this.getZ() + 0.5D);
    }

    @Overwrite
    public BlockPos offset(Vec3i other) {
        return this.offset(other.getX(), other.getY(), other.getZ());
    }

    @Overwrite
    public BlockPos subtract(Vec3i other) {
        return this.offset(-other.getX(), -other.getY(), -other.getZ());
    }

    @Overwrite
    public BlockPos multiply(int scalar) {
        if (scalar == 1) {
            return (BlockPos) (Object) this;
        }
        if (scalar == 0) {
            return ZERO;
        }
        return new BlockPos(this.getX() * scalar, this.getY() * scalar, this.getZ() * scalar);
    }

    @Overwrite
    public BlockPos above() {
        return new BlockPos(this.getX(), this.getY() + 1, this.getZ());
    }

    @Overwrite
    public BlockPos above(int distance) {
        return distance == 0 ? (BlockPos) (Object) this : new BlockPos(this.getX(), this.getY() + distance, this.getZ());
    }

    @Overwrite
    public BlockPos below() {
        return new BlockPos(this.getX(), this.getY() - 1, this.getZ());
    }

    @Overwrite
    public BlockPos below(int distance) {
        return distance == 0 ? (BlockPos) (Object) this : new BlockPos(this.getX(), this.getY() - distance, this.getZ());
    }

    @Overwrite
    public BlockPos north() {
        return new BlockPos(this.getX(), this.getY(), this.getZ() - 1);
    }

    @Overwrite
    public BlockPos north(int distance) {
        return distance == 0 ? (BlockPos) (Object) this : new BlockPos(this.getX(), this.getY(), this.getZ() - distance);
    }

    @Overwrite
    public BlockPos south() {
        return new BlockPos(this.getX(), this.getY(), this.getZ() + 1);
    }

    @Overwrite
    public BlockPos south(int distance) {
        return distance == 0 ? (BlockPos) (Object) this : new BlockPos(this.getX(), this.getY(), this.getZ() + distance);
    }

    @Overwrite
    public BlockPos west() {
        return new BlockPos(this.getX() - 1, this.getY(), this.getZ());
    }

    @Overwrite
    public BlockPos west(int distance) {
        return distance == 0 ? (BlockPos) (Object) this : new BlockPos(this.getX() - distance, this.getY(), this.getZ());
    }

    @Overwrite
    public BlockPos east() {
        return new BlockPos(this.getX() + 1, this.getY(), this.getZ());
    }

    @Overwrite
    public BlockPos east(int distance) {
        return distance == 0 ? (BlockPos) (Object) this : new BlockPos(this.getX() + distance, this.getY(), this.getZ());
    }

    @Overwrite
    public BlockPos relative(Direction direction) {
        return new BlockPos(this.getX() + direction.getStepX(), this.getY() + direction.getStepY(), this.getZ() + direction.getStepZ());
    }

    @Overwrite
    public BlockPos relative(Direction direction, int distance) {
        if (distance == 0) {
            return (BlockPos) (Object) this;
        }
        return new BlockPos(
                this.getX() + direction.getStepX() * distance,
                this.getY() + direction.getStepY() * distance,
                this.getZ() + direction.getStepZ() * distance
        );
    }

    @Overwrite
    public BlockPos relative(Direction.Axis axis, int distance) {
        if (distance == 0) {
            return (BlockPos) (Object) this;
        }
        final int x = axis == Direction.Axis.X ? distance : 0;
        final int y = axis == Direction.Axis.Y ? distance : 0;
        final int z = axis == Direction.Axis.Z ? distance : 0;
        return new BlockPos(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    @Overwrite
    public BlockPos rotate(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(-this.getZ(), this.getY(), this.getX());
            case CLOCKWISE_180 -> new BlockPos(-this.getX(), this.getY(), -this.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(this.getZ(), this.getY(), -this.getX());
            case NONE -> (BlockPos) (Object) this;
        };
    }

    @Overwrite
    public BlockPos cross(Vec3i other) {
        final int x = this.getY() * other.getZ() - this.getZ() * other.getY();
        final int y = this.getZ() * other.getX() - this.getX() * other.getZ();
        final int z = this.getX() * other.getY() - this.getY() * other.getX();
        return new BlockPos(x, y, z);
    }
}
