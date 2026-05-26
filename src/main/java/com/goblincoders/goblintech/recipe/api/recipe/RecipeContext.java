package com.goblincoders.goblintech.recipe.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
