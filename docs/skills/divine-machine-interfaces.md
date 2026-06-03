# Divine 机器核心接口参考

## 概述

本文档定义 Divine 机器的 5 个核心接口的完整签名、职责说明及实现要点。所有接口位于 `com.goblincoders.goblintech.api.machine.feature` 包，均继承 `IMachineFeature`。

---

## 0. IAwakened（已觉醒者）

**归属**：基础接口

**职责**：声明此机器已觉醒，可进行献祭和输出祝福。提供**懒加载**的 `OfferingBlessingManager` 实例，根据机器是否实现 `ITieredMachine` 自动选择创建模式（带虔诚等级限制或无限制）。作为 `IOmnipresentAvatar` 和 `IAscensionBlessed` 的公共基础接口。

```java
package com.goblincoders.goblintech.api.machine.feature;

import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;

/**
 * 已觉醒的机器 — 可进行献祭和输出祝福。
 * 提供祭品祝福管理器，支持显式初始化和懒加载兜底。
 */
public interface IAwakened extends IMachineFeature {

    /**
     * 获取祭品祝福管理器。
     * 
     * <p>优先返回已通过 {@link #initOfferingBlessingManager()} 显式初始化的管理器。
     * 如果尚未初始化，则自动触发懒加载（作为兜底机制）。
     * 
     * <p>机器实现类需要持有一个字段存储管理器：
     * <pre>
     * private volatile OfferingBlessingManager offeringBlessingManager;
     * </pre>
     *
     * @return 祭品祝福管理器实例（保证非 null）
     */
    default OfferingBlessingManager getOfferingBlessingManager() {
        OfferingBlessingManager manager = getManagerStorage();
        if (manager == null) {
            synchronized (this) {
                manager = getManagerStorage();
                if (manager == null) {
                    return initOfferingBlessingManager();
                }
            }
        }
        return manager;
    }

    /**
     * 显式初始化祭品祝福管理器。
     * 
     * <p>建议在机器构造函数中调用，确保管理器尽早初始化。
     * 
     * <p>初始化策略：
     * - 如果机器同时实现 ITieredMachine，创建带虔诚等级限制的管理器
     * - 如果仅实现 IAwakened，创建无限制模式的管理器
     *
     * @return 新创建的祭品祝福管理器
     */
    default OfferingBlessingManager initOfferingBlessingManager() {
        OfferingBlessingManager manager;
        if (this instanceof ITieredMachine tiered) {
            manager = new OfferingBlessingManager(tiered.getTier().getLevel());
        } else {
            manager = new OfferingBlessingManager();
        }
        setManagerStorage(manager);
        return manager;
    }

    /**
     * 配方匹配守卫 — 神谕是否觉醒。
     * 默认返回 true，表示已觉醒可以开始配方匹配。
     * 在 seekAndInterpretScripture() 中调用，决定是否开始匹配。
     *
     * @return true 表示觉醒，可以开始配方匹配
     */
    default boolean isAwakened() { return true; }

    // ==================== 内部存储访问器（由机器实现类提供） ====================

    /**
     * 获取管理器存储（内部使用）。
     * 机器实现类需要返回自己持有的管理器字段。
     */
    OfferingBlessingManager getManagerStorage();

    /**
     * 设置管理器存储（内部使用）。
     * 机器实现类需要将管理器存入自己的字段。
     */
    void setManagerStorage(OfferingBlessingManager manager);
}
```

**实现要点**：

| 方法 | 说明 |
|------|------|
| `getOfferingBlessingManager()` | 获取管理器，支持懒加载兜底。如果尚未初始化则自动调用 `initOfferingBlessingManager()` |
| `initOfferingBlessingManager()` | 显式初始化管理器，建议在机器构造函数中调用 |
| `isAwakened()` | 默认返回 `true`，表示机器已觉醒可进行配方匹配 |
| `getManagerStorage()` | 内部访问器，机器实现类返回自己持有的管理器字段 |
| `setManagerStorage(manager)` | 内部访问器，机器实现类将管理器存入自己的字段 |

**使用模式**：

