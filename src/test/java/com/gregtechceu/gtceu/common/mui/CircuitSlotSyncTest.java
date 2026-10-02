package com.gregtechceu.gtceu.common.mui;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import brachy.modularui.value.sync.IntSyncValue;
import io.netty.buffer.Unpooled;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("gtceu_regression")
@PrefixGameTestTemplate(false)
public class CircuitSlotSyncTest {

    @GameTest(template = "empty_5x5")
    public static void emptyMachineSlotAcceptsSyncedSelection(GameTestHelper helper) {
        var machine = TestUtils.setMachine(helper, new BlockPos(2, 1, 2), GTMachines.WIREMILL[GTValues.LV]);
        var trait = machine.getTrait(ProgrammableCircuitSlotTrait.class);
        AtomicInteger changes = new AtomicInteger();
        trait.addChangedListener(changes::incrementAndGet);
        IntSyncValue sync = GTMuiWidgets.createCircuitSlotSyncValue(
                stack -> trait.storage.setStackInSlot(0, stack), () -> trait.storage.getStackInSlot(0));
        helper.assertTrue(sync.isAllowC2S() && sync.getIntValue() == -1,
                "An empty machine slot must accept client configuration updates");

        for (int config : new int[] { 1, 32, -1, 7, 0 }) {
            receiveSelection(sync, config);
            ItemStack stack = trait.storage.getStackInSlot(0);
            helper.assertTrue(config < 0 ? stack.isEmpty() :
                    !stack.isEmpty() && stack.getCount() == 1 && trait.getCurrentCircuit() == config,
                    "Synced configuration " + config + " must persist in the machine slot");
            helper.assertTrue(!sync.updateCacheFromSource(false) && sync.getIntValue() == config,
                    "Server refresh must not undo the selected configuration");
        }
        helper.assertTrue(changes.get() == 5, "Each configuration update must notify the recipe handler");

        var saved = trait.storage.serializeNBT(helper.getLevel().registryAccess());
        trait.storage.setStackInSlot(0, ItemStack.EMPTY);
        trait.storage.deserializeNBT(helper.getLevel().registryAccess(), saved);
        helper.assertTrue(!trait.storage.getStackInSlot(0).isEmpty() && trait.getCurrentCircuit() == 0,
                "Configuration zero must survive saving and reloading the machine slot");
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void configuringHeldCircuitsPreservesStackCount(GameTestHelper helper) {
        AtomicReference<ItemStack> held = new AtomicReference<>(IntCircuitBehaviour.stack(3, 16));
        IntSyncValue sync = GTMuiWidgets.createCircuitSlotSyncValue(held::set, held::get);
        for (int config : new int[] { 32, 1, 0 }) {
            receiveSelection(sync, config);
            helper.assertTrue(held.get().getCount() == 16 &&
                    IntCircuitBehaviour.getCircuitConfiguration(held.get()) == config,
                    "Editing a held circuit must preserve all items in the stack");
        }
        helper.succeed();
    }

    private static void receiveSelection(IntSyncValue receiver, int config) {
        // Exercise the same value encoding and source setter used by a client selection packet.
        IntSyncValue sender = new IntSyncValue(() -> config);
        var packet = Unpooled.buffer();
        try {
            sender.write(packet);
            receiver.read(packet);
        } finally {
            packet.release();
        }
    }
}
