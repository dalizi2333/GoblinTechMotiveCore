package com.goblincoders.goblintech.api.recipe.condition;

/**
 * 【神棍描述】神罚惩戒方式 — 神祇对不虔诚信徒的惩戒模式
 *
 * <p>【工程描述】定义条件不满足时神谕者的反应行为。
 */
public enum ConditionFailBehavior {
    /**
     * 【神棍描述】等待神恩 — 信徒祈祷等待能量恢复
     *
     * <p>【工程描述】调用 {@code setWaiting()}，触发 runDelay 退避重试 + regressRecipe。
     * 适用于能量不足、低倍速等可自恢复场景。
     */
    WAITING,
    /**
     * 【神棍描述】冻结虔修 — 信徒保持虔诚但暂时无法推进工作
     *
     * <p>【工程描述】保持 WORKING 状态，progress 不变（进度冻结）。
     * 适用于转速不足等需要条件改善的场景。
     */
    HALT_PROGRESS,
    /**
     * 【神棍描述】神罚中断 — 配方失败，信徒需重新接受经文
     *
     * <p>【工程描述】立即中断当前配方，清空进度。
     * 适用于过速、过热等不可恢复的违规场景。
     */
    INTERRUPT
}