```java
// 机器实现类
public class MyBelieverMachine extends MetaMachine implements IBelieverOfScripture, ITieredMachine {
    
    // 机器自己持有管理器实例
    private volatile OfferingBlessingManager offeringBlessingManager;
    
    public MyBelieverMachine(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        // 主动初始化（推荐）
        initOfferingBlessingManager();
    }
    
    @Override
    public OfferingBlessingManager getManagerStorage() {
        return offeringBlessingManager;
    }
    
    @Override
    public void setManagerStorage(OfferingBlessingManager manager) {
        this.offeringBlessingManager = manager;
    }
}

// 外部调用
OfferingBlessingManager manager = machine.getOfferingBlessingManager();
```

**初始化策略**：

| 场景 | 行为 |
|------|------|
| 机器实现 `ITieredMachine` | 创建带虔诚等级限制的管理器：`new OfferingBlessingManager(tiered.getTier().getLevel())` |
| 机器仅实现 `IAwakened` | 创建无限制模式的管理器：`new OfferingBlessingManager()` |

---

## 1. IOmnipresentAvatar（可遍在化现）

**归属**：`OMNIPRESENT_ASCENSION` 修改器

**职责**：声明此机器可同时显现多份化身执行工作，为 `OMNIPRESENT_ASCENSION` 修改器提供化身上限。

```java
package com.goblincoders.goblintech.api.machine.feature;

import com.goblincoders.goblintech.api.GoblinTechValues;

/**
 * 可同时显现多份化身执行工作的机器。
 * 为 OMNIPRESENT_ASCENSION 修改器提供化身上限。
 */
public interface IOmnipresentAvatar extends IAwakened {

    /**
     * @return 最大遍在化现数量，默认值为 1
     */
    default int getOmnipresentAvatarLimit() {
        return 1;
    }
}
```

**实现要点**：

| 方法 | 说明 |
|------|------|
| `getOmnipresentAvatarLimit()` | 返回机器支持的化身上限。受机器等级、结构规模等影响。默认值为 1 |
| `getOfferingBlessingManager()` | 继承自 `IAwakened`，供 `GoblinOracleOfOmnipresence` 调用 `aggregateMaxOffering()` |
| `isAwakened()` | 继承自 `IAwakened`，默认返回 `true` |

---

## 2. IAscensionBlessed（蒙祝福可升腾）

**归属**：`ASCENSION_BENEDICTION` 修改器

**职责**：声明此机器被神祇赐福可执行倍率提升工作，为 `ASCENSION_BENEDICTION` 修改器提供虔诚等级。

```java
package com.goblincoders.goblintech.api.machine.feature;

import com.goblincoders.goblintech.api.GoblinTechValues;

/**
 * 被神祇赐福可执行倍率提升工作的机器。
 * 为 ASCENSION_BENEDICTION 修改器提供虔诚等级。
 */
public interface IAscensionBlessed extends IAwakened {

    /**
     * 虔诚等级 — 信徒的基础信仰位阶（ULV=0、LV=1、MV=2、HV=3...）。
     * 不受增益影响。
     *
     * @return 虔诚等级，默认值为 ULV (0)
     */
    default int getDevotionGrade() {
        return GoblinTechValues.ULV;
    }
}
```

**实现要点**：

| 方法 | 说明 |
|------|------|
| `getDevotionGrade()` | 机器的固定基础等级，不受增益影响。ULV=0, LV=1, MV=2, HV=3...。默认值为 `GoblinTechValues.ULV` (0) |
| `getOfferingBlessingManager()` | 继承自 `IAwakened`，供升腾计算调用 `aggregateMaxOffering()`（返回 `ActionResult`）和 `beginWork()`/`endWork()` 快照机制 |
| `isAwakened()` | 继承自 `IAwakened`，默认返回 `true` |

---

## 3. IBelieverOfScripture（经文信徒）

**归属**：`DIVINE_SUBTICK` 修改器 / 主循环调度 / 排放态管理

**职责**：经文信徒，聚合 `IOmnipresentAvatar`、`IAscensionBlessed` 子接口，扩展祭品祝福容槽管理、速率评估、排放态输出和配方守卫。

