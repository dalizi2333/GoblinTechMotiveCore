package com.goblincoders.goblintech.recipe.api.ambient;

public record AmbientEntry<T>(AmbientType<T> type, T parameter) {}
