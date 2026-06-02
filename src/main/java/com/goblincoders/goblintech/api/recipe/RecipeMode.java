package com.goblincoders.goblintech.api.recipe;

/**
 * 【神棍描述】经文解读模式 — 神祇以何种方式施展神迹
 *
 * <p>【工程描述】定义配方执行模式，影响机器如何处理输入输出。
 */
public enum RecipeMode {
    /**
     * 物品搬运型（默认）：将输入端物品/流体搬运至输出端，与 GTCEu 原始行为一致。
     */
    TRANSFORM,
    /**
     * 原地修改型：输入物品不消耗，输出直接修改原物品内容（如添加 trait、修改 NBT）。
     */
    MODIFY,
    /**
     * 环境提供型：不处理物品 IO，由机器自身环境条件（温度、转速等）决定是否满足。
     */
    AMBIENT
}