```java
package com.goblincoders.goblintech.api.machine.feature;

/**
 * 经文信徒 — 整合遍在化现与升腾能力，管理机器级的祭品/祝福容槽，提供守卫和排放态输出。
 */
public interface IBelieverOfScripture extends IRecipeLogicMachine, IOmnipresentAvatar, IAscensionBlessed {

    // ===== OfferingStack（祭品容槽）容量与状态 =====

    /** @return 祭品容槽最大容量（Bosom），默认值为 0 */
    default int getMaxBosom() {
        return 0;
    }

    /** @return 祝福容槽最大容量（Endurance），默认值为 0 */
    default int getMaxEndurance() {
        return 0;
    }

    /** @return 当前祭品存量（OfferingStack） */
    int getOfferingStock();

    /** @param value 设置当前祭品存量 */
    void setOfferingStock(int value);

    /** @return 当前祝福存量（BlessingStack，> 0 触发 PURGE 态） */
    int getBlessingStock();

    /** @param value 设置当前祝福存量 */
    void setBlessingStock(int value);


    // ===== 安全阈值 =====

    /** @return 祭品安全阈值（低于此值时不启动新配方），默认 Bosom 的 1/4 */
    default int getOfferingThreshold() { return getMaxBosom() / 4; }

    /** @return 祝福安全阈值（高于此值时阻止新配方输出），默认 Endurance 的 3/4 */
    default int getBlessingThreshold() { return getMaxEndurance() * 3 / 4; }


    // ===== 速率（由神谕者 assessBelieverState 计算并设置） =====

    /** @return 当前祭品供给率 [0, 1] */
    float getOfferingRate();

    /** @param rate 设置祭品供给率 */
    void setOfferingRate(float rate);

    /** @return 当前祝福倾倒率 [0, 1] */
    float getBlessingRate();

    /** @param rate 设置祝福倾倒率 */
    void setBlessingRate(float rate);

    /** @return 虔诚等级 = min(offeringRate, blessingRate) */
    float getPietyLevel();

    /** @param rate 设置虔诚等级 */
    void setPietyLevel(float rate);

    /** @return 神圣努力值 = pietyLevel × blessedEffort */
    int getDivineEffort();

    /** @param rate 设置神圣努力值 */
    void setDivineEffort(int rate);


    // ===== 神恩加持的努力（基础工作速率，进度缩放因子） =====

    /** @return 神恩加持的努力值，默认为 16 */
    default int getBlessedEffort() { return 16; }


    // ===== 内部计数器（由 aggregateMaxOffering / aggregateMaxBlessing 结果填充） =====

    /** @return 本 tick 已接收的祭品量 */
    int getOfferingReceived();

    /** @param value 设置本 tick 已接收的祭品量 */
    void setOfferingReceived(int value);

    /** @return 本 tick 已倾倒的祝福量 */
    int getBlessingPoured();

    /** @param value 设置本 tick 已倾倒的祝福量 */
    void setBlessingPoured(int value);


    // ===== assessBelieverState 委托方法（由神谕者每 tick 调用） =====

    /**
     * 评估祭品供给能力，更新 offeringRate。
     * 由 GoblinOracleOfScripture.assessBelieverState() 每 tick 调用。
     *
     * <p>默认实现：委托 OfferingBlessingManager.aggregateMaxOffering()
     * 聚合所有献祭模块的容量，结果通过 ActionResult.getContent() 返回。
     * GoblinOracleOfScripture 取 offering 与 blessing 两个 ActionResult 的
     * getContent() 值，计算综合献祭/祝福比（pietyRatio），传入 applyDivineWork()。
     *
     * @param divineOffering 配方 demand（经并行/超频修正后的 divineOffering）
     * @return ActionResult，getContent() 为聚合后的祭品容量
     */
    default ActionResult evaluateDivineOffering(int divineOffering) {
        var result = getOfferingBlessingManager().aggregateMaxOffering();
        if (!result.isSuccess()) {
            setOfferingRate(0);
            return result;
        }
        int maxOffering = result.getContent();
        setOfferingReceived(maxOffering);
        if (getOfferingStock() < getOfferingThreshold())
            setOfferingRate(Math.min(1f, (float) maxOffering / divineOffering));
        else
            setOfferingRate(1f);
        return result;
    }

    /**
     * 评估祝福倾倒能力，更新 blessingRate。
     * 由 GoblinOracleOfScripture.assessBelieverState() 每 tick 调用。
     *
     * <p>默认实现：委托 OfferingBlessingManager.aggregateMaxBlessing()
     * 聚合所有祝福模块的容量。
     *
     * @param divineBlessing 配方 yield（经并行/超频修正后的 divineBlessing）
     * @return ActionResult，getContent() 为聚合后的祝福容量
     */
    default ActionResult evaluateDivineBlessing(int divineBlessing) {
        var result = getOfferingBlessingManager().aggregateMaxBlessing();
        if (!result.isSuccess()) {
            setBlessingRate(0);
            return result;
        }
        int maxBlessing = result.getContent();
        setBlessingPoured(maxBlessing);
        if (getBlessingStock() > getBlessingThreshold())
            setBlessingRate(Math.min(1f, (float) maxBlessing / divineBlessing));
        else
            setBlessingRate(1f);
        return result;
    }


    // ===== performDivineWork 默认实现（由神谕者每 tick 调用） =====

    /**
     * 执行一轮献祭与祝福 — 按神谕者计算的综合比例批量产出。
     * 由 GoblinOracleOfScripture.performDivineWork() 在 assessBelieverState() 之后调用。
     *
     * <p>GoblinOracleOfScripture 计算逻辑：
     * <pre>
     * var offeringResult = evaluateDivineOffering(demand);
     * var blessingResult = evaluateDivineBlessing(yield);
     * float pietyRatio = min(
     *     (float)offeringResult.getContent() / demand,
     *     (float)blessingResult.getContent() / yield
     * );
     * applyDivineWork(pietyRatio);
     * </pre>
     *
     * <p>默认实现：将 pietyRatio 传入 OfferingBlessingManager.performDivineWork(ratio)，
     * Manager 内部依次调用 batchOffer(ratio) 和 batchBless(ratio)，
     * 在模块级节流阀基础上额外乘以 pietyRatio，返回聚合后的 ActionResult。
     *
     * @param pietyRatio 综合献祭/祝福比 [0, 1]，由神谕者从评估结果计算
     * @return ActionResult，任一模块产出成功即返回 SUCCESS
     */
    default ActionResult applyDivineWork(float pietyRatio) {
        return getOfferingBlessingManager().performDivineWork(pietyRatio);
    }


    // ===== 配方匹配守卫 =====
    // isAwakened() 继承自 IAwakened（通过 IOmnipresentAvatar / IAscensionBlessed）


    // ===== 子 tick 上限 =====

    /**
     * 最大子 tick 并行数 — 硬件上限。
     * 限制 DIVINE_SUBTICK 产生的最大并行补偿，避免单 tick 过度膨胀。
     *
     * @return 最大子 tick 并行数，默认 64
     */
    default int getMaxSubtickCount() { return 64; }


    // ===== 排放态输出 =====

    /**
     * 排放态祝福倾倒 — 每 tick 输出 Blessing 产物。
     * 在 conductBlessingPurge() 中调用，无需接入 GTM 的 handleTickRecipeIO。
     *
     * <p>默认实现：委托 OfferingBlessingManager.batchBless(1.0f)，
     * 此时 offeringRate=0，以全功率（pietyRatio=1.0）排放剩余祝福。
     *
     * @return 倾倒结果
     */
    default ActionResult pourSanctifiedBlessing() {
        return getOfferingBlessingManager().batchBless(1.0f);
    }
}
```

