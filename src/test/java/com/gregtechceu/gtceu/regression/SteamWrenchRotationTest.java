package com.gregtechceu.gtceu.regression;

import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine;
import com.gregtechceu.gtceu.client.util.ExtendedBlockModelRotation;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.gametest.util.TestUtils;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("gtceu_regression")
@PrefixGameTestTemplate(false)
public class SteamWrenchRotationTest {

    @GameTest(template = "empty_5x5")
    public static void bronzeMachinesRotateWithWrench(GameTestHelper helper) {
        checkMachines(helper, false);
    }

    @GameTest(template = "empty_5x5")
    public static void steelMachinesRotateWithWrench(GameTestHelper helper) {
        checkMachines(helper, true);
    }

    private static void checkMachines(GameTestHelper helper, boolean steel) {
        var entries = List.of(GTMachines.STEAM_FURNACE, GTMachines.STEAM_EXTRACTOR, GTMachines.STEAM_COMPRESSOR,
                GTMachines.STEAM_HAMMER, GTMachines.STEAM_ALLOY_SMELTER, GTMachines.STEAM_ROCK_CRUSHER,
                GTMachines.STEAM_MACERATOR);
        List<SimpleSteamMachine> machines = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = steel ? entries.get(i).right() : entries.get(i).left();
            machines.add((SimpleSteamMachine) TestUtils.setMachine(helper,
                    new BlockPos(1 + i % 3, 2, 1 + i / 3), entry));
        }
        helper.runAtTickTime(2, () -> {
            Player player = TestUtils.makeMockSurvivalServerPlayer(helper);
            player.setItemInHand(InteractionHand.MAIN_HAND, ToolHelper.get(GTToolType.WRENCH, GTMaterials.Iron));
            for (var machine : machines) {
                assertVentMatchesModel(helper, machine);
                for (Direction front : Direction.values()) {
                    if (!machine.getRotationState().test(front)) continue;
                    if (machine.getOutputFacing() == front) {
                        for (Direction safeOutput : Direction.values()) {
                            if (safeOutput != front && safeOutput != machine.getFrontFacing()) {
                                wrench(helper, machine, player, safeOutput, false);
                                break;
                            }
                        }
                    }
                    if (machine.getFrontFacing() != front) {
                        wrench(helper, machine, player, front, true);
                    }
                    helper.assertTrue(machine.getFrontFacing() == front, "Wrench must change machine front");
                    assertVentMatchesModel(helper, machine);
                    for (Direction output : Direction.values()) {
                        if (output == front) continue;
                        wrench(helper, machine, player, output, false);
                        helper.assertTrue(machine.getOutputFacing() == output, "Wrench must change output");
                        helper.assertTrue(machine.getExhaustVentTrait().getVentingDirection() == output,
                                "Physical vent must follow output");
                        assertVentMatchesModel(helper, machine);
                    }
                    Direction output = machine.getOutputFacing();
                    player.setShiftKeyDown(false);
                    machine.onToolClick(context(machine, player, front));
                    helper.assertTrue(machine.getOutputFacing() == output, "Output must not overlap the front");
                    player.setShiftKeyDown(true);
                    machine.onToolClick(context(machine, player, output));
                    helper.assertTrue(machine.getFrontFacing() == front, "Front must not overlap the output");
                }
            }
            helper.succeed();
        });
    }

    private static void wrench(GameTestHelper helper, SimpleSteamMachine machine, Player player,
                               Direction side, boolean sneaking) {
        player.setShiftKeyDown(sneaking);
        var result = machine.onToolClick(context(machine, player, side));
        helper.assertTrue(result.getSecond().consumesAction(), "Wrench operation must be accepted");
    }

    private static ExtendedUseOnContext context(SimpleSteamMachine machine, Player player, Direction side) {
        Vec3 hit = Vec3.atCenterOf(machine.getBlockPos())
                .add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
        return new ExtendedUseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, side, machine.getBlockPos(), false));
    }

    private static void assertVentMatchesModel(GameTestHelper helper, SimpleSteamMachine machine) {
        var vent = machine.getRenderState().getValue(GTMachineModelProperties.VENT_DIRECTION);
        var rotation = ExtendedBlockModelRotation.get(machine.getFrontFacing());
        // Compare against the blockstate model's Euler transform, independently of relative-facing lookup.
        var transform = new Quaternionf().rotateYXZ(
                (float) Math.toRadians(-rotation.getAngleY()),
                (float) Math.toRadians(-rotation.getAngleX()),
                (float) Math.toRadians(-rotation.getAngleZ()));
        var worldFace = transform.transform(vent.getDefaultFacing().step());
        var actual = Direction.getNearest(worldFace.x, worldFace.y, worldFace.z);
        helper.assertTrue(actual == machine.getOutputFacing(),
                "Model vent must match world output for " + machine.getFrontFacing() + "/" + machine.getOutputFacing());
    }
}
