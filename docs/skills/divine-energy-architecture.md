# Divine 能量自立门户体系架构

## 第0章 名字空间

> 本文档引入的所有新名字（`goblinrecipe-architecture.md` / `goblinmachine-ghost-hull.md` 中未出现者）。

### 0.1 类 / 接口

| 名字 | 章节 | 说明 |
|------|------|------|
| `ScriptureAptitude` | §6 | 配方 JSON 数据载体 + 并行限制逻辑（经文的慧根） |
| `AscensionBenediction` | §10 | 对标 `ELECTRIC_OVERCLOCK`，等级拒绝+超频（升华赐福） |
| `IDivineInputSource` | §8.3 | 输入源统一抽象接口 |
| `KineticOffering` | §8.3 | 应力输入源实现（生命力祭品） |
| `ThunderOffering` | §8.3 | CEE 电力输入源实现（雷祭品） |
| `FuelOffering` | §8.3 | 燃料输入源实现（燃料祭品） |
| `MediumOffering` | §8.3 | 介质输入源实现（介质祭品） |

### 0.2 `IBelieverOfScripture` 新增方法

| 名字 | 说明 |
|------|------|
| `isAwakened()` | 配方启动守卫，默认 false |
| `getMaxEndurance()` | 并行计算用，最大恩典承受量 |
| `getMaxOffering()` | 并行计算用，最大奉献量 |
| `getDevotionGrade()` | Divine 超频等级 |
| `setDevotionGrade(int)` | 等级联动写入 |
| `getConsecratedGobletCapacity()` | 节流阀配置值（≤ ordained上限，圣化后的圣杯容量） |
| `getOrdainedGobletCapacity()` | 硬件上限，hatch 配置决定（天定的圣杯容量） |
| `getRitualTempo()` | 多源合成后的处理速度倍率 |

### 0.3 `ScriptureAptitude` 新增方法

| 名字 | 说明 |
|------|------|
| `getMaxParallelByInput(holder, recipe, limit, tick)` | tick 时 `maxEndurance / demand`；非 tick 返回 limit |
| `limitMaxParallelByOutput(holder, recipe, limit, tick)` | tick 时 `maxOffering / output`；非 tick 返回 limit |

### 0.4 `AscensionBenediction` 相关

| 名字 | 说明 |
|------|------|
| `getDivineTier(recipe)` | 配方等级：`divineDemand / 32` |
| `applyAllButDivine` | 类比 `applyAllButEU`，防止并行倍增打到 Divine |

### 0.5 燃料执行器

| 名字 | 说明 |
|------|------|
| `burnSacrifice()` | 小配方执行器每 tick 逻辑 |
| `getFuelConsumeMultiplier()` | 燃料消耗倍率：LV=1, MV=6, HV=32 |
| `getFuelDivineMultiplier()` | Divine 产出倍率：LV=1, MV=4, HV=16 |
| `cachedDivine` | 燃料执行器缓存的 Divine/t |
| `fuelExecutor` / `小配方执行器` | 燃料→Divine/t 的内嵌执行器 |

### 0.6 排放态

| 名字 | 说明 |
|------|------|
| `pourSanctifiedBlessing()` | 配方完成后 offeringStock 未清空时的排放处理方法（倾倒圣化后的祝福） |

### 0.7 概念名（非代码标识符）

| 名字 | 说明 |
|------|------|
| `devotionGrade` | Divine 超频等级：`log₂(sanctifiedGobletCapacity / gracePerTier)` |
| `sanctifiedGobletCapacity` | 玩家可配置的节流值（≤ ordained上限，圣化后的圣杯容量） |
| `ordainedGobletCapacity` | hatch 配置决定的硬件上限（天定的圣杯容量） |
| `gracePerTier` | 每级基准 GraceReceived |
| `divineDemand` | 配方每 tick 所需 Divine/t |
| `divineOffering` | 配方每 tick 产出 Divine/t |
| `commonRunicNodes` | 普通仓接入点数（凡常符文节点） |
| `sanctifiedRunicNodes` | 二联/四联仓接入点数（神圣符文节点） |
| `isPluralityRunic` | 是否有多阵（多仓）升压（1x / 2x） |
| `drain phase` / `排放态` | 配方完成后 offeringStock 未清空的过渡阶段 |
| `sputtering` | 高流接入点电流 < 4A 时处理速度/2（符文节点噼啪不稳） |
| `sacredEffort` | 进度增量：`piety × blessedEffort × ritualTempo` |
| `ritualTempo` | 多源合成后的处理速度倍率 |
| `Kinetic` | 应力输入源（扭矩×转速） |
| `Thunder` | Create Electro Energetics 电力输入源 |
| `Medium` | 各等级专用的 IO 模块介质（工程学占位，DivineGrace 流经的载体） |

