# GoblinTech 命名规范

## 概述

本文档定义了 GoblinTech 项目的统一命名规范，包括类、接口、方法、字段的命名规则，以及神话叙事与工程术语的映射关系。

---

## 一、核心命名原则

### 1.1 Goblin 前缀使用规则

| 场景 | 是否使用 Goblin 前缀 | 理由 |
|------|---------------------|------|
| **继承自 GTCEu/Create 核心类** | ✅ 必须 | 明确标识这是 GoblinTech 的定制版本 |
| **领域顶层引擎/神** | ✅ 必须 | 如 `GoblinMachineDeity`，作为核心系统入口 |
| **对外暴露的 API 类** | ✅ 建议 | 便于使用者识别归属 |
| **内部实现类（同包）** | ❌ 不建议 | 如 `StructureBitmap`、`AnnoyanceTracker` |
| **数据结构/工具类** | ❌ 不建议 | 如 `CountConstraint`、`PlacementResult` |

### 1.2 命名模式

```
[前缀] + [角色] + Of + [领域]
```

| 组件类型 | 命名模式 | 示例 |
|---------|---------|------|
| 神谕者 | `GoblinOracleOf[Domain]` | `GoblinOracleOfScripture` |
| 经文 | `GoblinScripture` | `GoblinScripture` |
| 信徒接口 | `IBelieverOf[Domain]` | `IBelieverOfScripture` |
| 神谕条件 | `IDivineDecreeOf[Domain]` | `IDivineDecreeOfScripture` |

---

## 二、神话概念与工程概念映射

### 2.1 核心映射表

| 神话概念 | 工程概念 | 推荐命名 | 含义解读 |
|---------|---------|---------|---------|
| 神谕者 (Oracle) | 配方逻辑控制器 | `GoblinOracleOfScripture` | 管理配方执行流程的核心组件 |
| 经文 (Scripture) | 配方数据对象 | `GoblinScripture` | 存储配方输入输出、能量需求等数据 |
| 信徒 (Believer) | 机器实体 | `IBelieverOfScripture` | 执行配方的机器实例 |
| 虔诚度 (Piety) | 效率系数 | `pietyLevel` | 机器状态对工作效率的影响因子 |
| 神恩加持的努力 | 基础工作速率 | `blessedEffort` | 神祇赐予的基础劳动能力 |
| 神圣努力 | 实际工作速率 | `divineEffort` | 机器自管理的实际工作速率 |
| 神谕 (Decree) | 条件约束 | `divineDecree` | 配方执行的前置条件检查 |
| 祭品容槽 (Offering) | 输入能量栈 | `offeringStock` / `offeringCapacity` / `offeringThreshold` | 信徒从哥布林接收的祭品储备 |
| 祝福容槽 (Blessing) | 输出能量栈 | `blessingStock` / `blessingCapacity` / `blessingThreshold` | 信徒向哥布林回馈的祝福储备 |

### 2.2 领域分类扩展

| 领域 | 神谕者 | 信徒接口 | 神谕条件 |
|------|-------|---------|---------|
| 配方经文 | `GoblinOracleOfScripture` | `IBelieverOfScripture` | `IDivineDecreeOfScripture` |
| 结构验证 | `GoblinOracleOfStructure` | `IBelieverOfStructure` | `IDivineDecreeOfStructure` |
| 能量网络 | `GoblinOracleOfPower` | `IBelieverOfPower` | `IDivineDecreeOfPower` |

### 2.3 四层术语严格分层原则

以下四对术语是**不可混淆的铁律**，任何文档、代码、注释中必须严格按层级使用：

```
配方层    Input — Output     （物品或流体）
配方层    demand — yield      （祭品 / 祝福）
Tick 层   Offering — Blessing （祭品 / 祝福）
机器层    Bosom — Endurance   （祭品 / 祝福）
```

| 层级 | 英文术语对 | 中文 | 适用对象 | 示例 |
|------|----------|------|---------|------|
| **配方** | `Input` / `Output` | 输入 / 输出 | 配方层面的物品、流体 | 配方定义了 Input × N → Output × M |
| **配方** | `demand` / `yield` | 需求 / 产出 | 经文中定义的祭品总量 / 祝福总量 | `offeringDemand`、`blessingYield` |
| **Tick** | `Offering` / `Blessing` | 祭品 / 祝福 | 每 tick 的祭品 / 祝福 | `divineOffering`、`divineBlessing`、`IOfferingModule`、`IBlessingModule` |
| **机器** | `Bosom` / `Endurance` | 胸怀 / 耐力 | `IBelieverOfScripture` 的缓存值 | `maxBosom`、`maxEndurance` |

