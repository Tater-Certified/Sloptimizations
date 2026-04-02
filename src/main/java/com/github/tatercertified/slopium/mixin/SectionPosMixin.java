package com.github.tatercertified.slopium.mixin;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.core.*;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SectionPos.class)
public abstract class SectionPosMixin extends Vec3i {
    public SectionPosMixin(int x, int y, int z) {
        super(x, y, z);
    }

    @Overwrite
    public static SectionPos of(BlockPos pos) {
        return SectionPos.of(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    @Overwrite
    public static SectionPos of(ChunkPos pos, int y) {
        return SectionPos.of(pos.x(), y, pos.z());
    }

    @Overwrite
    public static SectionPos of(Position pos) {
        return SectionPos.of(SectionPos.blockToSectionCoord(pos.x()), SectionPos.blockToSectionCoord(pos.y()), SectionPos.blockToSectionCoord(pos.z()));
    }

    @Overwrite
    public static SectionPos of(long packed) {
        return SectionPos.of(SectionPos.x(packed), SectionPos.y(packed), SectionPos.z(packed));
    }

    @Overwrite
    public static long offset(long packed, Direction direction) {
        return SectionPos.offset(packed, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    @Overwrite
    public static long offset(long packed, int x, int y, int z) {
        return SectionPos.asLong(SectionPos.x(packed) + x, SectionPos.y(packed) + y, SectionPos.z(packed) + z);
    }

    @Overwrite
    public static int posToSectionCoord(double value) {
        return SectionPos.blockToSectionCoord(value);
    }

    @Overwrite
    public static int blockToSectionCoord(double value) {
        return ((int) Math.floor(value)) >> 4;
    }

    @Overwrite
    public static short sectionRelativePos(BlockPos pos) {
        return (short) (((pos.getX() & 15) << 8) | ((pos.getZ() & 15) << 4) | (pos.getY() & 15));
    }

    @Overwrite
    public int relativeToBlockX(short relative) {
        return (this.getX() << 4) + ((relative >>> 8) & 15);
    }

    @Overwrite
    public int relativeToBlockY(short relative) {
        return (this.getY() << 4) + (relative & 15);
    }

    @Overwrite
    public int relativeToBlockZ(short relative) {
        return (this.getZ() << 4) + ((relative >>> 4) & 15);
    }

    @Overwrite
    public BlockPos relativeToBlockPos(short relative) {
        return new BlockPos(this.relativeToBlockX(relative), this.relativeToBlockY(relative), this.relativeToBlockZ(relative));
    }

    @Overwrite
    public static int sectionToBlockCoord(int section, int offset) {
        return (section << 4) + offset;
    }

    @Overwrite
    public int x() {
        return this.getX();
    }

    @Overwrite
    public int y() {
        return this.getY();
    }

    @Overwrite
    public int z() {
        return this.getZ();
    }

    @Overwrite
    public int minBlockX() {
        return this.getX() << 4;
    }

    @Overwrite
    public int minBlockY() {
        return this.getY() << 4;
    }

    @Overwrite
    public int minBlockZ() {
        return this.getZ() << 4;
    }

    @Overwrite
    public int maxBlockX() {
        return (this.getX() << 4) + 15;
    }

    @Overwrite
    public int maxBlockY() {
        return (this.getY() << 4) + 15;
    }

    @Overwrite
    public int maxBlockZ() {
        return (this.getZ() << 4) + 15;
    }

    @Overwrite
    public static long blockToSection(long packed) {
        return SectionPos.asLong(BlockPos.getX(packed) >> 4, BlockPos.getY(packed) >> 4, BlockPos.getZ(packed) >> 4);
    }

    @Overwrite
    public static long getZeroNode(int x, int z) {
        return SectionPos.getZeroNode(SectionPos.asLong(x, 0, z));
    }

    @Overwrite
    public static long getZeroNode(long packed) {
        return packed & -1048576L;
    }

    @Overwrite
    public static long sectionToChunk(long packed) {
        return ChunkPos.pack(SectionPos.x(packed), SectionPos.z(packed));
    }

    @Overwrite
    public BlockPos origin() {
        return new BlockPos(this.getX() << 4, this.getY() << 4, this.getZ() << 4);
    }

    @Overwrite
    public BlockPos center() {
        return new BlockPos((this.getX() << 4) + 8, (this.getY() << 4) + 8, (this.getZ() << 4) + 8);
    }

    @Overwrite
    public ChunkPos chunk() {
        return new ChunkPos(this.getX(), this.getZ());
    }

    @Overwrite
    public static long asLong(BlockPos pos) {
        return SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    @Overwrite
    public static long asLong(int x, int y, int z) {
        return ((long) x & 4194303L) << 42 | ((long) z & 4194303L) << 20 | ((long) y & 1048575L);
    }

    @Overwrite
    public long asLong() {
        return SectionPos.asLong(this.getX(), this.getY(), this.getZ());
    }

    @Overwrite
    public SectionPos offset(int x, int y, int z) {
        if ((x | y | z) == 0) {
            return (SectionPos) (Object) this;
        }
        return SectionPos.of(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    @Overwrite
    public static void aroundAndAtBlockPos(BlockPos pos, LongConsumer consumer) {
        SectionPos.aroundAndAtBlockPos(pos.getX(), pos.getY(), pos.getZ(), consumer);
    }

    @Overwrite
    public static void aroundAndAtBlockPos(long packed, LongConsumer consumer) {
        SectionPos.aroundAndAtBlockPos(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed), consumer);
    }
}
