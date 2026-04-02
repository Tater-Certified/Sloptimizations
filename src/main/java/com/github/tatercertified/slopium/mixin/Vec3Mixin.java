package com.github.tatercertified.slopium.mixin;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Vec3.class)
public abstract class Vec3Mixin {
    @Shadow public static Vec3 ZERO;
    @Shadow @Final public double x;
    @Shadow @Final public double y;
    @Shadow @Final public double z;

    @Overwrite
    public static Vec3 atLowerCornerOf(Vec3i pos) {
        return new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }

    @Overwrite
    public static Vec3 atLowerCornerWithOffset(Vec3i pos, double x, double y, double z) {
        return new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
    }

    @Overwrite
    public static Vec3 atCenterOf(Vec3i pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    @Overwrite
    public static Vec3 atBottomCenterOf(Vec3i pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    @Overwrite
    public static Vec3 upFromBottomCenterOf(Vec3i pos, double y) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + y, pos.getZ() + 0.5D);
    }

    @Overwrite
    public Vec3 vectorTo(Vec3 other) {
        return new Vec3(other.x - this.x, other.y - this.y, other.z - this.z);
    }

    @Overwrite
    public Vec3 normalize() {
        final double length = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        if (length < 9.999999747378752E-6D) {
            return ZERO;
        }
        return new Vec3(this.x / length, this.y / length, this.z / length);
    }

    @Overwrite
    public double dot(Vec3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    @Overwrite
    public Vec3 cross(Vec3 other) {
        return new Vec3(
                this.y * other.z - this.z * other.y,
                this.z * other.x - this.x * other.z,
                this.x * other.y - this.y * other.x
        );
    }

    @Overwrite
    public Vec3 subtract(Vec3 other) {
        return new Vec3(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    @Overwrite
    public Vec3 subtract(double value) {
        return new Vec3(this.x - value, this.y - value, this.z - value);
    }

    @Overwrite
    public Vec3 subtract(double x, double y, double z) {
        return new Vec3(this.x - x, this.y - y, this.z - z);
    }

    @Overwrite
    public Vec3 add(double value) {
        return new Vec3(this.x + value, this.y + value, this.z + value);
    }

    @Overwrite
    public Vec3 add(Vec3 other) {
        return new Vec3(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    @Overwrite
    public Vec3 add(double x, double y, double z) {
        return new Vec3(this.x + x, this.y + y, this.z + z);
    }

    @Overwrite
    public boolean closerThan(Position other, double distance) {
        return this.distanceToSqr(other.x(), other.y(), other.z()) < distance * distance;
    }

    @Overwrite
    public double distanceTo(Vec3 other) {
        final double dx = other.x - this.x;
        final double dy = other.y - this.y;
        final double dz = other.z - this.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Overwrite
    public double distanceToSqr(Vec3 other) {
        final double dx = other.x - this.x;
        final double dy = other.y - this.y;
        final double dz = other.z - this.z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Overwrite
    public double distanceToSqr(double x, double y, double z) {
        final double dx = x - this.x;
        final double dy = y - this.y;
        final double dz = z - this.z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Overwrite
    public boolean closerThan(Vec3 other, double horizontalDistance, double verticalDistance) {
        final double dx = other.x - this.x;
        final double dy = other.y - this.y;
        final double dz = other.z - this.z;
        return dx * dx + dz * dz < horizontalDistance * horizontalDistance && Math.abs(dy) < verticalDistance;
    }

    @Overwrite
    public Vec3 scale(double scale) {
        return new Vec3(this.x * scale, this.y * scale, this.z * scale);
    }

    @Overwrite
    public Vec3 reverse() {
        return new Vec3(-this.x, -this.y, -this.z);
    }

    @Overwrite
    public Vec3 multiply(Vec3 other) {
        return new Vec3(this.x * other.x, this.y * other.y, this.z * other.z);
    }

    @Overwrite
    public Vec3 multiply(double x, double y, double z) {
        return new Vec3(this.x * x, this.y * y, this.z * z);
    }

    @Overwrite
    public Vec3 horizontal() {
        return new Vec3(this.x, 0.0D, this.z);
    }

    @Overwrite
    public Vec3 offsetRandom(RandomSource random, float amount) {
        return new Vec3(
                this.x + (random.nextFloat() - 0.5F) * amount,
                this.y + (random.nextFloat() - 0.5F) * amount,
                this.z + (random.nextFloat() - 0.5F) * amount
        );
    }

    @Overwrite
    public Vec3 offsetRandomXZ(RandomSource random, float amount) {
        return new Vec3(
                this.x + (random.nextFloat() - 0.5F) * amount,
                this.y,
                this.z + (random.nextFloat() - 0.5F) * amount
        );
    }

    @Overwrite
    public double length() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    @Overwrite
    public double lengthSqr() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    @Overwrite
    public double horizontalDistance() {
        return Math.sqrt(this.x * this.x + this.z * this.z);
    }

    @Overwrite
    public double horizontalDistanceSqr() {
        return this.x * this.x + this.z * this.z;
    }

    @Overwrite
    public double get(Direction.Axis axis) {
        if (axis == Direction.Axis.X) {
            return this.x;
        }
        return axis == Direction.Axis.Y ? this.y : this.z;
    }

    @Overwrite
    public Vec3 with(Direction.Axis axis, double value) {
        if (axis == Direction.Axis.X) {
            return new Vec3(value, this.y, this.z);
        }
        if (axis == Direction.Axis.Y) {
            return new Vec3(this.x, value, this.z);
        }
        return new Vec3(this.x, this.y, value);
    }

    @Overwrite
    public Vec3 relative(Direction direction, double distance) {
        return new Vec3(
                this.x + direction.getStepX() * distance,
                this.y + direction.getStepY() * distance,
                this.z + direction.getStepZ() * distance
        );
    }

    @Overwrite
    public boolean isFinite() {
        return Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z);
    }
}
