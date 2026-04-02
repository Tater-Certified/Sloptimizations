package com.github.tatercertified.slopium.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkPos.class)
public abstract class ChunkPosMixin {
    @Shadow @Final private int x;
    @Shadow @Final private int z;
    @Shadow public static int MAX_COORDINATE_VALUE;

    private static int abs(int value) {
        final int mask = value >> 31;
        return (value ^ mask) - mask;
    }

    @Overwrite
    public static ChunkPos containing(BlockPos pos) {
        return new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Overwrite
    public static ChunkPos unpack(long packed) {
        return new ChunkPos((int) packed, (int) (packed >> 32));
    }

    @Overwrite
    public static ChunkPos minFromRegion(int x, int z) {
        return new ChunkPos(x << 5, z << 5);
    }

    @Overwrite
    public static ChunkPos maxFromRegion(int x, int z) {
        return new ChunkPos((x << 5) + 31, (z << 5) + 31);
    }

    @Overwrite
    public boolean isValid() {
        return ChunkPos.isValid(this.x, this.z);
    }

    @Overwrite
    public static boolean isValid(int x, int z) {
        return Math.max(abs(x), abs(z)) <= MAX_COORDINATE_VALUE;
    }

    @Overwrite
    public long pack() {
        return ChunkPos.pack(this.x, this.z);
    }

    @Overwrite
    public static long pack(int x, int z) {
        return ((long) x & 4294967295L) | (((long) z & 4294967295L) << 32);
    }

    @Overwrite
    public static long pack(BlockPos pos) {
        return ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Overwrite
    public static int hash(int x, int z) {
        final int hx = 1664525 * x + 1013904223;
        final int hz = 1664525 * (z ^ -559038737) + 1013904223;
        return hx ^ hz;
    }

    @Overwrite
    public int hashCode() {
        return ChunkPos.hash(this.x, this.z);
    }

    @Overwrite
    public int getMiddleBlockX() {
        return (this.x << 4) + 8;
    }

    @Overwrite
    public int getMiddleBlockZ() {
        return (this.z << 4) + 8;
    }

    @Overwrite
    public int getMinBlockX() {
        return this.x << 4;
    }

    @Overwrite
    public int getMinBlockZ() {
        return this.z << 4;
    }

    @Overwrite
    public int getMaxBlockX() {
        return (this.x << 4) + 15;
    }

    @Overwrite
    public int getMaxBlockZ() {
        return (this.z << 4) + 15;
    }

    @Overwrite
    public int getRegionX() {
        return this.x >> 5;
    }

    @Overwrite
    public int getRegionZ() {
        return this.z >> 5;
    }

    @Overwrite
    public int getRegionLocalX() {
        return this.x & 31;
    }

    @Overwrite
    public int getRegionLocalZ() {
        return this.z & 31;
    }

    @Overwrite
    public BlockPos getBlockAt(int x, int y, int z) {
        return new BlockPos((this.x << 4) + x, y, (this.z << 4) + z);
    }

    @Overwrite
    public int getBlockX(int x) {
        return (this.x << 4) + x;
    }

    @Overwrite
    public int getBlockZ(int z) {
        return (this.z << 4) + z;
    }

    @Overwrite
    public BlockPos getMiddleBlockPosition(int y) {
        return new BlockPos((this.x << 4) + 8, y, (this.z << 4) + 8);
    }

    @Overwrite
    public boolean contains(BlockPos pos) {
        final int x = pos.getX();
        final int z = pos.getZ();
        return x >= (this.x << 4) && x <= ((this.x << 4) + 15) && z >= (this.z << 4) && z <= ((this.z << 4) + 15);
    }

    @Overwrite
    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }

    @Overwrite
    public BlockPos getWorldPosition() {
        return new BlockPos(this.x << 4, 0, this.z << 4);
    }

    @Overwrite
    public int getChessboardDistance(ChunkPos other) {
        return this.getChessboardDistance(other.x(), other.z());
    }

    @Overwrite
    public int getChessboardDistance(int x, int z) {
        return Math.max(abs(x - this.x), abs(z - this.z));
    }

    @Overwrite
    public int distanceSquared(ChunkPos other) {
        final int dx = other.x() - this.x;
        final int dz = other.z() - this.z;
        return dx * dx + dz * dz;
    }

    @Overwrite
    public int distanceSquared(long packed) {
        final int dx = ChunkPos.getX(packed) - this.x;
        final int dz = ChunkPos.getZ(packed) - this.z;
        return dx * dx + dz * dz;
    }
}
