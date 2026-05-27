package com.goblincoders.goblintech.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;

import com.goblincoders.goblintech.recipe.api.RecipeMode;
import com.goblincoders.goblintech.recipe.api.modifier.RecipeOutputModifier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class GoblinRecipe extends GTRecipe {

    public RecipeMode mode = RecipeMode.TRANSFORM;
    public List<RecipeOutputModifier> outputModifiers = List.of();
    public List<RecipeOutputModifier> tickOutputModifiers = List.of();

    public GoblinRecipe(GTRecipeType recipeType,
                        Map<RecipeCapability<?>, List<Content>> inputs,
                        Map<RecipeCapability<?>, List<Content>> outputs,
                        Map<RecipeCapability<?>, List<Content>> tickInputs,
                        Map<RecipeCapability<?>, List<Content>> tickOutputs,
                        Map<RecipeCapability<?>, ChanceLogic> inputChanceLogics,
                        Map<RecipeCapability<?>, ChanceLogic> outputChanceLogics,
                        Map<RecipeCapability<?>, ChanceLogic> tickInputChanceLogics,
                        Map<RecipeCapability<?>, ChanceLogic> tickOutputChanceLogics,
                        List<RecipeCondition<?>> conditions,
                        @NotNull CompoundTag data,
                        int duration,
                        @NotNull GTRecipeCategory recipeCategory,
                        int groupColor) {
        super(recipeType, inputs, outputs, tickInputs, tickOutputs,
                inputChanceLogics, outputChanceLogics, tickInputChanceLogics, tickOutputChanceLogics,
                conditions, data, duration, recipeCategory, groupColor);
    }

    public GoblinRecipe(GTRecipeType recipeType,
                        @Nullable ResourceLocation id,
                        Map<RecipeCapability<?>, List<Content>> inputs,
                        Map<RecipeCapability<?>, List<Content>> outputs,
                        Map<RecipeCapability<?>, List<Content>> tickInputs,
                        Map<RecipeCapability<?>, List<Content>> tickOutputs,
                        Map<RecipeCapability<?>, ChanceLogic> inputChanceLogics,
                        Map<RecipeCapability<?>, ChanceLogic> outputChanceLogics,
                        Map<RecipeCapability<?>, ChanceLogic> tickInputChanceLogics,
                        Map<RecipeCapability<?>, ChanceLogic> tickOutputChanceLogics,
                        List<RecipeCondition<?>> conditions,
                        List<?> ingredientActions,
                        @NotNull CompoundTag data,
                        int duration,
                        @NotNull GTRecipeCategory recipeCategory,
                        int groupColor) {
        super(recipeType, id, inputs, outputs, tickInputs, tickOutputs,
                inputChanceLogics, outputChanceLogics, tickInputChanceLogics, tickOutputChanceLogics,
                conditions, ingredientActions, data, duration, recipeCategory, groupColor);
    }
}
