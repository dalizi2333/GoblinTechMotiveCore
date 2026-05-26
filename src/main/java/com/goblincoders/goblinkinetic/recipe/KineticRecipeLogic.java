package com.goblincoders.goblinkinetic.recipe;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;

import com.goblincoders.goblintech.recipe.GoblinRecipeLogic;

public class KineticRecipeLogic extends GoblinRecipeLogic {

    protected float optimalRPM = 16f;

    public KineticRecipeLogic(IRecipeLogicMachine machine) {
        super(machine);
    }

    public void setOptimalRPM(float rpm) {
        this.optimalRPM = rpm;
    }

    /** 由动能机器在每 tick 中调用，更新 progressRate */
    public void updateFromRPM(float currentRPM) {
        if (!ambientSatisfied) {
            this.progressRate = 0;
            return;
        }
        if (optimalRPM == 0f) {
            this.progressRate = PROGRESS_SCALE;
            return;
        }
        float absRPM = Math.abs(currentRPM);
        if (absRPM == 0f) {
            this.progressRate = 0;
            return;
        }
        this.progressRate = (int) (absRPM / optimalRPM * PROGRESS_SCALE);
    }
}
