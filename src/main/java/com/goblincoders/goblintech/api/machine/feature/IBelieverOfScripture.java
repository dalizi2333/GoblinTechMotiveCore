package com.goblincoders.goblintech.api.machine.feature;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.ActionResult;

/**
 * 【神棍描述】经文信徒 — 信仰配方经文的机器
 *
 * <p>【工程描述】可执行哥布林配方的机器接口，定义能量容槽（GraceStack/OfferingStack）与速率属性。
 * 机器子类通过实现此接口接入 {@link com.goblincoders.goblintech.api.machine.trait.GoblinOracleOfScripture}。
 *
 * <p>【核心职责】
 * <ul>
 *   <li>管理圣恩容槽（graceStock）与奉献容槽（offeringStock）</li>
 *   <li>提供虔诚度（pietyLevel）与神圣努力（divineEffort）给神谕者评估</li>
 *   <li>执行实际能量充能/放能（performDivineWork）</li>
 * </ul>
 */
public interface IBelieverOfScripture extends IRecipeLogicMachine {

    /**
     * @return 圣恩容槽容量
     */
    default int getGraceCapacity() { return 0; }

    /**
     * @return 奉献容槽容量
     */
    default int getOfferingCapacity() { return 0; }

    /**
     * @return 圣恩容槽当前存量（已接收的恩赐能量）
     */
    int getGraceStock();

    void setGraceStock(int value);

    /**
     * @return 奉献容槽当前存量（待输出的奉献能量）
     */
    int getOfferingStock();

    void setOfferingStock(int value);

    /**
     * @return 圣恩容槽安全阈值 — 低于此值时 consumeRate 按比例缩减
     */
    default int getGraceThreshold() { return 0; }

    /**
     * @return 奉献容槽安全阈值 — 高于此值时 offerRate 按比例缩减
     */
    default int getOfferingThreshold() { return 0; }

    /**
     * @return 消耗速率（0.0~1.0），由 {@link #evaluateDivineConsumption(int)} 计算
     */
    float getConsumeRate();

    void setConsumeRate(float rate);

    /**
     * @return 奉献速率（0.0~1.0），由 {@link #evaluateDivineOffering(int)} 计算
     */
    float getOfferRate();

    void setOfferRate(float rate);

    /**
     * @return 虔诚度（效率系数 0.0~1.0），由 {@link #calculatePietyLevel()} 计算
     */
    float getPietyLevel();

    void setPietyLevel(float rate);

    /**
     * @return 神圣努力（实际工作速率），由 {@link #determineDivineEffort()} 计算
     */
    int getDivineEffort();

    void setDivineEffort(int rate);

    /**
     * 【神棍描述】神恩加持的努力 — 神祇赐予的基础劳动能力
     *
     * <p>【工程描述】基础工作速率（进度缩放因子），默认 16。短配方机器可覆写以提高精度。
     *
     * @return 基础工作速率
     */
    default int getBlessedEffort() { return 16; }

    /**
     * @return 本 tick 已接收的恩典
     */
    int getGraceReceived();

    void setGraceReceived(int value);

    /**
     * @return 本 tick 已奉献的恩典
     */
    int getGraceOffered();

    void setGraceOffered(int value);

    /**
     * 【神棍描述】消耗神圣能量 — 接收神祇的恩赐并存入圣恩容槽
     *
     * <p>【工程描述】将外部能量源的 tick 输入映射至 graceStock。
     * 默认实现将 graceStock 归零、graceReceived 置 0，子类应根据实际能量系统覆写。
     *
     * @param divineDemand 经文要求的输入能量
     * @return 操作结果
     */
    default ActionResult consumeDivinePower(int divineDemand) {
        setGraceStock(0);
        setGraceReceived(0);
        return ActionResult.SUCCESS;
    }

    /**
     * 【神棍描述】奉献神圣能量 — 将奉献容槽的能量回馈给神祇
     *
     * <p>【工程描述】将 offeringStock 映射至外部能量输出。
     * 默认实现填满 offeringStock 至容量上限、graceOffered 置 0。
     *
     * @param divineOffering 经文要求的输出能量
     * @return 操作结果
     */
    default ActionResult offerDivinePower(int divineOffering) {
        setOfferingStock(getOfferingCapacity());
        setGraceOffered(0);
        return ActionResult.SUCCESS;
    }

    /**
     * 【神棍描述】评估神圣消耗 — 检查信徒是否接收到足够的恩赐
     *
     * <p>【工程描述】调用 {@link #consumeDivinePower(int)}，若 graceStock 低于安全阈值则按比例缩减 consumeRate。
     */
    default void evaluateDivineConsumption(int divineDemand) {
        var result = consumeDivinePower(divineDemand);
        if (!result.isSuccess()) { setConsumeRate(0); return; }
        if (getGraceStock() < getGraceThreshold())
            setConsumeRate(Math.min(1f, (float) getGraceReceived() / divineDemand));
        else
            setConsumeRate(1f);
    }

    /**
     * 【神棍描述】评估神圣奉献 — 检查信徒的奉献是否积压
     *
     * <p>【工程描述】调用 {@link #offerDivinePower(int)}，若 offeringStock 高于安全阈值则按比例缩减 offerRate。
     */
    default void evaluateDivineOffering(int divineOffering) {
        var result = offerDivinePower(divineOffering);
        if (!result.isSuccess()) { setOfferRate(0); return; }
        if (getOfferingStock() > getOfferingThreshold())
            setOfferRate(Math.min(1f, (float) getGraceOffered() / divineOffering));
        else
            setOfferRate(1f);
    }

    /**
     * 【神棍描述】计算虔诚度 — 取消耗速率与奉献速率中的较小值
     *
     * <p>【工程描述】pietyLevel = min(consumeRate, offerRate)。
     */
    default void calculatePietyLevel() {
        setPietyLevel(Math.min(getConsumeRate(), getOfferRate()));
    }

    /**
     * 【神棍描述】确定神圣努力 — 根据虔诚度计算实际工作速率
     *
     * <p>【工程描述】divineEffort = pietyLevel × blessedEffort。
     */
    default void determineDivineEffort() {
        setDivineEffort((int) (getPietyLevel() * getBlessedEffort()));
    }

    /**
     * 【神棍描述】执行神圣工作 — 实际完成能量充能/放能操作
     *
     * <p>【工程描述】子类必须覆写。默认返回 {@link ActionResult#FAIL_NO_CAPABILITIES} 作为守卫。
     *
     * @return 操作结果
     */
    default ActionResult performDivineWork() {
        return ActionResult.FAIL_NO_CAPABILITIES;
    }
}