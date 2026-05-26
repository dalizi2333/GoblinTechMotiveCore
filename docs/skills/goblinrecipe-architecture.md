# GoblinRecipe 配方系统扩展架构



## 1. 概述

### 1.1 设计目标

对 GTCEu (GregTech CEu Modern) 配方系统进行深度扩展，提供以下核心能力：

1. **配方输出修改器（RecipeOutputModifier）**：在配方产出输出物品/流体时，根据匹配到的输入动态修改输出内容（复制 NBT、trait、food 属性等）。
2. **带参数的环境条件系统（AmbientType）**：替代 GTM 现有的二元 CleanroomType 判断，支持数值参数的条件匹配（洁净等级、温度、转速等），并支持三种失败行为（WAITING / HALT_PROGRESS / INTERRUPT）。
3. **多种配方执行模式（RecipeMode）**：TRANSFORM（物品搬运型）、MODIFY（原地修改型）、AMBIENT（环境提供型）。
4. **可变速加工**：`GoblinRecipeLogic` 子类通过 `progressRate`（PROGRESS_SCALE=16）和 `ambientSatisfied` 实现变速与 HALT_PROGRESS，支持转速影响加工速度、降速运行等。
5. **可扩展 RecipeCapability**：热量、扭矩等模块特有资源与 EU 同级，统一走 tickInput/tickOutput。

### 1.2 背景与动机

- **TFC 兼容**：TerraFirmaCraft 的配方有 ItemStackModifier 机制（copy_input、add_trait、copy_food、dye_leather 等），需要在 GTCEu 中支持。
- **Create 兼容**：Create 的转速影响加工速度，扭矩/转速可作为 tick input/output；带 NBT 的流体（如 `create:potion`）需保留属性。
- **机器行为扩展**：冰箱/提取机/真空冷冻机等需"原地修改物品属性"，超净间需数值等级 + 自然衰减。
- **模块化扩展**：供电不足降速、Mekanism 热量、Botania mana 等未来联动都通过同一套抽象接入。

### 1.3 设计原则

- **零 GTCEu 源码改动**：通过继承（`GoblinRecipe extends GTRecipe`、`GoblinRecipeLogic extends RecipeLogic`）扩展，不修改任何 GTM 原有文件。
- **模块解耦**：通用 API 层（goblintech.recipe）不依赖任何具体 mod。集成实现放在 goblintfc、goblinkinetic 等包中。
- **可剥离**：未来可将 goblinrecipe、goblintfc 等独立为附属模组。
- **向后兼容**：GTM 老机器使用原生 `RecipeLogic`，新机器使用 `GoblinRecipeLogic`，互不干扰。现有配方 JSON 全部兼容。

---

## 2. 包名架构

### 2.1 新增包

| 包名 | 层级 | 职责 |
|------|------|------|
| `com.goblincoders.goblintech.recipe.api` | 通用 API | RecipeOutputModifier、RecipeMode、ConditionFailBehavior |
| `com.goblincoders.goblintech.recipe.api.ambient` | 通用 API | AmbientType\<T\>、AmbientEntry\<T\>、AmbientCondition |
| `com.goblincoders.goblintech.recipe.api.modifier` | 通用 API | RecipeOutputModifier 接口 |
| `com.goblincoders.goblintech.recipe.api.recipe` | 通用 API | RecipeContext |
| `com.goblincoders.goblintech.recipe` | 核心实现 | GoblinRecipe（ext GTRecipe）、GoblinRecipeLogic（ext RecipeLogic） |
| `com.goblincoders.goblintech.recipe.modifiers` | 通用实现 | 内置通用修改器（CopyComponent、SetComponent 等） |
| `com.goblincoders.goblintech.recipe.ambient` | 基础实现 | 基础环境层：仅 CLEANROOM（静态等级） |
| `com.goblincoders.goblintech.machine.slot` | 机器逻辑 | SlotMode 枚举、可配置槽位接口 |
| `com.goblincoders.goblintfc.recipe` | TFC 集成 | TFC 专用修改器、HeatRecipeCapability、Ingredient 条件 |
| `com.goblincoders.goblintfc.recipe.ambient` | TFC 进阶 | TFC 加载时替换基础环境：CLEANLINESS（衰减）+ HEAT + COLD |
| `com.goblincoders.goblinkinetic.recipe` | Create 集成 | KineticRecipeLogic（ext GoblinRecipeLogic）、RPM ambient conditions |

### 2.2 GTCEu 原有文件的改动

**零改动。** 所有扩展通过继承实现：

| GTCEu 文件 | 替代方案 |
|---|---|
| `GTRecipe.java` | `GoblinRecipe extends GTRecipe` — 新增 mode、ambientConditions、outputModifiers 字段 |
| `RecipeLogic.java` | `GoblinRecipeLogic extends RecipeLogic` — 新增 PROGRESS_SCALE、progressRate、ambientSatisfied、recipeContext |
| `WorkableTieredMachine.java` | 新机器子类重写 `createRecipeLogic()` 返回 `GoblinRecipeLogic` |
| `RecipeRunner.java` | `GoblinRecipeLogic.handleRecipeWorking()` 内自分发 recipeContext |
| `GTRecipeSerializer.java` | GoblinRecipe 序列化待后续补充 |
| `GTRecipeBuilder.java` | 新 Builder 类（待实现） |

### 2.3 继承体系

```
GTRecipe (GTM, 零改动)
  └─ GoblinRecipe (mode + ambientConditions + outputModifiers)

RecipeLogic (GTM, 零改动)
  └─ GoblinRecipeLogic (PROGRESS_SCALE=16 + progressRate + ambientSatisfied + recipeContext)
      └─ KineticRecipeLogic (updateFromRPM → progressRate映射)
```

### 2.4 机器接入方式

`WorkableTieredMachine` 中用 `new RecipeLogic(this)` 硬编码创建。新机器子类覆盖 `createRecipeLogic()`：

```java
public class MyGoblinMachine extends WorkableTieredMachine {
    @Override
    protected RecipeLogic createRecipeLogic() {
        return new GoblinRecipeLogic(this);
    }
}
```

动能机器：

```java
public class KineticMillstone extends MyGoblinMachine {
    private final KineticRecipeLogic logic;

    public KineticMillstone(...) {
        this.logic = (KineticRecipeLogic) createRecipeLogic();
    }

    @Override
    protected RecipeLogic createRecipeLogic() {
        return new KineticRecipeLogic(this);
    }

    // 在自己的 tick 订阅中更新 progressRate
    void kineticTick() {
        logic.updateFromRPM(getSpeed());
    }
}
```

---

## 3. GoblinRecipe（扩展配方）

`GoblinRecipe extends GTRecipe`，新增字段（全部有默认值，不影响 GTRecipe 原有行为）：

