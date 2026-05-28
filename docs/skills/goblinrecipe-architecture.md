# GoblinRecipe 配方系统扩展架构

## 1. 概述

### 1.1 设计目标

对 GTCEu (GregTech CEu Modern) 配方系统进行深度扩展，提供以下核心能力：

1. **配方输出修改器（RecipeOutputModifier）**：在配方产出输出物品/流体时，根据匹配到的输入动态修改输出内容（复制 NBT、trait、food 属性等）。
2. **参数化 RecipeCondition + 失败行为**：继承 GTM 原生的 `RecipeCondition` 体系，通过 `IDivineDecreeOfScripture` 接口扩展失败行为（WAITING / HALT_PROGRESS / INTERRUPT）。
3. **多种配方执行模式（RecipeMode）**：TRANSFORM（物品搬运型）、MODIFY（原地修改型）、AMBIENT（环境提供型）。
4. **GraceStack/OfferingStack 可变速加工**：信徒机器自管理 `graceStock`/`offeringStock`，神谕者每 tick 通过 `assessBelieverState()` 计算虔诚度与神圣努力，天然兼容多种能量、降速运行。
5. **可扩展 RecipeCapability**：热量、扭矩等模块特有资源与 Power 同级，统一走 tickInput/tickOutput。

### 1.2 设计原则

- **零 GTCEu 源码改动**：通过继承（`GoblinScripture extends GTRecipe`、`GoblinOracleOfScripture extends RecipeLogic`）扩展。
- **复用原生体系**：条件统一使用 GTM 原生 `RecipeCondition`，通过 `IDivineDecreeOfScripture` 接口扩展失败行为。条件 JSON 格式与 GTM 原生一致。
- **模块解耦**：通用 API 层（goblintech.recipe）不依赖任何具体 mod。集成实现放在 goblintfc、goblintech.create.kinetic 等包中。
- **向后兼容**：GTM 老机器使用原生 `RecipeLogic`，新机器使用 `GoblinOracleOfScripture`，互不干扰。

---

## 2. 包名架构

### 2.1 包结构

| 包名 | 层级 | 职责 |
|------|------|------|
| `com.goblincoders.goblintech.recipe.api` | 通用 API | RecipeOutputModifier、RecipeMode、ConditionFailBehavior |
| `com.goblincoders.goblintech.recipe.api.condition` | 通用 API | IDivineDecreeOfScripture 接口 |
| `com.goblincoders.goblintech.recipe.api.modifier` | 通用 API | RecipeOutputModifier 接口 |
| `com.goblincoders.goblintech.recipe.api.recipe` | 通用 API | RecipeContext |
| `com.goblincoders.goblintech.recipe` | 核心实现 | GoblinScripture、GoblinOracleOfScripture、IBelieverOfScripture |
| `com.goblincoders.goblintech.recipe.modifiers` | 通用实现 | 内置通用修改器（CopyComponent、SetComponent 等） |
| `com.goblincoders.goblintech.recipe.conditions` | 通用实现 | 内置 RecipeCondition（MinPowerRate、Cleanliness 等） |
| `com.goblincoders.goblintech.machine.slot` | 机器逻辑 | SlotMode 枚举、可配置槽位接口 |
| `com.goblincoders.goblintfc.recipe` | TFC 集成 | TFC 专用修改器、HeatRecipeCapability、Ingredient 条件 |
| `com.goblincoders.goblintfc.recipe.conditions` | TFC 集成 | TFC 专用条件（Heat、Cold 等） |

### 2.2 继承体系

```
IBelieverOfScripture (extends IRecipeLogicMachine)
  └─ GraceStack/OfferingStack 属性 + consumeRate/offerRate/pietyLevel/divineEffort
  └─ consumeDivinePower() / offerDivinePower() / performDivineWork()

GTRecipe (GTM, 零改动)
  └─ GoblinScripture (mode + outputModifiers + tickOutputModifiers)
      conditions 字段复用父类 List<RecipeCondition>

RecipeLogic (GTM, 零改动)
  └─ GoblinOracleOfScripture (assessBelieverState + verifyDivineDecree + runDelay)
```

