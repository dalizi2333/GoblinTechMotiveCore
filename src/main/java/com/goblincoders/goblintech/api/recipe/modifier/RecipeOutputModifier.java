package com.goblincoders.goblintech.api.recipe.modifier;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import java.util.List;
import java.util.Map;

/**
 * 【神棍描述】经文修改器 — 在神迹显现时对奉献之物进行调整
 *
 * <p>【工程描述】配方产出时的输出修改器接口。在 {@code onRecipeFinish()}（完成修改器）
 * 或每 tick 输出时（tick 修改器）对输出物品/流体应用变换。
 */
public interface RecipeOutputModifier {
    void apply(GTRecipe recipe, Map<RecipeCapability<?>, List<Object>> outputs);
}