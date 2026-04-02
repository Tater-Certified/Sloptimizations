package com.github.tatercertified.slopium.mixin;

import net.minecraft.core.AxisCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockPos.MutableBlockPos.class)
public abstract class MutableBlockPosMixin extends Vec3i {

    public MutableBlockPosMixin(int x, int y, int z) {
        super(x, y, z);
    }

    @Shadow public abstract BlockPos.MutableBlockPos setX(int value);
    @Shadow public abstract BlockPos.MutableBlockPos setY(int value);
    @Shadow public abstract BlockPos.MutableBlockPos setZ(int value);

    @Overwrite
    public BlockPos.MutableBlockPos set(int x, int y, int z) {
        this.setX(x);
        this.setY(y);
        this.setZ(z);
        return (BlockPos.MutableBlockPos) (Object) this;
    }

    @Overwrite
    public BlockPos.MutableBlockPos set(double x, double y, double z) {
        return this.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    @Overwrite
    public BlockPos.MutableBlockPos set(Vec3i value) {
        return this.set(value.getX(), value.getY(), value.getZ());
    }

    @Overwrite
    public BlockPos.MutableBlockPos set(long packed) {
        return this.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
    }

    @Overwrite
    public BlockPos.MutableBlockPos set(AxisCycle cycle, int x, int y, int z) {
        return this.set(
                cycle.cycle(x, y, z, Direction.Axis.X),
                cycle.cycle(x, y, z, Direction.Axis.Y),
                cycle.cycle(x, y, z, Direction.Axis.Z)
        );
    }

    @Overwrite
    public BlockPos.MutableBlockPos setWithOffset(Vec3i value, Direction direction) {
        return this.set(value.getX() + direction.getStepX(), value.getY() + direction.getStepY(), value.getZ() + direction.getStepZ());
    }

    @Overwrite
    public BlockPos.MutableBlockPos setWithOffset(Vec3i value, int x, int y, int z) {
        return this.set(value.getX() + x, value.getY() + y, value.getZ() + z);
    }

    @Overwrite
    public BlockPos.MutableBlockPos setWithOffset(Vec3i value, Vec3i offset) {
        return this.set(value.getX() + offset.getX(), value.getY() + offset.getY(), value.getZ() + offset.getZ());
    }

    @Overwrite
    public BlockPos.MutableBlockPos move(Direction direction) {
        return this.move(direction, 1);
    }

    @Overwrite
    public BlockPos.MutableBlockPos move(Direction direction, int distance) {
        return this.set(
                this.getX() + direction.getStepX() * distance,
                this.getY() + direction.getStepY() * distance,
                this.getZ() + direction.getStepZ() * distance
        );
    }

    @Overwrite
    public BlockPos.MutableBlockPos move(int x, int y, int z) {
        return this.set(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    @Overwrite
    public BlockPos.MutableBlockPos move(Vec3i offset) {
        return this.set(this.getX() + offset.getX(), this.getY() + offset.getY(), this.getZ() + offset.getZ());
    }

    @Overwrite
    public BlockPos.MutableBlockPos clamp(Direction.Axis axis, int min, int max) {
        if (axis == Direction.Axis.X) {
            return this.set(Mth.clamp(this.getX(), min, max), this.getY(), this.getZ());
        }
        if (axis == Direction.Axis.Y) {
            return this.set(this.getX(), Mth.clamp(this.getY(), min, max), this.getZ());
        }
        return this.set(this.getX(), this.getY(), Mth.clamp(this.getZ(), min, max));
    }

    @Overwrite
    public BlockPos immutable() {
        return new BlockPos(this.getX(), this.getY(), this.getZ());
    }
}
