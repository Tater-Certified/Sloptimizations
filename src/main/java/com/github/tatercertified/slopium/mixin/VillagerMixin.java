package com.github.tatercertified.slopium.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.SpawnUtil;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager {
    private static final long GOLEM_DETECTED_MEMORY_TICKS = 600L;

    @Shadow private static EntityDimensions BABY_DIMENSIONS;

    @Shadow private int updateMerchantTimer;
    @Shadow private boolean increaseProfessionLevelOnUpdate;
    @Shadow private Player lastTradedPlayer;
    @Shadow private int foodLevel;
    @Shadow @Final private GossipContainer gossips;
    @Shadow private long lastGossipTime;
    @Shadow private long lastGossipDecayTime;
    @Shadow private long lastRestockGameTime;
    @Shadow private int numberOfRestocksToday;
    @Shadow private boolean assignProfessionWhenSpawned;

    @Shadow public abstract VillagerData getVillagerData();
    @Shadow protected abstract void setVillagerData(VillagerData villagerData);
    @Shadow protected abstract void increaseMerchantCareer(ServerLevel level);
    @Shadow protected abstract void updateSpecialPrices(Player player);
    @Shadow protected abstract void resendOffersToTradingPlayer();
    @Shadow protected abstract void updateDemand();
    @Shadow protected abstract void maybeDecayGossip();
    @Shadow protected abstract boolean golemSpawnConditionsMet(long gameTime);

    protected VillagerMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * @author tatercertified
     * @reason Flattens hot villager AI control flow and avoids repeated virtual calls.
     */
    @Overwrite
    protected void customServerAiStep(ServerLevel level) {
        final ProfilerFiller profiler = Profiler.get();
        profiler.push("villagerBrain");
        ((Brain) this.getBrain()).tick(level, (Villager) (Object) this);
        profiler.pop();

        this.assignProfessionWhenSpawned = false;

        if (!this.isTrading()) {
            final int timer = this.updateMerchantTimer - 1;
            if (this.updateMerchantTimer > 0) {
                this.updateMerchantTimer = timer;
                if (timer == 0) {
                    if (this.increaseProfessionLevelOnUpdate) {
                        this.increaseMerchantCareer(level);
                        this.increaseProfessionLevelOnUpdate = false;
                    }

                    this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
                }
            }
        }

        final Player tradedPlayer = this.lastTradedPlayer;
        if (tradedPlayer != null) {
            level.onReputationEvent(net.minecraft.world.entity.ai.village.ReputationEventType.TRADE, tradedPlayer, (Villager) (Object) this);
            level.broadcastEntityEvent(this, (byte) 14);
            this.lastTradedPlayer = null;
        }

        if (!this.isNoAi() && this.random.nextInt(100) == 0) {
            final Raid raid = level.getRaidAt(this.blockPosition());
            if (raid != null && raid.isActive() && !raid.isOver()) {
                level.broadcastEntityEvent(this, (byte) 42);
            }
        }

        final VillagerData villagerData = this.getVillagerData();
        if (villagerData.profession().is(VillagerProfession.NONE) && this.isTrading()) {
            this.stopTrading();
        }

        super.customServerAiStep(level);
    }

    /**
     * @author tatercertified
     * @reason Avoids duplicate unhappy counter reads on a per-tick path.
     */
    @Overwrite
    public void tick() {
        super.tick();
        final int unhappy = this.getUnhappyCounter();
        if (unhappy > 0) {
            this.setUnhappyCounter(unhappy - 1);
        }

        this.maybeDecayGossip();
    }

    /**
     * @author tatercertified
     * @reason Inlines tiny helper calls in a hot interaction path.
     */
    @Overwrite
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        final ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.is(Items.VILLAGER_SPAWN_EGG) || !this.isAlive() || this.isTrading() || this.isSleeping()) {
            return super.mobInteract(player, hand);
        }

        if (this.isBaby()) {
            this.setUnhappyCounter(40);
            if (!this.level().isClientSide()) {
                this.makeSound(net.minecraft.sounds.SoundEvents.VILLAGER_NO);
            }

            return InteractionResult.SUCCESS;
        }

        if (!this.level().isClientSide()) {
            final MerchantOffers offers = this.getOffers();
            final boolean empty = offers.isEmpty();
            if (hand == InteractionHand.MAIN_HAND) {
                if (empty) {
                    this.setUnhappyCounter(40);
                    this.makeSound(net.minecraft.sounds.SoundEvents.VILLAGER_NO);
                }

                player.awardStat(Stats.TALKED_TO_VILLAGER);
            }

            if (empty) {
                return InteractionResult.CONSUME;
            }

            this.updateSpecialPrices(player);
            this.setTradingPlayer(player);
            this.openTradingScreen(player, this.getDisplayName(), this.getVillagerData().level());
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * @author tatercertified
     * @reason Replaces iterator allocation with indexed access.
     */
    @Overwrite
    public void restock() {
        this.updateDemand();

        final MerchantOffers offers = this.getOffers();
        for (int i = 0, size = offers.size(); i < size; i++) {
            offers.get(i).resetUses();
        }

        this.resendOffersToTradingPlayer();
        this.lastRestockGameTime = this.level().getGameTime();
        this.numberOfRestocksToday++;
    }

    /**
     * @author tatercertified
     * @reason Avoids repeated lookups and branches in an inventory hot path.
     */
    @Overwrite
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        final Item item = stack.getItem();
        return (stack.is(ItemTags.VILLAGER_PICKS_UP) || this.getVillagerData().profession().value().requestedItems().contains(item))
                && this.getInventory().canAddItem(stack);
    }

    /**
     * @author tatercertified
     * @reason Removes stream/map allocation from food counting.
     */
    @Overwrite
    private int countFoodPointsInInventory() {
        final SimpleContainer inventory = this.getInventory();
        return (inventory.countItem(Items.BREAD) << 2)
                + inventory.countItem(Items.POTATO)
                + inventory.countItem(Items.CARROT)
                + inventory.countItem(Items.BEETROOT);
    }

    /**
     * @author tatercertified
     * @reason Uses the streamlined counter directly.
     */
    @Overwrite
    public boolean hasExcessFood() {
        return this.countFoodPointsInInventory() >= 24;
    }

    /**
     * @author tatercertified
     * @reason Uses the streamlined counter directly.
     */
    @Overwrite
    public boolean wantsMoreFood() {
        return this.countFoodPointsInInventory() < 12;
    }

    /**
     * @author tatercertified
     * @reason Replaces predicate allocation with a direct slot scan.
     */
    @Overwrite
    public boolean hasFarmSeeds() {
        final SimpleContainer inventory = this.getInventory();
        for (int i = 0, size = inventory.getContainerSize(); i < size; i++) {
            if (inventory.getItem(i).is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @author tatercertified
     * @reason Avoids duplicate field loads and helper churn.
     */
    @Overwrite
    public void gossip(ServerLevel level, Villager other, long gameTime) {
        final VillagerMixin otherVillager = (VillagerMixin) (Object) other;
        final long thisLast = this.lastGossipTime;
        if (gameTime >= thisLast && gameTime < thisLast + 1200L) {
            return;
        }

        final long otherLast = otherVillager.lastGossipTime;
        if (gameTime >= otherLast && gameTime < otherLast + 1200L) {
            return;
        }

        this.gossips.transferFrom(otherVillager.gossips, this.random, 10);
        this.lastGossipTime = gameTime;
        otherVillager.lastGossipTime = gameTime;
        this.spawnGolemIfNeeded(level, gameTime, 5);
    }

    /**
     * @author tatercertified
     * @reason Replaces stream/filter/limit/toList with a straight loop.
     */
    @Overwrite
    public void spawnGolemIfNeeded(ServerLevel level, long gameTime, int requiredVillagers) {
        if (!this.wantsToSpawnGolem(gameTime)) {
            return;
        }

        final AABB searchBox = this.getBoundingBox().inflate(10.0D, 10.0D, 10.0D);
        final java.util.List<Villager> villagers = level.getEntitiesOfClass(Villager.class, searchBox);
        int agreeingVillagers = 0;
        for (int i = 0, size = villagers.size(); i < size; i++) {
            if (villagers.get(i).wantsToSpawnGolem(gameTime) && ++agreeingVillagers >= requiredVillagers) {
                break;
            }
        }

        if (agreeingVillagers < requiredVillagers) {
            return;
        }

        final BlockPos pos = this.blockPosition();
        if (SpawnUtil.trySpawnMob(EntityType.IRON_GOLEM, EntitySpawnReason.MOB_SUMMONED, level, pos, 10, 8, 6, SpawnUtil.Strategy.LEGACY_IRON_GOLEM, false).isEmpty()) {
            return;
        }

        for (int i = 0, size = villagers.size(); i < size; i++) {
            villagers.get(i).getBrain().setMemoryWithExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY, true, GOLEM_DETECTED_MEMORY_TICKS);
        }
    }

    /**
     * @author tatercertified
     * @reason Uses the supplied time and avoids extra local churn.
     */
    @Overwrite
    public boolean wantsToSpawnGolem(long gameTime) {
        return this.golemSpawnConditionsMet(gameTime) && !this.getBrain().hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY);
    }

    /**
     * @author tatercertified
     * @reason Uses getBrain directly and avoids field-level indirection.
     */
    @Overwrite
    public void stopSleeping() {
        super.stopSleeping();
        this.getBrain().setMemory(MemoryModuleType.LAST_WOKEN, this.level().getGameTime());
    }
}
