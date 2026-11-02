package com.gregtechceu.gtceu.regression;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.machines.GCYMMachines;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.ResearchManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.HashMap;
import java.util.List;

@GameTestHolder("gtceu_regression")
@PrefixGameTestTemplate(false)
public class UpstreamMachineRecipeTest {

    @GameTest(template = "empty_5x5")
    public static void dataItemsRespectDataBank(GameTestHelper helper) {
        for (var item : List.of(GTItems.TOOL_DATA_STICK, GTItems.TOOL_DATA_ORB)) {
            helper.assertTrue(ResearchManager.isStackDataItem(item.asStack(), false),
                    "Ordinary data items must work outside a data bank");
            helper.assertTrue(ResearchManager.isStackDataItem(item.asStack(), true),
                    "Ordinary data items must also work in a data bank");
        }
        helper.assertTrue(!ResearchManager.isStackDataItem(GTItems.TOOL_DATA_MODULE.asStack(), false) &&
                ResearchManager.isStackDataItem(GTItems.TOOL_DATA_MODULE.asStack(), true),
                "Data modules must require a data bank");
        for (var stack : List.of(ItemStack.EMPTY, new ItemStack(Items.STONE))) {
            helper.assertTrue(!ResearchManager.isStackDataItem(stack, false) &&
                    !ResearchManager.isStackDataItem(stack, true), "Non-data items must be rejected");
        }
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void welderAndSpoolerCraftingRecipesExist(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        for (var machines : List.of(GTMachines.WELDER, GTMachines.SPOOLING)) {
            for (int tier = GTValues.LV; tier <= GTValues.UV; tier++) {
                var machine = machines[tier];
                helper.assertTrue(machine != null, "Expected a machine at tier " + tier);
                var recipe = manager.byKey(machine.getId().withPrefix("shaped/")).orElseThrow().value();
                helper.assertTrue(ItemStack.isSameItem(recipe.getResultItem(helper.getLevel().registryAccess()),
                        machine.asStack()), "Crafting recipe must produce the requested machine");
                helper.assertTrue(!recipe.getIngredients().isEmpty(), "Machine recipe must have ingredients");
                for (var ingredient : recipe.getIngredients()) {
                    helper.assertTrue(!ingredient.isEmpty() && ingredient.getItems().length > 0,
                            "Machine recipe ingredients must resolve");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void hsseCasingUsesTungstenFrame(GameTestHelper helper) {
        var recipe = (GTRecipe) helper.getLevel().getRecipeManager()
                .byKey(GTCEu.id("welder/casing_hsse_sturdy")).orElseThrow().value();
        var tungsten = ChemicalHelper.get(TagPrefix.frameGt, GTMaterials.Tungsten);
        var europium = ChemicalHelper.get(TagPrefix.frameGt, GTMaterials.Europium);
        var inputs = recipe.getInputContents(ItemRecipeCapability.CAP).stream()
                .map(content -> ItemRecipeCapability.CAP.of(content.content())).toList();
        helper.assertTrue(inputs.stream().anyMatch(input -> input.test(tungsten)),
                "HSS-E casing must accept a tungsten frame");
        helper.assertTrue(inputs.stream().noneMatch(input -> input.test(europium)),
                "HSS-E casing must not require an europium frame");
        helper.assertTrue(recipe.getOutputContents(ItemRecipeCapability.CAP).stream()
                .map(content -> ItemRecipeCapability.CAP.of(content.content()))
                .anyMatch(output -> output.count() == ConfigHolder.INSTANCE.recipes.casingsPerCraft &&
                        output.ingredient().test(GTBlocks.CASING_HSSE_STURDY.asStack())),
                "Welder recipe must produce the configured casing count");
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void largeSpoolerRequiresOneEnergyHatch(GameTestHelper helper) {
        var pattern = (BlockPattern) GCYMMachines.LARGE_SPOOLER.get().getStructurePatterns()
                .get(MultiblockControllerMachine.DEFAULT_STRUCTURE).get();
        var repeats = new Int2IntOpenHashMap();
        for (int i = 0; i < pattern.getSlices().length; i++) {
            repeats.put(i, pattern.getSlices()[i].getMinRepeats());
        }
        var structure = new HashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(repeats)
                .populate(structure, pattern, null, Direction.NORTH, Direction.UP, false);
        var energy = structure.entrySet().stream()
                .filter(entry -> PartAbility.INPUT_ENERGY.isApplicable(entry.getValue().getBlockState().getBlock()))
                .toList();
        var casings = structure.entrySet().stream()
                .filter(entry -> entry.getValue().getBlockState().is(GTBlocks.CASING_HSSE_STURDY.get())).toList();
        helper.assertTrue(energy.size() == 1, "Preview must supply exactly one energy hatch");
        helper.assertTrue(casings.size() > 50, "Fixture needs a spare casing above the minimum");
        var origin = helper.absolutePos(new BlockPos(0, 1, 0));
        structure.forEach((pos, info) -> helper.getLevel().setBlockAndUpdate(origin.offset(pos), info.getBlockState()));
        var center = origin.offset(structure.entrySet().stream()
                .filter(entry -> entry.getValue().getBlockState().is(GCYMMachines.LARGE_SPOOLER.getBlock()))
                .findFirst().orElseThrow().getKey());
        boolean one = pattern.checkPatternAt(helper.getLevel(), new PatternState(), center,
                Direction.NORTH, Direction.UP, false);
        helper.assertTrue(one, "A valid spooler with one energy hatch must form");
        var energyPos = origin.offset(energy.getFirst().getKey());
        helper.getLevel().setBlockAndUpdate(energyPos, GTBlocks.CASING_HSSE_STURDY.get().defaultBlockState());
        boolean zero = pattern.checkPatternAt(helper.getLevel(), new PatternState(), center,
                Direction.NORTH, Direction.UP, false);
        helper.getLevel().setBlockAndUpdate(energyPos, energy.getFirst().getValue().getBlockState());
        helper.getLevel().setBlockAndUpdate(origin.offset(casings.getFirst().getKey()),
                GTMachines.ENERGY_INPUT_HATCH[GTValues.LV].getBlock().defaultBlockState());
        boolean two = pattern.checkPatternAt(helper.getLevel(), new PatternState(), center,
                Direction.NORTH, Direction.UP, false);
        GTCEu.LOGGER.info("Spooler energy-hatch regression: zero={}, one={}, two={}", zero, one, two);
        helper.assertTrue(!zero, "A spooler without an energy hatch must not form");
        helper.assertTrue(!two, "A spooler with two energy hatches must not form");
        helper.succeed();
    }
}