### 2.3 机器接入方式

```java
public class MyGoblinMachine extends WorkableTieredMachine implements IBelieverOfScripture {
    @Override
    protected RecipeLogic createRecipeLogic() {
        return new GoblinOracleOfScripture(this);
    }
}
```

---

## 3. IBelieverOfScripture（信徒接口）

```java
// 工程→神话 映射:
//   getProgressScale()             → getBlessedEffort()
//   getProgressRate()              → getDivineEffort()
//   getPowerRate()                 → getPietyLevel()
//   getConsumePowerRate()          → getConsumeRate()
//   getPushPowerRate()             → getOfferRate()
//   updateConsumeRate()            → evaluateDivineConsumption()
//   updatePushRate()               → evaluateDivineOffering()
//   updatePowerRate()              → calculatePietyLevel()
//   updateProgressRate()           → determineDivineEffort()
//   applyPowerStacks()             → performDivineWork()
//   updateConsumePower()           → consumeDivinePower()
//   updatePushPower()              → offerDivinePower()
//   getInputPowerStack()           → getGraceStock()
//   getOutputPowerStack()          → getOfferingStock()
//   getInputPowerStackSize()       → getGraceCapacity()
//   getOutputPowerStackSize()      → getOfferingCapacity()
//   getSafeInputThreshold()        → getGraceThreshold()
//   getSafeOutputThreshold()       → getOfferingThreshold()
```

### 3.1 GraceStock/OfferingStack 属性

```java
public interface IBelieverOfScripture extends IRecipeLogicMachine {

    // ===== GraceStack（圣恩容槽）容量与状态 =====
    int getGraceCapacity();
    int getOfferingCapacity();
    int getGraceStock();
    void setGraceStock(int value);
    int getOfferingStock();
    void setOfferingStock(int value);

    // ===== 安全阈值 =====
    int getGraceThreshold();
    int getOfferingThreshold();

    // ===== 速率（由神谕者 assessBelieverState 计算并设置） =====
    float getConsumeRate();
    void setConsumeRate(float rate);
    float getOfferRate();
    void setOfferRate(float rate);
    float getPietyLevel();
    void setPietyLevel(float rate);
    int getDivineEffort();
    void setDivineEffort(int rate);

    // ===== 神恩加持的努力（基础工作速率，进度缩放因子） =====
    default int getBlessedEffort() { return 16; }

    // ===== 内部计数器（由 consumeDivinePower / offerDivinePower 设置） =====
    int getGraceReceived();
    void setGraceReceived(int value);
    int getGraceOffered();
    void setGraceOffered(int value);
}
```

### 3.2 信徒响应方法

```java
// 子类必须覆写。默认给 graceStock 归零、设置 graceReceived = 0。
default ActionResult consumeDivinePower(int tickPowerInput) {
    setGraceStock(0);
    setGraceReceived(0);
    return ActionResult.SUCCESS;
}

// 子类必须覆写。默认填满 offeringStock、设置 graceOffered = 0。
default ActionResult offerDivinePower(int tickPowerOutput) {
    setOfferingStock(getOfferingCapacity());
    setGraceOffered(0);
    return ActionResult.SUCCESS;
}

// 以下由神谕者 GoblinOracleOfScripture.assessBelieverState() 调用
default void evaluateDivineConsumption(int tickPowerInput) {
    var result = consumeDivinePower(tickPowerInput);
    if (!result.isSuccess()) { setConsumeRate(0); return; }
    if (getGraceStock() < getGraceThreshold())
        setConsumeRate(Math.min(1f, (float) getGraceReceived() / tickPowerInput));
    else
        setConsumeRate(1f);
}

default void evaluateDivineOffering(int tickPowerOutput) {
    var result = offerDivinePower(tickPowerOutput);
    if (!result.isSuccess()) { setOfferRate(0); return; }
    if (getOfferingStock() > getOfferingThreshold())
        setOfferRate(Math.min(1f, (float) getGraceOffered() / tickPowerOutput));
    else
        setOfferRate(1f);
}

default void calculatePietyLevel() {
    setPietyLevel(Math.min(getConsumeRate(), getOfferRate()));
}

default void determineDivineEffort() {
    setDivineEffort((int) (getPietyLevel() * getBlessedEffort()));
}

// 子类必须覆写：实际执行 power stack 充能/放能
default ActionResult performDivineWork() {
    return ActionResult.FAIL_NO_CAPABILITIES;
}
```