### 0.8 数值常量

| 名字 | 值 |
|------|-----|
| 熔断阈值 | `0.4`（`consumeRate < 0.4` 触发 WAITING） |
| 低电流惩罚线 | `4A`（sanctifiedRunicNodes 电流低于此值速度/2） |
| CEE 电流上限 | `3.8A` |
| 燃料效率 | LV 100%, MV 67%, HV 50% |
| 燃料消耗倍率 | LV ×1, MV ×6, HV ×32 |
| Divine 产出倍率 | LV ×1, MV ×4, HV ×16 |
| Medium 权重 | `1.0`（固定保底） |
| Kinetic 权重 | `转速 / 16` |
| Thunder 权重 | `电流量 (A)` |

---

## 1. 概述

### 1.1 设计动机

Divine 能量是 GoblinTech 自立门户的通用能量体系，**不接入 GTCEu 标准 RecipeRunner/IO 管线**，而是通过 `GoblinOracleOfScripture.assessBelieverState()` 自定义路径执行。

设计目标：

1. **可变速加工**：处理速度由多个能量输入源的供应情况按权重合成，而非二元的"有电/没电"
2. **多源合一**：应力（Kinetic）、CEE 电力、燃料燃烧统一作为祭品汇入 `offeringStock`，共同决定单 tick 进度
3. **等级驱动**：机器等级直接影响 IO 上限和处理速度倍率，驱动玩家升级

### 1.2 与 GTCEu IO 管线的关系

```
GTCEu 标准路径（物品/流体）:
  RecipeRunner → IRecipeHandler → 标准仓室

Divine 自立路径（能量）:
  GoblinOracleOfScripture.assessBelieverState()
    → believer.evaluateOffering(offeringDemand)
        → believer.receiveOffering()       ← 机器子类聚合多源输入
    → believer.calculatePietyLevel()
    → believer.determineDivineEffort()
    → progress += divineEffort
```

**DivineRecipeCapability** 作为配方 JSON 的数据载体（`"divine": [32]`），同时参与 ParallelLogic 计算（`doMatchInRecipe() = true`），但不走 RecipeRunner 的 tick 执行（因为 `handleRecipeWorking()` 被 `GoblinOracleOfScripture` 完全覆写）。

### 1.3 配方启动验证

`DivineRecipeCapability` 参与 ParallelLogic 和 RecipeRunner matching，但 Divine 的具体执行（能量输入输出、进度推进）由 `assessBelieverState()` 自定义管线处理。因此配方启动时有两层额外守卫：

```
checkRecipe(recipe)
  → super.checkRecipe(recipe)         ← 物品/流体走 RecipeRunner simulated
  → 若配方含 divine:
      ├─ believer.hasDivineCapability() == false → 拒绝启动
      └─ DivineRecipeCapability.getMaxParallelByInput(machine, recipe, 1, true) == 0 → 拒绝启动
```

`hasDivineCapability()` 默认返回 `false`（无 divine 输入源的机器），接入任意 Divine 输入源后覆写为 `true`。运行时能量消耗由 `assessBelieverState()` 每 tick 验证。

---

## 2. OfferingStock / BlessingStock 快速回顾

| 字段 | 神棍名 | 工程含义 |
|------|--------|----------|
| `offeringStock` | 祭品容槽 | 已接收的祭品能量缓冲 |
| `offeringCapacity` | 祭品容量 | 最大缓冲值 |
| `offeringThreshold` | 祭品安全阈值 | 低于此值时 offerRate 按比例缩减 |
| `blessingStock` | 祝福容槽 | 待输出的祝福能量缓冲 |
| `blessingCapacity` | 祝福容量 | 最大缓冲值 |
| `blessingThreshold` | 祝福安全阈值 | 高于此值时 pourRate 按比例缩减 |
| `offeringReceived` | 本 tick 接收的祭品 | 所有输入源贡献的祭品总和 |
| `blessingPoured` | 本 tick 倾泻的祝福 | 实际输出的祝福总和 |
| `offerRate` | 祭品供应速率 | `offeringReceived / offeringDemand` (0.0~1.0) |
| `pourRate` | 祝福倾泻速率 | `blessingPoured / blessingYield` (0.0~1.0) |
| `pietyLevel` | 虔诚度 | `min(offerRate, pourRate)` |
| `divineEffort` | 神圣努力 | `pietyLevel × blessedEffort × ritualTempo`，每 tick 进度增量 |

---

## 3. Divine/t 速率体系

### 3.1 IO 上限

