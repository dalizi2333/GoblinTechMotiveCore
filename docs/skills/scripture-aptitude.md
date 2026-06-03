# ScriptureAptitude — 经文天赋（Divine 能量配方能力）

## 概述

`ScriptureAptitude` 是 Divine 能量在配方系统中的**类型标识 + 数据载体**，继承自 GTCEu 的 `RecipeCapability<Integer>`。它负责经文配方中 Divine 能量（祭品/祝福）的序列化、并行限制计算，以及为烘焙阶段修改器提供数据读写接口。

**包路径**：`com.goblincoders.goblintech.api.recipe.capability`

**注册名**：`goblintech:divine`

---

## 1. 类定义

```java
package com.goblincoders.goblintech.api.recipe.capability;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.SerializerInteger;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;

/**
 * Divine 能量配方能力 — 经文天赋。
 * 作为配方 JSON 中 divine 能量的数据载体，同时提供并行限制逻辑。
 *
 * 构造时自动注册到 {@link DivinePowerRegistry}，
 * 使 tickInputModifier 等批量修饰器在 applyAllButPower 时跳过 Divine 内容。
 */
public class ScriptureAptitude extends RecipeCapability<Integer> {

    public static final ScriptureAptitude CAP = new ScriptureAptitude();

    protected ScriptureAptitude() {
        super("goblintech:divine", SerializerInteger.INSTANCE);
        // 注册为 Power 资源，避免被 tickInputModifier 误伤
        DivinePowerRegistry.INSTANCE.register(this);
    }

    @Override
    public Integer copyInner(Integer content) {
        return content;
    }

    @Override
    public Integer copyWithModifier(Integer content, ContentModifier modifier) {
        return modifier.apply(content);
    }
}
```

---

## 2. 配方 JSON 格式

在配方 JSON 中通过 `divine` 键声明 Divine 能量需求：

```json
{
  "tickInputs": {
    "divine": [32]
  },
  "tickOutputs": {
    "divine": [16]
  }
}
```

| 字段 | 含义 | 对应术语 |
|------|------|----------|
| `tickInputs.divine` | 每 tick 消耗的祭品量 | `offeringDemand` |
| `tickOutputs.divine` | 每 tick 产出的祝福量 | `blessingYield` |

---

## 3. 并行限制方法

`ScriptureAptitude` 提供两个并行限制方法，由 `GoblinOracleOfOmnipresence`（继承 `ParallelLogic`）在 BAKE 阶段计算化现数量时，通过 GTCEu 的 capability 遍历机制间接调用。

### 3.1 调用链

`GoblinOracleOfOmnipresence` 本身不直接持有 `OfferingBlessingManager`。它通过 `ParallelLogic.getParallelAmount()` 遍历配方 capabilities 时触发：

```
GoblinOracleOfOmnipresence.getOmnipresentAvatarCount(machine, scripture, avatarLimit)
  └── ParallelLogic.getParallelAmount(machine, scripture, avatarLimit)
        ├── getMaxByInput(machine, scripture, avatarLimit)
        │     └── 遍历 recipe.tickInputs 的每个 capability
        │           └── ScriptureAptitude.CAP.getMaxParallelByInput(machine, recipe, limit, tick=true)
        │
        └── limitByOutputMerging(machine, scripture, avatarLimit)
              └── 遍历 recipe.tickOutputs 的每个 capability
                    └── ScriptureAptitude.CAP.limitMaxParallelByOutput(machine, recipe, limit, tick=true)
```

### 3.2 getMaxOffering

```java
/**
 * 获取机器的最大祭品容量（原始值，不做除法）。
 * 供 ASCENSION_BENEDICTION 等配方修改器在 BAKE 阶段调用，
 * 是外部访问 OfferingBlessingManager 的唯一入口。
 *
 * @param holder 机器能力持有者（IRecipeLogicMachine，实际为 IAwakened）
 * @return ActionResult，内部值为 maxOffering
 */
public ActionResult getMaxOffering(Object holder);
```

**内部实现**：

```java
if (!(holder instanceof IAwakened awakened)) return ActionResult.fail("not_awakened");
OfferingBlessingManager manager = awakened.getOfferingBlessingManager();
if (manager == null) return ActionResult.fail("no_manager");
return manager.aggregateMaxOffering();
```