**关键规则**：

- `Endurance` **仅**出现在 `IBelieverOfScripture` 机器缓存层（`maxEndurance`）
- `OfferingBlessingManager` 及其以下层级（模块接口、Manager API）使用 `Blessing`，不是 `Endurance`
- `Blessing` 用于 Tick 层和模块层，`Endurance` 用于机器缓存层
- `Input`/`Output` 用于配方物品/流体，`Offering`/`Blessing` 用于能量层面的消耗/产出

---

## 三、包结构与 GTCEu 对齐

**包名不改**，但结构保持与 GTCEu 一致，增加可读性。`com.goblincoders.goblintech` 下的子包镜像 `com.gregtechceu.gtceu` 的布局。

### 3.1 包对照表

| 包路径 | GTCEu 对应包 | 说明 |
|--------|-------------|------|
| `api.recipe` | `api.recipe` | 配方数据（GoblinScripture、RecipeContext、RecipeMode） |
| `api.recipe.condition` | `api.recipe.condition` | 条件与失败行为（IDivineDecreeOfScripture、ConditionFailBehavior） |
| `api.recipe.modifier` | `api.recipe.modifier` | 输出修改器（RecipeOutputModifier） |
| `api.machine.feature` | `api.machine.feature` | 机器特性接口（IBelieverOfScripture ↔ IRecipeLogicMachine） |
| `api.machine.trait` | `api.machine.trait` | 机器 trait（GoblinOracleOfScripture ↔ RecipeLogic） |

### 3.2 文件对照表

| GoblinTech | GTCEu | 角色 |
|-----------|-------|------|
| `api/recipe/GoblinScripture.java` | `api/recipe/GTRecipe.java` | 经文 |
| `api/recipe/RecipeMode.java` | — | 经文解读模式 |
| `api/recipe/RecipeContext.java` | — | 经文上下文 |
| `api/recipe/condition/IDivineDecreeOfScripture.java` | `api/recipe/condition/RecipeConditionType.java` | 神谕条件接口 |
| `api/recipe/condition/ConditionFailBehavior.java` | — | 失败行为枚举 |
| `api/recipe/modifier/RecipeOutputModifier.java` | `api/recipe/modifier/` | 输出修改器 |
| `api/machine/feature/IBelieverOfScripture.java` | `api/machine/feature/IRecipeLogicMachine.java` | 信徒接口 |
| `api/machine/trait/GoblinOracleOfScripture.java` | `api/machine/trait/RecipeLogic.java` | 神谕者 |

---

## 四、类命名规范

### 4.1 机器类命名

| 类名 | 继承关系 | 说明 |
|------|---------|------|
| `GoblinPartMachine` | 继承 `MultiblockPartMachine` | 多方块 Part 组件 |
| `GoblinTieredPartMachine` | 继承 `TieredPartMachine` | 带等级的 Part |
| `GoblinTieredIOPartMachine` | 继承 `TieredIOPartMachine` | 带 IO 的 Tiered Part |
| `GoblinControllerMachine` | 继承 `MultiblockControllerMachine` | 多方块控制器 |
| `GoblinWorkableMachine` | 继承 `MetaMachine`，实现 `IBelieverOfScripture` | 单方块工作机 |

### 4.2 引擎/核心逻辑类

| 类名 | 职责 | 包路径 | 状态 |
|------|------|--------|------|
| `GoblinOracleOfScripture` | 掌管配方执行逻辑 | `api.machine.trait` | 对应原 `GoblinRecipeLogic` |
| `GoblinMachineDeity` | 掌管多方块机器成型状态 | — | 计划中 |

### 4.3 内部实现类（不加 Goblin 前缀）

| 类名 | 说明 |
|------|------|
| `StructureBitmap` | 结构位图（Deity 内部使用） |
| `SectionedBitmap` | 分段位图（Deity 内部使用） |
| `EnclosureValidator` | 围合验证器（Deity 内部使用） |
| `AnnoyanceTracker` | 厌烦追踪器（Deity 内部使用） |
| `RecipeContext` | 配方上下文（`api.recipe` 包，神谕者内部使用） |

---

## 五、接口命名规范

### 5.1 信徒接口

