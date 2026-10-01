package com.gregtechceu.gtceu.api.machine.steam;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;
import com.gregtechceu.gtceu.common.machine.trait.ExhaustVentMachineTrait;
import com.gregtechceu.gtceu.common.recipe.condition.VentCondition;

import net.minecraft.core.Direction;

import lombok.Getter;

import java.util.*;

/**
 * A singleblock steam machine with recipe logic and item IO.
 */
public class SimpleSteamMachine extends SteamWorkableMachine {

    @SaveField
    public final NotifiableItemStackHandler importItems;
    @SaveField
    public final NotifiableItemStackHandler exportItems;

    @Getter
    private final ExhaustVentMachineTrait exhaustVentTrait;

    /**
     * Creates a {@link SimpleSteamMachine}.
     *
     * @param info        {@link BlockEntityCreationInfo}
     * @param recipeLogic The recipe logic to use.
     * @param importSlots The amount of item input slots this machine should have (can be 0).
     * @param exportSlots The amount of item output slots this machine should have (can be 0).
     */
    public SimpleSteamMachine(BlockEntityCreationInfo info, RecipeLogic recipeLogic, boolean isHighPressure,
                              int importSlots, int exportSlots) {
        super(info, isHighPressure, recipeLogic);
        this.importItems = attachTrait(new NotifiableItemStackHandler(importSlots, IO.IN));
        this.exportItems = attachTrait(new NotifiableItemStackHandler(exportSlots, IO.OUT));
        this.exhaustVentTrait = attachTrait(new ExhaustVentMachineTrait());
    }

    public SimpleSteamMachine(BlockEntityCreationInfo info, boolean isHighPressure) {
        super(info, isHighPressure);
        this.importItems = attachTrait(
                new NotifiableItemStackHandler(getDefinition().getInputSize(ItemRecipeCapability.CAP, getRecipeTypes()),
                        IO.IN));
        this.exportItems = attachTrait(
                new NotifiableItemStackHandler(
                        getDefinition().getOutputSize(ItemRecipeCapability.CAP, getRecipeTypes()),
                        IO.OUT));

        this.exhaustVentTrait = attachTrait(new ExhaustVentMachineTrait());
        exhaustVentTrait.setVentingDamageAmount(isHighPressure() ? 12F : 6F);
        MachineRenderState renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.VENT_DIRECTION)) {
            // outputFacing will always be opposite the front facing on init
            setRenderState(renderState.setValue(GTMachineModelProperties.VENT_DIRECTION, RelativeDirection.BACK));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        exhaustVentTrait.setVentingDirection(Objects.requireNonNull(getOutputFacing()));
        updateModelVentDirection();
        // Simulate an EU machine via a SteamEnergyHandler
        this.addHandlerList(RecipeHandlerList.of(IO.IN, new SteamEnergyRecipeHandler(steamTank, getConversionRate())));
    }

    //////////////////////////////////////
    // ****** Venting Logic ******//
    //////////////////////////////////////

    public void updateModelVentDirection() {
        MachineRenderState renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.VENT_DIRECTION)) {
            Direction front = getFrontFacing();
            // Match the model's orientation: ordinary vertical machines have their top facing north.
            Direction modelUp = allowExtendedFacing() ? getUpwardsFacing() :
                    front.getAxis().isVertical() ? Direction.NORTH : Direction.UP;
            if (front.getAxis() == modelUp.getAxis()) {
                // Extended front rotation updates the up and front blockstate properties separately.
                // The outer setFrontFacing call refreshes the model once the complete frame is valid.
                return;
            }
            // Invert the model's local-to-world frame, including roll for extended-facing machines.
            for (RelativeDirection relative : RelativeDirection.VALUES) {
                if (relative.getRelativeFacing(front, modelUp) == exhaustVentTrait.getVentingDirection()) {
                    if (renderState.getValue(GTMachineModelProperties.VENT_DIRECTION) != relative) {
                        setRenderState(renderState.setValue(GTMachineModelProperties.VENT_DIRECTION, relative));
                    }
                    return;
                }
            }
        }
    }

    @Override
    public void setOutputFacing(Direction outputFacing) {
        var oldFacing = getOutputFacing();
        super.setOutputFacing(outputFacing);
        if (getOutputFacing() != oldFacing) {
            exhaustVentTrait.setVentingDirection(outputFacing);
            updateModelVentDirection();
        }
    }

    @Override
    public void setFrontFacing(Direction facing) {
        var oldFacing = getFrontFacing();
        super.setFrontFacing(facing);
        if (getFrontFacing() != oldFacing) {
            updateModelVentDirection();
        }
    }

    @Override
    public void setUpwardsFacing(Direction upwardsFacing) {
        var oldFacing = getUpwardsFacing();
        super.setUpwardsFacing(upwardsFacing);
        if (getUpwardsFacing() != oldFacing) {
            updateModelVentDirection();
        }
    }

    public double getConversionRate() {
        return isHighPressure() ? 2.0 : 1.0;
    }

    //////////////////////////////////////
    // ****** Recipe Logic ******//
    //////////////////////////////////////

    /**
     * Recipe Modifier for <b>Simple Steam Machines</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * Recipe is rejected if tier is greater than LV or if machine cannot vent.<br>
     * Duration is multiplied by {@code 2} if the machine is low pressure
     * </p>
     *
     * @param machine a {@link SimpleSteamMachine}
     * @param recipe  recipe
     * @return A {@link ModifierFunction} for the given Steam Machine
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof SimpleSteamMachine steamMachine)) {
            return RecipeModifier.nullWrongType(SimpleSteamMachine.class, machine);
        }
        if (RecipeHelper.getRecipeEUtTier(recipe) > GTValues.LV || !steamMachine.exhaustVentTrait.checkVenting()) {
            return ModifierFunction.NULL;
        }

        var builder = ModifierFunction.builder().conditions(VentCondition.INSTANCE);
        if (!steamMachine.isHighPressure) builder.durationMultiplier(2);
        return builder.build();
    }
}
