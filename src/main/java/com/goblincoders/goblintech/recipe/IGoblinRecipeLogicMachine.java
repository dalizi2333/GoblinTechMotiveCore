package com.goblincoders.goblintech.recipe;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.ActionResult;

public interface IGoblinRecipeLogicMachine extends IRecipeLogicMachine {

    // ===== PowerStack 容量与状态 =====
    default int getInputPowerStackSize() { return 0; }
    default int getOutputPowerStackSize() { return 0; }
    int getInputPowerStack();
    void setInputPowerStack(int value);
    int getOutputPowerStack();
    void setOutputPowerStack(int value);

    // ===== 安全阈值 =====
    default int getSafeInputThreshold() { return 0; }
    default int getSafeOutputThreshold() { return 0; }

    // ===== 速率（由 handlePowerStacks 计算并设置） =====
    float getConsumePowerRate();
    void setConsumePowerRate(float rate);
    float getPushPowerRate();
    void setPushPowerRate(float rate);
    float getPowerRate();
    void setPowerRate(float rate);
    int getProgressRate();
    void setProgressRate(int rate);

    // ===== 进度缩放 =====
    default int getProgressScale() { return 16; }

    // ===== 内部计数（由 updateConsumePower / updatePushPower 设置） =====
    int getPowerReceive();
    void setPowerReceive(int value);
    int getPowerPush();
    void setPowerPush(int value);

    // 子类必须覆写。默认给 inputPowerStack 归零、设置 powerReceive = 0。
    default ActionResult updateConsumePower(int tickPowerInput) {
        setInputPowerStack(0);
        setPowerReceive(0);
        return ActionResult.SUCCESS;
    }

    // 子类必须覆写。默认填满 outputPowerStack、设置 powerPush = 0。
    default ActionResult updatePushPower(int tickPowerOutput) {
        setOutputPowerStack(getOutputPowerStackSize());
        setPowerPush(0);
        return ActionResult.SUCCESS;
    }

    // 以下由 GoblinRecipeLogic.handlePowerStacks 调用，有默认实现：
    default void updateConsumeRate(int tickPowerInput) {
        var result = updateConsumePower(tickPowerInput);
        if (!result.isSuccess()) { setConsumePowerRate(0); return; }
        if (getInputPowerStack() < getSafeInputThreshold())
            setConsumePowerRate(Math.min(1f, (float) getPowerReceive() / tickPowerInput));
        else
            setConsumePowerRate(1f);
    }

    default void updatePushRate(int tickPowerOutput) {
        var result = updatePushPower(tickPowerOutput);
        if (!result.isSuccess()) { setPushPowerRate(0); return; }
        if (getOutputPowerStack() > getSafeOutputThreshold())
            setPushPowerRate(Math.min(1f, (float) getPowerPush() / tickPowerOutput));
        else
            setPushPowerRate(1f);
    }

    default void updatePowerRate() {
        setPowerRate(Math.min(getConsumePowerRate(), getPushPowerRate()));
    }

    default void updateProgressRate() {
        setProgressRate((int) (getPowerRate() * getProgressScale()));
    }

    // 子类必须覆写：实际执行 power stack 充能/放能
    default ActionResult applyPowerStacks() {
        return ActionResult.FAIL_NO_CAPABILITIES;
    }
}