```java
public RecipeMode mode = RecipeMode.TRANSFORM;
public Map<ResourceLocation, AmbientEntry<?>> ambientConditions = Map.of();
public List<RecipeOutputModifier> outputModifiers = List.of();
public List<RecipeOutputModifier> tickOutputModifiers = List.of();
```

### 3.1 RecipeMode（配方执行模式）

```java
package com.goblincoders.goblintech.recipe.api;

public enum RecipeMode {
    /** 转换型（默认，与现有 GTCEu 行为一致） */
    TRANSFORM,

    /** 原地修改型：输入 == 输出（同一物品引用，属性被修改，不搬运） */
    MODIFY,

    /** 环境提供型：不处理物品 IO，只更新 AmbientProviderTrait 参数 */
    AMBIENT
}
```

`"mode"` 在配方 JSON 中为可选字段（默认 `"transform"`）：
```java
Codec.STRING.optionalFieldOf("mode", "transform")
    .xmap(RecipeMode::valueOf, RecipeMode::name)
```
GTCEu 老配方无需添加此字段，行为完全不变。

### 3.2 TRANSFORM 模式（现有逻辑）

- IN 消耗 → 进度 → OUT 产出
- 物品在槽位之间搬运
- 适用于：电弧炉、组装机、真空冷冻机（独立配方体系）

### 3.3 MODIFY 模式（新增）

**核心行为**：
- 不消耗输入物品（不调用 `handleRecipeIO(IN)`）
- 配方执行过程中物品留在原槽位不动
- 完成后对原物品引用原地应用 `outputModifiers`
- 仍消耗 `tickInputs`（电力等）

**防止重复施加**：
- 每个 modifier 自身做幂等性检查（如 `FoodCapability.applyTrait` 内部已有 `!food.hasTrait(trait)`）
- 配方输入端通过 Ingredient 过滤已施加过 modifier 的物品（如 `goblintfc:not_has_trait`）
- MODIFY 配方完成后，`checkRecipe()` 若不再匹配则 RecipeLogic 进入 IDLE

**配方 JSON 示例**：
```json
{
  "type": "gtceu:fridge",
  "mode": "modify",
  "inputs": {
    "item": {
      "type": "neoforge:all_of",
      "children": [
        { "tag": "c:foods" },
        { "type": "goblintfc:not_has_trait", "trait": "tfc:preserved" }
      ]
    }
  },
  "tickInputs": { "EU": 8 },
  "outputModifiers": [
    { "type": "goblintfc:add_trait", "trait": "tfc:preserved" }
  ],
  "duration": 200
}
```

### 3.4 AMBIENT 模式（新增）

- 物品/流体 IO 全部跳过
- 仅运行 tickInputs 消耗（如电力）
- 执行期间通过 AmbientProviderTrait 向周围广播环境参数
- 完成时不产生物品输出
- 适用于：冷库、窑炉、超净间

---

## 4. RecipeOutputModifier（输出修改器）

> **作用域**：专门处理 TFC 兼容的"物品/流体属性修改"——`copy_input`、`copy_food`、`add_trait`、`dye_leather` 等。热量升降、扭矩这些资源类的操作走 `RecipeCapability`（tickInput/tickOutput），与 EU 同级。

### 4.1 接口定义

```java
package com.goblincoders.goblintech.recipe.api;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import java.util.Set;

public interface RecipeOutputModifier {

    Object apply(RecipeCapability<?> cap, Object output, RecipeContext ctx);

    Set<RecipeCapability<?>> targetCapabilities();

    default boolean dependsOnInput() { return false; }

    RecipeOutputModifierType<?> type();
}
```

### 4.2 RecipeOutputModifierType（类型注册）

```java
public record RecipeOutputModifierType<T extends RecipeOutputModifier>(
    ResourceLocation id,
    MapCodec<T> codec,
    StreamCodec<RegistryFriendlyByteBuf, T> streamCodec
) {}
```

```java
public class GoblinRecipeModifiers {
    public static final ResourceKey<Registry<RecipeOutputModifierType<?>>> KEY =
        ResourceKey.createRegistryKey(RL("goblinrecipe:output_modifier_type"));

    public static final Registry<RecipeOutputModifierType<?>> REGISTRY =
        new RegistryBuilder<>(KEY).create();
}
```

### 4.3 两类修改器：outputModifiers vs tickOutputModifiers

| 字段 | 触发时机 | 触发频率 | 典型用途 |
|------|---------|---------|---------|
| `outputModifiers` | `onRecipeFinish()` | 每次配方完成一次 | 加 trait、复制 food 属性、复制 NBT、染色 |
| `tickOutputModifiers` | 每 tick 的 `handleRecipeIO(OUT)` | 每 tick | 流体颜色渐变、持续施加 trait |

**示例：冰箱保鲜施加 trait**：
```json
{
  "mode": "modify",
  "outputModifiers": [
    { "type": "goblintfc:add_trait", "trait": "tfc:preserved" }
  ],
  "duration": 200
}
```

### 4.4 内置通用修改器（goblintech.recipe.modifiers）

TFC 兼容的属性复制类修改器：

| 修改器 ID | 说明 | 依赖 |
|-----------|------|------|
| `goblinrecipe:copy_components` | 复制所有 DataComponents | 无 |
| `goblinrecipe:copy_component` | 复制指定 DataComponent | 无 |
| `goblinrecipe:set_component` | 设置/覆盖 DataComponent | 无 |

### 4.5 序列化格式

```json
{
  "outputModifiers": [
    { "type": "goblinrecipe:copy_components" },
    { "type": "goblintfc:add_trait", "trait": "tfc:preserved" }
  ]
}
```

Codec 实现：
```java
RecordCodecBuilder.<GTRecipe>create(instance -> instance.group(
    // ... 现有字段 ...
    Codec.STRING.optionalFieldOf("mode", "transform")
        .xmap(RecipeMode::valueOf, RecipeMode::name)
        .forGetter(r -> r.mode.name().toLowerCase()),
    Codec.list(GoblinRecipeModifiers.REGISTRY.byNameCodec().dispatch(
        RecipeOutputModifier::type, RecipeOutputModifierType::codec
    )).optionalFieldOf("outputModifiers", List.of())
        .forGetter(r -> r.outputModifiers),
).apply(instance, GTRecipe::new))
```

---

## 5. RecipeContext（配方上下文）

### 5.1 核心定义

