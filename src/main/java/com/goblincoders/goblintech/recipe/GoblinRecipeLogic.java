package com.goblincoders.goblintech.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.common.cover.MachineControllerCover;
import com.gregtechceu.gtceu.utils.GTMath;

import com.goblincoders.goblintech.recipe.api.recipe.RecipeContext;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

public class GoblinRecipeLogic extends RecipeLogic {

    private static final Component PROGRESS_FROZEN_REASON = Component.translatable("goblintech.recipe.progress_frozen");

    protected @Nullable RecipeContext recipeContext;
    protected int tickPowerInput;
    protected int tickPowerOutput;

    public GoblinRecipeLogic(IRecipeLogicMachine machine) {
        super(machine);
    }

    private IGoblinRecipeLogicMachine gm() {
        return (IGoblinRecipeLogicMachine) machine;
    }

    public static ActionResult frozen() {
        return new ActionResult(true, PROGRESS_FROZEN_REASON, null, null);
    }

    public static boolean isFrozen(ActionResult result) {
        return result.isSuccess() && result.reason() == PROGRESS_FROZEN_REASON;
    }

    @Override
    public int getProgress() {
        return progress / gm().getProgressScale();
    }

    @Override
    public int getMaxProgress() {
        return duration / gm().getProgressScale();
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
            duration = recipe.duration * gm().getProgressScale();
            recipeContext = new RecipeContext(recipe);
            tickPowerInput = extractTickPower(recipe, IO.IN);
            tickPowerOutput = extractTickPower(recipe, IO.OUT);
        }
    }

    private static int extractTickPower(GTRecipe recipe, IO io) {
        var contentMap = io == IO.IN ? recipe.tickInputs : recipe.tickOutputs;
        for (var entry : contentMap.entrySet()) {
            var capability = entry.getKey();
            var contents = entry.getValue();
            if (!contents.isEmpty() && capability.getClass().getName().contains("EURecipeCapability")) {
                var stack = contents.get(0).getContent();
                if (stack instanceof Number n) {
                    return n.intValue();
                }
            }
        }
        return 0;
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
        var powerResult = handlePowerStacks();
        if (powerResult.isSuccess()) {
            if (isFrozen(powerResult)) {
                setStatus(Status.WORKING);
                if (!machine.onWorking()) { this.interruptRecipe(); return; }
                totalContinuousRunningTime++;
            } else {
                setStatus(Status.WORKING);
                if (!machine.onWorking()) { this.interruptRecipe(); return; }
                progress += gm().getProgressRate();
                totalContinuousRunningTime++;
            }
        } else {
            setWaiting(powerResult.reason());
            runAttempt++;
            runAttempt = (int) GTMath.clamp(runAttempt, 0, 5);
            if (runAttempt == 5) {
                boolean preventPowerFail = false;
                if (machine instanceof MultiblockControllerMachine mcm) {
                    var covers = mcm.self().getCoverContainer().getCovers();
                    for (var cover : covers) {
                        if (cover instanceof MachineControllerCover mcc) {
                            if (mcc.preventPowerFail()) { preventPowerFail = true; break; }
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
        if (isWaiting() || isSuspend()) {
            regressRecipe();
        }
    }

    protected ActionResult handlePowerStacks() {
        IGoblinRecipeLogicMachine gm = gm();

        if (tickPowerInput != 0) gm.updateConsumeRate(tickPowerInput);
        else gm.setConsumePowerRate(1f);

        if (tickPowerOutput != 0) gm.updatePushRate(tickPowerOutput);
        else gm.setPushPowerRate(1f);

        gm.updatePowerRate();
        gm.updateProgressRate();

        var condResult = checkConditions();
        if (!condResult.isSuccess()) return condResult;

        if (gm.getProgressRate() == 0) return frozen();

        return gm.applyPowerStacks();
    }

    protected ActionResult checkConditions() {
        boolean hasMinPowerRate = false;

        for (var condition : lastRecipe.conditions) {
            if (condition instanceof IGoblinCondition) {
                hasMinPowerRate = true;
            }
            if (!condition.check(lastRecipe, this)) {
                if (condition instanceof IGoblinCondition gc) {
                    return switch (gc.getFailBehavior()) {
                        case WAITING -> ActionResult.fail(condition.getTooltips(), null, null);
                        case HALT_PROGRESS -> frozen();
                        case INTERRUPT -> {
                            interruptRecipe();
                            yield ActionResult.fail(condition.getTooltips(), null, null);
                        }
                    };
                }
                return ActionResult.fail(condition.getTooltips(), null, null);
            }
        }

        if (!hasMinPowerRate && gm().getPowerRate() < 0.5f) {
            return ActionResult.fail(
                    Component.translatable("goblintech.recipe.condition.min_power_rate", 0.5f), null, null);
        }

        return ActionResult.SUCCESS;
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
