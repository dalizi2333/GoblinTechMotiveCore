package com.goblincoders.goblintech.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 【神棍描述】经文上下文 — 记录解读经文时捕获的原始输入信息
 *
 * <p>【工程描述】配方执行过程中捕获的输入物品/流体信息，供 {@link com.goblincoders.goblintech.api.recipe.modifier.RecipeOutputModifier} 使用。
 * 修改器根据此处缓存的输入数据（如 NBT、食物属性）修改输出。
 */
public class RecipeContext {

    public final GTRecipe recipe;

    private final Map<RecipeCapability<?>, List<Object>> capturedInputs = new Reference2ObjectOpenHashMap<>();
    private final Map<RecipeCapability<?>, List<Object>> capturedTickInputs = new Reference2ObjectOpenHashMap<>();

    public RecipeContext(GTRecipe recipe) {
        this.recipe = recipe;
    }

    public void captureInput(RecipeCapability<?> cap, List<Object> contents) {
        capturedInputs.put(cap, List.copyOf(contents));
    }

    public List<Object> getInputContents(RecipeCapability<?> cap) {
        return capturedInputs.getOrDefault(cap, Collections.emptyList());
    }

    public void captureTickInput(RecipeCapability<?> cap, List<Object> contents) {
        capturedTickInputs.put(cap, List.copyOf(contents));
    }

    public List<Object> getTickInputContents(RecipeCapability<?> cap) {
        return capturedTickInputs.getOrDefault(cap, Collections.emptyList());
    }
}