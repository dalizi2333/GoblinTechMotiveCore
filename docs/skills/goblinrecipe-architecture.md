# GoblinRecipe 配方系统扩展架构

## 1. 概述

### 1.1 设计目标

对 GTCEu (GregTech CEu Modern) 配方系统进行深度扩展，提供以下核心能力：

1. **配方输出修改器（RecipeOutputModifier）**：在配方产出输出物品/流体时，根据匹配到的输入动态修改输出内容（复制 NBT、trait、food 属性等）。
2. **参数化 RecipeCondition + 失败行为**：继承 GTM 原生的 `RecipeCondition` 体系，通过 `IGoblinCondition` 接口扩展失败行为（WAITING / HALT_PROGRESS / INTERRUPT），替代原 Ambient 自定义系统。
3. **多种配方执行模式（RecipeMode）**：TRANSFORM（物品搬运型）、MODIFY（原地修改型）、AMBIENT（环境提供型）。
4. **PowerStack 可变速加工**：机器自管理 `inputPowerStack`/`outputPowerStack`，通过 `handlePowerStacks()` 每 tick 计算 `consumePowerRate`/`pushPowerRate`/`powerRate`/`progressRate`，天然兼容多种能量、降速运行。
5. **可扩展 RecipeCapability**：热量、扭矩等模块特有资源与 Power 同级，统一走 tickInput/tickOutput。

### 1.2 设计原则

- **零 GTCEu 源码改动**：通过继承（`GoblinRecipe extends GTRecipe`、`GoblinRecipeLogic extends RecipeLogic`）扩展。
- **复用原生体系**：Ambient 条件系统废弃，改用 `RecipeCondition` 子类 + `IGoblinCondition` 接口。条件 JSON 格式与 GTM 原生一致。
- **模块解耦**：通用 API 层（goblintech.recipe）不依赖任何具体 mod。集成实现放在 goblintfc、goblintech.create.kinetic 等包中。
- **向后兼容**：GTM 老机器使用原生 `RecipeLogic`，新机器使用 `GoblinRecipeLogic`，互不干扰。

---

## 2. 包名架构

### 2.1 包结构

| 包名 | 层级 | 职责 |
|------|------|------|
| `com.goblincoders.goblintech.recipe.api` | 通用 API | RecipeOutputModifier、RecipeMode、ConditionFailBehavior |
| `com.goblincoders.goblintech.recipe.api.condition` | 通用 API | IGoblinCondition 接口 |
| `com.goblincoders.goblintech.recipe.api.modifier` | 通用 API | RecipeOutputModifier 接口 |
| `com.goblincoders.goblintech.recipe.api.recipe` | 通用 API | RecipeContext |
| `com.goblincoders.goblintech.recipe` | 核心实现 | GoblinRecipe、GoblinRecipeLogic、IGoblinRecipeLogicMachine |
| `com.goblincoders.goblintech.recipe.modifiers` | 通用实现 | 内置通用修改器（CopyComponent、SetComponent 等） |
| `com.goblincoders.goblintech.recipe.conditions` | 通用实现 | 内置 RecipeCondition（MinPowerRate、Cleanliness 等） |
| `com.goblincoders.goblintech.machine.slot` | 机器逻辑 | SlotMode 枚举、可配置槽位接口 |
| `com.goblincoders.goblintfc.recipe` | TFC 集成 | TFC 专用修改器、HeatRecipeCapability、Ingredient 条件 |
| `com.goblincoders.goblintfc.recipe.conditions` | TFC 集成 | TFC 专用条件（Heat、Cold 等） |
| `com.goblincoders.goblintech.create.kinetic.recipe` | Create 集成 | KineticRecipeLogic、RPM/MaxRPM 条件 |
| `com.goblincoders.goblintech.create.kinetic.recipe.conditions` | Create 集成 | RPMCondition、MaxRPMCondition |

### 2.2 继承体系