每个等级机器有数值上等于原 GTM 同等级 EU/t 的 **祭品/祝福 IO 上限**——即每 tick 的 OfferingReceived 和 BlessingPoured 不应超过此值。

与 EU 的关键区别：**Divine/t 可被分配**。多个输入源同时提供能量时，各源贡献的 Divine/t 自动累加，总和受 IO 上限约束。

### 3.2 分配模型

```
OfferingReceived = Σ(各输入源的祭品贡献)
OfferRate = OfferingReceived / offeringDemand

仪式速度倍率 = 由各输入源的"权重因子"合成（见第 5 节）
```

关键语义：**祭品量决定能否工作（offerRate），权重因子决定工作多快（仪式速度倍率）。两者解耦。**

---

## 4. 输入源设计

每个输入源实现两个职责：

1. **贡献祭品**：填入 `offeringReceived`
2. **贡献权重因子**：影响合成后的仪式速度倍率

### 4.1 应力输入 (Kinetic)

| 属性 | 值 |
|------|-----|
| OfferingReceived | `1 扭矩 = 1 Divine/t` |
| 权重因子 | `转速 / 16` |

**设计意图**：扭矩决定"有没有能量跑起来"，转速决定"跑得多快"。低转速高扭矩机器可以运行但处理缓慢。

#### 示例

| 场景 | 扭矩 | 转速 | GraceReceived | 处理倍率 |
|------|------|------|---------------|----------|
| 满速 | 4 | 256 RPM | 4 Divine/t | 16× |
| 半速 | 4 | 128 RPM | 4 Divine/t | 8× |
| 低速 | 4 | 32 RPM | 4 Divine/t | 2× |
| 扭矩不足 | 2 | 256 RPM | 2 Divine/t | 16×（但 consumeRate 低） |

#### 节流节能

扭矩 × 转速 = 应力总量 = 恒定（由动力源决定）。主动降低 `configuredGraceReceived` → 扭矩减小 → 转速升高 → 处理加速：

| 配置 | 扭矩 | 转速 | GraceReceived | 处理倍率 |
|------|------|------|---------------|----------|
| 全扭矩 | 64 | 4 | 64 | 0.25× |
| 半扭矩 | 32 | 8 | 32 | 0.5× |
| 低扭矩 | 16 | 16 | 16 | 1.0× |

**应力机器适合低扭矩高转速**：损耗等级换取速度，主动降配后 `goblinRecipeTier` 同步降低。

### 4.2 CEE 电力输入

> CEE = Create Electro Energetics

| 属性 | 值 |
|------|-----|
| GraceReceived | `1 电阻值 = 1 Divine/t` |
| 权重因子 | `电流量 (A)` |
| 上限 | 最大 3.8A（超过烧毁） |
| 常态 | 断路（0A 通过） |

**电气模型**：

```
I = V / R
P = I² × R  （不直接使用，仅作为烧毁判定依据）

其中：
  V = CEE 供电电压
  R = 机器的电阻值 = Divine/t 上限（即 GraceCapacity）
  I = 实际通过的电流
```

**设计意图**：

- 机器常态是**断路**，需玩家激活才开始耗电。断路状态下电流 = 0，权重因子 = 0
- 收到红石信号或机器工作时闭合电路，电流由 V/R 决定
- **32V 供电 + 32Ω 机器** → 恰好 1A → 1× 处理倍率
- **64V 供电 + 32Ω 机器** → 2A → 2× 处理倍率
- **120V 供电 + 32Ω 机器** → 3.75A → 3.75× 处理倍率（已达极限）
- **128V 供电 + 32Ω 机器** → 4A → **烧毁**（超过 3.8A 上限，必须升级 MV 机器）

**强制升级路径**：

| 机器等级 | 电阻 (R) | 最大安全电压 (3.8A 时) | 对应的处理倍率 |
|----------|----------|------------------------|----------------|
| LV | 32Ω | ~122V (32×3.8) | 1~3.8× |
| MV | 128Ω | ~486V (128×3.8) | 1~3.8× |
| HV | 512Ω | ~1946V | 1~3.8× |

> 玩家若想用更高电压加速，必须提升机器等级以匹配更高的电阻值，否则电流超过 3.8A 烧毁。LV 机器在 120V 时电流 3.75A 勉强安全，升到 128V 就必然要升级 MV。

**电压不足时的降速**：

```
32V 供电 + 128Ω (MV) 机器 → I = 32/128 = 0.25A → 0.25× 处理倍率
```

#### 状态机

```
        红石信号 / 工作状态
  断路 ──────────────────→ 闭合电路
  (I=0, 权重=0)           (I=V/R, 权重=I)
                              │
                              │ I > 3.8A
                              ↓
                           烧毁/熔断
```