**继承层级**：

```
IBelieverOfScripture
├── IRecipeLogicMachine       (GT 标准配方逻辑)
├── IOmnipresentAvatar        (遍在化现)
│   └── getOmnipresentAvatarLimit()
└── IAscensionBlessed         (升腾祝福)
    └── getDevotionGrade()

IAwakened                     (公共基础)
    ├── getOfferingBlessingManager()
    ├── isAwakened()
    ├── IOmnipresentAvatar
    └── IAscensionBlessed
```

**方法分组与实现要点**：

| 分组 | 方法 | 说明 |
|------|------|------|
| **容槽容量** | `getMaxBosom()` / `getMaxEndurance()` | 机器的祭品/祝福容槽上限 |
| **容槽状态** | `getOfferingStock()` / `setOfferingStock()` / `getBlessingStock()` / `setBlessingStock()` | 当前存量，`blessingStock > 0` 触发 PURGE 态 |
| **安全阈值** | `getOfferingThreshold()` / `getBlessingThreshold()` | 防资源耗尽/溢出，供 `evaluateDivineOffering` / `evaluateDivineBlessing` 默认实现使用 |
| **速率评估** | `getOfferingRate()` / `setOfferingRate()` / `getBlessingRate()` / `setBlessingRate()` | 由 `assessBelieverState()` 每 tick 计算 |
| **虔诚/努力** | `getPietyLevel()` / `setPietyLevel()` / `getDivineEffort()` / `setDivineEffort()` | `pietyLevel = min(offeringRate, blessingRate)`；`divineEffort = pietyLevel × blessedEffort` |
| **基础速率** | `getBlessedEffort()` | 默认 16，进度缩放因子 `duration = recipe.duration × blessedEffort` |
| **内部计数器** | `getOfferingReceived()` / `setOfferingReceived()` / `getBlessingPoured()` / `setBlessingPoured()` | 由 `ActionResult.getContent()` 结果填充 |
| **评估委托** | `evaluateDivineOffering(int) → ActionResult` / `evaluateDivineBlessing(int) → ActionResult` | 委托 `Manager.aggregateMaxOffering/Blessing()`，返回 ActionResult 供神谕者计算 pietyRatio |
| **工作委托** | `applyDivineWork(float pietyRatio) → ActionResult` | 委托 `Manager.performDivineWork(ratio)`，节流阀 × pietyRatio，返回聚合结果 |
| **守卫** | `isAwakened()` | 匹配前置检查（激活、服务端、非暂停、结构完整），继承自 `IAwakened` |
| **子 tick** | `getMaxSubtickCount()` | `DIVINE_SUBTICK` 硬件上限 |
| **排放** | `pourSanctifiedBlessing() → ActionResult` | 默认委托 `Manager.batchBless(1.0f)`，PURGE 态全功率排放 |

