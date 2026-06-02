# GoblinRecipe 配方系统扩展架构

## 1. 概述

### 1.1 设计目标

对 GTCEu (GregTech CEu Modern) 配方系统进行深度扩展，提供以下核心能力：

1. **配方输出修改器（RecipeOutputModifier）**：在配方产出输出物品/流体时，根据匹配到的输入动态修改输出内容（复制 NBT、trait、food 属性等）。
2. **参数化 RecipeCondition + 失败行为**：继承 GTM 原生的 `RecipeCondition` 体系，通过 `IDivineDecreeOfScripture` 接口扩展失败行为（WAITING / HALT_PROGRESS / INTERRUPT）。
3. **多种配方执行模式（RecipeMode）**：TRANSFORM（物品搬运型）、MODIFY（原地修改型）、AMBIENT（环境提供型）。
4. **OfferingStack/BlessingStack 可变速加工**：信徒机器自管理 `offeringStock`/`blessingStock`，神谕者每 tick 通过 `assessBelieverState()` 计算虔诚度与神圣努力，天然兼容多种能量、降速运行。
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
  └─ OfferingStack/BlessingStack 属性 + offerRate/pourRate/pietyLevel/divineEffort
  └─ receiveOffering() / pourBlessing() / performDivineWork()
```

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
//   getConsumePowerRate()          → getOfferRate()
//   getPushPowerRate()             → getPourRate()
//   updateConsumeRate()            → evaluateOffering()
//   updatePushRate()               → evaluateBlessing()
//   updatePowerRate()              → calculatePietyLevel()
//   updateProgressRate()           → determineDivineEffort()
//   applyPowerStacks()             → performDivineWork()
//   updateConsumePower()           → receiveOffering()
//   updatePushPower()              → pourBlessing()
//   getInputPowerStack()           → getOfferingStock()
//   getOutputPowerStack()          → getBlessingStock()
//   getInputPowerStackSize()       → getOfferingCapacity()
//   getOutputPowerStackSize()      → getBlessingCapacity()
//   getSafeInputThreshold()        → getOfferingThreshold()
//   getSafeOutputThreshold()       → getBlessingThreshold()
```

### 3.1 OfferingStack/BlessingStack 属性

```java
public interface IBelieverOfScripture extends IRecipeLogicMachine {

    // ===== OfferingStack（祭品容槽）容量与状态 =====
    int getOfferingCapacity();
    int getBlessingCapacity();
    int getOfferingStock();
    void setOfferingStock(int value);
    int getBlessingStock();
    void setBlessingStock(int value);

    // ===== 安全阈值 =====
    int getOfferingThreshold();
    int getBlessingThreshold();

    // ===== 速率（由神谕者 assessBelieverState 计算并设置） =====
    float getOfferRate();
    void setOfferRate(float rate);
    float getPourRate();
    void setPourRate(float rate);
    float getPietyLevel();
    void setPietyLevel(float rate);
    int getDivineEffort();
    void setDivineEffort(int rate);

    // ===== 神恩加持的努力（基础工作速率，进度缩放因子） =====
    default int getBlessedEffort() { return 16; }

    // ===== 内部计数器（由 receiveOffering / pourBlessing 设置） =====
    int getOfferingReceived();
    void setOfferingReceived(int value);
    int getBlessingPoured();
    void setBlessingPoured(int value);
}
```

### 3.2 信徒响应方法

