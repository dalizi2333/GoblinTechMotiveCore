package com.goblincoders.goblintech.recipe.api.ambient;

import com.goblincoders.goblintech.recipe.api.ConditionFailBehavior;
import net.minecraft.resources.ResourceLocation;

public record AmbientType<T>(ResourceLocation id, ConditionFailBehavior failBehavior) {}