---

## 4. GoblinScripture（经文）

`GoblinScripture extends GTRecipe`。条件统一使用父类的 `List<RecipeCondition> conditions`。

```java
public class GoblinScripture extends GTRecipe {
    public RecipeMode mode = RecipeMode.TRANSFORM;
    public List<RecipeOutputModifier> outputModifiers = List.of();
    public List<RecipeOutputModifier> tickOutputModifiers = List.of();
}
```

**tickInput/tickOutput 可含 item/fluid**，但仅在 JEI 等配方查看工具中展示，`GoblinOracleOfScripture` 内只处理 Power 类型。物品/流体的 tick IO 期望由机器注册时的配方类型限制来约束，不在神谕者层面强制。

### 4.1 RecipeMode

```java
public enum RecipeMode {
    TRANSFORM,  // 物品搬运型（默认，与 GTCEu 行为一致）
    MODIFY,     // 原地修改型：输入不消耗，输出原地修改
    AMBIENT     // 环境提供型：不处理物品 IO
}
```

---

## 5. RecipeOutputModifier（输出修改器）

### 5.1 接口

```java
public interface RecipeOutputModifier {
    Object apply(RecipeCapability<?> cap, Object output, RecipeContext ctx);
    Set<RecipeCapability<?>> targetCapabilities();
    default boolean dependsOnInput() { return false; }
    RecipeOutputModifierType<?> type();
}
```

### 5.2 两类修改器

| 字段 | 触发时机 | 典型用途 |
|------|---------|---------|
| `outputModifiers` | `onRecipeFinish()` | 加 trait、复制 food 属性、复制 NBT |
| `tickOutputModifiers` | 每 tick `handleRecipeIO(OUT)` | 流体颜色渐变 |

### 5.3 内置通用修改器

| 修改器 ID | 说明 |
|-----------|------|
| `goblinrecipe:copy_components` | 复制所有 DataComponents |
| `goblinrecipe:copy_component` | 复制指定 DataComponent |
| `goblinrecipe:set_component` | 设置/覆盖 DataComponent |

---

## 6. RecipeContext（配方上下文）

```java
public class RecipeContext {
    public final GTRecipe recipe;
    public final IRecipeCapabilityHolder holder;
    // capturedInputs / capturedTickInputs / data / phase
}
```

- `capturedInputs`：TFC `copy_food`/`copy_input` 等修改器读取原始输入属性
- `data`：修改器之间链式协作、传递中间结果
- `phase`：修改器根据阶段调整行为（MATCHING → CONSUMING → PROCESSING → PRODUCING → FINISHING）

---

## 7. 参数化 RecipeCondition + 失败行为（IDivineDecreeOfScripture）

### 7.1 设计

继承 GTM 原生的 `RecipeCondition` 体系，用 `IDivineDecreeOfScripture` 接口为条件子类提供失败行为：

```java
// 工程→神话: IGoblinCondition → IDivineDecreeOfScripture
public interface IDivineDecreeOfScripture {
    ConditionFailBehavior getFailBehavior();
}
```

### 7.2 ConditionFailBehavior

```java
public enum ConditionFailBehavior {
    WAITING,        // setWaiting() + runDelay 退避 + regressRecipe
    HALT_PROGRESS,  // 保持 WORKING，progress 不变（进度冻结）
    INTERRUPT       // 立即中断配方
}
```

### 7.3 内置条件

