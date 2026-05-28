package com.goblincoders.goblintech.recipe;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.ActionResult;

public interface IGoblinRecipeLogicMachine extends IRecipeLogicMachine {

    // ===== GraceStack（圣恩容槽）容量与状态 =====
    default int getGraceCapacity() { return 0; }
    default int getOfferingCapacity() { return 0; }
    int getGraceStock();
    void setGraceStock(int value);
    int getOfferingStock();
    void setOfferingStock(int value);

    // ===== 安全阈值 =====
    default int getGraceThreshold() { return 0; }
    default int getOfferingThreshold() { return 0; }

    // ===== 速率（由 assessBelieverState 计算并设置） =====
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

    // ===== 内部计数器（由 consumeDivinePower / offerDivinePower 设置） =====
    int getPowerReceive();
    void setPowerReceive(int value);
    int getPowerPush();
    void setPowerPush(int value);

    // 子类必须覆写。默认给 graceStock 归零、设置 powerReceive = 0。
    default ActionResult updateConsumePower(int tickPowerInput) {
        setGraceStock(0);
        setPowerReceive(0);
        return ActionResult.SUCCESS;
    }

    // 子类必须覆写。默认填满 offeringStock、设置 powerPush = 0。
    default ActionResult updatePushPower(int tickPowerOutput) {
        setOfferingStock(getOfferingCapacity());
        setPowerPush(0);
        return ActionResult.SUCCESS;
    }

    // 以下由 GoblinRecipeLogic.assessBelieverState 调用，有默认实现：
    default void updateConsumeRate(int tickPowerInput) {
        var result = updateConsumePower(tickPowerInput);
        if (!result.isSuccess()) { setConsumePowerRate(0); return; }
        if (getGraceStock() < getGraceThreshold())
            setConsumePowerRate(Math.min(1f, (float) getPowerReceive() / tickPowerInput));
        else
            setConsumePowerRate(1f);
    }

    default void updatePushRate(int tickPowerOutput) {
        var result = updatePushPower(tickPowerOutput);
        if (!result.isSuccess()) { setPushPowerRate(0); return; }
        if (getOfferingStock() > getOfferingThreshold())
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

    // 子类必须覆写：实际执行能量充能/放能
    default ActionResult applyPowerStacks() {
        return ActionResult.FAIL_NO_CAPABILITIES;
    }
}