```java
package com.goblincoders.goblintech.recipe.api;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;

import java.util.*;

public class RecipeContext {

    public final GTRecipe recipe;
    public final IRecipeCapabilityHolder holder;

    private final Map<RecipeCapability<?>, List<Object>> capturedInputs = new HashMap<>();
    private final Map<RecipeCapability<?>, List<Object>> capturedTickInputs = new HashMap<>();
    private final Map<String, Object> data = new HashMap<>();

    public enum Phase { MATCHING, CONSUMING, PROCESSING, PRODUCING, FINISHING }
    private Phase phase = Phase.MATCHING;
}
```

### 5.2 字段用途说明

| 字段 | 存活周期 | 用途 |
|------|---------|------|
| `capturedInputs` | 单次配方周期 | TFC `copy_food` / `copy_input` 等修改器读取原始输入属性 |
| `capturedTickInputs` | 单次配方周期 | tick 修改器读取每 tick 的输入变化 |
| `data` | 单次配方周期 | 修改器之间链式协作、传递中间结果 |
| `phase` | 单次配方周期 | 修改器根据阶段调整行为 |

### 5.3 关键方法

```java
public <T> Optional<T> getFirstCapturedInput(RecipeCapability<T> cap) { ... }
public <T> List<T> getCapturedInputs(RecipeCapability<T> cap) { ... }
public <T> T getData(String key, Class<T> type) { ... }
public void setData(String key, Object value) { ... }
public void setPhase(Phase phase) { this.phase = phase; }
public boolean isPhase(Phase phase) { return this.phase == phase; }
void applyOutputModifiers(GTRecipe recipe, Map<RecipeCapability<?>, List<Object>> outputs) { ... }
```

### 5.4 生命周期

```
RecipeLogic.findAndHandleRecipe()
    ↓ 创建 RecipeContext
RecipeLogic.setupRecipe()
    ↓ TRANSFORM：快照消耗前的输入
    ↓ MODIFY：不消耗，HOLD 槽物品引用写入 context
每 tick: handleTickRecipe(IO.IN/OUT) → tickOutputModifiers 每 tick 应用
RecipeLogic.onRecipeFinish()
    ↓ 应用 outputModifiers → recipeContext = null
```

### 5.5 MODIFY 模式的 input capture

```java
public void captureHoldingInputs() {
    for (var slot : machine.getSlots(SlotMode.HOLD)) {
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty()) {
            capturedInputs.computeIfAbsent(ItemRecipeCapability.CAP, k -> new ArrayList<>())
                .add(stack.copy());
        }
    }
}
```

---

## 6. Ambient 参数化环境条件系统

### 6.1 核心动机

GTM 现有的 `CleanroomType` 是纯标签匹配（二元判断），无法表达带参数的条件。本系统用泛型参数化替代标签匹配，使得任何"环境条件"都可以携带数值参数。

> **基础 vs 进阶**：
> - **goblintech.recipe（基础）**：仅提供 CLEANROOM 环境类型（静态整数等级，无衰减）。HEAT/COLD 在基础版中**不作为环境条件**——真空冷冻机等使用 GTM 原有 TRANSFORM 模式（热锭 → 普通锭），无需环境参数。
> - **goblintfc（进阶）**：TFC 加载后，额外注册带衰减的超净程度、带参数的 HEAT/COLD 环境类型。提取机、冰箱等 MODIFY 模式的机器依赖这些进阶环境条件。
> - **goblinkinetic（动能）**：Create 加载后注册 RPM 环境条件，转速不足时 HALT_PROGRESS，超上限时 INTERRUPT。

### 6.2 ConditionFailBehavior（条件失败行为）

配方执行过程中，GTM 每 tick 都会重新检查条件（见 §9.2 RecipeLogic 改动）。旧 GTM 对所有条件失败都执行 `setWaiting()` + `regressRecipe()`，但不同条件应允许不同的失败行为：

```java
package com.goblincoders.goblintech.recipe.api.ambient;

public enum ConditionFailBehavior {
    /** 进入 WAITING 状态，触发 regressRecipe()（进度可能回退）。旧 GTM 行为。
     *  适用于清洁室、排气等"硬条件"——条件不满足意味着机器出问题了。 */
    WAITING,

    /** 进度暂停但不改变状态（保持 WORKING），progress 不增不减。
     *  适用于 RPM、环境温度等"可变条件"——可能因外部因素波动而自动恢复。 */
    HALT_PROGRESS,

    /** 立即中断配方。适用于安全阀类条件。
     *  如"转速超过 4096 则紧急停止"。 */
    INTERRUPT
}
```

**执行语义**：
- `WAITING`：条件失败 → `setWaiting()` → `regressRecipe()` → 进度回退 → 恢复后需重新加速
- `HALT_PROGRESS`：条件失败 → 状态保持 WORKING → `progress` 不变 → 恢复后无缝继续
- `INTERRUPT`：条件失败 → 中断配方 → 物品/流体在槽位中不变

**与 `ambientSatisfied` 的对接**：条件失败行为为 `HALT_PROGRESS` 时，`GoblinRecipeLogic` 设置 `ambientSatisfied = false`，子类检查后设 `progressRate = 0`（进度冻结，tick IO 仍执行）。

### 6.3 AmbientType\<T\>（环境类型注册项）

```java
public record AmbientType<T>(
    ResourceLocation id,
    Codec<T> parameterCodec,
    StreamCodec<RegistryFriendlyByteBuf, T> streamCodec,

    /** provider 参数是否满足 recipe 要求。prov=提供的，req=配方要求的 */
    BiFunction<T, T, Boolean> satisfies,

    /** 多个提供方提供同类型环境时如何合并。如多个热源取最高温 */
    BinaryOperator<T> merge,

    /** 每 tick 的自然衰减函数（nullable = 不衰减） */
    @Nullable BiFunction<T, Long, T> decayPerTick,

    /** 配方执行中条件不满足时的默认行为 */
    ConditionFailBehavior defaultFailBehavior
) {}
```

### 6.4 内置环境类型注册

```java
// goblinrecipe.ambient.GoblinAmbientTypes（基础环境类型）

public class GoblinAmbientTypes {

    public static final AmbientType<Integer> CLEANROOM = register(
        "goblintech:ambient_cleanroom",
        Codec.INT, ByteBufCodecs.INT,
        (prov, req) -> prov >= req,
        Math::max,
        null,
        ConditionFailBehavior.WAITING
    );
}
```

