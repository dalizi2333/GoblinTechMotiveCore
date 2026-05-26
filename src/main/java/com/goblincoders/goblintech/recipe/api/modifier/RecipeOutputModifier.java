package com.goblincoders.goblintech.recipe.api.modifier;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import java.util.List;
import java.util.Map;

public interface RecipeOutputModifier {
    void apply(GTRecipe recipe, Map<RecipeCapability<?>, List<Object>> outputs);
}
