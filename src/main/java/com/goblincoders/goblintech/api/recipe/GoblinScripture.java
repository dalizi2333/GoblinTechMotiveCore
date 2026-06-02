package com.goblincoders.goblintech.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;

import com.goblincoders.goblintech.api.recipe.modifier.RecipeOutputModifier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 【神棍描述】经文 — 记录神祇的配方旨意
 *
 * <p>【工程描述】哥布林配方数据对象，继承自 GTCEu {@link GTRecipe}。
 * 扩展了执行模式、输出修改器等属性，条件复用父类的 {@link List}{@code <RecipeCondition>}。
 *
 * <p>【核心职责】
 * <ul>
 *   <li>定义配方执行模式（{@link RecipeMode}）</li>
 *   <li>存储输出修改器（{@link RecipeOutputModifier}），支持完成时与每 tick 修改</li>
 *   <li>复用父类 conditions 字段，不额外定义 ambientConditions</li>
 * </ul>
 */
public class GoblinScripture extends GTRecipe {

    public RecipeMode mode = RecipeMode.TRANSFORM;
    public List<RecipeOutputModifier> outputModifiers = List.of();
    public List<RecipeOutputModifier> tickOutputModifiers = List.of();

    public GoblinScripture(GTRecipeType recipeType,
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

    public GoblinScripture(GTRecipeType recipeType,
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