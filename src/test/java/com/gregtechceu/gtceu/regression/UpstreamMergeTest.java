package com.gregtechceu.gtceu.regression;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.item.datacomponents.AoESymmetrical;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;

@GameTestHolder("gtceu_regression")
@PrefixGameTestTemplate(false)
public class UpstreamMergeTest {

    @GameTest(template = "empty_5x5")
    public static void currentAoeAndToolLimits(GameTestHelper helper) {
        var value = AoESymmetrical.of(0, 1, 0);
        var encoded = AoESymmetrical.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        helper.assertTrue(value.equals(AoESymmetrical.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow()),
                "New saved AOE must round trip");
        var buffer = Unpooled.buffer();
        try {
            AoESymmetrical.STREAM_CODEC.encode(buffer, value);
            helper.assertTrue(value.equals(AoESymmetrical.STREAM_CODEC.decode(buffer)), "AOE network round trip");
        } finally {
            buffer.release();
        }
        ItemStack hammer = ToolHelper.get(GTToolType.MINING_HAMMER, GTMaterials.Iron);
        helper.assertTrue(!hammer.isEmpty(), "Mining hammer must exist");
        helper.assertTrue(AoESymmetrical.of(1, 1, 0).equals(hammer.get(GTDataComponents.MAX_AOE)),
                "Existing tool prototypes must supply the new max AOE component");
        hammer.set(GTDataComponents.AOE, value);
        helper.assertTrue(value.equals(ToolHelper.getAoEDefinition(hammer)), "Preserve selected range");
        hammer.set(GTDataComponents.AOE, AoESymmetrical.of(9, 9, 9));
        helper.assertTrue(AoESymmetrical.of(1, 1, 0).equals(ToolHelper.getAoEDefinition(hammer)),
                "Requested range must be limited by the tool maximum");
        hammer.set(GTDataComponents.AOE, AoESymmetrical.ZERO);
        helper.assertTrue(AoESymmetrical.of(1, 1, 0).equals(ToolHelper.getAoEStateMutable(hammer)
                .increaseColumn().increaseRow().toImmutable()), "Zero range must still be configurable");
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void oreRecipesKeepHostRock(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        GTRecipe stone = (GTRecipe) manager.byKey(GTCEu.id("macerator/macerate_iron_ore_to_crushed_ore"))
                .orElseThrow().value();
        GTRecipe nether = (GTRecipe) manager.byKey(GTCEu.id("macerator/macerate_" +
                TagPrefix.oreNetherrack.name + "_iron_ore_to_crushed_ore")).orElseThrow().value();
        var stoneInput = ItemRecipeCapability.CAP
                .of(stone.getInputContents(ItemRecipeCapability.CAP).getFirst().content());
        var netherInput = ItemRecipeCapability.CAP
                .of(nether.getInputContents(ItemRecipeCapability.CAP).getFirst().content());
        ItemStack netherOre = ChemicalHelper.get(TagPrefix.oreNetherrack, GTMaterials.Iron);
        helper.assertTrue(!netherOre.isEmpty(), "Nether iron ore must exist");
        helper.assertTrue(stoneInput.test(new ItemStack(Items.IRON_ORE)), "Stone ore recipe accepts stone iron ore");
        helper.assertTrue(!stoneInput.test(netherOre), "Stone recipe must not consume doubled nether ore");
        helper.assertTrue(netherInput.test(netherOre), "Nether recipe accepts nether iron ore");
        helper.assertTrue(!netherInput.test(new ItemStack(Items.IRON_ORE)), "Nether recipe must not accept stone ore");
        helper.succeed();
    }
}