```java
// goblintech.recipe.conditions — 基础条件

// 最低处理速度：pietyLevel ≥ minRate，失败 WAITING（触发熔断退避）
public class MinPowerRateCondition extends RecipeCondition<MinPowerRateCondition>
        implements IDivineDecreeOfScripture {
    float minRate;
    @Override public ConditionFailBehavior getFailBehavior() { return ConditionFailBehavior.WAITING; }
}

// 超净等级：cleanliness ≥ minLevel，失败 WAITING
public class CleanlinessCondition extends RecipeCondition<CleanlinessCondition>
        implements IDivineDecreeOfScripture {
    int minLevel;
    @Override public ConditionFailBehavior getFailBehavior() { return ConditionFailBehavior.WAITING; }
}
```

```java
// goblintech.create.kinetic.recipe.conditions — Create 动能条件

// 转速下限：rpm ≥ minRPM，失败 HALT_PROGRESS
public class RPMCondition extends RecipeCondition<RPMCondition>
        implements IDivineDecreeOfScripture {
    float minRPM;
    @Override public ConditionFailBehavior getFailBehavior() { return ConditionFailBehavior.HALT_PROGRESS; }
}

// 转速上限：rpm ≤ maxRPM，失败 INTERRUPT
public class MaxRPMCondition extends RecipeCondition<MaxRPMCondition>
        implements IDivineDecreeOfScripture {
    float maxRPM;
    @Override public ConditionFailBehavior getFailBehavior() { return ConditionFailBehavior.INTERRUPT; }
}
```

```java
// goblintfc.recipe.conditions — TFC 条件

public class HeatCondition extends RecipeCondition<HeatCondition>
        implements IDivineDecreeOfScripture {
    float minHeat;
    @Override public ConditionFailBehavior getFailBehavior() { return ConditionFailBehavior.WAITING; }
}

public class ColdCondition extends RecipeCondition<ColdCondition>
        implements IDivineDecreeOfScripture {
    float minCold;
    @Override public ConditionFailBehavior getFailBehavior() { return ConditionFailBehavior.WAITING; }
}
```

### 7.4 配方 JSON 格式（与 GTM 原生一致）

```json
{
  "type": "gtceu:millstone",
  "conditions": [
    { "type": "goblintech:min_power_rate", "minRate": 0.2 },
    { "type": "goblintech:rpm", "minRPM": 64.0 },
    { "type": "goblintech:max_rpm", "maxRPM": 4096.0 }
  ]
}
```

---

## 8. GraceStack/OfferingStack 可变速加工（神圣评估）

### 8.1 核心流程

```
每 tick: serverTick() → executeDivineWorkCycle()
  │
  └─ assessBelieverState()
       │
       ├─ divineDemand != 0 ?
       │   └─ believer.evaluateDivineConsumption(divineDemand)
       │       ├─ believer.consumeDivinePower() → 子类映射 Power → graceStock
       │       └─ graceStock < graceThreshold ?
       │            consumeRate = graceReceived / divineDemand (≤1)
       │          : consumeRate = 1
       │
       ├─ divineOffering != 0 ?
       │   └─ believer.evaluateDivineOffering(divineOffering)
       │       ├─ believer.offerDivinePower() → 子类映射 Power → offeringStock
       │       └─ offeringStock > offeringThreshold ?
       │            offerRate = graceOffered / divineOffering (≤1)
       │          : offerRate = 1
       │
       ├─ believer.calculatePietyLevel() → pietyLevel = min(consumeRate, offerRate)
       ├─ believer.determineDivineEffort() → divineEffort = pietyLevel × blessedEffort
       │
       ├─ verifyDivineDecree() → 遍历 GoblinScripture.conditions
       │   对每个 IDivineDecreeOfScripture → 按 failBehavior 返回 ActionResult
       │
       ├─ divineEffort == 0 ? → PROGRESS_FROZEN
       └─ believer.performDivineWork() → 子类实现
```

### 8.2 executeDivineWorkCycle() 完整逻辑

```java
// 工程→神话: handleRecipeWorking() → executeDivineWorkCycle()
@Override
public void executeDivineWorkCycle() {
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
    if (isWaiting() || isSuspend()) { regressRecipe(); }
}
```