```java
// goblintfc.recipe.ambient.TfcAmbientTypes（进阶环境类型，TFC 加载后注册）

public class TfcAmbientTypes {

    // 超净程度（衰减由 AmbientProviderTrait 内部 ghost ItemStack + TFC HeatComponent 处理）
    public static final AmbientType<Float> CLEANLINESS = register(
        "goblintfc:ambient_cleanliness",
        Codec.FLOAT, ByteBufCodecs.FLOAT,
        (prov, req) -> prov >= req,
        Math::max,
        null,  // 衰减不在此处理，见 §6.7 假物品方案
        ConditionFailBehavior.WAITING
    );

    public static final AmbientType<Float> HEAT = register(
        "goblintfc:ambient_heat",
        Codec.FLOAT, ByteBufCodecs.FLOAT,
        (prov, req) -> prov >= req,
        Math::max,
        null,  // 温度不自然衰减，由热源机器维持
        ConditionFailBehavior.WAITING
    );

    public static final AmbientType<Float> COLD = register(
        "goblintfc:ambient_cold",
        Codec.FLOAT, ByteBufCodecs.FLOAT,
        (prov, req) -> prov >= req,
        Math::max,
        null,  // 制冷能力不自然衰减，由冷源机器维持
        ConditionFailBehavior.WAITING
    );
}
```

```java
// goblinkinetic.recipe.ambient.KineticAmbientTypes（动能环境类型）

public class KineticAmbientTypes {
    public static final AmbientType<Float> RPM = register(
        "goblinkinetic:ambient_rpm",
        Codec.FLOAT, ByteBufCodecs.FLOAT,
        (provided, required) -> provided >= required,
        Math::max,
        null,
        ConditionFailBehavior.HALT_PROGRESS
    );

    /** 超限 = 安全风险（离心机飞散），INTERRUPT 立即中断配方。 */
    public static final AmbientType<Float> MAX_RPM = register(
        "goblinkinetic:ambient_max_rpm",
        Codec.FLOAT, ByteBufCodecs.FLOAT,
        (provided, required) -> provided <= required,
        Math::min,
        null,
        ConditionFailBehavior.INTERRUPT
    );
}
```

**RPM 为什么用 `HALT_PROGRESS` 而不是 `WAITING`**：RPM 是可恢复的波动（风速变化、水车卡了一下），不应触发 WAITING 的进度回退惩罚。转速恢复后配方无缝继续。`ambient_max_rpm` 则不同——超限意味着安全风险，必须 INTERRUPT。

### 6.5 AmbientEntry / ProviderTrait / ReceiverTrait / AmbientCondition

```java
/** 不可变的环境条目：类型 + 参数值。不提供硬编码工厂——调用方直接 new AmbientEntry<>(type, value)。 */
public record AmbientEntry<T>(AmbientType<T> type, T parameter) {}
```

```java
// AmbientProviderTrait — 提供方 trait（新超净间、冷库、窑炉等）
// 与旧的 CleanroomProviderTrait 是独立的两套系统
public class AmbientProviderTrait extends MachineTrait {
    private final Map<AmbientType<?>, AmbientEntry<?>> entries = new HashMap<>();
    @Getter @Setter private boolean isActive;

    public <T> void setEntry(AmbientType<T> type, T parameter) { ... }
    public <T> @Nullable T getParameter(AmbientType<T> type) { ... }
    public Collection<AmbientEntry<?>> getEntries() { ... }
    public void tickDecay() { /* 应用衰减 */ }
}
```

```java
// AmbientReceiverTrait — 接收方 trait
// 与旧的 CleanroomReceiverTrait 是独立的两套系统
public class AmbientReceiverTrait extends MachineTrait {
    @Setter private AmbientProviderTrait provider;

    public <T> boolean satisfies(AmbientType<T> type, T required) {
        if (provider == null || !provider.isActive()) return false;
        T provided = provider.getParameter(type);
        return provided != null && type.satisfies().apply(provided, required);
    }
}
```

```java
// AmbientCondition — 配方条件（旧 CleanroomCondition 保留不动）
public class AmbientCondition extends RecipeCondition<AmbientCondition> {
    private final Map<AmbientType<?>, Object> requirements;

    @Override
    protected boolean testCondition(GTRecipe recipe, RecipeLogic logic) {
        AmbientReceiverTrait receiver = logic.getMachine()
            .getTraitHolder().getTrait(AmbientReceiverTrait.TYPE);
        if (receiver == null) return false;
        for (var req : requirements.entrySet()) {
            if (!check(receiver, req.getKey(), req.getValue())) return false;
        }
        return true;
    }
}
```

配方 JSON 示例：
```json
{
  "ambientConditions": {
    "goblintfc:ambient_heat": 1600.0,
    "goblintech:ambient_cleanroom": 3
  }
}
```

### 6.6 三种环境的分层

| 环境类型 | goblinrecipe（基础） | goblintfc（TFC 进阶） | goblinkinetic（动能） |
|---------|---------------------|----------------------|---------------------|
| **Cleanroom** | `CLEANROOM`：静态整数，无衰减 | `CLEANLINESS`：float + 衰减 | — |
| **HEAT** | 无（用 GTM TRANSFORM） | `HEAT`：float，多源取 max | — |
| **COLD** | 无（用 GTM TRANSFORM） | `COLD`：float，多源取 max | — |
| **RPM** | — | — | `RPM`：浮动范围检查 |
| **MAX_RPM** | — | — | `MAX_RPM`：安全上限 |

旧超净间（GTM 原有的 `CleanroomType`/`CleanroomCondition`/`CleanroomProviderTrait`）**完全不改动**。本项目的"新超净间"是一台**全新的机器**，使用 `AmbientType`/`AmbientCondition`/`AmbientProviderTrait`，与旧系统零耦合。

### 6.7 假物品方案（超净间衰减）

**核心思路**：在 `AmbientProviderTrait` 内部维护一个"幽灵 ItemStack"——一个永不渲染、永不暴露给玩家的标记物品，挂上 TFC 的 `HeatComponent`。超净程度的衰减完全外包给 TFC 热力系统。

```
概念映射：
  cleanliness   ←→ HeatComponent.temperature
  retentionCapacity ←→ HeatComponent.heatCapacity  
  dirtRate       ←→ TFCConfig.itemCoolingModifier
```

| 我们想做的 | TFC 已提供 | 我们只需 |
|----------|-----------|---------|
| 值随时间衰减 | `HeatComponent.getTemperature()` 调用 `adjustTemp()` | 读取 |
| 自然衰减速率配置 | `TFCConfig.SERVER.itemCoolingModifier` | 复用 |
| 升温/充电 | `HeatCapability.setTemperature()` / `addTemp()` | 写入 |
| 多源叠加 | `IHeat.addTemperatureFromSourceWithHeatCapacity()` | 调用 |
| 序列化/持久化 | `HeatComponent.CODEC` | 自动 |

