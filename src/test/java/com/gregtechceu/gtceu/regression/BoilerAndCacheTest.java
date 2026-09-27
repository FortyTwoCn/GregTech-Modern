package com.gregtechceu.gtceu.regression;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.steam.SteamSolidBoilerMachine;
import com.gregtechceu.gtceu.gametest.util.TestUtils;
import com.gregtechceu.gtceu.integration.map.cache.GridCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.mojang.serialization.Lifecycle;

@GameTestHolder("gtceu_regression")
@PrefixGameTestTemplate(false)
public class BoilerAndCacheTest {

    @GameTest(template = "empty_5x5", timeoutTicks = 2700)
    public static void bronzeBoilerBurnsAndProducesSteam(GameTestHelper helper) {
        checkBoiler(helper, false);
    }

    @GameTest(template = "empty_5x5", timeoutTicks = 2700)
    public static void steelBoilerBurnsAndProducesSteam(GameTestHelper helper) {
        checkBoiler(helper, true);
    }

    private static void checkBoiler(GameTestHelper helper, boolean highPressure) {
        var entry = highPressure ? GTMachines.STEAM_SOLID_BOILER.right() : GTMachines.STEAM_SOLID_BOILER.left();
        var boiler = (SteamSolidBoilerMachine) TestUtils.setMachine(helper, new BlockPos(2, 1, 2), entry);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!boiler.fuelHandler.insertItem(0, new ItemStack(Items.STONE), false).isEmpty(),
                    "Boiler must reject non-fuel");
            helper.assertTrue(!boiler.fuelHandler.insertItem(0, new ItemStack(Items.LAVA_BUCKET), false).isEmpty(),
                    "Solid boiler must reject fluid containers");
            helper.assertTrue(boiler.fuelHandler.insertItem(0, new ItemStack(Items.COAL, 8), false).isEmpty(),
                    "Boiler must accept coal through its external item handler");
            boiler.waterTank.fill(GTMaterials.Water.getFluid(16000), FluidAction.EXECUTE);
        });
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(boiler.recipeLogic.isWorking(), "Server must start the dynamic solid fuel recipe");
            helper.assertTrue(boiler.fuelHandler.getStackInSlot(0).getCount() < 8, "Burning must consume fuel");
            helper.assertTrue(boiler.getCurrentTemperature() > 0, "Burning must heat the boiler");
        });
        helper.succeedWhen(() -> helper.assertTrue(boiler.steamTank.getFluidInTank(0).getAmount() > 0,
                "Boiler must produce steam after reaching boiling temperature"));
    }

    @GameTest(template = "empty_5x5")
    public static void representativeRecipesIncludeSolidFuel(GameTestHelper helper) {
        var type = GTRecipeTypes.STEAM_BOILER_RECIPES.value();
        type.buildRepresentativeRecipes();
        helper.assertTrue(
                type.getRecipesInCategory(type.getCategory()).stream()
                        .anyMatch(recipe -> recipe.getInputContents(ItemRecipeCapability.CAP).stream()
                                .anyMatch(content -> ItemRecipeCapability.CAP.of(content.content())
                                        .test(new ItemStack(Items.COAL)))),
                "JEI representative recipes must include coal");
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void cacheRebindsForeignRegistryHolders(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var current = registries.registryOrThrow(GTRegistries.Keys.ORE_VEIN).holders().findFirst().orElseThrow();
        var foreignRegistry = new MappedRegistry<GTOreDefinition>(GTRegistries.Keys.ORE_VEIN, Lifecycle.stable());
        var foreign = foreignRegistry.register(current.key(), current.value(), RegistrationInfo.BUILT_IN);
        var original = new GeneratedVeinMetadata(new ChunkPos(4, -7), new BlockPos(70, 23, -105), foreign, true);
        GridCache cache = new GridCache();
        cache.addVein(original);
        ListTag encoded = cache.toNBT(registries);
        helper.assertTrue(encoded.size() == 1, "Foreign holder must be rebound, not dropped");
        GridCache restored = new GridCache();
        restored.fromNBT(encoded, registries);
        var vein = restored.getVeins().getFirst();
        helper.assertTrue(vein.definition() == current, "Loaded holder must belong to the current registry");
        helper.assertTrue(vein.depleted() && vein.center().equals(original.center()) &&
                vein.originChunk().equals(original.originChunk()), "Cache must preserve position and depleted state");
        helper.assertTrue(original.definition() == foreign, "Saving must not mutate shared vein metadata");
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void invalidCacheEntriesDoNotDiscardValidVeins(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var definition = registries.registryOrThrow(GTRegistries.Keys.ORE_VEIN).holders().findFirst().orElseThrow();
        GridCache source = new GridCache();
        source.addVein(new GeneratedVeinMetadata(new ChunkPos(0, 0), BlockPos.ZERO, definition));
        ListTag entries = source.toNBT(registries);
        CompoundTag removedDefinition = entries.getCompound(0).copy();
        removedDefinition.putString("definition", GTCEu.id("removed_test_vein").toString());
        entries.add(removedDefinition);
        entries.add(new CompoundTag());
        GridCache restored = new GridCache();
        restored.fromNBT(entries, registries);
        helper.assertTrue(restored.getVeins().size() == 1,
                "Missing or corrupt entries must not prevent loading valid veins");
        helper.succeed();
    }
}