**调用链路**：

```
GoblinOracleOfScripture.serverTick()
├── IDLE 态 → seekAndInterpretScripture()
│              └── isAwakened()                                    ← 守卫检查
├── WORKING 态 → assessBelieverState()
│   ├── var offResult = evaluateDivineOffering(demand)             ← 返回 ActionResult
│   ├── var blessResult = evaluateDivineBlessing(yield)            ← 返回 ActionResult
│   ├── pietyRatio = min(offResult.content/demand, blessResult.content/yield)
│   └── setPietyLevel() / setDivineEffort()
├── performDivineWork()
│   └── applyDivineWork(pietyRatio)                                ← 传入综合比
│       └── Manager.performDivineWork(pietyRatio)   ← batchOffer + batchBless，节流阀 × ratio
├── PURGE 态 → conductBlessingPurge()
│   ├── evaluateDivineBlessing()                                   ← offeringRate=0
│   └── pourSanctifiedBlessing()
│       └── Manager.batchBless(1.0f)         ← 全功率排放
└── completeScriptureRite()
    └── Manager.endWork()                     ← 清理快照 + 模块回调
```

---

## 4. IPresenceOfOracle（神谕在场）

**归属**：预言者体系（SNAP 见证 / FINISH 应用）

**职责**：声明此机器支持预言者（Augur）的介入。两个核心职责——见证输入快照和应用预言者序列到输出槽。

