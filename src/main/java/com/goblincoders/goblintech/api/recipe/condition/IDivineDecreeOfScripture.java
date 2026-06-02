package com.goblincoders.goblintech.api.recipe.condition;

/**
 * 【神棍描述】神谕条件 — 经文信徒必须遵守的神圣戒律
 *
 * <p>【工程描述】为 GTM 原生 {@link com.gregtechceu.gtceu.api.recipe.RecipeCondition} 子类提供的失败行为扩展接口。
 * 条件不满足时，根据实现决定是等待、冻结进度还是中断配方。
 */
public interface IDivineDecreeOfScripture {

    /**
     * @return 条件不满足时的失败行为
     */
    ConditionFailBehavior getFailBehavior();
}