### 4.3 介质输入 (Medium)

机器不直接消耗流体，而是通过**各等级专用的介质 IO 模块**将介质转换为 Divine/t 的 `graceReceived`。每台机器只能安装符合其等级的介质 IO 模块。

#### 4.3.1 介质分级

| 等级 | 介质 | 排废 | 适用机器类型 |
|------|------|------|-------------|
| ULV | 蒸汽 → | 蒸馏水 / 通气阀 | 通用（早期） |
| LV | 高压蒸汽 → | 蒸馏水 / 通气阀 | 通用（早期） |
| MV | 高压液压油 → | 液压油 | 动力加工类 |
| HV | 高温 NaK 合金 → | NaK 合金 | 热力加工类 |
| EV | 能量浆液 → | 红石浆液 | 全能（液压/电力/热力） |
| IV (LuV) | 能量浆液 → | 红石浆液 | 全能 |

#### 4.3.2 能量浆液（EV+）

能量浆液是 EV 及以上等级的主要介质，具有 **液压 + 电力 + 热力三合一** 特性：

- **MEK 集成**：修改 MEK 能量导线配方，使导线内需罐装能量浆液——MEK 本质上是把能量浆液当作能量池使用
- **格雷机器**：MEK 的能量浆液直接对应 Divine/t，对格雷机器来说既是液压也是电力也是热力

#### 4.3.3 与 Weight 因子

介质输入固定贡献 **1× 权重因子**。作为保底倍率，介质独自驱动时机器至少以 1× 速度运行。接入应力（最高 16×）或 CEE 电力（最高 3.8×）后，更高权重因子会自然淹没介质的 1×——因此介质的权重在正常情况下不起决定作用，但在纯介质供电场景下保证机器至少能低速运转。

安装介质 IO 模块后，机器的 `hasDivineCapability() = true`。

#### 4.3.4 介质 IO 生命周期

介质 IO 同时检查输入槽和输出槽——输入有介质且输出有空间容纳废液时才能工作：

```
                    每 tick
                       │
          ┌────────────┼────────────┐
          │  输入有介质 && 输出有空间  │  输入/输出任一不足
          ↓                          ↓
    消耗介质（输入→GraceReceived）    GraceReceived = 0
    填充废液至输出槽                 等待下一次检查
    设置 cooldown
    hasDivineCapability = true       hasDivineCapability = false
```

### 4.4 燃料输入 (Fuel) — 内嵌小配方执行器

#### 4.4.1 原理

燃料输入与介质输入不同：介质通过专用 IO 模块转换，而燃料通过**内嵌的小配方执行器**匹配 GTCEu 的发电机配方（内燃/燃气轮机/蒸汽轮机），将 EU/t 输出映射为 Divine/t。

#### 4.4.2 递减效率

不同于等级倍率线性增长（2^(tier-1)），燃料输入采用**递减效率**设计——高等级机器消耗更多燃料，产出虽增加但效率下降：

| 机器等级 | 燃料消耗倍率 | Divine/t 产出倍率 | 效率 |
|----------|-------------|-----------------|------|
| LV | ×1 | ×1（配方 EU/t） | 100% |
| MV | ×6 | ×4 | 67% |
| HV | ×32 | ×16 | 50% |

**大机器不走这套**，高阶大机器有专用的配方和输出模块。

#### 4.4.3 机器类型区分

燃料系统区分两类机器：

| 类型 | 说明 | 燃料效率 |
|------|------|---------|
| 动力加工类 | MV 耗 6 倍燃料产 4 倍 Divine/t | 67% |
| 热力加工类 | 同上，但燃料类型不同 | 67% |

两类机器使用不同燃料类型（燃烧 vs 热力），但递减效率公式一致。

#### 4.4.4 小配方执行器生命周期

```
                    检查计时器
                        │
          ┌─────────────┼─────────────┐
          │ timer > 0   │ timer == 0  │
          ↓              ↓             ↓
    继续当前燃料     检查燃料槽      无燃料
          │              │             │
          │      ┌───────┼───────┐     │
          │      │ 匹配成功  │ 匹配失败 │
          │      ↓          ↓          ↓
          │   消耗流体    OfferingReceived  OfferingReceived
          │   设置 timer    = 0          = 0
          │   缓存 EU/t   缓存归零      缓存归零
          │      │
          ↓      ↓
    OfferingReceived = 缓存的 EU/t × 等级倍率
    timer = timer - 1
```

伪代码：

