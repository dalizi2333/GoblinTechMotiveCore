package com.goblincoders.goblintech.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.cover.MachineControllerCover;
import com.gregtechceu.gtceu.utils.GTMath;

import com.goblincoders.goblintech.api.machine.feature.IBelieverOfScripture;
import com.goblincoders.goblintech.api.recipe.condition.IDivineDecreeOfScripture;
import com.goblincoders.goblintech.api.recipe.RecipeContext;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * 【神棍描述】经文神谕者 — 配方之神在人间的代言人
 *
 * <p>【工程描述】配方执行逻辑控制器，继承自 GTCEu {@link RecipeLogic}。
 * 负责解析经文数据、评估信徒状态、管理工作进度、处理能量输入输出。
 *
 * <p>【核心职责】
 * <ul>
 *   <li>解读经文：解析 {@link com.goblincoders.goblintech.api.recipe.GoblinScripture} 中的配方数据</li>
 *   <li>评估信徒：通过 {@link #assessBelieverState()} 计算虔诚度与神圣努力</li>
 *   <li>验证神谕：检查 {@link IDivineDecreeOfScripture} 条件</li>
 * </ul>
 */
public class GoblinOracleOfScripture extends RecipeLogic {

    private static final Component PROGRESS_FROZEN_REASON = Component.translatable("goblintech.recipe.progress_frozen");

    protected @Nullable RecipeContext scriptureContext;
    protected int divineDemand;
    protected int divineOffering;

    public GoblinOracleOfScripture(IBelieverOfScripture believer) {
        super(believer);
    }

    private IBelieverOfScripture believer() {
        return (IBelieverOfScripture) machine;
    }

    /**
     * @return 表示"进度冻结"的 ActionResult（isSuccess=true，reason=PROGRESS_FROZEN_REASON）
     */
    public static ActionResult frozen() {
        return new ActionResult(true, PROGRESS_FROZEN_REASON, null, null);
    }

    /**
     * @return 是否是进度冻结状态
     */
    public static boolean isFrozen(ActionResult result) {
        return result.isSuccess() && result.reason() == PROGRESS_FROZEN_REASON;
    }

    /**
     * 【神棍描述】获取已完成的经文份数
     *
     * <p>【工程描述】对外展示进度时除以 blessedEffort，消除内部精度缩放。
     */
    @Override
    public int getProgress() {
        return progress / believer().getBlessedEffort();
    }

    /**
     * 【神棍描述】获取经文总份数
     *
     * <p>【工程描述】对外展示总进度时除以 blessedEffort。
     */
    @Override
    public int getMaxProgress() {
        return duration / believer().getBlessedEffort();
    }

    @Override
    public void resetRecipeLogic() {
        super.resetRecipeLogic();
        scriptureContext = null;
    }

    /**
     * 【神棍描述】解读经文
     *
     * <p>【工程描述】从配方中提取 duration、divineDemand、divineOffering，应用进度缩放。
     */
    @Override
    public void setupRecipe(GTRecipe recipe) {
        super.setupRecipe(recipe);
        if (lastRecipe == recipe) {
            duration = recipe.duration * believer().getBlessedEffort();
            scriptureContext = new RecipeContext(recipe);
            divineDemand = extractDivineDemand(recipe, IO.IN);
            divineOffering = extractDivineDemand(recipe, IO.OUT);
        }
    }

    private static int extractDivineDemand(GTRecipe recipe, IO io) {
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
                scriptureContext = null;
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
                scriptureContext = null;
            }
        }
    }

    /**
     * 【神棍描述】执行神圣工作循环 — 每 tick 的主循环
     *
     * <p>【工程描述】调用 {@link #assessBelieverState()} 评估信徒状态，根据结果决定：
     * <ul>
     *   <li>SUCCESS + 非冻结：progress += divineEffort</li>
     *   <li>SUCCESS + 冻结：progress 不变，保持 WORKING</li>
     *   <li>失败：runDelay 退避，省 tick</li>
     * </ul>
     */
    @Override
    public void handleRecipeWorking() {
        assert lastRecipe != null;
        var powerResult = assessBelieverState();
        if (powerResult.isSuccess()) {
            if (isFrozen(powerResult)) {
                setStatus(Status.WORKING);
                if (!machine.onWorking()) { this.interruptRecipe(); return; }
                totalContinuousRunningTime++;
            } else {
                setStatus(Status.WORKING);
                if (!machine.onWorking()) { this.interruptRecipe(); return; }
                progress += believer().getDivineEffort();
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

    /**
     * 【神棍描述】评估信徒状态 — 检查能量输入输出，计算虔诚度与神圣努力
     *
     * <p>【工程描述】核心每 tick 逻辑：
     * <ol>
     *   <li>调用 {@link IBelieverOfScripture#evaluateDivineConsumption(int)}</li>
     *   <li>调用 {@link IBelieverOfScripture#evaluateDivineOffering(int)}</li>
     *   <li>计算虔诚度 ({@link IBelieverOfScripture#calculatePietyLevel()})</li>
     *   <li>确定神圣努力 ({@link IBelieverOfScripture#determineDivineEffort()})</li>
     *   <li>验证神谕 ({@link #verifyDivineDecree()})</li>
     *   <li>执行神圣工作 ({@link IBelieverOfScripture#performDivineWork()})</li>
     * </ol>
     */
    protected ActionResult assessBelieverState() {
        IBelieverOfScripture believer = believer();

        if (divineDemand != 0) believer.evaluateDivineConsumption(divineDemand);
        else believer.setConsumeRate(1f);

        if (divineOffering != 0) believer.evaluateDivineOffering(divineOffering);
        else believer.setOfferRate(1f);

        believer.calculatePietyLevel();
        believer.determineDivineEffort();

        var decreeResult = verifyDivineDecree();
        if (!decreeResult.isSuccess()) return decreeResult;

        if (believer.getDivineEffort() == 0) return frozen();

        return believer.performDivineWork();
    }

    /**
     * 【神棍描述】验证神谕 — 检查所有经文条件是否满足
     *
     * <p>【工程描述】遍历 {@link GTRecipe#conditions}，同时兼容 GTM 原生 RecipeCondition 与 {@link IDivineDecreeOfScripture}。
     * 未显式声明 MinPowerRateCondition 时，默认 pietyLevel < 0.5 触发 WAITING。
     *
     * @return SUCCESS 如果所有条件满足
     */
    protected ActionResult verifyDivineDecree() {
        boolean hasMinPowerRate = false;

        for (var condition : lastRecipe.conditions) {
            if (condition instanceof IDivineDecreeOfScripture) {
                hasMinPowerRate = true;
            }
            if (!condition.check(lastRecipe, this)) {
                if (condition instanceof IDivineDecreeOfScripture decree) {
                    return switch (decree.getFailBehavior()) {
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

        if (!hasMinPowerRate && believer().getPietyLevel() < 0.4f) {
            return ActionResult.fail(
                    Component.translatable("goblintech.recipe.condition.min_power_rate", 0.4f), null, null);
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
            scriptureContext = null;
        }
    }
}