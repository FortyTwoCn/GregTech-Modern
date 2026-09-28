package com.gregtechceu.gtceu.regression;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterialItems;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@GameTestHolder("gtceu_regression")
@PrefixGameTestTemplate(false)
public class MaterialEntryCacheTest {

    @GameTest(template = "empty_5x5", timeoutTicks = 1200)
    public static void concurrentLookupsAndReload(GameTestHelper helper) throws Exception {
        List<Item> items = BuiltInRegistries.ITEM.stream().toList();
        List<MaterialEntry> expected = new ArrayList<>(items.size());
        for (Item item : items) {
            expected.add(ChemicalHelper.getMaterialEntry(item));
        }
        long missing = expected.stream().filter(Objects::isNull).count();
        helper.assertTrue(missing > 100, "Stress test needs enough uncached items to grow the negative cache");
        var executor = Executors.newFixedThreadPool(8);
        try {
            for (int round = 0; round < 6; round++) {
                synchronized (ItemMaterialData.class) {
                    GTMaterialItems.ITEMS_WITHOUT_MATERIAL.clear();
                    // Force repeated growth of the exact fastutil set involved in the reported crash.
                    if (GTMaterialItems.ITEMS_WITHOUT_MATERIAL instanceof ObjectOpenHashSet<Item> set) {
                        set.trim();
                    }
                }
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> tasks = new ArrayList<>();
                for (int worker = 0; worker < 8; worker++) {
                    int offset = worker * items.size() / 8;
                    tasks.add(executor.submit(() -> {
                        start.await();
                        for (int i = 0; i < items.size(); i++) {
                            int index = (i + offset) % items.size();
                            var actual = ChemicalHelper.getMaterialEntry(items.get(index));
                            if (!Objects.equals(actual, expected.get(index))) {
                                throw new IllegalStateException("Concurrent lookup changed material for " +
                                        BuiltInRegistries.ITEM.getKey(items.get(index)));
                            }
                        }
                        return null;
                    }));
                }
                start.countDown();
                // Exercise recipe reload while lookup workers are active.
                if ((round & 1) != 0) {
                    ItemMaterialData.reinitializeMaterialData();
                }
                for (var task : tasks) {
                    task.get(45, TimeUnit.SECONDS);
                }
                synchronized (ItemMaterialData.class) {
                    helper.assertTrue(GTMaterialItems.ITEMS_WITHOUT_MATERIAL.size() == missing,
                            "Negative cache must retain exactly all missing entries after concurrent growth");
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        // A newly registered explicit entry must override a previous negative lookup.
        helper.assertTrue(ChemicalHelper.getMaterialEntry(Items.APPLE) == null, "Apple starts without material");
        var registered = new MaterialEntry(TagPrefix.ingot, GTMaterials.Iron);
        try {
            ItemMaterialData.registerMaterialEntry(() -> Items.APPLE, registered);
            helper.assertTrue(registered.equals(ChemicalHelper.getMaterialEntry(Items.APPLE)),
                    "Lazy explicit registration must invalidate the negative result");
        } finally {
            ItemMaterialData.reinitializeMaterialData();
        }
        helper.assertTrue(ChemicalHelper.getMaterialEntry(Items.APPLE) == null,
                "Reload must discard a removed material mapping");
        helper.assertTrue(GTMaterials.Iron.equals(ChemicalHelper.getMaterialEntryOrThrow(Items.IRON_INGOT).material()),
                "Reload must preserve real material mappings");
        helper.succeed();
    }
}