```java
package com.goblincoders.goblintech.api.machine.feature;

import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;
import com.goblincoders.goblintech.api.recipe.Augur;
import com.goblincoders.goblintech.api.recipe.ScriptureContext;

import java.util.List;

/**
 * 声明此机器支持预言者（Augur）的介入。
 *
 * <p>当 GoblinScripture（哥布林经文）带有预言者时，setupScripture 会检查
 * 机器是否实现此接口——未实现则拒绝仪式。
 *
 * <p>两个职责：
 * <ul>
 *   <li>{@link #witnessInputs} — after beforeWorking, before handleRecipeIO(IN)（见证输入快照）</li>
 *   <li>{@link #applyAugurs} — after handleRecipeIO(OUT)（应用预言者）</li>
 * </ul>
 *
 * <p>预言者（Augur）作用于输出物品的 DataComponent（NBT、食物属性、TFC trait、温度等），
 * 而非 tick I/O。因此预言者体系中的"输入/输出"指物品槽内容，不涉及 Divine 层级的 Offering/Blessing。
 */
public interface IPresenceOfOracle extends IMachineFeature {

    /**
     * 见证当前输入槽位内容到 ctx.witnessedInputs，供 {@code requiresVision=true} 的预言者读取。
     * 调用时机：beforeWorking 之后、handleRecipeIO(IN) 之前。
     * 允许机器在 beforeWorking 中修改输入后再见证。
     *
     * <p>实现要点：遍历输入槽位的 ItemStackHandler / FluidTank，
     * 将每个槽位的 ItemStack（含 DataComponent）或 FluidStack 存入
     * {@code ctx.witnessedInputs.get(capability)}。
     */
    void witnessInputs(ScriptureContext ctx);

    /**
     * 遍历输出槽位，按序应用预言者序列，修改输出物品的 DataComponent。
     * 调用时机：completeScriptureRite 中 handleRecipeIO(OUT) 之后。
     *
     * <p>默认实现流程：
     * <ol>
     *   <li>遍历 {@code getCapabilitiesProxy().get(IO.OUT)} 获取输出能力代理</li>
     *   <li>对每个槽位（ItemStackHandler → getSlots / FluidTank → getTanks）：</li>
     *   <li>取出当前内容 → 遍历 augurs 序列 → {@code augur.apply(cap, content, ctx)}</li>
     *   <li>每个预言者返回修改后的内容（如添加/移除/复制 DataComponent）</li>
     *   <li>将最终内容写回槽位</li>
     * </ol>
     *
     * <p>机器可覆写以适配非标准槽位结构（如自定义输出代理）。
     */
    default void applyAugurs(List<Augur> augurs, ScriptureContext ctx) {
        var proxy = getCapabilitiesProxy().get(IO.OUT);
        if (proxy == null) return;

        // 物品槽位：遍历每个 slot，链式应用所有 Augur
        if (proxy instanceof NotifiableItemStackHandler itemHandler) {
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                var stack = itemHandler.getStackInSlot(i);
                if (stack.isEmpty()) continue;

                Object modified = stack;
                for (Augur augur : augurs) {
                    if (augur.targetCapabilities().contains(ItemRecipeCapability.CAP)) {
                        modified = augur.apply(ItemRecipeCapability.CAP, modified, ctx);
                    }
                }
                itemHandler.setStackInSlot(i, (ItemStack) modified);
            }
        }

        // 流体槽位：遍历每个 tank，链式应用所有 Augur
        if (proxy instanceof NotifiableFluidTank fluidTank) {
            for (int i = 0; i < fluidTank.getTanks(); i++) {
                var fluid = fluidTank.getFluidInTank(i);
                if (fluid.isEmpty()) continue;

                Object modified = fluid;
                for (Augur augur : augurs) {
                    if (augur.targetCapabilities().contains(FluidRecipeCapability.CAP)) {
                        modified = augur.apply(FluidRecipeCapability.CAP, modified, ctx);
                    }
                }
                fluidTank.setFluidInTank(i, (FluidStack) modified);
            }
        }
    }
}
```

**实现要点**：

| 方法 | 说明 |
|------|------|
| `witnessInputs(ScriptureContext ctx)` | 在 `beforeWorking()` 之后、`handleRecipeIO(IN)` 之前调用。遍历输入槽位，将 ItemStack（含 DataComponent）存入 `ctx.witnessedInputs`，供 `requiresVision=true` 的预言者读取 |
| `applyAugurs(List<Augur>, ScriptureContext)` | 在 `handleRecipeIO(OUT)` 之后调用。遍历输出槽位，对每个槽位的内容链式应用所有预言者，每个预言者可读写 DataComponent（NBT、食物属性、TFC trait、温度等）。提供默认实现，机器可覆写 |