```
IGoblinRecipeLogicMachine (extends IRecipeLogicMachine)
  └─ PowerStack 属性 + consumePowerRate/pushPowerRate/powerRate/progressRate
  └─ updateConsumePower() / updatePushPower() / applyPowerStacks()

GTRecipe (GTM, 零改动)
  └─ GoblinRecipe (mode + outputModifiers + tickOutputModifiers)
      conditions 字段复用父类 List<RecipeCondition>，不再单独定义 ambientConditions

RecipeLogic (GTM, 零改动)
  └─ GoblinRecipeLogic (handlePowerStacks + checkConditions + runDelay)
      └─ KineticRecipeLogic (标记类)
```

### 2.3 机器接入方式

```java
public class MyGoblinMachine extends WorkableTieredMachine implements IGoblinRecipeLogicMachine {
    @Override
    protected RecipeLogic createRecipeLogic() {
        return new GoblinRecipeLogic(this);
    }
}
```

---

## 3. IGoblinRecipeLogicMachine（机器接口）

### 3.1 PowerStack 属性

```java
public interface IGoblinRecipeLogicMachine extends IRecipeLogicMachine {

    // ===== PowerStack 容量与状态 =====
    int getInputPowerStackSize();        // 输入栈容量，默认 0
    int getOutputPowerStackSize();       // 输出栈容量，默认 0
    int getInputPowerStack();
    void setInputPowerStack(int value);
    int getOutputPowerStack();
    void setOutputPowerStack(int value);

    // ===== 安全阈值 =====
    int getSafeInputThreshold();         // 低于此值时 consumePowerRate < 1
    int getSafeOutputThreshold();        // 高于此值时 pushPowerRate < 1

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

    // ===== 内部计数器 =====
    int powerReceive = 0;  // 本 tick 实际接收量，由 updateConsumePower 设置
    int powerPush = 0;     // 本 tick 实际推送量，由 updatePushPower 设置
}
```

### 3.2 PowerStack 操作方法

```java
// 子类必须覆写。默认返回 FAIL_NO_CAPABILITIES 作为守卫。
ActionResult updateConsumePower(int tickPowerInput);
ActionResult updatePushPower(int tickPowerOutput);

// 以下由 handlePowerStacks 调用，有默认实现：
default void updateConsumeRate(int tickPowerInput) {
    var result = updateConsumePower(tickPowerInput);
    if (!result.isSuccess()) { setConsumePowerRate(0); return; }
    if (getInputPowerStack() < getSafeInputThreshold())
        setConsumePowerRate(Math.min(1f, (float) powerReceive / tickPowerInput));
    else
        setConsumePowerRate(1f);
}

default void updatePushRate(int tickPowerOutput) {
    var result = updatePushPower(tickPowerOutput);
    if (!result.isSuccess()) { setPushPowerRate(0); return; }
    if (getOutputPowerStack() > getSafeOutputThreshold())
        setPushPowerRate(Math.min(1f, (float) powerPush / tickPowerOutput));
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
```

---

## 4. GoblinRecipe（扩展配方）

`GoblinRecipe extends GTRecipe`。**Ambient 系统已废弃**，条件统一使用父类的 `List<RecipeCondition> conditions`。

```java
public class GoblinRecipe extends GTRecipe {
    public RecipeMode mode = RecipeMode.TRANSFORM;
    public List<RecipeOutputModifier> outputModifiers = List.of();
    public List<RecipeOutputModifier> tickOutputModifiers = List.of();
}
```

**tickInput/tickOutput 可含 item/fluid**，但仅在 JEI 等配方查看工具中展示，`GoblinRecipeLogic` 内只处理 Power 类型。物品/流体的 tick IO 期望由机器注册时的配方类型限制来约束，不在 RecipeLogic 层面强制。

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

## 7. 参数化 RecipeCondition + 失败行为

### 7.1 设计

**废弃**原有的 `AmbientType<T>`/`AmbientEntry<T>`/`AmbientCondition` 自定义系统，改用 GTM 原生的 `RecipeCondition` 继承体系。

新增 `IGoblinCondition` 接口，为 `RecipeCondition` 子类提供失败行为：