```java
public class CleanroomMarkerItem extends Item {
    public CleanroomMarkerItem() { super(new Properties().stacksTo(1)); }
}

// AmbientProviderTrait 中：
public class AmbientProviderTrait extends MachineTrait {
    private ItemStack ghostStack;

    public AmbientProviderTrait(Machine machine) {
        super(machine);
        ghostStack = new ItemStack(GoblinItems.CLEANROOM_MARKER.get());
        ghostStack.set(TFCComponents.HEAT, HeatComponent.of(retentionCapacity, 0f));
    }

    public float getCleanliness() {
        return HeatCapability.getTemperature(ghostStack); // 自动计算自然衰减
    }

    public void rechargeCleanliness(float amount) {
        IHeat heat = HeatCapability.get(ghostStack);
        if (heat != null)
            heat.addTemperatureFromSourceWithHeatCapacity(amount, 1.0f);
    }
}
```

**效果**：
- 超净程度在 TFC 日历时间推进中**自动衰减**，无需任何 tick 逻辑
- 衰减速率由 `TFCConfig.SERVER.itemCoolingModifier` 控制
- 多台超净间 → `addTemperatureFromSourceWithHeatCapacity()` 加权平均叠加
- 序列化完全由 `HeatComponent.CODEC` 完成，存档兼容性好

#### HEAT / COLD 温度变化（直接调用 TFC 热力 API）

TFC 热力系统核心 API：
```
自然衰减: temp - (ticksSinceUpdate * itemCoolingModifier) / heatCapacity
升温:     initialTemp + modifier / heatCapacity
热传导:   (当前温度 × 当前热容 + 源温度 × 源热容) / (当前热容 + 源热容)
```

物品只要被 TFC 的 `HeatComponent` 管理，**自动随世界时间自然降温**。我们只需在需要时主动调 `IHeat.setTemperature()` 或 `addTemperatureFromSourceWithHeatCapacity()`。AMBIENT 模式的冷库/窑炉通过 `AmbientProviderTrait` 提供 `COLD`/`HEAT` 参数，内部的 MODIFY 机器读取参数后调用 TFC 的 API 即可。

### 6.8 新超净间设计

- 消耗电力，执行配方调用 `rechargeCleanliness(amount)` 提升超净程度
- 超净程度稳定值 = 产出率 × heatCapacity / decayRate
- 配方 JSON：
```json
{
  "type": "gtceu:cleanroom",
  "mode": "ambient",
  "tickInputs": { "EU": 32 },
  "outputModifiers": [
    { "type": "goblinrecipe:set_ambient", "ambient": "goblintfc:ambient_cleanliness", "value": 10.0 }
  ],
  "duration": -1
}
```

### 6.9 配方条件对比

旧配方（老超净间，不改）：
```json
{ "type": "gtceu:cleanroom_condition", "cleanroom": "cleanroom" }
```

新配方（goblintfc 整合包）：
```json
{ "ambientConditions": { "goblintfc:ambient_cleanliness": 15.0 } }
```

新配方（无 TFC 的包，用 goblintech 主包的静态等级）：
```json
{ "ambientConditions": { "goblintech:ambient_cleanroom": 3 } }
```

### 6.10 goblinrecipe 基础实现（GoblinAmbientTypes）

`com.goblincoders.goblintech.recipe.ambient` 提供基础环境层。仅包含 CLEANROOM（静态整数等级，无衰减）。HEAT/COLD 在基础版中通过 GTM 原有 TRANSFORM 模式实现。当前因尚未实现子包加载检测，代码注释保留：

```java
// goblinrecipe.ambient.GoblinAmbientTypes — 待子包检测就绪后启用
//
// public class GoblinAmbientTypes {
//     public static final AmbientType<Integer> CLEANROOM = register(
//         "goblintech:ambient_cleanroom",
//         Codec.INT, ByteBufCodecs.INT,
//         (prov, req) -> prov >= req,
//         Math::max, null,
//         ConditionFailBehavior.WAITING
//     );
// }
//
// 后续启用条件：
// if (ModList.get().isLoaded("tfc")) { TfcAmbientTypes } else { GoblinAmbientTypes }
```

基础版冷热处理（无 TFC，无需 AmbientCondition，纯 TRANSFORM）：
```json
{
  "type": "gtceu:vacuum_freezer",
  "inputs": { "item": "gtceu:hot_iron_ingot" },
  "outputs": { "item": "minecraft:iron_ingot" },
  "duration": 100
}
```

---

## 7. 可变速加工（PROGRESS_SCALE + progressRate）

### 7.1 动机

原有 `progress++` 只有恒速。Create 的转速动态影响加工速度，供电不足也应能降速运行而非停机。

### 7.2 设计

`GoblinRecipeLogic extends RecipeLogic`，`progressRate` 是机器的每 tick 可变属性。

**纯整数**：`PROGRESS_SCALE = 16`。配方 `duration × 16`，`progress += progressRate`。默认 `progressRate = 16` → 与 `progress += 1` 等效。无需 `float`、无需 `computeProgressRate()` 回调。

```java
// 对比
旧: duration = 200,   progress += 1    → 200 ticks
新: duration = 3200,  progress += 16   → 200 ticks（等效）

RPM 0.5× → progressRate=8   → 50% 速度
RPM 2.0× → progressRate=32  → 200% 速度
```

**对外 getter 自动除以 scale**，GUI 剩余时间 / 进度条百分比不受影响：

```java
@Override public int getProgress()    { return progress / PROGRESS_SCALE; }
@Override public int getMaxProgress()  { return duration / PROGRESS_SCALE; }
// getProgressPercent() = progress / duration — 1600/3200 = 50%，天然正确
```

**HALT_PROGRESS**：`ambientSatisfied = false` → 子类将 `progressRate` 设为 0。进度冻结，tick IO 仍正常执行。

### 7.3 GoblinRecipeLogic 核心字段

```java
public class GoblinRecipeLogic extends RecipeLogic {
    public static final int PROGRESS_SCALE = 16;
    protected int progressRate = PROGRESS_SCALE;
    protected @Nullable RecipeContext recipeContext;
    protected boolean ambientSatisfied = true;
}
```

### 7.4 速度倍率是机器的唯一物理属性

不在 RecipeLogic 中提供 Provider 链、不分拆为多个独立乘数。降耗（tick input 消耗缩放）由机器子类在 `handleTickRecipeIO()` 中自行处理，RecipeLogic / RecipeRunner 不介入。单一个 int 进度量，没有 ambiguity。

**动能机器不降耗**——扭矩占传动轴本身就是对能量网络的负载，再缩放消耗量反而不合理。

### 7.5 KineticRecipeLogic（RPM → progressRate）

```java
public class KineticRecipeLogic extends GoblinRecipeLogic {
    protected float optimalRPM = 16f;

    /** 由动能机器的 tick 订阅调用 */
    public void updateFromRPM(float currentRPM) {
        if (!ambientSatisfied) { this.progressRate = 0; return; }
        if (optimalRPM == 0f)   { this.progressRate = PROGRESS_SCALE; return; }
        float absRPM = Math.abs(currentRPM);
        if (absRPM == 0f)       { this.progressRate = 0; return; }
        this.progressRate = (int)(absRPM / optimalRPM * PROGRESS_SCALE);
    }
}
```