```java
// 每 tick 在 receiveOffering() 中调用
void tickFuelExecutor() {
    float speed = getRitualTempo();

    if (timer > 0) {
        offeringReceived = cachedDivine;
        timer -= speed;
        return;
    }

    // timer == 0，检查是否有下一个配方
    var recipe = findGeneratorRecipe(fuelTank.getFluid());
    if (recipe != null && isMachineWorking()) {
        int fuelMultiplier = getFuelConsumeMultiplier(); // LV=1, MV=6, HV=32
        int divineMultiplier = getFuelDivineMultiplier(); // LV=1, MV=4, HV=16
        fuelTank.drain(recipe.getFluidInput(), fuelMultiplier);
        timer = recipe.duration - speed;
        cachedDivine = recipe.getTickOutputEUt() * divineMultiplier;
        offeringReceived = cachedDivine;
    } else {
        offeringReceived = 0;
        cachedDivine = 0;
    }
}
```

#### 4.4.5 配方完成时的行为

当前配方完成时（`onRecipeFinish()`），必须**归零小配方执行器**：

```
timer = 0
cachedDivine = 0
offeringReceived = 0
```

这确保机器在空闲时不消耗燃料也不产生 Divine。

---

## 5. 仪式速度倍率合成

### 5.1 GUI 权重调节

仪式速度倍率由各输入源的权重因子及玩家手动分配的权重合成。机器放下并配置输入方式后，通过 **GUI 滑块调节各源的处理速度权重**：

```
最终仪式速度倍率 = Σ(输入源i的权重因子 × 玩家分配权重i) / Σ(玩家分配权重i)
```

玩家可以自由调配，例如：

| 应力权重 | 电力权重 | 燃料权重 | 效果 |
|----------|----------|----------|------|
| 50% | 50% | 0% | 应力 + 电力各半 |
| 100% | 0% | 0% | 纯应力驱动 |
| 0% | 100% | 0% | 纯电力驱动 |
| 30% | 30% | 40% | 三源混合 |

未接入的输入源权重因子为 0，即使分配了权重也不生效。

### 5.2 各源权重因子

| 输入源 | 权重因子 |
|--------|----------|
| 应力 | `转速 / 16`（最高 16×） |
| CEE 电力 | `电流量 (A)`（最高 3.8×） |
| 燃料 | `1.0`（固定） |
| 介质 | `1.0`（固定保底） |

### 5.3 降速与熔断

```
consumeRate < 1.0  →  divineEffort < blessedEffort → 慢速运行（不熔断）
consumeRate < 0.4  →  触发 MinPowerRateCondition 默认熔断（WAITING 退避）
consumeRate = 0    →  停止（配方中断或冻结，取决于 decree）
```

---

## 6. DivineRecipeCapability

### 6.1 定位

配方 JSON 中 Divine 能量需求的**类型标识 + 数据载体**：

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

### 6.2 实现要点

| 属性/方法 | 值/行为 |
|-----------|---------|
| 基类 | `RecipeCapability<Integer>` |
| 注册名 | `goblintech:divine` |
| 序列化器 | `SerializerInteger.INSTANCE` |
| `doMatchInRecipe()` | `true`（默认，不复写）——参与 ParallelLogic 和 RecipeRunner matching |
| `copyInner(Integer)` | `return content` |
| `copyWithModifier(Integer, ContentModifier)` | `return modifier.apply(content)` |
| `getMaxParallelByInput(holder, recipe, limit, tick)` | tick 时：`maxGraceReceived / demand`；非 tick：`limit` |
| `limitMaxParallelByOutput(holder, recipe, limit, tick)` | tick 时：`maxGraceOffered / output`；非 tick：`limit` |

### 6.3 不走 RecipeRunner 执行的原因

`doMatchInRecipe() = true` 确保 Divine 参与 ParallelLogic 计算（并行限制）和 RecipeRunner matching（配方匹配），但 Divine 的**具体能量消费**由 `GoblinOracleOfScripture.assessBelieverState()` 处理，不走 RecipeRunner 的 tick 执行管线。原因有二：

**1. `handleRecipeWorking()` 被完全覆写**：`GoblinOracleOfScripture` 覆写了 `handleRecipeWorking()`，使其不调用父类的 tick IO 处理（`handleTickRecipe → handleTickRecipeIO`），而是走自定义的 `assessBelieverState()`。

**2. 跨 IO 方向耦合（根本原因）**：GTCEu 的 RecipeRunner 将 IN 和 OUT 作为**两次独立的串行调用**处理：

```
handleTickRecipe(recipe)
  → handleTickRecipeIO(IO.IN)    ← 独立判断，失败则 OUT 不执行
  → handleTickRecipeIO(IO.OUT)   ← 独立判断，失败时 IN 已消费不可回滚
```

