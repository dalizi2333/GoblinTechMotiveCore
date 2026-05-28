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
| 圣恩容槽 (Grace) | 输入能量栈 | `graceStock` / `graceCapacity` / `graceThreshold` | 信徒从神祇接收的恩赐储备 |
| 奉献容槽 (Offering) | 输出能量栈 | `offeringStock` / `offeringCapacity` / `offeringThreshold` | 信徒向神祇回馈的奉献储备 |

### 2.2 领域分类扩展

| 领域 | 神谕者 | 信徒接口 | 神谕条件 |
|------|-------|---------|---------|
| 配方经文 | `GoblinOracleOfScripture` | `IBelieverOfScripture` | `IDivineDecreeOfScripture` |
| 结构验证 | `GoblinOracleOfStructure` | `IBelieverOfStructure` | `IDivineDecreeOfStructure` |
| 能量网络 | `GoblinOracleOfPower` | `IBelieverOfPower` | `IDivineDecreeOfPower` |

---

## 三、类命名规范

### 3.1 机器类命名

| 类名 | 继承关系 | 说明 |
|------|---------|------|
| `GoblinPartMachine` | 继承 `MultiblockPartMachine` | 多方块 Part 组件 |
| `GoblinTieredPartMachine` | 继承 `TieredPartMachine` | 带等级的 Part |
| `GoblinTieredIOPartMachine` | 继承 `TieredIOPartMachine` | 带 IO 的 Tiered Part |
| `GoblinControllerMachine` | 继承 `MultiblockControllerMachine` | 多方块控制器 |
| `GoblinWorkableMachine` | 继承 `MetaMachine`，实现 `IBelieverOfScripture` | 单方块工作机 |

### 3.2 引擎/核心逻辑类

| 类名 | 职责 | 状态 |
|------|------|------|
| `GoblinMachineDeity` | 掌管多方块机器成型状态 | 计划中 |
| `GoblinOracleOfScripture` | 掌管配方执行逻辑 | 对应原 `GoblinRecipeLogic` |

### 3.3 内部实现类（不加 Goblin 前缀）

| 类名 | 说明 |
|------|------|
| `StructureBitmap` | 结构位图（Deity 内部使用） |
| `SectionedBitmap` | 分段位图（Deity 内部使用） |
| `EnclosureValidator` | 围合验证器（Deity 内部使用） |
| `AnnoyanceTracker` | 厌烦追踪器（Deity 内部使用） |
| `RecipeContext` | 配方上下文（Oracle 内部使用） |

---

## 四、接口命名规范

### 4.1 信徒接口

| 接口名 | 说明 |
|--------|------|
| `IBelieverOfScripture` | 信仰配方经文的机器 |
| `IBelieverOfStructure` | 信仰结构之神的机器 |
| `IBelieverOfPower` | 信仰能量之神的机器 |

### 4.2 神谕条件接口

| 接口名 | 说明 |
|--------|------|
| `IDivineDecreeOfScripture` | 配方经文的条件约束 |
| `IDivineDecreeOfStructure` | 结构验证的条件约束 |
| `IDivineDecreeOfPower` | 能量网络的条件约束 |

---

## 五、方法命名规范

### 5.1 信徒接口方法

| 方法名 | 说明 |
|--------|------|
| `getPietyLevel()` | 获取虔诚度（效率系数） |
| `getBlessedEffort()` | 获取神恩加持的努力（基础工作速率） |
| `isPious()` | 检查是否虔诚 |
| `onDivineRetribution()` | 接受神罚（错误处理回调） |

### 5.2 神谕者方法

| 方法名 | 说明 |
|--------|------|
| `setupScripture()` | 解读经文并初始化 |
| `consultDivinePower()` | 向神祇祈求指引（条件验证） |
| `executeDivineWork()` | 执行神圣工作（配方执行） |
| `applyDivineRetribution()` | 执行神罚（错误处理） |

---

## 六、字段命名规范

### 6.1 核心字段

| 字段名 | 说明 |
|--------|------|
| `pietyLevel` | 虔诚度（效率系数，0.0~1.0） |
| `blessedEffort` | 神恩加持的努力（基础工作速率） |
| `divineEffort` | 神圣努力（实际工作速率） |
| `graceStock` | 圣恩容槽存量（已接收的恩赐能量） |
| `graceCapacity` | 圣恩容槽容量 |
| `graceThreshold` | 圣恩容槽安全阈值 |
| `offeringStock` | 奉献容槽存量（待输出的奉献能量） |
| `offeringCapacity` | 奉献容槽容量 |
| `offeringThreshold` | 奉献容槽安全阈值 |
| `graceReceived` | 本 tick 已接收的恩典 |
| `graceOffered` | 本 tick 已奉献的恩典 |
| `divineDemand` | 经文要求的输入能量（神谕需求） |
| `divineOffering` | 经文要求的输出能量（神谕奉献） |
| `currentScripture` | 当前解读的经文 |