不设上限——`rpm = optimalRPM × 10` → `progressRate = 160`。实际上限由 Create 应力网络约束。

### 7.6 带降速的 EU 机器（示例）

```java
public class DeredatingMaceratorLogic extends GoblinRecipeLogic {
    private final TickInputTracker tracker = new TickInputTracker(20); // 20tick 窗口

    void updateDeration() {
        if (!ambientSatisfied) { progressRate = 0; return; }
        float trend = tracker.getOverallRate();
        progressRate = (int)Math.clamp(trend * PROGRESS_SCALE, 1, PROGRESS_SCALE);
    }

    // 降耗：重写 handleTickRecipeIO，对 IN 阶段按 progressRate / PROGRESS_SCALE 缩放
}

### 7.7 与现有行为的兼容

| 场景 | 旧 GTCEu 行为 | 新行为 |
|------|-------------|-------|
| GTM 老机器 | `progress++`（RecipeLogic） | 不受影响——使用原生 RecipeLogic |
| 新机器 | 无 | `progress += 16`（GoblinRecipeLogic 默认） |
| RPM 超速 | 无 | `progressRate` = 8..160..320，可 >16 |
| HALT_PROGRESS | 无 | `ambientSatisfied=false` → `progressRate=0` |
| 降速运行 | 进 WAITING 停机 | 子类维持 WORKING，设降速 progressRate |

---



## 8. 槽位语义（SlotMode）

### 8.1 为什么需要

GTCEu 现有槽位仅分输入/输出。MODIFY 模式要求物品**原地不动**，需要一种新的槽位语义。

### 8.2 SlotMode 枚举

```java
package com.goblincoders.goblintech.machine.slot;

import com.gregtechceu.gtceu.api.capability.recipe.IO;

public enum SlotMode {
    INPUT_ONLY(IO.IN),
    OUTPUT_ONLY(IO.OUT),
    BOTH(IO.BOTH),          // 缓冲器默认
    HOLD(IO.IN),            // MODIFY 专用——参与 IN 匹配但不被消耗
    SPECIAL_FLUID(IO.BOTH); // MEK 风格的独立交互面

    public final IO defaultIO;
    SlotMode(IO defaultIO) { this.defaultIO = defaultIO; }
}
```

### 8.3 可配置槽位接口

```java
public interface IConfigurableSlot {
    SlotMode getSlotMode();
    void setSlotMode(SlotMode mode);
    Set<Direction> getAccessibleFaces();
    void setAccessibleFaces(Set<Direction> faces);
    boolean isOutputEnabled();
    void setOutputEnabled(boolean enabled);
}
```

### 8.4 各机器的默认槽位配置

| 机器 | 物品槽 | 流体槽 | 特殊流体槽 |
|------|--------|--------|-----------|
| 缓冲器 | BOTH | INPUT_ONLY (隔离) | SPECIAL_FLUID + 可配置面 |
| 冰箱 | HOLD (食物) + INPUT_ONLY (电) | INPUT_ONLY (制冷剂) | 无 |
| 提取机 | HOLD (物品) + INPUT_ONLY (电) | 无 | 无 |
| 真空冷冻机 | INPUT_ONLY + OUTPUT_ONLY | INPUT_ONLY + OUTPUT_ONLY | 无 |
| 冷库 | 无 | 无 | 无 |
| 窑炉 | 无 | 无 | 无 |

---

## 9. 核心继承类细节

### 9.1 GoblinRecipe（extends GTRecipe）

```java
public class GoblinRecipe extends GTRecipe {
    public RecipeMode mode = RecipeMode.TRANSFORM;
    public Map<ResourceLocation, AmbientEntry<?>> ambientConditions = Map.of();
    public List<RecipeOutputModifier> outputModifiers = List.of();
    public List<RecipeOutputModifier> tickOutputModifiers = List.of();
    // 构造器透传 GTRecipe 全部参数
}
```

`GTRecipe` 零改动。新配方用 `GoblinRecipe` 替代 `GTRecipe`，默认值保证未填充字段时行为与 GTM 一致。

### 9.2 GoblinRecipeLogic（extends RecipeLogic）

重写三个核心方法：

**setupRecipe()** — 将 `duration × PROGRESS_SCALE`，重置 `progressRate`、`recipeContext`：

```java
@Override
public void setupRecipe(GTRecipe recipe) {
    super.setupRecipe(recipe);
    if (lastRecipe == recipe) {
        duration = recipe.duration * PROGRESS_SCALE;
        progressRate = PROGRESS_SCALE;
        recipeContext = new RecipeContext(recipe);
    }
}
```

**handleRecipeWorking()** — 条件检查 + ambientSatisfied + `progress += progressRate`：

```java
@Override
public void handleRecipeWorking() {
    var conditionResult = RecipeHelper.checkConditions(lastRecipe, this);
    if (conditionResult.isSuccess()) {
        ambientSatisfied = true;
        var handleTick = handleTickRecipe(lastRecipe);
        if (handleTick.isSuccess()) {
            setStatus(Status.WORKING);
            if (!machine.onWorking()) { this.interruptRecipe(); return; }
            progress += progressRate;
            totalContinuousRunningTime++;
        } else {
            setWaiting(handleTick.reason());
            ambientSatisfied = false;
            // ... EU power failure delay logic unchanged ...
        }
    } else {
        ambientSatisfied = false;
        setWaiting(conditionResult.reason());
    }
    if (isWaiting() || isSuspend()) { regressRecipe(); }
}
```

**onRecipeFinish()** — 保留父类逻辑，结束时清理 `recipeContext`。

**getProgress() / getMaxProgress()** — 对外返回除以 `PROGRESS_SCALE` 的值。

### 9.3 与 RecipeRunner 的关系

RecipeRunner 通过 `RecipeHelper.handleRecipe()` 创建，不在 `GoblinRecipeLogic` 内直接持有引用。`tickOutputModifiers` 和 `outputModifiers` 的应用点待实现（需修改 RecipeRunner 或通过 mixin）。

### 9.4 降速与降耗的分工

| 角色 | 职责 |
|------|------|
| `GoblinRecipeLogic` | `progressRate` 决定进度增加量。`ambientSatisfied` 标记条件状态 |
| 动能子类 | `updateFromRPM(rpm)` 将转速映射为 progressRate。HALT_PROGRESS → 0 |
| 带降速的 EU 子类 | 自维护 tick input 统计，`progressRate` 按趋势缩放 |
| 降耗 | 机器子类在 `handleTickRecipeIO()` 重写中按 `progressRate / PROGRESS_SCALE` 缩放 IN 消耗 |

---

## 10. TFC 集成（goblintfc.recipe）

### 10.1 设计原则

- **不重写 TFC 逻辑**：所有 TFC 操作通过公开 API 完成
- **goblintech.recipe.api 层零 TFC 依赖**
- **热量走 RecipeCapability**：与 EU 同级，不是 RecipeOutputModifier

### 10.2 TFC API 速查

#### 热量系统

| API | 用途 |
|-----|------|
| `HeatCapability.get(stack)` | 获取可变 IHeat |
| `HeatCapability.getTemperature(stack)` | 获取当前温度（**自动计算自然衰减**） |
| `HeatCapability.setTemperature(stack, temp)` | 设置温度，更新 lastTick |
| `IHeat.setTemperatureIfWarmer(temp)` | 仅当更热时设置 |
| `IHeat.addTemperatureFromSourceWithHeatCapacity(temp, heatCapacity)` | **热传导**：按热容加权平均 |
| `HeatCapability.adjustTemp(temp, heatCap, ticks)` | 自然衰减公式 |

#### 食物系统

| API | 用途 |
|-----|------|
| `FoodCapability.applyTrait(stack, trait)` | 施加食物 trait |
| `FoodCapability.removeTrait(stack, trait)` | 移除食物 trait |
| `FoodTraits.REGISTRY` | FoodTrait 注册表 |
| `TFCComponents.HEAT` | HeatComponent 数据组件 key |
| `HeatIngredient` | 温度条件 Ingredient（TFC 已提供） |

### 10.3 goblintfc 实现

分为两类：**RecipeOutputModifier**（物品属性修改）和 **RecipeCapability**（热量资源流）。

#### RecipeOutputModifier — 物品属性修改

```java
// AddTraitModifier
public class AddTraitModifier implements RecipeOutputModifier {
    private final Holder<FoodTrait> trait;