> **设计意图**：所有外部调用方（配方修改器、ParallelLogic 等）统一通过 `ScriptureAptitude.CAP` 访问机器能力，不直接调用 `getOfferingBlessingManager().aggregateMaxOffering()`。

### 3.3 getMaxParallelByInput

```java
/**
 * 按祭品输入能力限制并行数。
 * 由 ParallelLogic.getMaxByInput() 遍历 recipe.tickInputs 时调用。
 *
 * @param holder 机器能力持有者（IRecipeLogicMachine，实际为 IAwakened）
 * @param recipe 经文配方
 * @param limit  硬件上限
 * @param tick   是否按 tick 限制
 * @return 最大并行数
 */
public int getMaxParallelByInput(Object holder, GTRecipe recipe, int limit, boolean tick);
```

**内部实现**：

```java
if (!tick) return limit;  // 非 tick 场景不做限制

// 准入条件：机器必须已觉醒
if (!(holder instanceof IAwakened awakened)) return limit;
OfferingBlessingManager manager = awakened.getOfferingBlessingManager();
if (manager == null) return limit;

// 聚合祭品容量
ActionResult result = manager.aggregateMaxOffering();
if (!result.isSuccess()) return 0;
long maxOffering = result.value();

// 提取配方 demand
long offeringDemand = extractDemand(recipe);
if (offeringDemand <= 0) return limit;

return (int) (maxOffering / offeringDemand);
```

| 参数 | tick=true | tick=false |
|------|-----------|------------|
| 计算方式 | `floor(maxOffering / offeringDemand)` | 返回 `limit`（不做限制） |

### 3.4 limitMaxParallelByOutput

```java
/**
 * 按祝福输出能力限制并行数。
 * 由 ParallelLogic.limitByOutputMerging() 遍历 recipe.tickOutputs 时调用。
 *
 * @param holder 机器能力持有者（IRecipeLogicMachine，实际为 IAwakened）
 * @param recipe 经文配方
 * @param limit  硬件上限
 * @param tick   是否按 tick 限制
 * @return 最大并行数
 */
public int limitMaxParallelByOutput(Object holder, GTRecipe recipe, int limit, boolean tick);
```

**内部实现**：

```java
if (!tick) return limit;

// 准入条件：机器必须已觉醒
if (!(holder instanceof IAwakened awakened)) return limit;
OfferingBlessingManager manager = awakened.getOfferingBlessingManager();
if (manager == null) return limit;

ActionResult result = manager.aggregateMaxBlessing();
if (!result.isSuccess()) return 0;
long maxBlessing = result.value();

long blessingYield = extractYield(recipe);
if (blessingYield <= 0) return limit;

return (int) (maxBlessing / blessingYield);
```

| 参数 | tick=true | tick=false |
|------|-----------|------------|
| 计算方式 | `floor(maxBlessing / blessingYield)` | 返回 `limit`（不做限制） |

### 3.5 接口要求

| ScriptureAptitude 方法 | 需要的机器接口 | 获取的数据 |
|------------------------|--------------|-----------|
| `getMaxOffering()` | `IAwakened` | `OfferingBlessingManager.aggregateMaxOffering()` — 原始容量值 |
| `getMaxParallelByInput()` | `IAwakened` | `OfferingBlessingManager.aggregateMaxOffering()` — 并行限制用 |
| `limitMaxParallelByOutput()` | `IAwakened` | `OfferingBlessingManager.aggregateMaxBlessing()` |

> **说明**：两个方法的准入条件统一为 `IAwakened`，通过该接口获取 `OfferingBlessingManager`。`IOmnipresentAvatar` 和 `IAscensionBlessed` 都继承自 `IAwakened`，因此实现任一接口的机器都可使用经文配方。

---

## 4. 烘焙阶段数据接口

供 `ASCENSION_BENEDICTION` 和 `DIVINE_SUBTICK` 修改器读写的配方数据方法。

### 4.1 extractDemand

```java
/**
 * 提取经文配方的总 demand（祭品需求总量）。
 * 用于 ASCENSION_BENEDICTION 计算升腾次数。
 *
 * 读取 tickInputs.divine 的第一个元素，乘以 recipe.duration。
 *
 * @param recipe 经文配方
 * @return 总 demand（祭品需求总量）
 */
public long extractDemand(GTRecipe recipe);
```

### 4.2 setSubtickOverflow / extractSubtickOverflow