```java
public interface IGoblinCondition {
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

// 最低处理速度：powerRate ≥ minRate，失败 WAITING（触发熔断退避）
// 用于"仅 0.1 倍速时直接熔断"的场景，而非 HALT_PROGRESS 冻结
public class MinPowerRateCondition extends RecipeCondition<MinPowerRateCondition>
        implements IGoblinCondition {
    float minRate;
    @Override public ConditionFailBehavior getFailBehavior() { return ConditionFailBehavior.WAITING; }
}

// 超净等级：cleanliness ≥ minLevel，失败 WAITING
public class CleanlinessCondition extends RecipeCondition<CleanlinessCondition>
        implements IGoblinCondition {
    int minLevel;
    @Override public ConditionFailBehavior getFailBehavior() { return ConditionFailBehavior.WAITING; }
}
```

```java
// goblintech.create.kinetic.recipe.conditions — Create 动能条件

// 转速下限：rpm ≥ minRPM，失败 HALT_PROGRESS
public class RPMCondition extends RecipeCondition<RPMCondition>
        implements IGoblinCondition {
    float minRPM;
    @Override public ConditionFailBehavior getFailBehavior() { return ConditionFailBehavior.HALT_PROGRESS; }
}

// 转速上限：rpm ≤ maxRPM，失败 INTERRUPT
public class MaxRPMCondition extends RecipeCondition<MaxRPMCondition>
        implements IGoblinCondition {
    float maxRPM;
    @Override public ConditionFailBehavior getFailBehavior() { return ConditionFailBehavior.INTERRUPT; }
}
```

```java
// goblintfc.recipe.conditions — TFC 条件

// 热源：heat ≥ minHeat，失败 WAITING
public class HeatCondition extends RecipeCondition<HeatCondition>
        implements IGoblinCondition {
    float minHeat;
    @Override public ConditionFailBehavior getFailBehavior() { return ConditionFailBehavior.WAITING; }
}

// 冷源：cold ≥ minCold，失败 WAITING
public class ColdCondition extends RecipeCondition<ColdCondition>
        implements IGoblinCondition {
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

## 8. PowerStack 可变速加工

### 8.1 核心流程

```
每 tick: serverTick() → handleRecipeWorking()
  │
  └─ handlePowerStacks()
       │
       ├─ tickPowerInput != 0 ?
       │   └─ gm.updateConsumeRate(tickPowerInput)
       │       ├─ gm.updateConsumePower() → 子类映射 Power → inputPowerStack
       │       └─ inputPowerStack < safeInputThreshold ?
       │            consumePowerRate = powerReceive / tickPowerInput (≤1)
       │          : consumePowerRate = 1
       │
       ├─ tickPowerOutput != 0 ?
       │   └─ gm.updatePushRate(tickPowerOutput)
       │       ├─ gm.updatePushPower() → 子类映射 outputPowerStack → Power
       │       └─ outputPowerStack > safeOutputThreshold ?
       │            pushPowerRate = powerPush / tickPowerOutput (≤1)
       │          : pushPowerRate = 1
       │
       ├─ gm.updatePowerRate() → powerRate = min(consumePowerRate, pushPowerRate)
       ├─ gm.updateProgressRate() → progressRate = powerRate × progressScale
       │
       ├─ checkConditions() → 遍历 GoblinRecipe.conditions
       │   对每个 IGoblinCondition → 按 failBehavior 返回 ActionResult
       │
       ├─ progressRate == 0 ? → PROGRESS_FROZEN
       └─ gm.applyPowerStacks() → 子类实现