| 接口名 | 位置 | 说明 |
|--------|------|------|
| `IBelieverOfScripture` | `api.machine.feature` | 信仰配方经文的机器，GTCEu 对应 `IRecipeLogicMachine` |
| `IBelieverOfStructure` | — | 信仰结构之神的机器 |
| `IBelieverOfPower` | — | 信仰能量之神的机器 |

### 5.2 神谕条件接口

| 接口名 | 位置 | 说明 |
|--------|------|------|
| `IDivineDecreeOfScripture` | `api.recipe.condition` | 配方经文的条件约束 |

---

## 六、方法命名规范

### 6.1 信徒接口方法（`IBelieverOfScripture`）

| 方法名 | 神棍描述 | 工程说明 |
|--------|---------|---------|
| `getPietyLevel()` | 获取虔诚度 | 效率系数 0.0~1.0 |
| `setPietyLevel()` | 设置虔诚度 | — |
| `getBlessedEffort()` | 神恩加持的努力 | 基础工作速率（进度缩放因子），默认 16 |
| `getDivineEffort()` | 获取神圣努力 | 实际工作速率 |
| `setDivineEffort()` | 设置神圣努力 | — |
| `getOfferRate()` | 获取祭品供应速率 | 0.0~1.0 |
| `setOfferRate()` | 设置祭品供应速率 | — |
| `getPourRate()` | 获取祝福倾泻速率 | 0.0~1.0 |
| `setPourRate()` | 设置祝福倾泻速率 | — |
| `evaluateOffering()` | 评估祭品供应 | 调用 receiveOffering 后按比例缩减 offerRate |
| `evaluateBlessing()` | 评估祝福倾泻 | 调用 pourBlessing 后按比例缩减 pourRate |
| `calculatePietyLevel()` | 计算虔诚度 | pietyLevel = min(offerRate, pourRate) |
| `determineDivineEffort()` | 确定神圣努力 | divineEffort = pietyLevel × blessedEffort |
| `receiveOffering()` | 接收祭品 | 将外部能量/物品映射至 offeringStock |
| `pourBlessing()` | 倾泻祝福 | 将 blessingStock 映射回外部能量/物品 |
| `performDivineWork()` | 执行神圣工作 | 实际完成能量充能/放能操作 |

### 6.2 神谕者方法（`GoblinOracleOfScripture`）

| 方法名 | 神棍描述 | 工程说明 | 继承关系 |
|--------|---------|---------|---------|
| `handleRecipeWorking()` | 执行神圣工作循环 | 每 tick 主循环入口 | 覆写 `RecipeLogic` |
| `assessBelieverState()` | 评估信徒状态 | 核心每 tick 逻辑 | 新增 |
| `verifyDivineDecree()` | 验证神谕 | 遍历条件，默认 pietyLevel < 0.5 → WAITING | 新增 |
| `setupRecipe()` | 解读经文 | 提取 duration/divineDemand/divineOffering，应用进度缩放 | 覆写 `RecipeLogic` |
| `getProgress()` | 经文完成份数 | 对外展示时除以 blessedEffort | 覆写 `RecipeLogic` |
| `getMaxProgress()` | 经文总份数 | 对外展示总进度时除以 blessedEffort | 覆写 `RecipeLogic` |
| `frozen()` | 冻结 | 返回 PROGRESS_FROZEN 状态 | 新增 static |
| `isFrozen()` | 是否冻结 | 判断是否是 PROGRESS_FROZEN | 新增 static |

---

## 七、字段命名规范

### 7.1 核心字段

| 字段名 | 说明 |
|--------|------|
| `pietyLevel` | 虔诚度（效率系数，0.0~1.0） |
| `blessedEffort` | 神恩加持的努力（基础工作速率） |
| `divineEffort` | 神圣努力（实际工作速率） |
| `offeringStock` | 祭品容槽存量（已接收的祭品能量） |
| `offeringCapacity` | 祭品容槽容量 |
| `offeringThreshold` | 祭品容槽安全阈值 |
| `blessingStock` | 祝福容槽存量（待输出的祝福能量） |
| `blessingCapacity` | 祝福容槽容量 |
| `blessingThreshold` | 祝福容槽安全阈值 |
| `offeringReceived` | 本 tick 已接收的祭品 |
| `blessingPoured` | 本 tick 已倾泻的祝福 |
| `offeringDemand` | 经文要求的祭品量（神谕祭品需求） |
| `blessingYield` | 经文产出的祝福量（神谕祝福产出） |
| `currentScripture` | 当前解读的经文 |