### 8.3 assessBelieverState() — 评估信徒状态

```java
// 工程→神话: handlePowerStacks() → assessBelieverState()
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
```

### 8.4 verifyDivineDecree() — 验证神谕

遍历 `GoblinScripture.conditions`，同时兼容 GTM 原生 `RecipeCondition`（如老超净间 `CleanroomCondition`）和 `IDivineDecreeOfScripture`。**两套超净间系统独立，写配方时不混用即可。**

**默认低倍速熔断保护**：如果配方 `conditions` 中没有显式的 `MinPowerRateCondition`，自动补一个默认值 `minRate = 0.5`。没声明最低虔诚度的配方，降速到一半以下时也会触发 WAITING + 熔断退避。

```java
// 工程→神话: checkConditions() → verifyDivineDecree()
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

    if (!hasMinPowerRate && believer().getPietyLevel() < 0.5f) {
        return ActionResult.fail(
                Component.translatable("goblintech.recipe.condition.min_power_rate", 0.5f), null, null);
    }

    return ActionResult.SUCCESS;
}
```

配方如要禁用默认熔断（允许任意低倍速运行）：
```json
{
  "conditions": [
    { "type": "goblintech:min_power_rate", "minRate": 0.0 }
  ]
}
```

### 8.5 runAttempt / runDelay 退避机制

复用原版 RecipeLogic 的机制：彻底断电（WAITING 失败）时逐次延长重试间隔，节省 tick 开销。

| runAttempt | runDelay | 行为 |
|-----------|----------|------|
| 1 | 60 ticks | 60 ticks 后重试 |
| 2 | 120 ticks | 120 ticks 后重试 |
| ... | ... | ... |
| 5 | — | SUSPEND（多方块，除非有 preventPowerFail cover） |

`interpretScripture()`（原 `setupRecipe()`）时 `runAttempt = 0; runDelay = 0;`。

### 8.6 三种运行场景

| 场景 | pietyLevel | divineEffort | 返回 | 行为 |
|------|-----------|-------------|------|------|
| 正常 | 1.0 | blessedEffort | SUCCESS | `progress += blessedEffort` |
| 降速 | 0.3 | 0.3×effort | SUCCESS | `progress += 0.3×effort`，慢但不停 |
| 冻结 | 0 | 0 | PROGRESS_FROZEN | progress 不变，保持 WORKING |
| 断电 | 0 + fail=WAITING | — | FAIL | runDelay 退避，省 tick |

### 8.7 降速与降耗的分工

| 角色 | 职责 |
|------|------|
| `GoblinOracleOfScripture` | 每 tick 调用 `assessBelieverState()` 计算 `divineEffort` |
| `IBelieverOfScripture` | `consumeDivinePower/offerDivinePower` 映射 Power ↔ stack |
| 降速 | `pietyLevel < 1` → `divineEffort` 自动缩小 |
| 降耗 | 机器在 `handleTickRecipeIO()` 重写中按 `pietyLevel` 缩放 IN 消耗 |

### 8.8 兼容性

| 场景 | 行为 |
|------|------|
| GTM 老机器 | 使用原生 `RecipeLogic`，不受影响 |
| 新机器 | `progress += believer.getDivineEffort()`（默认 = blessedEffort） |
| 信徒机器 | `consumeDivinePower` 将各种能量（EU、热量等）映射至 graceStock |
| 信徒机器 | `offerDivinePower` 将 offeringStock 映射回各种能量输出 |

---

## 9. 槽位语义（SlotMode）

### 9.1 枚举

```java
public enum SlotMode {
    INPUT_ONLY(IO.IN),
    OUTPUT_ONLY(IO.OUT),
    BOTH(IO.BOTH),          // 缓冲器默认
    HOLD(IO.IN),            // MODIFY 专用：参与 IN 匹配但不被消耗
    SPECIAL_FLUID(IO.BOTH); // MEK 风格的独立交互面
}
```

