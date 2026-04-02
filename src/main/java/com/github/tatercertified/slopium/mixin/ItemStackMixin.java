package com.github.tatercertified.slopium.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Objects;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Shadow public static ItemStack EMPTY;

    @Shadow @Final private Holder<Item> item;
    @Shadow private int count;
    @Shadow @Final private PatchedDataComponentMap components;

    /**
     * @author tatercertified
     * @reason Keeps the ubiquitous emptiness check to raw field reads.
     */
    @Overwrite
    public boolean isEmpty() {
        return (Object) this == EMPTY || this.count <= 0 || this.item.value() == Items.AIR;
    }

    /**
     * @author tatercertified
     * @reason Keeps list comparisons indexed and avoids repeated size reads.
     */
    @Overwrite
    public static boolean listMatches(List<ItemStack> left, List<ItemStack> right) {
        final int size = left.size();
        if (size != right.size()) {
            return false;
        }

        for (int i = 0; i < size; i++) {
            if (!ItemStack.matches(left.get(i), right.get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * @author tatercertified
     * @reason Avoids method indirection on a common equality helper.
     */
    @Overwrite
    public static boolean isSameItem(ItemStack left, ItemStack right) {
        return left == right || left.getItem() == right.getItem();
    }

    /**
     * @author tatercertified
     * @reason Keeps stack comparisons on direct item/component checks.
     */
    @Overwrite
    public static boolean isSameItemSameComponents(ItemStack left, ItemStack right) {
        if (left == right) {
            return true;
        }

        if (left.getItem() != right.getItem()) {
            return false;
        }

        if (left.isEmpty()) {
            return right.isEmpty();
        }

        return Objects.equals(((ItemStackMixin) (Object) left).components, ((ItemStackMixin) (Object) right).components);
    }
}