### 7.2 命名风格

- **驼峰命名**：使用小驼峰命名法（`camelCase`）
- **语义明确**：避免缩写，使用完整单词
- **避免歧义**：使用 `divineBlessing` 而非 `blessing`

---

## 八、双层面注释规范

### 8.1 注释结构

```java
/**
 * 【神棍描述】- 用神话叙事描述职责和寓意
 * 
 * <p>【工程描述】- 用技术术语描述实际功能和实现细节
 * 
 * <p>【核心职责】（可选）- 列出主要功能点
 * <ul>
 *   <li>职责1：功能说明</li>
 *   <li>职责2：功能说明</li>
 * </ul>
 * 
 * <p>【技术特性】（可选）- 关键技术实现细节
 * <ul>
 *   <li>特性1：技术说明</li>
 *   <li>特性2：技术说明</li>
 * </ul>
 */
```

### 8.2 类级注释示例

```java
/**
 * 【神棍描述】经文神谕者 — 配方之神在人间的代言人
 * 
 * <p>【工程描述】配方执行逻辑控制器，继承自 GTCEu {@link RecipeLogic}。
 * 负责解析经文数据、评估信徒状态、管理工作进度、处理能量输入输出。
 * 
 * <p>【核心职责】
 * <ul>
 *   <li>解读经文：解析 {@link com.goblincoders.goblintech.api.recipe.GoblinScripture} 中的配方数据</li>
 *   <li>评估信徒：通过 {@code assessBelieverState()} 计算虔诚度与神圣努力</li>
 *   <li>验证神谕：检查 {@link com.goblincoders.goblintech.api.recipe.condition.IDivineDecreeOfScripture} 条件</li>
 * </ul>
 */
public class GoblinOracleOfScripture extends RecipeLogic {
    // ...
}
```

### 8.3 方法级注释示例

```java
/**
 * 【神棍描述】评估信徒状态 — 检查能量输入输出，计算虔诚度与神圣努力
 * 
 * <p>【工程描述】核心每 tick 逻辑，由 {@link #handleRecipeWorking()} 调用。
 * 
 * @return SUCCESS 或对应失败状态
 */
protected ActionResult assessBelieverState() {
    // ...
}
```

### 8.4 字段级注释示例

```java
/**
     * 【神棍描述】神谕祭品要求 — 经文要求的祭品量
     * 
     * <p>【工程描述】从配方 tickInputs 中解析的能量/物品需求值，每 tick 用于评估信徒祭品供应速率。
     */
    protected int offeringDemand;
```

---

## 九、示例代码

### 9.1 完整接口示例

```java
/**
 * 【神棍描述】经文信徒 — 信仰配方经文的机器
 * 
 * <p>【工程描述】可执行哥布林配方的机器接口，定义能量容槽与速率属性。
 * 位于 {@code api.machine.feature} 包，GTCEu 对应 {@code IRecipeLogicMachine}。
 */
public interface IBelieverOfScripture extends IRecipeLogicMachine {

    /**
     * 【神棍描述】神恩加持的努力 — 神祇赐予的基础劳动能力
     * <p>【工程描述】基础工作速率（进度缩放因子），默认 16。
     * @return 基础工作速率
     */
    default int getBlessedEffort() { return 16; }

    /**
     * 【神棍描述】获取虔诚度
     * <p>【工程描述】效率系数 0.0~1.0。
     * @return 效率系数
     */
    float getPietyLevel();

    /**
     * 【神棍描述】获取神圣努力
     * <p>【工程描述】机器自管理的实际工作速率。
     * @return 实际工作速率
     */
    int getDivineEffort();

    /**
     * 【神棍描述】接收祭品
     * <p>【工程描述】将外部能量/物品映射至 offeringStock。
     * @param offeringDemand 经文要求的祭品量
     * @return 操作结果
     */
    default ActionResult receiveOffering(int offeringDemand) {
        setOfferingStock(0);
        setOfferingReceived(0);
        return ActionResult.SUCCESS;
    }

    /**
     * 【神棍描述】倾泻祝福
     * <p>【工程描述】将 blessingStock 映射回外部能量/物品。
     * @param blessingYield 经文产出的祝福量
     * @return 操作结果
     */
    default ActionResult pourBlessing(int blessingYield) {
        setBlessingStock(getBlessingCapacity());
        setBlessingPoured(0);
        return ActionResult.SUCCESS;
    }

    /**
     * 【神棍描述】评估祭品供应
     * <p>【工程描述】调用 receiveOffering 后按比例缩减 offerRate。
     */
    default void evaluateOffering(int offeringDemand) {
        var result = receiveOffering(offeringDemand);
        if (!result.isSuccess()) { setOfferRate(0); return; }
        if (getOfferingStock() < getOfferingThreshold())
            setOfferRate(Math.min(1f, (float) getOfferingReceived() / offeringDemand));
        else
            setOfferRate(1f);
    }

    /**
     * 【神棍描述】评估祝福倾泻
     * <p>【工程描述】调用 pourBlessing 后按比例缩减 pourRate。
     */
    default void evaluateBlessing(int blessingYield) {
        var result = pourBlessing(blessingYield);
        if (!result.isSuccess()) { setPourRate(0); return; }
        if (getBlessingStock() > getBlessingThreshold())
            setPourRate(Math.min(1f, (float) getBlessingPoured() / blessingYield));
        else
            setPourRate(1f);
    }

    /**
     * 【神棍描述】计算虔诚度
     * <p>【工程描述】pietyLevel = min(offerRate, pourRate)。
     */
    default void calculatePietyLevel() {
        setPietyLevel(Math.min(getOfferRate(), getPourRate()));
    }

    /**
     * 【神棍描述】执行神圣工作
     * <p>【工程描述】实际完成祭品接收/祝福倾泻操作。
     * @return 操作结果
     */
    ActionResult performDivineWork();
}
```

