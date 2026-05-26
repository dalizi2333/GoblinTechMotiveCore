package com.goblincoders.goblintech.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.cover.MachineControllerCover;
import com.gregtechceu.gtceu.utils.GTMath;

import com.goblincoders.goblintech.recipe.api.recipe.RecipeContext;

import org.jetbrains.annotations.Nullable;

public class GoblinRecipeLogic extends RecipeLogic {

    public static final int PROGRESS_SCALE = 16;

    protected int progressRate = PROGRESS_SCALE;
    protected @Nullable RecipeContext recipeContext;
    protected boolean ambientSatisfied = true;

    public GoblinRecipeLogic(IRecipeLogicMachine machine) {
        super(machine);
    }

    @Override
    public int getProgress() {
        return progress / PROGRESS_SCALE;
    }

    @Override
    public int getMaxProgress() {
        return duration / PROGRESS_SCALE;
    }

    @Override
    public void resetRecipeLogic() {
        super.resetRecipeLogic();
        recipeContext = null;
    }

    @Override
    public void setupRecipe(GTRecipe recipe) {
        super.setupRecipe(recipe);
        if (lastRecipe == recipe) {
            duration = recipe.duration * PROGRESS_SCALE;
            progressRate = PROGRESS_SCALE;
            recipeContext = new RecipeContext(recipe);
        }
    }

    @Override
    public void onRecipeFinish() {
        machine.afterWorking();
        if (lastRecipe != null) {
            runAttempt = 0;
            runDelay = 0;
            consecutiveRecipes++;
            handleRecipeIO(lastRecipe, IO.OUT);
            if (suspendAfterFinish) {
                setStatus(Status.SUSPEND);
                consecutiveRecipes = 0;
                progress = 0;
                duration = 0;
                isActive = false;
                recipeContext = null;
                lastRecipe = null;
                return;
            }
            if (machine.alwaysTryModifyRecipe()) {
                if (lastOriginRecipe != null) {
                    var modified = machine.fullModifyRecipe(lastOriginRecipe.copy());
                    if (modified == null) {
                        markLastRecipeDirty();
                    } else {
                        lastRecipe = modified;
                    }
                } else {
                    markLastRecipeDirty();
                }
            }
            var recipeCheck = checkRecipe(lastRecipe);
            if (!recipeDirty && recipeCheck.isSuccess()) {
                setupRecipe(lastRecipe);
            } else {
                setStatus(Status.IDLE);
                consecutiveRecipes = 0;
                progress = 0;
                duration = 0;
                isActive = false;
                recipeContext = null;
            }
        }
    }

    @Override
    public void handleRecipeWorking() {
        assert lastRecipe != null;
        var conditionResult = RecipeHelper.checkConditions(lastRecipe, this);
        if (conditionResult.isSuccess()) {
            ambientSatisfied = true;
            var handleTick = handleTickRecipe(lastRecipe);
            if (handleTick.isSuccess()) {
                setStatus(Status.WORKING);
                if (!machine.onWorking()) {
                    this.interruptRecipe();
                    return;
                }
                progress += progressRate;
                totalContinuousRunningTime++;
            } else {
                setWaiting(handleTick.reason());
                ambientSatisfied = false;
                if (handleTick.io() == IO.IN && handleTick.capability() == EURecipeCapability.CAP) {
                    runAttempt++;
                    runAttempt = (int) GTMath.clamp(runAttempt, 0, 5);
                    if (runAttempt == 5) {
                        boolean preventPowerFail = false;
                        if (machine instanceof MultiblockControllerMachine) {
                            var covers = machine.self().getCoverContainer().getCovers();
                            for (var cover : covers) {
                                if (cover instanceof MachineControllerCover mcc) {
                                    if (mcc.preventPowerFail()) {
                                        preventPowerFail = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (machine instanceof MultiblockControllerMachine && !preventPowerFail) {
                            runAttempt = 0;
                            setStatus(Status.SUSPEND);
                        }
                    }
                    runDelay = runAttempt * 60;
                }
            }
        } else {
            ambientSatisfied = false;
            setWaiting(conditionResult.reason());
        }
        if (isWaiting() || isSuspend()) {
            regressRecipe();
        }
    }

    @Override
    public void interruptRecipe() {
        machine.afterWorking();
        if (lastRecipe != null) {
            setStatus(Status.IDLE);
            progress = 0;
            duration = 0;
            recipeContext = null;
        }
    }
}