**调用时序**：

```
setupScripture()
├── beforeWorking()                    // 机器前置钩子
├── witnessInputs(ctx)                 // ← 见证输入快照（含 DataComponent）
├── handleRecipeIO(IN) / matchRecipe() // 消耗或验证输入
└── ...

completeScriptureRite()
├── handleRecipeIO(OUT)                // 产出物品到输出槽
├── applyAugurs(augurs, ctx)           // ← 链式应用预言者修改 DataComponent
└── enterBlessingPurgeIfNeeded()       // 检查排放态
```

---

## 5. IFavoredByOracle（蒙神谕恩惠者）

**归属**：`ORACLE_FAVOR` 修改器

**职责**：蒙神谕恩惠者，被经文神谕者青睐的机器。为 `ORACLE_FAVOR` 提供批处理开关和打包阈值。

```java
package com.goblincoders.goblintech.api.machine.feature;

import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;

/**
 * 蒙神谕恩惠者 — 被经文神谕者青睐的机器。
 * 为 ORACLE_FAVOR 提供批处理开关，仅受物品/流体限制，不受神力资源影响。
 * 神谕恩惠：经文神谕者不忍心频繁惊扰 serverTick 大神，
 * 于是施以恩惠，将多份短经文打包成一份长经文，一次性呈递。
 */
public interface IFavoredByOracle extends IMachineFeature {

    boolean isOracleFavorEnabled();

    void setOracleFavorEnabled(boolean enabled);

    default int getOracleFavorDuration() {
        return ConfigHolder.INSTANCE.machines.batchDuration;
    }
}
```

**实现要点**：

| 方法 | 说明 |
|------|------|
| `isOracleFavorEnabled()` | 是否启用神谕恩惠（打包短经文） |
| `setOracleFavorEnabled(boolean)` | 设置恩惠开关 |
| `getOracleFavorDuration()` | 返回打包阈值时长。有默认实现，从配置读取 |

**设计意图**：`ORACLE_FAVOR` 将 `duration < 阈值` 的短经文打包成批次，仅受物品/流体输入限制，不受神力资源（Bosom/Endurance）影响。

---

## 接口归属速查表

| 接口 | 修改器/阶段 | 核心方法 |
|------|-------------|----------|
| `IAwakened` | 基础接口 | `getOfferingBlessingManager()`、`isAwakened()` |
| `IOmnipresentAvatar` | `OMNIPRESENT_ASCENSION` | `getOmnipresentAvatarLimit()` |
| `IAscensionBlessed` | `ASCENSION_BENEDICTION` | `getDevotionGrade()` |
| `IBelieverOfScripture` | `DIVINE_SUBTICK` / 守卫 / 排放 | `getMaxSubtickCount()`、`pourSanctifiedBlessing()` |
| `IPresenceOfOracle` | 预言者体系（SNAP+FINISH） | `witnessInputs(ctx)`、`applyAugurs(augurs, ctx)` |
| `IFavoredByOracle` | `ORACLE_FAVOR` | `isOracleFavorEnabled()`、`getOracleFavorDuration()` |

## 继承关系

```
IMachineFeature (GT 标准)
├── IAwakened                 — 已觉醒者（基础）
│   ├── getOfferingBlessingManager()
│   ├── isAwakened()
│   ├── IOmnipresentAvatar    — 遍在化现
│   │   └── getOmnipresentAvatarLimit()
│   └── IAscensionBlessed     — 升腾祝福
│       └── getDevotionGrade()
├── IPresenceOfOracle         — 神谕在场
│   ├── witnessInputs(ctx)
│   └── applyAugurs(augurs, ctx)
├── IFavoredByOracle          — 神谕恩惠
│   ├── isOracleFavorEnabled()
│   └── getOracleFavorDuration()

IBelieverOfScripture (聚合接口)
├── IRecipeLogicMachine       (GT 标准)
├── IOmnipresentAvatar        (聚合)
└── IAscensionBlessed         (聚合)
```