### 9.2 各机器默认配置

| 机器 | 物品槽 | 流体槽 |
|------|--------|--------|
| 缓冲器 | BOTH | INPUT_ONLY + SPECIAL_FLUID |
| 冰箱 | HOLD (食物) + INPUT_ONLY (电) | INPUT_ONLY (制冷剂) |
| 提取机 | HOLD (物品) + INPUT_ONLY (电) | — |
| 真空冷冻机 | INPUT_ONLY + OUTPUT_ONLY | INPUT_ONLY + OUTPUT_ONLY |

---

## 10. 核心类细节

### 10.1 GoblinOracleOfScripture（extends RecipeLogic）

```java
// 工程→神话: GoblinRecipeLogic → GoblinOracleOfScripture
// 工程→神话: recipeContext → scriptureContext
// 工程→神话: tickPowerInput → divineDemand
// 工程→神话: tickPowerOutput → divineOffering
public class GoblinOracleOfScripture extends RecipeLogic {
    protected @Nullable RecipeContext scriptureContext;
    protected int divineDemand;
    protected int divineOffering;

    public GoblinOracleOfScripture(IBelieverOfScripture believer) {
        super(believer);
    }

    private IBelieverOfScripture believer() {
        return (IBelieverOfScripture) machine;
    }
}
```

重写的方法：

| 神谕者方法 | 工程对应 | 改动 |
|-----------|---------|------|
| `interpretScripture()` | `setupRecipe()` | `duration = recipe.duration × blessedEffort`；缓存 `divineDemand/divineOffering` |
| `executeDivineWorkCycle()` | `handleRecipeWorking()` | 用 `assessBelieverState()` 替代 `RecipeHelper.checkConditions()` + `handleTickRecipe()` |
| `assessBelieverState()` | `handlePowerStacks()` | 评估信徒状态 → 虔诚度 → 神圣努力 |
| `verifyDivineDecree()` | `checkConditions()` | 遍历条件，默认 `pietyLevel < 0.5 → WAITING` |
| `getProgress()` / `getMaxProgress()` | — | 对外除以 `blessedEffort` |
| `interpretScripture()` 内 | — | 额外创建 `scriptureContext`

### 10.2 GoblinScripture（extends GTRecipe）

额外字段：

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `mode` | `TRANSFORM` | 配方执行模式 |
| `outputModifiers` | `List.of()` | 完成时一次性施加的修改器 |
| `tickOutputModifiers` | `List.of()` | 每 tick 施加的修改器 |

---

## 11. TFC 集成（goblintfc.recipe）

### 11.1 RecipeOutputModifier

| 修改器 | 说明 |
|--------|------|
| `goblintfc:add_trait` | 施加 TFC FoodTrait |
| `goblintfc:copy_food` | 复制 TFC 食物属性 |
| `goblintfc:copy_input` | 复制输入物品 NBT |

### 11.2 RecipeCapability（热量）

`HeatRecipeCapability` 走 `tickOutputs`，与 Power 同级：

```java
public record HeatRecipeData(float temperature, float heatCapacity) {}
```

机器 handler 调用 TFC API：
```java
IHeat heat = HeatCapability.get(stack);
heat.addTemperatureFromSourceWithHeatCapacity(data.temperature(), data.heatCapacity());
```

---

## 12. 工程 ↔ 神话命名映射（Agent 快速查找）

### 12.1 类/接口映射

| 工程名 | 神话名 | 角色 |
|--------|--------|------|
| `IGoblinRecipeLogicMachine` | `IBelieverOfScripture` | 信徒接口 |
| `GoblinRecipeLogic` | `GoblinOracleOfScripture` | 神谕者 |
| `GoblinRecipe` | `GoblinScripture` | 经文 |
| `IGoblinCondition` | `IDivineDecreeOfScripture` | 神谕条件 |

### 12.2 信徒接口方法映射