```java
/**
 * 设置子 tick 溢出量。
 * ASCENSION_BENEDICTION 在升腾循环中，将因 duration 过短无法继续升腾的
 * 剩余次数写入此字段，供 DIVINE_SUBTICK 读取。
 */
public void setSubtickOverflow(GTRecipe recipe, int overflow);

/**
 * 提取子 tick 溢出量。
 * DIVINE_SUBTICK 读取后转化为 subtickParallel = 2^overflow 的并行补偿。
 */
public int extractSubtickOverflow(GTRecipe recipe);
```

### 4.3 multiplyContent

```java
/**
 * 倍增配方中 Divine 内容（tickInputs/tickOutputs）。
 * 供 ASCENSION_BENEDICTION 在 BAKE 阶段调用。
 *
 * 由于 ScriptureAptitude 已注册到 DivinePowerRegistry，
 * tickInputModifier 在 applyAllButPower 时会跳过 Divine 内容，
 * 因此需要此方法单独倍增。
 *
 * 未来 ScriptureScribe 落地后，可通过 builder.divineMultiplier(n) 替代此调用。
 *
 * @param recipe    经文配方
 * @param multiplier 倍增系数
 */
public void multiplyContent(GTRecipe recipe, double multiplier) {
    var contents = recipe.tickInputs.get(this);
    if (contents != null) {
        for (var content : contents) {
            content.content = copyWithModifier(content.content, ContentModifier.multiplier(multiplier));
        }
    }
}
```

---

## 5. 与 RecipeRunner 的关系

`doMatchInRecipe() = true`，确保 Divine 参与：
- **ParallelLogic 计算**：并行限制（§3）
- **RecipeRunner matching**：配方匹配检查

但 Divine 的**具体能量消费**不走 RecipeRunner 的 tick 执行管线。`GoblinOracleOfScripture` 覆写了 `handleRecipeWorking()`，改为自定义的 `assessBelieverState()` 处理。

**原因**：GTCEu 的 RecipeRunner 将 IN/OUT 作为两次独立串行调用，无法表达 Divine 体系"输出堵塞反作用于输入"的闭环信仰经济（`pietyLevel = min(offeringRate, blessingRate)`）。

---

## 6. 调用链一览

```
BAKE 阶段
├── OMNIPRESENT_ASCENSION
│   └── GoblinOracleOfOmnipresence.getOmnipresentAvatarCount()
│       ├── getMaxParallelByInput(holder, recipe, limit, true)   ← §3.1
│       └── limitMaxParallelByOutput(holder, recipe, limit, true) ← §3.2
│
└── ASCENSION_BENEDICTION
│   ├── extractDemand(recipe)                                     ← §4.1
│   └── setSubtickOverflow(recipe, overflow)                      ← §4.2
│
└── DIVINE_SUBTICK
    └── extractSubtickOverflow(recipe)                            ← §4.2

配方守卫
└── isAwakened()
    └── getMaxParallelByInput(machine, recipe, 1, true) == 0      ← §3.1
        → FAIL ("OfferingReceived 不足")

ORACLE_FAVOR
└── getMortalAvatarCount()
    └── 排除 ScriptureAptitude.CAP（祭品/祝福不参与打包限制）
```

---

## 7. 关键代码位置

| 功能 | 方法 | 位置 |
|------|------|------|
| 祭品并行限制 | `ScriptureAptitude.CAP.getMaxParallelByInput()` | `ScriptureAptitude.java` |
| 祝福并行限制 | `ScriptureAptitude.CAP.limitMaxParallelByOutput()` | `ScriptureAptitude.java` |
| 提取总 demand | `ScriptureAptitude.CAP.extractDemand()` | `ScriptureAptitude.java` |
| 设置子 tick 溢出 | `ScriptureAptitude.CAP.setSubtickOverflow()` | `ScriptureAptitude.java` |
| 提取子 tick 溢出 | `ScriptureAptitude.CAP.extractSubtickOverflow()` | `ScriptureAptitude.java` |
| 遍在神谕者 | `GoblinOracleOfOmnipresence` | `GoblinOracleOfOmnipresence.java` |
| 升腾修改器 | `DivineRecipeModifiers.ASCENSION_BENEDICTION` | `DivineRecipeModifiers.java` |
| 子 tick 修改器 | `DivineRecipeModifiers.DIVINE_SUBTICK` | `DivineRecipeModifiers.java` |
