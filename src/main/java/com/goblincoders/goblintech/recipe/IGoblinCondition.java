package com.goblincoders.goblintech.recipe;

import com.goblincoders.goblintech.recipe.api.ConditionFailBehavior;

public interface IGoblinCondition {

    ConditionFailBehavior getFailBehavior();
}
