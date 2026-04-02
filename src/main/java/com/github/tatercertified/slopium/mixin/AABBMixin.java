package com.github.tatercertified.slopium.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AABB.class)
public abstract class AABBMixin {
    @Shadow @Final public double minX;
    @Shadow @Final public double minY;
    @Shadow @Final public double minZ;
    @Shadow @Final public double maxX;
    @Shadow @Final public double maxY;
    @Shadow @Final public double maxZ;

    /**
     * @author tatercertified
     * @reason Avoids the extra method hop on a very hot collision helper.
     */
    @Overwrite
    public boolean intersects(AABB other) {
        return this.minX < other.maxX
                && this.maxX > other.minX
                && this.minY < other.maxY
                && this.maxY > other.minY
                && this.minZ < other.maxZ
                && this.maxZ > other.minZ;
    }

    /**
     * @author tatercertified
     * @reason Keeps the hot collision check branch-light.
     */
    @Overwrite
    public boolean intersects(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.minX < maxX
                && this.maxX > minX
                && this.minY < maxY
                && this.maxY > minY
                && this.minZ < maxZ
                && this.maxZ > minZ;
    }

    /**
     * @author tatercertified
     * @reason Avoids the generic overload when moving boxes by block coordinates.
     */
    @Overwrite
    public AABB move(BlockPos pos) {
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();
        return new AABB(
                this.minX + x,
                this.minY + y,
                this.minZ + z,
                this.maxX + x,
                this.maxY + y,
                this.maxZ + z
        );
    }

    /**
     * @author tatercertified
     * @reason Avoids the Vec3 overload hop on another hot bounds test.
     */
    @Overwrite
    public boolean contains(double x, double y, double z) {
        return x >= this.minX
                && x < this.maxX
                && y >= this.minY
                && y < this.maxY
                && z >= this.minZ
                && z < this.maxZ;
    }

    /**
     * @author tatercertified
     * @reason Replaces nested Math.max calls and helper dispatch with direct arithmetic.
     */
    @Overwrite
    public double distanceToSqr(Vec3 pos) {
        double dx = 0.0D;
        if (pos.x < this.minX) {
            dx = this.minX - pos.x;
        } else if (pos.x > this.maxX) {
            dx = pos.x - this.maxX;
        }

        double dy = 0.0D;
        if (pos.y < this.minY) {
            dy = this.minY - pos.y;
        } else if (pos.y > this.maxY) {
            dy = pos.y - this.maxY;
        }

        double dz = 0.0D;
        if (pos.z < this.minZ) {
            dz = this.minZ - pos.z;
        } else if (pos.z > this.maxZ) {
            dz = pos.z - this.maxZ;
        }

        return dx * dx + dy * dy + dz * dz;
    }
}
