package com.github.tatercertified.slopium.mixin;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Vec3i.class)
public abstract class Vec3iMixin {
    @Shadow public static Vec3i ZERO;

    @Shadow public abstract int getX();
    @Shadow public abstract int getY();
    @Shadow public abstract int getZ();

    private static int abs(int value) {
        final int mask = value >> 31;
        return (value ^ mask) - mask;
    }

    @Overwrite
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vec3i vec3i)) {
            return false;
        }
        return this.getX() == vec3i.getX() && this.getY() == vec3i.getY() && this.getZ() == vec3i.getZ();
    }

    @Overwrite
    public int hashCode() {
        return (this.getY() + this.getZ() * 31) * 31 + this.getX();
    }

    @Overwrite
    public int compareTo(Vec3i other) {
        final int dy = this.getY() - other.getY();
        if (dy != 0) {
            return dy;
        }

        final int dz = this.getZ() - other.getZ();
        return dz != 0 ? dz : this.getX() - other.getX();
    }

    @Overwrite
    public Vec3i offset(int x, int y, int z) {
        if ((x | y | z) == 0) {
            return (Vec3i) (Object) this;
        }
        return new Vec3i(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    @Overwrite
    public Vec3i offset(Vec3i other) {
        return this.offset(other.getX(), other.getY(), other.getZ());
    }

    @Overwrite
    public Vec3i subtract(Vec3i other) {
        return this.offset(-other.getX(), -other.getY(), -other.getZ());
    }

    @Overwrite
    public Vec3i multiply(int scalar) {
        if (scalar == 1) {
            return (Vec3i) (Object) this;
        }
        if (scalar == 0) {
            return ZERO;
        }
        return new Vec3i(this.getX() * scalar, this.getY() * scalar, this.getZ() * scalar);
    }

    @Overwrite
    public Vec3i multiply(int x, int y, int z) {
        return new Vec3i(this.getX() * x, this.getY() * y, this.getZ() * z);
    }

    @Overwrite
    public Vec3i above() {
        return new Vec3i(this.getX(), this.getY() + 1, this.getZ());
    }

    @Overwrite
    public Vec3i above(int distance) {
        return distance == 0 ? (Vec3i) (Object) this : new Vec3i(this.getX(), this.getY() + distance, this.getZ());
    }

    @Overwrite
    public Vec3i below() {
        return new Vec3i(this.getX(), this.getY() - 1, this.getZ());
    }

    @Overwrite
    public Vec3i below(int distance) {
        return distance == 0 ? (Vec3i) (Object) this : new Vec3i(this.getX(), this.getY() - distance, this.getZ());
    }

    @Overwrite
    public Vec3i north() {
        return new Vec3i(this.getX(), this.getY(), this.getZ() - 1);
    }

    @Overwrite
    public Vec3i north(int distance) {
        return distance == 0 ? (Vec3i) (Object) this : new Vec3i(this.getX(), this.getY(), this.getZ() - distance);
    }

    @Overwrite
    public Vec3i south() {
        return new Vec3i(this.getX(), this.getY(), this.getZ() + 1);
    }

    @Overwrite
    public Vec3i south(int distance) {
        return distance == 0 ? (Vec3i) (Object) this : new Vec3i(this.getX(), this.getY(), this.getZ() + distance);
    }

    @Overwrite
    public Vec3i west() {
        return new Vec3i(this.getX() - 1, this.getY(), this.getZ());
    }

    @Overwrite
    public Vec3i west(int distance) {
        return distance == 0 ? (Vec3i) (Object) this : new Vec3i(this.getX() - distance, this.getY(), this.getZ());
    }

    @Overwrite
    public Vec3i east() {
        return new Vec3i(this.getX() + 1, this.getY(), this.getZ());
    }

    @Overwrite
    public Vec3i east(int distance) {
        return distance == 0 ? (Vec3i) (Object) this : new Vec3i(this.getX() + distance, this.getY(), this.getZ());
    }

    @Overwrite
    public Vec3i relative(Direction direction) {
        return new Vec3i(this.getX() + direction.getStepX(), this.getY() + direction.getStepY(), this.getZ() + direction.getStepZ());
    }

    @Overwrite
    public Vec3i relative(Direction direction, int distance) {
        if (distance == 0) {
            return (Vec3i) (Object) this;
        }
        return new Vec3i(
                this.getX() + direction.getStepX() * distance,
                this.getY() + direction.getStepY() * distance,
                this.getZ() + direction.getStepZ() * distance
        );
    }

    @Overwrite
    public Vec3i relative(Direction.Axis axis, int distance) {
        if (distance == 0) {
            return (Vec3i) (Object) this;
        }

        final int x = axis == Direction.Axis.X ? distance : 0;
        final int y = axis == Direction.Axis.Y ? distance : 0;
        final int z = axis == Direction.Axis.Z ? distance : 0;
        return new Vec3i(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    @Overwrite
    public Vec3i cross(Vec3i other) {
        final int x = this.getY() * other.getZ() - this.getZ() * other.getY();
        final int y = this.getZ() * other.getX() - this.getX() * other.getZ();
        final int z = this.getX() * other.getY() - this.getY() * other.getX();
        return new Vec3i(x, y, z);
    }

    @Overwrite
    public boolean closerThan(Vec3i other, double distance) {
        return this.distSqr(other) < distance * distance;
    }

    @Overwrite
    public boolean closerToCenterThan(Position other, double distance) {
        return this.distToCenterSqr(other) < distance * distance;
    }

    @Overwrite
    public double distSqr(Vec3i other) {
        return this.distToLowCornerSqr(other.getX(), other.getY(), other.getZ());
    }

    @Overwrite
    public double distToCenterSqr(Position other) {
        return this.distToCenterSqr(other.x(), other.y(), other.z());
    }

    @Overwrite
    public double distToCenterSqr(double x, double y, double z) {
        final double dx = this.getX() + 0.5D - x;
        final double dy = this.getY() + 0.5D - y;
        final double dz = this.getZ() + 0.5D - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Overwrite
    public double distToLowCornerSqr(double x, double y, double z) {
        final double dx = this.getX() - x;
        final double dy = this.getY() - y;
        final double dz = this.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Overwrite
    public int distManhattan(Vec3i other) {
        return abs(other.getX() - this.getX()) + abs(other.getY() - this.getY()) + abs(other.getZ() - this.getZ());
    }

    @Overwrite
    public int distChessboard(Vec3i other) {
        final int dx = abs(this.getX() - other.getX());
        final int dy = abs(this.getY() - other.getY());
        final int dz = abs(this.getZ() - other.getZ());
        return Math.max(dx, Math.max(dy, dz));
    }

    @Overwrite
    public int get(Direction.Axis axis) {
        if (axis == Direction.Axis.X) {
            return this.getX();
        }
        return axis == Direction.Axis.Y ? this.getY() : this.getZ();
    }
}
