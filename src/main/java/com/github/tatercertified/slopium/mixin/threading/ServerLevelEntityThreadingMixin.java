package com.github.tatercertified.slopium.mixin.threading;

import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Iterator;
import java.util.function.Consumer;

@Mixin(ServerLevel.class)
public abstract class ServerLevelEntityThreadingMixin {
    @Unique private static final int SLOPIUM$ENTITY_CACHE_TTL = 3;
    @Unique private static final int SLOPIUM$CHUNK_CACHE_TTL = 3;
    @Unique private static final int SLOPIUM$CACHE_TRIM_INTERVAL = 200;
    @Unique private static final int SLOPIUM$MIN_OPTIMIZED_ENTITY_COUNT = 192;

    @Shadow @Final private ServerChunkCache chunkSource;
    @Shadow public abstract void tickNonPassenger(Entity entity);

    @Unique private Entity[] slopium$entitySnapshot = new Entity[0];
    @Unique private long[] slopium$entityChunkKeys = new long[0];
    @Unique private boolean[] slopium$entityEligible = new boolean[0];
    @Unique private final Long2ByteOpenHashMap slopium$tickChunkEligibility = new Long2ByteOpenHashMap();
    @Unique private final Long2ByteOpenHashMap slopium$cachedChunkEligibility = new Long2ByteOpenHashMap();
    @Unique private final Long2LongOpenHashMap slopium$cachedChunkEligibilityTick = new Long2LongOpenHashMap();
    @Unique private final Int2LongOpenHashMap slopium$cachedEntityChunkKey = new Int2LongOpenHashMap();
    @Unique private final Int2LongOpenHashMap slopium$cachedEntityEligibilityTick = new Int2LongOpenHashMap();
    @Unique private final Int2ByteOpenHashMap slopium$cachedEntityEligibility = new Int2ByteOpenHashMap();

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"
            )
    )
    private void slopium$cachedEntityTickIteration(EntityTickList entityTickList, Consumer<Entity> consumer) {
        EntityTickListAccessor accessor = (EntityTickListAccessor) entityTickList;
        if (accessor.slopium$getIterated() != null) {
            entityTickList.forEach(consumer);
            return;
        }

        Int2ObjectMap<Entity> active = accessor.slopium$getActive();
        int entityCount = active.size();
        if (entityCount < SLOPIUM$MIN_OPTIMIZED_ENTITY_COUNT) {
            entityTickList.forEach(consumer);
            return;
        }

        this.slopium$ensureCapacity(entityCount);

        accessor.slopium$setIterated(active);
        try {
            Iterator<Entity> iterator = active.values().iterator();
            for (int index = 0; index < entityCount && iterator.hasNext(); index++) {
                this.slopium$entitySnapshot[index] = iterator.next();
            }

            this.slopium$runCachedEntityTickLoop(entityCount);
        } finally {
            accessor.slopium$setIterated(null);
        }
    }

    @Unique
    private void slopium$runCachedEntityTickLoop(int entityCount) {
        ServerLevel level = (ServerLevel) (Object) this;
        TickRateManager tickRateManager = level.tickRateManager();
        DistanceManager distanceManager = this.chunkSource.chunkMap.getDistanceManager();
        ProfilerFiller profiler = Profiler.get();
        long gameTime = level.getGameTime();

        this.slopium$tickChunkEligibility.clear();
        this.slopium$fillEligibility(entityCount, tickRateManager, distanceManager, gameTime);

        for (int index = 0; index < entityCount; index++) {
            if (!this.slopium$entityEligible[index]) {
                continue;
            }

            Entity entity = this.slopium$entitySnapshot[index];
            if (entity == null || entity.isRemoved()) {
                continue;
            }

            Entity vehicle = entity.getVehicle();
            if (vehicle != null) {
                if (vehicle.isRemoved() || !vehicle.hasPassenger(entity)) {
                    entity.stopRiding();
                } else {
                    continue;
                }
            }

            profiler.push("checkDespawn");
            entity.checkDespawn();
            profiler.pop();

            profiler.push("tick");
            ((Level) (Object) this).guardEntityTick(this::tickNonPassenger, entity);
            profiler.pop();
        }

        this.slopium$trimCaches(gameTime, entityCount);
    }

    @Unique
    private void slopium$fillEligibility(int entityCount, TickRateManager tickRateManager, DistanceManager distanceManager, long gameTime) {
        for (int index = 0; index < entityCount; index++) {
            Entity entity = this.slopium$entitySnapshot[index];
            boolean eligible = false;
            long chunkKey = Long.MIN_VALUE;

            if (entity != null && !entity.isRemoved() && !tickRateManager.isEntityFrozen(entity)) {
                if (entity instanceof ServerPlayer) {
                    eligible = true;
                } else {
                    chunkKey = entity.chunkPosition().pack();
                    eligible = this.slopium$isChunkEligible(entity.getId(), chunkKey, distanceManager, gameTime);
                }
            }

            this.slopium$entityChunkKeys[index] = chunkKey;
            this.slopium$entityEligible[index] = eligible;
        }
    }

    @Unique
    private boolean slopium$isChunkEligible(int entityId, long chunkKey, DistanceManager distanceManager, long gameTime) {
        if (this.slopium$cachedEntityChunkKey.containsKey(entityId)
                && this.slopium$cachedEntityChunkKey.get(entityId) == chunkKey
                && this.slopium$cachedEntityEligibilityTick.containsKey(entityId)
                && gameTime - this.slopium$cachedEntityEligibilityTick.get(entityId) <= SLOPIUM$ENTITY_CACHE_TTL) {
            return this.slopium$cachedEntityEligibility.get(entityId) != 0;
        }

        boolean eligible;
        if (this.slopium$tickChunkEligibility.containsKey(chunkKey)) {
            eligible = this.slopium$tickChunkEligibility.get(chunkKey) != 0;
        } else if (this.slopium$cachedChunkEligibilityTick.containsKey(chunkKey)
                && gameTime - this.slopium$cachedChunkEligibilityTick.get(chunkKey) <= SLOPIUM$CHUNK_CACHE_TTL) {
            eligible = this.slopium$cachedChunkEligibility.get(chunkKey) != 0;
            this.slopium$tickChunkEligibility.put(chunkKey, (byte) (eligible ? 1 : 0));
        } else {
            eligible = distanceManager.inEntityTickingRange(chunkKey);
            byte encoded = (byte) (eligible ? 1 : 0);
            this.slopium$tickChunkEligibility.put(chunkKey, encoded);
            this.slopium$cachedChunkEligibility.put(chunkKey, encoded);
            this.slopium$cachedChunkEligibilityTick.put(chunkKey, gameTime);
        }

        this.slopium$cachedEntityChunkKey.put(entityId, chunkKey);
        this.slopium$cachedEntityEligibility.put(entityId, (byte) (eligible ? 1 : 0));
        this.slopium$cachedEntityEligibilityTick.put(entityId, gameTime);
        return eligible;
    }

    @Unique
    private void slopium$ensureCapacity(int entityCount) {
        if (this.slopium$entitySnapshot.length >= entityCount) {
            return;
        }

        int capacity = Integer.highestOneBit(Math.max(16, entityCount - 1)) << 1;
        this.slopium$entitySnapshot = new Entity[capacity];
        this.slopium$entityChunkKeys = new long[capacity];
        this.slopium$entityEligible = new boolean[capacity];
    }

    @Unique
    private void slopium$trimCaches(long gameTime, int entityCount) {
        if (gameTime % SLOPIUM$CACHE_TRIM_INTERVAL != 0L) {
            return;
        }

        long chunkCutoff = gameTime - (SLOPIUM$CHUNK_CACHE_TTL * 4L);
        Long2LongOpenHashMap.FastEntrySet chunkTicks = this.slopium$cachedChunkEligibilityTick.long2LongEntrySet();
        Iterator<it.unimi.dsi.fastutil.longs.Long2LongMap.Entry> chunkIterator = chunkTicks.fastIterator();
        while (chunkIterator.hasNext()) {
            it.unimi.dsi.fastutil.longs.Long2LongMap.Entry entry = chunkIterator.next();
            if (entry.getLongValue() < chunkCutoff) {
                long key = entry.getLongKey();
                chunkIterator.remove();
                this.slopium$cachedChunkEligibility.remove(key);
            }
        }

        if (this.slopium$cachedEntityEligibilityTick.size() <= entityCount * 3) {
            return;
        }

        long entityCutoff = gameTime - (SLOPIUM$ENTITY_CACHE_TTL * 4L);
        Iterator<it.unimi.dsi.fastutil.ints.Int2LongMap.Entry> entityIterator = this.slopium$cachedEntityEligibilityTick.int2LongEntrySet().fastIterator();
        while (entityIterator.hasNext()) {
            it.unimi.dsi.fastutil.ints.Int2LongMap.Entry entry = entityIterator.next();
            if (entry.getLongValue() < entityCutoff) {
                int entityId = entry.getIntKey();
                entityIterator.remove();
                this.slopium$cachedEntityChunkKey.remove(entityId);
                this.slopium$cachedEntityEligibility.remove(entityId);
            }
        }
    }
}