这对 EU/t 体系是正确的——电网给不给电和仓室满不满没有因果关系。但 Divine 体系要求**输出堵塞反作用于输入**（`pietyLevel = min(offerRate, pourRate)`），这在 GTCEu 的二元串行模型中无法表达。`assessBelieverState()` 在同一方法内同时处理 IN/OUT 并取 `min()`，是实现闭环信仰经济的唯一路径。

---

## 7. 与 GoblinOracleOfScripture 的交互流

```
每 tick:
  handleRecipeWorking()
    → if (progress >= duration) handleDrainPhase()  ← 排放态（见第 12 节）
    → assessBelieverState()
        │
        ├─ 1. believer.evaluateOffering(offeringDemand)
        │       → believer.receiveOffering(offeringDemand)  ← 机器子类实现
        │           ├─ 聚合应力输入 → offeringReceived += torque
        │           ├─ 聚合 CEE 输入 → offeringReceived += resistance
        │           ├─ tickFuelExecutor() → offeringReceived += cachedEUt
        │           └─ 受 IO 上限约束
        │       → offeringStock += offeringReceived
        │       → offerRate = offeringReceived / offeringDemand
        │
        ├─ 2. believer.evaluateBlessing(blessingYield)   ← 同理
        │
        ├─ 3. believer.calculatePietyLevel()
        │       → piety = min(offerRate, pourRate)
        │
        ├─ 4. believer.determineDivineEffort()
        │       → divineEffort = piety × blessedEffort × getRitualTempo()
        │
        ├─ 5. verifyDivineDecree()
        │       → 检查条件熔断
        │
        └─ 6. believer.performDivineWork()
                → 实际充能/放能
```

### 7.1 仪式速度倍率注入点

仪式速度倍率（来自第 5 节的多源合成）在 `determineDivineEffort()` 中作为第三因子乘入：

```java
default void determineDivineEffort() {
    setDivineEffort((int)(getPietyLevel() * getBlessedEffort() * getRitualTempo()));
}
```

`getRitualTempo()` 是 `IBelieverOfScripture` 的 default method，返回 1.0，由机器子类覆写实现多源权重合成。

---

## 8. 抽象接口展望（下个会话）

### 8.1 待定义接口

| 接口/方法 | 目的 | 定义位置 |
|-----------|------|----------|
| `isAwakened()` | 机器是否已接入祭品输入源（配方启动验证用） | `IBelieverOfScripture` |
| `getRitualTempo()` | 仪式速度倍率合成 | `IBelieverOfScripture` |
| `IDivineInputSource` | 输入源抽象（应力/电力/燃料的统一接口） | 新接口 |
| `getDivineSources()` | 返回机器挂载的所有输入源 | `IBelieverOfScripture` |
| `getMaxEndurance()` | 每 tick 最大祭品接收量（并行计算用） | `IBelieverOfScripture` |
| `getMaxOffering()` | 每 tick 最大祝福倾泻量（并行计算用） | `IBelieverOfScripture` |

### 8.2 `isAwakened()` — 配方启动守卫

```java
// IBelieverOfScripture
default boolean isAwakened() {
    return false;  // 默认无祭品能力，子类接入输入源后覆写
}
```

**调用链**：

```
GoblinOracleOfScripture.checkRecipe(recipe)
  → super.checkRecipe(recipe)                         // 物品/流体走 RecipeRunner
  → if (offeringDemand > 0 || blessingYield > 0):
      → if (!believer().isAwakened()):
          → return FAIL ("机器未配置祭品输入源")
      → if (ScriptureAptitude.CAP.getMaxParallelByInput(machine, recipe, 1, true) == 0):
          → return FAIL ("OfferingReceived 不足")
  → return SUCCESS
```

### 8.3 输入源统一抽象（草案）

```java
interface IDivineInputSource {
    int getDivineContribution();      // 本 tick 贡献的 Divine/t
    float getProcessingWeight();      // 权重因子
    void tick(IBelieverOfScripture believer);  // 每 tick 更新
}
```

三种输入源分别实现：

- `KineticDivineSource` — 读取扭矩 × 转速
- `CEEDivineSource` — 读取电压 / 电阻
- `FuelDivineSource` — 内嵌小配方执行器

---

## 9. 等级体系

### 9.1 双层 GraceReceived

| 层 | 含义 | 来源 |
|---|---|---|
| **硬件上限** | 当前仓室配置能提供的**最大** GraceReceived | `getHardwareMaxGraceReceived()`——hatch 配置决定 |
| **节流配置** | 玩家主动设置的有效 GraceReceived | `getConfiguredGraceReceived()`——GUI 节流阀，可设为 ≤ 硬件上限的任意值 |

玩家把节流阀从 4x 拧到 2x → `configuredGraceReceived = 2x`。

### 9.2 等级联动