```java
// 子类必须覆写。默认给 offeringStock 归零、设置 offeringReceived = 0。
default ActionResult receiveOffering(int tickOfferingInput) {
    setOfferingStock(0);
    setOfferingReceived(0);
    return ActionResult.SUCCESS;
}

// 子类必须覆写。默认填满 blessingStock、设置 blessingPoured = 0。
default ActionResult pourBlessing(int tickBlessingOutput) {
    setBlessingStock(getBlessingCapacity());
    setBlessingPoured(0);
    return ActionResult.SUCCESS;
}

// 以下由神谕者 GoblinOracleOfScripture.assessBelieverState() 调用
default void evaluateOffering(int tickOfferingInput) {
    var result = receiveOffering(tickOfferingInput);
    if (!result.isSuccess()) { setOfferRate(0); return; }
    if (getOfferingStock() < getOfferingThreshold())
        setOfferRate(Math.min(1f, (float) getOfferingReceived() / tickOfferingInput));
    else
        setOfferRate(1f);
}

default void evaluateBlessing(int tickBlessingOutput) {
    var result = pourBlessing(tickBlessingOutput);
    if (!result.isSuccess()) { setPourRate(0); return; }
    if (getBlessingStock() > getBlessingThreshold())
        setPourRate(Math.min(1f, (float) getBlessingPoured() / tickBlessingOutput));
    else
        setPourRate(1f);
}

default void calculatePietyLevel() {
    setPietyLevel(Math.min(getOfferRate(), getPourRate()));
}

default void determineDivineEffort() {
    setDivineEffort((int) (getPietyLevel() * getBlessedEffort()));
}

// 子类必须覆写：实际执行祭品/祝福 充能/放能
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
    TRANSFER,   // 供奉展示型：输入不消耗，输出独立产物（仪式等场景）
}
```

### 4.2 consumeInputs（输入不消耗）

```java
// GoblinScripture 新增字段
public boolean consumeInputs = true;  // 默认 true，与 GTCEu 行为一致
```

当 `consumeInputs = false` 时，`GoblinOracleOfScripture` 在配方完成后**不清空输入槽位**，输入物品原封不动保留。用户下次触发配方时可复用同一套供奉品。

> 与 `RecipeMode.MODIFY` 的区别：`MODIFY` 是"输入不消耗但原地修改"（如物品 NBT 变更），`TRANSFER` 是"输入展示、独立产出"（仪式：供奉钢块展示给神明 → 神明回赐蓝图，钢块不扣）。两者共享"输入不消耗"机制，但语义不同。

### 4.3 fromOracleTemplate（配方自动生成）

`GoblinScripture` 新增一个简洁构造器，6 个空 `Map.of()`（除 tickInputs 外的 tick output + chance）藏进父类参数默认值：

```java
// GoblinScripture.java 新增构造器
public GoblinScripture(GTRecipeType recipeType,
                       Map<RecipeCapability<?>, List<Content>> inputs,
                       Map<RecipeCapability<?>, List<Content>> outputs,
                       Map<RecipeCapability<?>, List<Content>> tickInputs,
                       int duration) {
    super(recipeType, inputs, outputs, tickInputs,
          Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
          List.of(), new CompoundTag(), duration,
          recipeType.getCategory("default"), 0);
}
```

然后 `fromOracleTemplate` 干净多了：

```java
public static GoblinScripture fromOracleTemplate(
        OracleTemplate template, ResourceLocation definitionId) {

    Map<Block, Integer> counts = template.countBlocks();
    int totalBlocks = counts.values().stream().mapToInt(i -> i).sum();

    var scripture = new GoblinScripture(
        GTOracleOfScripture.DEITY_RITUAL,
        counts.entrySet().stream()
            .collect(Collectors.toMap(
                e -> ItemRecipeCapability.CAP,
                e -> List.of(new Content(e.getKey().asItem().getDefaultInstance(), e.getValue()))
            )),
        Map.of(ItemRecipeCapability.CAP, List.of(new Content(
            GoblinShamanItem.getIdFor(definitionId), 1))),
        Map.of(),   // tickInputs 暂空（待 PowerRecipeCapability 方案确定后改为 3 EU/t）
        Math.max(20, totalBlocks * 20));

    scripture.mode = RecipeMode.TRANSFER;
    scripture.consumeInputs = false;
    return scripture;
}
```

> `fromOracleTemplate` 是纯数据映射：遍历 `blockTypes[]` 去重计数 → 构造 `inputs` Map → 产出 1 个指定物品。
>
> **JEI 可见性**：`DEITY_RITUAL` 注册时必须调用 `setXEIVisible(false)`。仪式配方仅用于机器执行，JEI 中不单独开配方页——由多方块 Tier 1 结构预览接替（参幽灵外壳文档 §8.7.16）。

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