    @Override
    public Object apply(RecipeCapability<?> cap, Object output, RecipeContext ctx) {
        if (!(output instanceof ItemStack stack)) return output;
        return FoodCapability.applyTrait(stack, trait);
    }

    @Override
    public Set<RecipeCapability<?>> targetCapabilities() {
        return Set.of(ItemRecipeCapability.CAP);
    }
}

// CopyFoodModifier — 对应 TFC 的 "tfc:copy_food"
// CopyInputModifier — 对应 TFC 的 "tfc:copy_input"
```

#### RecipeCapability — 热量 tickOutput

热量升降通过 TFC 的 `IHeat` API 改变物品温度，与 EU、扭矩同属于资源流：

```java
public class HeatRecipeCapability extends RecipeCapability<HeatRecipeData> { ... }

public record HeatRecipeData(float temperature, float heatCapacity) {
    public static final Codec<HeatRecipeData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.FLOAT.fieldOf("temperature").forGetter(HeatRecipeData::temperature),
        Codec.FLOAT.fieldOf("heatCapacity").forGetter(HeatRecipeData::heatCapacity)
    ).apply(i, HeatRecipeData::new));
}
```

机器 handler 示例：
```java
public void applyHeat(HeatRecipeData data, ItemStack stack) {
    IHeat heat = HeatCapability.get(stack);
    if (heat != null)
        heat.addTemperatureFromSourceWithHeatCapacity(data.temperature(), data.heatCapacity());
}
```

> **为什么 heat 做成 RecipeCapability 而非 RecipeOutputModifier**：heat 的语义是资源流（和 EU、扭矩同属每 tick 消耗/产出），放在 RecipeCapability 层级让配方 JSON 更统一。`copy_food`、`add_trait` 等则是"配方完成时一次性施加"，保留在 `outputModifiers` 中。

### 10.4 goblintfc Ingredient 条件

| Ingredient ID | 说明 | 是否 TFC 已有 |
|---------------|------|-------------|
| `tfc:heat` | 温度范围条件 | ✅ (`HeatIngredient`) |
| `tfc:not_rotten` | 未腐烂 | ✅ |
| `tfc:has_trait` | 具有指定 trait | ✅ |
| `goblintfc:not_hot` | 不高于指定温度 | ❌ 需新增 |
| `goblintfc:not_has_trait` | 不具有指定 trait | ❌ 需新增 |

### 10.5 典型配方示例

#### 冰箱保鲜（MODIFY + add_trait）
```json
{
  "type": "gtceu:fridge",
  "mode": "modify",
  "inputs": {
    "item": {
      "type": "neoforge:all_of",
      "children": [
        { "tag": "c:foods" },
        { "type": "goblintfc:not_has_trait", "trait": "tfc:preserved" }
      ]
    }
  },
  "tickInputs": { "EU": 8 },
  "outputModifiers": [
    { "type": "goblintfc:add_trait", "trait": "tfc:preserved" }
  ],
  "duration": 200
}
```

#### 高温锭降温（TRANSFORM + heat tickOutput）
```json
{
  "type": "gtceu:vacuum_freezer",
  "inputs": {
    "item": { "type": "tfc:heat", "min": 1000.0 }
  },
  "outputModifiers": [
    { "type": "goblintfc:copy_food" }
  ],
  "tickOutputs": {
    "goblintfc:heat": { "temperature": 0.0, "heatCapacity": 0.5 }
  },
  "outputs": { "item": "minecraft:iron_ingot" },
  "duration": 100
}
```

#### 橄榄油冷却（MODIFY + ambient cold + heat tickOutput）
```json
{
  "type": "gtceu:extractor",
  "mode": "modify",
  "ambientConditions": {
    "goblintfc:ambient_cold": 50.0
  },
  "inputs": {
    "item": { "type": "tfc:heat", "min": 0.1 }
  },
  "tickOutputs": {
    "goblintfc:heat": { "temperature": 0.0, "heatCapacity": 0.1 }
  },
  "duration": -1
}
```

---

## 11. Create 动集成（goblinkinetic）

### 11.1 概述

Create 的旋转动力系统涉及两个维度：

| 维度 | 性质 | 在配方系统中的角色 |
|------|------|------------------|
| **转速（RPM）** | 可变（受风力/水流/齿轮组影响） | 环境条件 + 速度乘数 |
| **应力（Stress/SU）** | 方块级固定值 × 转速 | 动力网络内部管理，可作为 tickInput |

**应力是动态的（已验证）**：Create 附属模组中制动器通电能改变 `calculateStressApplied()`，柴油发动机不同燃料/涡轮影响转速和容量。因此扭矩可以作为 RecipeCapability 的 tickInput/tickOutput——机器在配方执行期间向 Create 网络声明不同的应力值，网络过载 → `getSpeed()=0` → `ambient_rpm` 条件失败 → `HALT_PROGRESS`。

**三层职责划分**：

| 层 | 机制 | 作用 | 持有方 |
|---|------|------|--------|
| 配方启动条件 | `AmbientCondition` + `ambient_rpm` | 配方声明最低 RPM | 配方 JSON |
| 配方运行保障 | `ConditionFailBehavior.HALT_PROGRESS` | 转速低于最低限 → 进度暂停 | `AmbientType` 声明 |
| 配方安全阀 | `ConditionFailBehavior.INTERRUPT` | 转速超过 `ambient_max_rpm` → 立即中断 | `AmbientType` 声明 |
| 配方执行速度 | `KineticRecipeLogic.updateFromRPM()` | 转速 / optimalRPM → progressRate | 机器子类 |
| 扭矩消耗/产出 | `RecipeCapability` (tickInput/tickOutput) | 向网络声明 SU 需求或提供 SU/RPM | 机器 handler |

### 11.2 RPM 作为环境条件

已在 §6.4 中完整定义——`goblinkinetic:ambient_rpm`（≥，HALT_PROGRESS）、`goblinkinetic:ambient_max_rpm`（≤，INTERRUPT）。

### 11.3 RPM 影响加工速度

`KineticRecipeLogic extends GoblinRecipeLogic`，机器本体在自己的 tick 订阅中调用 `logic.updateFromRPM(currentRPM)` 更新 `progressRate`：

```java
public class KineticRecipeLogic extends GoblinRecipeLogic {
    public void updateFromRPM(float currentRPM) {
        if (!ambientSatisfied) { progressRate = 0; return; }
        if (optimalRPM == 0f)  { progressRate = PROGRESS_SCALE; return; }
        progressRate = (int)(Math.abs(currentRPM) / optimalRPM * PROGRESS_SCALE);
    }
}
```

不设速度上限——`rpm = optimalRPM × 10` → `progressRate = 160`。实际上限由 Create 应力网络约束。

### 11.4 完整流程：Create 石磨粉碎骨头

```text
配方 JSON：
  ambientConditions: { rpm: 64.0 }
  duration: 200

