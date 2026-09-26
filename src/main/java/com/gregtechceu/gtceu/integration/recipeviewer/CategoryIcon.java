package com.gregtechceu.gtceu.integration.recipeviewer;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.emi.emi.api.render.EmiRenderable;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiStack;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IJeiHelpers;
import org.jetbrains.annotations.Nullable;

// Generic recipe viewer category icon
public class CategoryIcon {

    @Nullable
    private final ResourceLocation texture;
    @Nullable
    private final ItemStack stack;
    @Nullable
    private EmiRenderable emiValue;

    public CategoryIcon(ResourceLocation texture) {
        this.texture = texture;
        this.stack = null;
    }

    public CategoryIcon(ItemStack stack) {
        this.texture = null;
        this.stack = stack.copy();
    }

    @Nullable
    public Object get() {
        if (!GTCEu.isClientSide() || !GTCEu.Mods.isEMILoaded()) return null;
        if (emiValue == null) {
            emiValue = texture != null ? EmiCallWrapper.getRenderable(texture) :
                    EmiCallWrapper.getRenderable(stack);
        }
        return emiValue;
    }

    public IDrawable getJeiDrawable(IJeiHelpers helpers) {
        return texture != null ? JeiCallWrapper.getRenderable(helpers, texture) :
                JeiCallWrapper.getRenderable(helpers, stack);
    }

    private static class EmiCallWrapper {

        public static EmiRenderable getRenderable(ResourceLocation location) {
            return new EmiTexture(location, 0, 0, 16, 16, 16, 16, 16, 16);
        }

        public static EmiRenderable getRenderable(ItemStack stack) {
            return EmiStack.of(stack);
        }
    }

    private static class JeiCallWrapper {

        public static IDrawable getRenderable(IJeiHelpers helpers, ResourceLocation location) {
            return helpers.getGuiHelper().drawableBuilder(location, 0, 0, 16, 16)
                    .setTextureSize(16, 16).build();
        }

        public static IDrawable getRenderable(IJeiHelpers helpers, ItemStack stack) {
            return helpers.getGuiHelper().createDrawableItemStack(stack);
        }
    }
}