| 工程方法 | 神棍方法 | 说明 |
|---------|---------|------|
| `getProgressScale()` | `getBlessedEffort()` | 神恩加持的努力 |
| `getProgressRate()` | `getDivineEffort()` | 神圣努力（机器自管理） |
| `setProgressRate()` | `setDivineEffort()` | — |
| `getPowerRate()` | `getPietyLevel()` | 虔诚度 |
| `setPowerRate()` | `setPietyLevel()` | — |
| `getConsumePowerRate()` | `getConsumeRate()` | 消耗速率 |
| `setConsumePowerRate()` | `setConsumeRate()` | — |
| `getPushPowerRate()` | `getOfferRate()` | 奉献速率 |
| `setPushPowerRate()` | `setOfferRate()` | — |
| `updateConsumeRate()` | `evaluateDivineConsumption()` | 评估神圣消耗 |
| `updatePushRate()` | `evaluateDivineOffering()` | 评估神圣奉献 |
| `updatePowerRate()` | `calculatePietyLevel()` | 计算虔诚度 |
| `updateProgressRate()` | `determineDivineEffort()` | 确定神圣努力 |
| `applyPowerStacks()` | `performDivineWork()` | 执行神圣工作 |
| `updateConsumePower()` | `consumeDivinePower()` | 消耗神圣能量 |
| `updatePushPower()` | `offerDivinePower()` | 奉献神圣能量 |
| `getPowerReceive()` | `getGraceReceived()` | 已接收恩典 |
| `getPowerPush()` | `getGraceOffered()` | 已奉献恩典 |

### 12.3 GraceStack/OfferingStack 字段映射

| 工程字段 | 神话字段 | 说明 |
|---------|---------|------|
| `getInputPowerStackSize()` | `getGraceCapacity()` | 圣恩容槽容量 |
| `getOutputPowerStackSize()` | `getOfferingCapacity()` | 奉献容槽容量 |
| `getInputPowerStack()` | `getGraceStock()` | 圣恩容槽存量 |
| `getOutputPowerStack()` | `getOfferingStock()` | 奉献容槽存量 |
| `setInputPowerStack()` | `setGraceStock()` | — |
| `setOutputPowerStack()` | `setOfferingStock()` | — |
| `getSafeInputThreshold()` | `getGraceThreshold()` | 圣恩容槽安全阈值 |
| `getSafeOutputThreshold()` | `getOfferingThreshold()` | 奉献容槽安全阈值 |

### 12.4 神谕者字段映射

| 工程字段 | 神话字段 | 说明 |
|---------|---------|------|
| `progressRate` | `divineEffort` | 神圣努力 |
| `powerRate` | `pietyLevel` | 虔诚度 |
| `tickPowerInput` | `divineDemand` | 神谕要求 |
| `tickPowerOutput` | `divineOffering` | 神谕奉献 |
| `recipeContext` | `scriptureContext` | 经文上下文 |

### 12.5 神谕者方法映射

| 工程方法 | 神棍方法 | 说明 |
|---------|---------|------|
| `handleRecipeWorking()` | `executeDivineWorkCycle()` | 执行神圣工作循环 |
| `handlePowerStacks()` | `assessBelieverState()` | 评估信徒状态 |
| `checkConditions()` | `verifyDivineDecree()` | 验证神谕 |
| `setupRecipe()` | `interpretScripture()` | 解读经文 |

---

## 13. 与 GTCEu 的兼容

| 组件 | 改动 |
|------|------|
| `GTRecipe.java` | 零改动 |
| `RecipeLogic.java` | 零改动（GoblinOracleOfScripture extends） |
| `RecipeHelper.java` | 零改动（GoblinOracleOfScripture 不走 `RecipeHelper.checkConditions`，但 `verifyDivineDecree()` 内部仍调用原生 `RecipeCondition.check()`） |
| `RecipeRunner.java` | 零改动 |
| `WorkableTieredMachine.java` | 零改动（机器子类覆盖 `createRecipeLogic()`） |
| `CleanroomCondition.java` | 零改动（GoblinOracleOfScripture 走自己的 `verifyDivineDecree()` 统一遍历，原生 CleanroomCondition 仍可正常使用） |