机器 KineticMillstone（KineticRecipeLogic extends GoblinRecipeLogic）：
  → AmbientProviderTrait 提供 RPM = 128.0
  → optimalRPM = 128f

tick 1（蒸汽引擎 128 RPM）：
  → AmbientCondition 检查: 128 ≥ 64 ✓, ambientSatisfied = true
  → updateFromRPM(128): progressRate = 128/128 × 16 = 16
  → progress += 16

tick 100（锅炉缺水，转速降到 48 RPM）：
  → AmbientCondition 检查: 48 ≥ 64 ✗
  → failBehavior = HALT_PROGRESS
  → 状态保持 WORKING，ambientSatisfied = false
  → updateFromRPM(48): ambientSatisfied=false → progressRate = 0 → progress 冻结

tick 150（锅炉加水，转速恢复 128 RPM）：
  → AmbientCondition 检查: 128 ≥ 64 ✓
  → ambientSatisfied = true → progress 从冻结值继续
  → 无 regressRecipe() 惩罚，无缝恢复
```

### 11.5 为什么 optimalRPM 放在机器上而非配方里

`optimalRPM` 是机器的物理设计参数，不是配方的属性。一台石磨粉碎任何东西的快慢取决于齿轮传动比例，与粉碎什么无关。若需要"加速版石磨"→ 定义不同的 MachineDefinition 即可。

---

## 12. 机器映射总表

### 12.1 新增机器的设计定位

| 机器 | RecipeMode | Slot 配置 | Ambient 角色 | 实现位置 |
|------|-----------|----------|-------------|------------|
| 缓冲器 | 无（纯储存） | BOTH + SPECIAL_FLUID | 无 | goblintech.machine |
| 冰箱 | MODIFY | HOLD + INPUT | 自提供 COLD | goblintech.machine |
| 提取机 | MODIFY | HOLD + INPUT | 自提供 HEAT | goblintech.machine |
| 真空冷冻机 | TRANSFORM | INPUT + OUTPUT | 无 | goblintech.machine |
| 冷库 | AMBIENT | 无 | 提供 COLD（带参数） | goblintech.machine |
| 窑炉 | AMBIENT | 无 | 提供 HEAT（带参数） | goblintech.machine |
| 超净间 | AMBIENT | 无 | 提供 CLEANROOM（带等级） | goblintech.machine |
| GTM 老机器 | TRANSFORM | — | — | （不改动） |
| 石磨（Create） | TRANSFORM | INPUT + OUTPUT | 提供 RPM | goblinkinetic |
| 搅拌器（Create） | TRANSFORM | INPUT + OUTPUT | 提供 RPM | goblinkinetic |
| 辊压机（Create） | TRANSFORM | INPUT + OUTPUT | 提供 RPM | goblinkinetic |

### 12.2 与 GTM 现有机器的兼容

| GTM 现有机器 | 行为 | 改动 |
|-------------|------|------|
| 电弧炉 | TRANSFORM（默认） | 零改动 |
| 组装机 | TRANSFORM（默认） | 零改动 |
| 超净间（旧） | AMBIENT via CleanroomProviderTrait | 完全不改动，保留旧逻辑 |
| 超净间（新） | AMBIENT via AmbientProviderTrait | 新机器，用 AmbientCondition |
| 所有现有 WorkableMachine | TRANSFORM（默认） | 零改动 |

---

## 13. 剥离策略与 Mixin 清单

### 13.1 当前阶段：继承式开发（零 Mixin）

`GoblinRecipe extends GTRecipe`、`GoblinRecipeLogic extends RecipeLogic`。GTCEu 源码零改动。新机器通过 `createRecipeLogic()` 工厂方法接入。

需要 Mixin 的部分：
- `RecipeRunner` 的 `handleContents()` — 应用 `outputModifiers` / `tickOutputModifiers`

### 13.2 剥离后的 Mixin（备查）

若未来不直接依赖 GTM 源码、走附属模组路线：

**RecipeRunner Mixin**：
```java
@Mixin(RecipeRunner.class)
public class RecipeRunnerMixin {
    @Inject(method = "handleContents", at = @At("TAIL"))
    private void applyOutputModifiers(CallbackInfoReturnable<ActionResult> cir) {
        if (io == IO.OUT && recipe instanceof GoblinRecipe gr) {
            gr.outputModifiers.forEach(m -> m.apply(recipe, outputs));
        }
    }
}
```

**注意**：`WorkableTieredMachine.createRecipeLogic()` 无需 Mixin——直接在机器子类中覆盖即可。RecipeLogic 也未修改——走子类覆盖。`GoblinRecipe` 也不依赖 Mixin 注入。整体 Mixin 需求远小于旧方案。