package com.gregtechceu.gtceu.regression;

import com.gregtechceu.gtceu.api.cover.filter.CompositeFilter;
import com.gregtechceu.gtceu.api.cover.filter.Filters;
import com.gregtechceu.gtceu.api.cover.filter.SimpleItemFilter;
import com.gregtechceu.gtceu.api.cover.filter.TagFilter;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import com.gregtechceu.gtceu.common.item.behavior.ItemMagnetBehavior.FilterMode;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.mojang.serialization.JsonOps;

import java.util.ArrayList;
import java.util.Collections;

@GameTestHolder("gtceu_regression")
@PrefixGameTestTemplate(false)
public class FilterMergeTest {

    @GameTest(template = "empty_5x5")
    public static void filterEditsPersistAndMagnetCopiesAllSlots(GameTestHelper helper) {
        ItemStack filterItem = GTItems.ITEM_FILTER.asStack();
        var filter = (SimpleItemFilter) Filters.loadItemFilter(filterItem);
        filter.getMatches()[0] = new ItemStack(Items.DIAMOND);
        filter.getMatches()[8] = new ItemStack(Items.IRON_INGOT);
        filter.setIgnoreNbt(true);
        helper.assertTrue(filterItem.get(GTDataComponents.SIMPLE_ITEM_FILTER) == filter,
                "Editing a filter must write to the registered component type");
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var saved = ItemStack.CODEC.encodeStart(ops, filterItem).getOrThrow();
        var restored = Filters.loadItemFilter(ItemStack.CODEC.parse(ops, saved).getOrThrow());
        helper.assertTrue(restored.test(new ItemStack(Items.DIAMOND)) &&
                restored.test(new ItemStack(Items.IRON_INGOT)) && !restored.test(new ItemStack(Items.STONE)),
                "Saved filter must retain both first and ninth slots");

        ItemStack magnet = GTItems.ITEM_MAGNET_LV.asStack();
        magnet.set(GTDataComponents.SIMPLE_ITEM_FILTER, filter);
        var copy = (SimpleItemFilter) FilterMode.SIMPLE.loadFilter(magnet);
        helper.assertTrue(copy != filter && copy.getMatches().length == 9,
                "Magnet editor must copy all nine slots without changing the source");
        helper.assertTrue(copy.test(new ItemStack(Items.IRON_INGOT)), "Magnet copy must retain the ninth slot");
        copy.getMatches()[0].setCount(2);
        helper.assertTrue(filter.getMatches()[0].getCount() == 1, "Editing the copy must not mutate source stacks");
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void restoredTagAndCompositeFiltersMatch(GameTestHelper helper) {
        var tag = new TagFilter<ItemStack, Item>("c:ingots/iron", ItemStack::getItem, ItemStack::getTags);
        helper.assertTrue(tag.test(new ItemStack(Items.IRON_INGOT)) && !tag.test(new ItemStack(Items.DIAMOND)),
                "A loaded tag expression must work before reopening the editor");

        ItemStack nested = GTItems.ITEM_FILTER.asStack();
        nested.set(GTDataComponents.SIMPLE_ITEM_FILTER, SimpleItemFilter.forItems(true, new ItemStack(Items.DIAMOND)));
        var slots = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        slots.set(0, nested);
        var composite = new CompositeFilter<>(slots, ItemStack.class);
        var codec = CompositeFilter.codec(ItemStack.class);
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var saved = codec.encodeStart(ops, composite).getOrThrow();
        var restored = codec.parse(ops, saved).getOrThrow();
        helper.assertTrue(restored.test(new ItemStack(Items.DIAMOND)) && !restored.test(new ItemStack(Items.STONE)),
                "Composite filters must round trip with unused slots");
        helper.succeed();
    }
}
