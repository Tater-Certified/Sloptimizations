package com.github.tatercertified.slopium.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow protected ServerLevel level;
    @Shadow protected ServerPlayer player;
    @Shadow private int gameTicks;
    @Shadow private int lastSentState;

    /**
     * @author tatercertified
     * @reason Avoids repeated field walks on the per-tick server block-breaking path.
     */
    @Overwrite
    private float incrementDestroyProgress(BlockState state, BlockPos pos, int startTick) {
        final ServerPlayer player = this.player;
        final ServerLevel level = this.level;
        final float progress = state.getDestroyProgress(player, level, pos) * (this.gameTicks - startTick + 1);
        final int stage = (int) (progress * 10.0F);

        if (stage != this.lastSentState) {
            level.destroyBlockProgress(player.getId(), pos, stage);
            this.lastSentState = stage;
        }

        return progress;
    }
}