`goblinRecipeTier` 与 `configuredGraceReceived` 绑定：

```
goblinRecipeTier = log₂(configuredGraceReceived / baselineGracePerTier)
```

| 节流配置 | 扭矩(应力) | 转速(应力) | goblinRecipeTier | 超频等级增量 |
|----------|------------|------------|-----------------|-------------|
| 1x | 64 | 4 | LV (0) | +0 |
| 2x | 32 | 8 | MV (1) | +1 |
| 4x | 16 | 16 | HV (2) | +2 |
| 8x | 8 | 32 | EV (3) | +3 |

**主动降配**：玩家开节流阀将 `configuredGraceReceived` 从 4x 降到 2x → `goblinRecipeTier` 从 HV 降到 MV → 超频可用等级减少一次 → 高等级配方被拒。这是合法的降级策略。

### 9.3 等级拒绝

在 `DivineOverclockModifier` 中实现（见第 10 节），与 EU 体系的 `getRecipeEUtTier > getMaxOverclockTier` 类似：

```
if (recipeDivineTier > machine.getGoblinRecipeTier()) {
    return ModifierFunction.cancel("等级不够");
}
```

---

## 10. DivineOverclockModifier

### 10.1 定位

对标 GTCEu 的 `ELECTRIC_OVERCLOCK` RecipeModifier，处理 Divine 的等级拒绝、超频和 sub-tick 并行。放在 modifier 链中：

```
[DIVINE_OVERCLOCK, PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE]
```

与 `ELECTRIC_OVERCLOCK` 的对比：

| | EU 版 | Divine 版 |
|---|---|---|
| maxVoltage/tier 来源 | `overclockMachine.getOverclockVoltage()`（死值） | `believer.getGoblinRecipeTier()`（受升压/节流影响） |
| 等级拒绝 | `recipeEUtTier > getMaxOverclockTier()` | `recipeDivineTier > getGoblinRecipeTier()` |
| 超频内容 | EUt × 4, duration × 0.25 | `divineDemand × 4^OC`, `duration × 0.25^OC` |
| ParallelLogic 调用 | `getParallelAmount` 含 EU 限制 | `getParallelAmount` 含 Divine 限制 |

### 10.2 核心逻辑

```
if (!(machine instanceof IBelieverOfScripture believer)) return IDENTITY;

// 等级拒绝
int recipeDivineTier = getDivineTier(recipe);  // divineDemand / 32
if (recipeDivineTier > believer.getGoblinRecipeTier()) {
    return ModifierFunction.cancel("等级不够");
}

// 超频计算
int OCs = believer.getGoblinRecipeTier() - recipeDivineTier;
int maxParallels = ParallelLogic.getParallelAmount(machine, recipe, ...);
// subTickParallelOC 循环：
//   divineDemand × 4^OC
//   duration × 0.25^OC
//   duration < 1 → 转并行，上限 maxParallels
// 返回 ModifierFunction：duration、parallels、tickInputModifier(divine)
```

### 10.3 烘焙注意

`Divine` 的 tickInputs 需要通过 `applyAllButDivine`（类比 EU 的 `applyAllButEU`）在 `modifyAllContents` 中单独处理，防止并行倍增打到 Divine 值上——因为 Divine 的并行值已在 `subTickParallelOC` 中手动计算好了。

---

## 11. 多仓升压体系

### 11.1 公式

```
hardwareMaxGraceReceived = isDualHatch(2x) × (lowCurrentPorts + 2 × highCurrentPorts)

where:
  isDualHatch    = 是否有双仓升压（单仓=1x，双仓=2x）
  lowCurrentPorts  = 普通仓接入点数
  highCurrentPorts = 二联/四联仓接入点数（每仓提供 2 个点）
```

### 11.2 配置推演

| 配置 | 升压增益 | low 点 | high 点 | 公式 | hardwareMax | 超频增量 |
|------|---------|--------|---------|------|-------------|---------|
| 单仓×普通 | 1x | 1 | 0 | 1×(1+0) | **1x** | +0 |
| 双仓×普通 | 2x | 2 | 0 | 2×(2+0) | **4x** | +2 |
| 双仓+二联×2 | 2x | 0 | 4 | 2×(0+8) | **16x** | +4 |
| 四仓+四联×4 | 2x | 0 | 16 | 2×(0+32) | **64x** | +6 |

### 11.3 双仓升压的额外效果

双仓升压不仅倍增硬件 GraceReceived，还允许 **goblinRecipeTier +1**（分担电流，等效提高电压等级）。

### 11.4 节流降级