### 9.2 完整神谕者示例

```java
/**
 * 【神棍描述】经文神谕者 — 配方之神在人间的代言人
 * 
 * <p>【工程描述】配方执行逻辑控制器，继承自 GTCEu {@link RecipeLogic}。
 * 位于 {@code api.machine.trait} 包，GTCEu 对应 {@code RecipeLogic}。
 */
public class GoblinOracleOfScripture extends RecipeLogic {

    /**
     * 【神棍描述】当前解读的经文上下文
     * <p>【工程描述】存储配方执行过程中捕获的输入信息。
     */
    protected @Nullable RecipeContext scriptureContext;

    /**
     * 【神棍描述】神谕祭品要求
     * <p>【工程描述】经文要求的 tick 祭品量。
     */
    protected int offeringDemand;

    public GoblinOracleOfScripture(IBelieverOfScripture believer) {
        super(believer);
    }

    private IBelieverOfScripture believer() {
        return (IBelieverOfScripture) machine;
    }

    @Override
    public void handleRecipeWorking() {
        var result = assessBelieverState();
        if (result.isSuccess() && !isFrozen(result)) {
            progress += believer().getDivineEffort();
        }
    }

    /**
     * 【神棍描述】评估信徒状态 — 检查能量输入输出，计算虔诚度与神圣努力
     * <p>【工程描述】核心每 tick 逻辑。
     */
    protected ActionResult assessBelieverState() {
        IBelieverOfScripture believer = believer();
        if (offeringDemand != 0) believer.evaluateOffering(offeringDemand);
        else believer.setOfferRate(1f);
        believer.calculatePietyLevel();
        believer.determineDivineEffort();
        return believer.performDivineWork();
    }
}
```

---

## 十、命名规范检查表

| 检查项 | 要求 |
|--------|------|
| ✅ | 继承 GTCEu/Create 核心类的必须加 `Goblin` 前缀 |
| ✅ | 领域顶层引擎类必须加 `Goblin` 前缀 |
| ✅ | 对外接口使用 `IBelieverOf[Domain]` 命名模式 |
| ✅ | 内部实现类不加 `Goblin` 前缀 |
| ✅ | 方法和字段使用小驼峰命名 |
| ✅ | 类级注释包含神棍描述和工程描述 |
| ✅ | 关键字段和方法有详细注释 |
| ✅ | 神话概念与工程概念有清晰映射 |
| ✅ | 包结构对齐 GTCEu（`api.recipe`、`api.machine.feature`、`api.machine.trait`） |

---

## 十一、版本历史

| 版本 | 日期 | 变更说明 |
|------|------|---------|
| 1.0 | 2026-05-28 | 初始版本，定义核心命名规范 |
| 1.1 | 2026-05-28 | 新增 Grace/Offering 字段映射；删除 kinetic 条目；更新示例代码 |
| 1.2 | 2026-05-28 | 新增包结构与 GTCEu 对齐章节；修正方法名为实际代码名；更新所有示例代码 |