```

### 8.2 handleRecipeWorking() 完整逻辑

```java
@Override
public void handleRecipeWorking() {
    assert lastRecipe != null;
    var powerResult = handlePowerStacks();
    if (powerResult.isSuccess()) {
        setStatus(Status.WORKING);
        if (!machine.onWorking()) { this.interruptRecipe(); return; }
        progress += gm().getProgressRate();
        totalContinuousRunningTime++;
    } else if (powerResult == ActionResult.PROGRESS_FROZEN) {
        // HALT_PROGRESS：保持 WORKING，progress 不变
        setStatus(Status.WORKING);
        if (!machine.onWorking()) { this.interruptRecipe(); return; }
        totalContinuousRunningTime++;
    } else {
        // WAITING / INTERRUPT / FAIL
        setWaiting(powerResult.reason());
        ambientSatisfied = false;
        runAttempt++;
        runAttempt = (int) GTMath.clamp(runAttempt, 0, 5);
        if (runAttempt == 5) {
            boolean preventPowerFail = false;
            if (machine instanceof MultiblockControllerMachine) {
                var covers = machine.self().getCoverContainer().getCovers();
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

### 8.3 handlePowerStacks()

```java
protected ActionResult handlePowerStacks() {
    IGoblinRecipeLogicMachine gm = gm();

    if (tickPowerInput != 0) gm.updateConsumeRate(tickPowerInput);
    else gm.setConsumePowerRate(1f);

    if (tickPowerOutput != 0) gm.updatePushRate(tickPowerOutput);
    else gm.setPushPowerRate(1f);

    gm.updatePowerRate();
    gm.updateProgressRate();

    // 检查所有条件（原生 GTM + IGoblinCondition，统一遍历 lastRecipe.conditions）
    var condResult = checkConditions();
    if (!condResult.isSuccess()) return condResult;

    if (gm.getProgressRate() == 0) return ActionResult.PROGRESS_FROZEN;

    return gm.applyPowerStacks();
}
```

### 8.4 checkConditions() — 检查原生 + Goblin 条件

遍历 `lastRecipe.conditions`，同时兼容 GTM 原生 `RecipeCondition`（如老超净间 `CleanroomCondition`）和 Goblin 的 `IGoblinCondition`。**两套超净间系统独立，写配方时不混用即可。**

**默认低倍速熔断保护**：如果配方 `conditions` 中没有显式的 `MinPowerRateCondition`，`checkConditions()` 自动补一个默认值 `minRate = 0.5`。也就是说，没声明最低处理速度的配方，降速到一半以下时也会触发 WAITING + 熔断退避。

```java
protected ActionResult checkConditions() {
    boolean hasMinPowerRate = false;

    for (var condition : lastRecipe.conditions) {
        if (condition.testCondition(lastRecipe, this) == condition.isReverse()) {
            if (condition instanceof IGoblinCondition gc) {
                return switch (gc.getFailBehavior()) {
                    case WAITING -> ActionResult.fail(condition.getTooltips());
                    case HALT_PROGRESS -> ActionResult.PROGRESS_FROZEN;
                    case INTERRUPT -> {
                        interruptRecipe();
                        yield ActionResult.fail(condition.getTooltips());
                    }
                };
            }
            // GTM 原生条件（如 CleanroomCondition、BiomeCondition 等）
            return ActionResult.fail(condition.getTooltips());
        }
        if (condition instanceof MinPowerRateCondition) {
            hasMinPowerRate = true;
        }
    }

    // 没声明 MinPowerRateCondition → 默认 0.5
    if (!hasMinPowerRate && gm().getPowerRate() < 0.5f) {
        return ActionResult.fail(Component.translatable("goblintech.recipe.condition.min_power_rate",
                0.5f));
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

`onRecipeFinish()` 时 `runAttempt = 0; runDelay = 0;`。

### 8.6 三种运行场景

| 场景 | powerRate | progressRate | 返回 | 行为 |
|------|-----------|-------------|------|------|
| 正常 | 1.0 | progressScale | SUCCESS | `progress += progressScale` |
| 降速 | 0.3 | 0.3×scale | SUCCESS | `progress += 0.3×scale`，慢但不停 |
| 冻结 | 0 | 0 | PROGRESS_FROZEN | progress 不变，保持 WORKING |
| 断电 | 0 + fail=WAITING | — | FAIL | runDelay 退避，省 tick |

### 8.7 降速与降耗的分工

| 角色 | 职责 |
|------|------|
| `GoblinRecipeLogic` | 每 tick 调用 `handlePowerStacks()` 计算 `progressRate` |
| `IGoblinRecipeLogicMachine` | `updateConsumePower/updatePushPower` 映射 Power ↔ stack |
| 降速 | `powerRate < 1` → `progressRate` 自动缩小 |
| 降耗 | 机器在 `handleTickRecipeIO()` 重写中按 `powerRate` 缩放 IN 消耗 |

### 8.8 兼容性

| 场景 | 行为 |
|------|------|
| GTM 老机器 | 使用原生 `RecipeLogic`，不受影响 |
| 新机器 | `progress += getProgressRate()`（默认 = progressScale） |
| 动能机器 | `updateConsumePower` 读取 RPM/扭矩 → inputPowerStack |
| 纯电力机器 | `updateConsumePower` 读取 EU → inputPowerStack |

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

### 10.1 GoblinRecipeLogic（extends RecipeLogic）

重写的方法：

| 方法 | 改动 |
|------|------|
| `setupRecipe()` | `duration = recipe.duration × progressScale`；缓存 `tickPowerInput/tickPowerOutput` |
| `handleRecipeWorking()` | 用 `handlePowerStacks()` 替代 `RecipeHelper.checkConditions()` + `handleTickRecipe()` |
| `getProgress()` / `getMaxProgress()` | 对外除以 `progressScale` |
| `onRecipeFinish()` | 保留父类逻辑 + 清理 `recipeContext` |
| `resetRecipeLogic()` | 额外清理 `recipeContext` |

### 10.2 GoblinRecipe（extends GTRecipe）

额外字段：

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `mode` | `TRANSFORM` | 配方执行模式 |
| `outputModifiers` | `List.of()` | 完成时一次性施加的修改器 |
| `tickOutputModifiers` | `List.of()` | 每 tick 施加的修改器 |

---

## 11. 动能集成（goblintech.create.kinetic）

### 11.1 KineticRecipeLogic

标记类，无额外逻辑。RPM → progressRate 的映射由机器在 `updateConsumePower()` 中实现：

```java
public class KineticMillstone extends WorkableTieredMachine implements IGoblinRecipeLogicMachine {
    @Override
    public ActionResult updateConsumePower(int tickPowerInput) {
        // 读取 Create 转速 → 写入 inputPowerStack
        float rpm = Math.abs(getSpeed());
        int power = (int)(rpm / optimalRPM * tickPowerInput);
        setInputPowerStack(power);
        powerReceive = power;
        return ActionResult.SUCCESS;
    }
}
```

### 11.2 RPM 条件

`RPMCondition`（fail=HALT_PROGRESS）和 `MaxRPMCondition`（fail=INTERRUPT）作为 `RecipeCondition` 子类注册。

---

## 12. TFC 集成（goblintfc.recipe）

### 12.1 RecipeOutputModifier

| 修改器 | 说明 |
|--------|------|
| `goblintfc:add_trait` | 施加 TFC FoodTrait |
| `goblintfc:copy_food` | 复制 TFC 食物属性 |
| `goblintfc:copy_input` | 复制输入物品 NBT |

### 12.2 RecipeCapability（热量）

`HeatRecipeCapability` 走 `tickOutputs`，与 Power 同级：

```java
public record HeatRecipeData(float temperature, float heatCapacity) {}
```

机器 handler 调用 TFC API：
```java
IHeat heat = HeatCapability.get(stack);
heat.addTemperatureFromSourceWithHeatCapacity(data.temperature(), data.heatCapacity());
```

### 12.3 环境条件

`HeatCondition`/`ColdCondition` 作为 `RecipeCondition` 子类（fail=WAITING），通过 `IGoblinCondition` 接口声明。

---

## 13. 与 GTCEu 的兼容

| 组件 | 改动 |
|------|------|
| `GTRecipe.java` | 零改动 |
| `RecipeLogic.java` | 零改动（GoblinRecipeLogic extends） |
| `RecipeHelper.java` | 零改动（GoblinRecipeLogic 不走 `RecipeHelper.checkConditions`，但 `checkConditions()` 内部仍调用原生 `RecipeCondition.testCondition()`） |
| `RecipeRunner.java` | 零改动 |
| `WorkableTieredMachine.java` | 零改动（机器子类覆盖 `createRecipeLogic()`） |
| `CleanroomCondition.java` | 零改动（GoblinRecipeLogic 走自己的 `checkConditions()` 统一遍历，原生 CleanroomCondition 仍可正常使用） |