```
hardwareMax = 16x

玩家配置 = 16x → goblinRecipeTier = +4, 超频可用, 全速
玩家配置 = 8x  → goblinRecipeTier = +3, 超频降一级
玩家配置 = 4x  → goblinRecipeTier = +2
...
```

### 11.5 小机器高流接入

小机器（单方块）也允许接入 **1 个高电流接入点**，带来 2x 的 maxGraceReceived 增益：

| 配置 | 升压增益 | low 点 | high 点 | 公式 | hardwareMax | 超频增量 |
|------|---------|--------|---------|------|-------------|---------|
| 单方块+二联×1 | 1x | 0 | 1 | 1×(0+2) | **2x** | +0 |

**关键**：2x 增益来自高流接入点本身的吞吐翻倍，但仅 2x 不足以触发超频——`goblinRecipeTier` 不变。这部分增益仅用于**并行**（`getMaxParallelByInput` 可以识别更高的 GraceReceived），不改变等级判断。

### 11.6 低电流惩罚

任何高电流接入点（二联/四联）在实际通过的**电流小于 4A** 时，该接入点对应的处理速度降为 **1/2**。

| 条件 | 电流 < 4A | 电流 ≥ 4A |
|------|-----------|-----------|
| 处理速度倍率 | ×1（被惩罚，修正为 ×0.5） | ×2（正常高流高增益） |
| GraceReceived | 不变 | 不变 |

**设计意图**：高流接入点设计为高位电流输送，玩家用低压电源驱动高位接口时，虽然不影响能量吞吐（GraceReceived），但处理速度会打折扣，迫使玩家匹配适当的电压等级。

**与熔断阈值配合**：默认最低处理速度已下调至 0.4（见 5.3 节），即使被惩罚降至 `×0.5` 仍高于 0.4，配方不会中断。

---

## 12. pourSanctifiedBlessing 倾倒祝福态

### 12.1 问题

配方完成时（`progress >= duration`），`blessingStock` 可能仍有残余祝福需要倾倒。如果直接进入 `onRecipeFinish()`，这部分祝福会被丢弃。需要一段只倾倒不接收祭品的过渡阶段。

### 12.2 流程图

```
handleRecipeWorking():
  progress >= duration ?
    YES → pourSanctifiedBlessing()
    │      ├─ blessingStock <= 0 → onRecipeFinish()
    │      │
    │      ├─ evaluateBlessing(blessingYield)   ← 只倾倒
    │      ├─ offerRate = 0                          ← 不接收
    │      ├─ pietyLevel = pourRate (绕过 min(0, x))
    │      ├─ divineEffort = piety × blessedEffort
    │      ├─ divineEffort == 0 → WAITING
    │      └─ performDivineWork() → totalContinuousRunningTime++
    │
    NO  → assessBelieverState() (正常管线)
           ├─ progress += divineEffort
           └─ ...
```

### 12.3 正确的 pietyLevel

倾倒态不能调用通用的 `calculatePietyLevel()`（`min(0, pourRate) = 0`），需要直接赋值：

```java
// 倾倒态专用
believer.setOfferRate(0f);
believer.setPietyLevel(believer.getPourRate());  // 绕过 min(0, x)
believer.setDivineEffort((int)(believer.getPietyLevel() * believer.getBlessedEffort()));
```

### 12.4 onRecipeFinish 守卫

```java
@Override
public void onRecipeFinish() {
    if (believer().getBlessingStock() > 0) {
        return;  // 倾倒未完成，等待下一 tick pourSanctifiedBlessing
    }
    // ... 原有逻辑
}
```

---

## 13. 更新计划

| 优先级 | 任务 | 状态 |
|--------|------|------|
| 🔴 | 创建 `ScriptureAptitude` | 待实现 |
| 🔴 | 创建 `AscensionBenediction` | 待实现 |
| 🔴 | `IBelieverOfScripture` 新增 `isAwakened` / `getMaxEndurance` / `getMaxOffering` / `devotionGrade` / 节流属性 | 待实现 |
| 🔴 | `GoblinOracleOfScripture.extractOfferingDemand()` 用 `instanceof` | 待实现 |
| 🔴 | `GoblinOracleOfScripture.checkRecipe()` 覆写 | 待实现 |
| 🔴 | `GoblinOracleOfScripture.pourSanctifiedBlessing()` 倾倒态 | 待实现 |
| 🔴 | 架构文档同步 | 已完成 |
| 🟡 | `IBelieverOfScripture.getRitualTempo()` | 下个会话 |
| 🟡 | `IDivineInputSource` 抽象接口 | 下个会话 |
| 🟢 | KineticOffering 实现 | 后续 |
| 🟢 | ThunderOffering 实现 | 后续 |
| 🟢 | FuelOffering（内嵌小配方执行器）实现 | 后续 |