### 6.2 命名风格

- **驼峰命名**：使用小驼峰命名法（`camelCase`）
- **语义明确**：避免缩写，使用完整单词
- **避免歧义**：使用 `divineBlessing` 而非 `blessing`

---

## 七、双层面注释规范

### 7.1 注释结构

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

### 7.2 类级注释示例

```java
/**
 * 【神棍描述】经文神谕者 - 配方之神在人间的代言人
 * 
 * <p>【工程描述】配方执行逻辑控制器，继承自 GTCEu {@link RecipeLogic}。
 * 负责解析配方数据、管理工作进度、处理能量输入输出。
 * 
 * <p>【核心职责】
 * <ul>
 *   <li>解读经文：解析 {@link GoblinScripture} 中的配方数据</li>
 *   <li>神恩判定：根据信徒虔诚度计算工作速率加成</li>
 *   <li>神谕验证：检查 {@link IDivineDecreeOfScripture} 条件</li>
 * </ul>
 */
public class GoblinOracleOfScripture extends RecipeLogic {
    // ...
}
```

### 7.3 方法级注释示例

```java
/**
 * 【神棍描述】向神祇祈求指引，检查神谕是否被满足
 * 
 * <p>【工程描述】遍历所有神谕条件，逐一验证是否满足。
 * 
 * @return {@link ActionResult#SUCCESS} 如果所有条件满足
 */
protected ActionResult consultDivinePower() {
    // ...
}
```

### 7.4 字段级注释示例

```java
/**
 * 【神棍描述】神恩等级 - 神祇赐予的神圣加成
 * 
 * <p>【工程描述】工作速率加成倍数，计算公式：
 * <pre>
 * divineBlessing = pietyLevel × scripture.sanctityLevel
 * </pre>
 */
protected float divineBlessing = 1.0f;
```

---

## 八、示例代码

### 8.1 完整接口示例

```java
/**
 * 【神棍描述】经文信徒 - 信仰配方经文的机器
 * 
 * <p>【工程描述】可执行哥布林配方的机器接口。
 */
public interface IBelieverOfScripture {
    
    /**
     * 【神棍描述】获取信徒的虔诚度
     * 
     * <p>【工程描述】返回效率系数，范围 0.0~1.0。
     * 
     * @return 效率系数
     */
    float getPietyLevel();
    
    /**
     * 【神棍描述】获取神恩加持的努力
     * 
     * <p>【工程描述】返回基础工作速率。
     * 实际工作速率 = blessedEffort × divineBlessing
     * 
     * @return 基础工作速率
     */
    float getBlessedEffort();
    
    /**
     * 【神棍描述】检查信徒是否仍然虔诚
     * 
     * <p>【工程描述】判断是否满足最低工作条件（虔诚度 >= 0.3）。
     */
    boolean isPious();
    
    /**
     * 【神棍描述】接受神罚
     * 
     * <p>【工程描述】错误处理回调。
     */
    void onDivineRetribution();
}
```

### 8.2 完整类示例

```java
/**
 * 【神棍描述】经文神谕者 - 配方之神在人间的代言人
 * 
 * <p>【工程描述】配方执行逻辑控制器。
 */
public class GoblinOracleOfScripture extends RecipeLogic {
    
    /**
     * 【神棍描述】当前解读的经文
     * 
     * <p>【工程描述】存储当前执行的配方数据。
     */
    protected @Nullable GoblinScripture currentScripture;
    
    /**
     * 【神棍描述】信徒的虔诚度
     * 
     * <p>【工程描述】效率系数，由评估信徒状态时计算得出。
     */
    protected float pietyLevel = 1.0f;

    public GoblinOracleOfScripture(IBelieverOfScripture believer) {
        super((IRecipeLogicMachine) believer);
    }

    private IBelieverOfScripture believer() {
        return (IBelieverOfScripture) machine;
    }

    @Override
    public void executeDivineWorkCycle() {
        progress += believer().getDivineEffort();
    }
}
```

---

## 九、命名规范检查表

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

---

## 十、版本历史

| 版本 | 日期 | 变更说明 |
|------|------|---------|
| 1.0 | 2026-05-28 | 初始版本，定义核心命名规范 |
| 1.1 | 2026-05-28 | 新增 Grace/Offering 字段映射；删除 kinetic 条目；更新示例代码 |