## 8. OfferingStack/BlessingStack 可变速加工（神圣评估）

### 8.1 核心流程

```
每 tick: serverTick() → executeDivineWorkCycle()
  │
  └─ assessBelieverState()
       │
       ├─ offeringDemand != 0 ?
       │   └─ believer.evaluateOffering(offeringDemand)
       │       ├─ believer.receiveOffering() → 子类映射祭品 → offeringStock
       │       └─ offeringStock < offeringThreshold ?
       │            offerRate = offeringReceived / offeringDemand (≤1)
       │          : offerRate = 1
       │
       ├─ blessingYield != 0 ?
       │   └─ believer.evaluateBlessing(blessingYield)
       │       ├─ believer.pourBlessing() → 子类映射祝福 → blessingStock
       │       └─ blessingStock > blessingThreshold ?
       │            pourRate = blessingPoured / blessingYield (≤1)
       │          : pourRate = 1
       │
       ├─ believer.calculatePietyLevel() → pietyLevel = min(offerRate, pourRate)
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

    if (offeringDemand != 0) believer.evaluateOffering(offeringDemand);
    else believer.setOfferRate(1f);

    if (blessingYield != 0) believer.evaluateBlessing(blessingYield);
    else believer.setPourRate(1f);

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
// 工程→神话: tickPowerInput → offeringDemand
// 工程→神话: tickPowerOutput → blessingYield
public class GoblinOracleOfScripture extends RecipeLogic {
    protected @Nullable RecipeContext scriptureContext;
    protected int offeringDemand;
    protected int blessingYield;

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
| `interpretScripture()` | `setupRecipe()` | `duration = recipe.duration × blessedEffort`；缓存 `offeringDemand/blessingYield` |
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
| `getConsumePowerRate()` | `getOfferRate()` | 祭品接收速率 |
| `setConsumePowerRate()` | `setOfferRate()` | — |
| `getPushPowerRate()` | `getPourRate()` | 祝福倾泻速率 |
| `setPushPowerRate()` | `setPourRate()` | — |
| `updateConsumeRate()` | `evaluateOffering()` | 评估祭品接收 |
| `updatePushRate()` | `evaluateBlessing()` | 评估祝福倾泻 |
| `updatePowerRate()` | `calculatePietyLevel()` | 计算虔诚度 |
| `updateProgressRate()` | `determineDivineEffort()` | 确定神圣努力 |
| `applyPowerStacks()` | `performDivineWork()` | 执行神圣工作 |
| `updateConsumePower()` | `receiveOffering()` | 接收祭品能量 |
| `updatePushPower()` | `pourBlessing()` | 倾泻祝福能量 |
| `getPowerReceive()` | `getOfferingReceived()` | 已接收祭品 |
| `getPowerPush()` | `getBlessingPoured()` | 已倾泻祝福 |

### 12.3 OfferingStack/BlessingStack 字段映射

| 工程字段 | 神话字段 | 说明 |
|---------|---------|------|
| `getInputPowerStackSize()` | `getOfferingCapacity()` | 祭品容槽容量 |
| `getOutputPowerStackSize()` | `getBlessingCapacity()` | 祝福容槽容量 |
| `getInputPowerStack()` | `getOfferingStock()` | 祭品容槽存量 |
| `getOutputPowerStack()` | `getBlessingStock()` | 祝福容槽存量 |
| `setInputPowerStack()` | `setOfferingStock()` | — |
| `setOutputPowerStack()` | `setBlessingStock()` | — |
| `getSafeInputThreshold()` | `getOfferingThreshold()` | 祭品容槽安全阈值 |
| `getSafeOutputThreshold()` | `getBlessingThreshold()` | 祝福容槽安全阈值 |

### 12.4 神谕者字段映射

| 工程字段 | 神话字段 | 说明 |
|---------|---------|------|
| `progressRate` | `divineEffort` | 神圣努力 |
| `powerRate` | `pietyLevel` | 虔诚度 |
| `tickPowerInput` | `offeringDemand` | 祭品需求 |
| `tickPowerOutput` | `blessingYield` | 祝福产出 |
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
