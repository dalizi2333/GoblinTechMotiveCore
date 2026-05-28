# GoblinMachine 幽灵外壳架构

## 1. 概述

### 1.1 设计目标

为 GoblinTechMotive 的多方块 Part 机器提供"幽灵外壳/伪装方块"能力——Part 机器可以在运行时替换外观纹理（包括 Create 的 CTM 连接纹理），同时保持机器原有功能。外部看是普通方块，内部是 GTCEu 多方块部件。

核心需求：
1. **伪装外观**：Part 机壳纹理即时变化，使用物品操作后替换为目标方块的完整外观（含 CTM）
2. **CTM 连接**：相邻方块正确识别本机外观，实现连续纹理
3. **蓝图建造**：参考 Create 强力胶 + 蓝图机制，先定义结构再放置/替换
4. **多供能单方块**：WorkableTieredGoblinMachine 原生支持电能/动能/蒸汽等
5. **控制器改造**：MultiblockControllerGoblinMachine 阻止老旧定时检查，改用事件驱动

### 1.2 设计原则

- **最小侵入**：GoblinMultiblockPartMachine 继承 GTCEu 的 `MultiblockPartMachine`，只覆写一个方法
- **原生渲染**：覆写 `MetaMachine.getBlockAppearance()` 返回 hullState，Minecraft 原生渲染管线处理一切（含 Create CTM）
- **无需 DynamicRender**：不需要透明底座 + 动态渲染来绘制外壳
- **无需 IGhostMachine 渲染层**：伪装外观通过 GTCEu 原生外观系统实现
- **事件驱动**：结构验证不再定时轮询，改为参考 Create `EntityPlaceEvent` 事件驱动 + BFS 漫水填充

### 1.3 核心洞察：getBlockAppearance() 足矣

GTCEu 注册多方块机器时有 `.appearanceBlock(CASING)` 参数，本质是告诉系统"这个多方块成型后，Part 方块看起来像这个 CASING"。其渲染链路为：

```
NeoForge Block.getAppearance()        ← Minecraft 原生 CTM 查询入口（Create CTM 从此查询相邻方块）
  → MetaMachineBlock.getAppearance()  ← GTCEu 转发给机器实例
    → MetaMachine.getBlockAppearance() ← ★ 关键方法！
      → 先查 Cover 是否覆写了外观
      → 再查 IMultiPart.getFormedAppearance()（如多方块已成型）
      → 最后 fallback 到 getDefinition().getAppearance()（即 .appearanceBlock() 指定的默认壳）
```

**关键发现**：在 `GoblinMultiblockPartMachine` 中直接覆写 `getBlockAppearance()` 返回存储的 `hullState`，就能让 Minecraft 原生渲染管线渲染目标方块的完整模型——包括 Create 的 CTM。因为 CTM 通过 NeoForge 的 `Block.getAppearance()` 查询相邻方块外观，而我们的覆写恰好在这个入口处返回了伪装方块。

**结论**：不需要 `DynamicRender`、不需要透明底座模型、不需要 `IGhostMachine` 的渲染层面。只需要一个方法覆写。

---

## 2. 包名架构

### 2.1 包结构

| 包名 | 层级 | 职责 |
|------|------|------|
| `com.goblincoders.goblintech.api.machine.multiblock.part` | API | GoblinMultiblockPartMachine、TieredPartGoblinMachine、TieredIOPartGoblinMachine |
| `com.goblincoders.goblintech.api.machine.multiblock` | API | MultiblockControllerGoblinMachine（未来） |
| `com.goblincoders.goblintech.api.machine.deity` | API | GoblinMachineDeity、IDeityOracle、StructureBitmap、SectionedBitmap、EnclosureValidator |
| `com.goblincoders.goblintech.api.machine` | API | WorkableTieredGoblinMachine（未来） |
| `com.goblincoders.goblintech.api.item` | API | ShamanItem（蓝图腾） |
| `com.goblincoders.goblintech.client.shaman` | 客户端 | ShamanItemHandler（Outliner 可视化 + 仪式交互） |
| `com.goblincoders.goblintech.network.shaman` | 网络 | ShamanNetwork（仪式确认网络包） |

> **已移除**：`com.goblincoders.goblintech.api.machine.feature.multiblock` — GhostPartHullRender/RenderType/Model 不再需要，IGhostMachine 接口也不再需要。伪装外观通过直接覆写 `MetaMachine.getBlockAppearance()` 实现。

### 2.2 与现有包的关系

```
goblintech（主包）
├── api.machine.feature.multiblock   ← 本次新增：幽灵外壳 API
├── api.machine.multiblock.part      ← 本次新增：Part 机器层次
├── api.machine.multiblock           ← 未来：Controller 改造
├── api.machine                      ← 未来：WorkableTieredGoblinMachine
├── recipe                           ← 已有：GoblinRecipe 配方系统
├── client.blueprint                 ← 未来：蓝图可视化
└── create.kinetic.recipe            ← 已有：Create 动能配方
```

---

## 3. 类层次设计

### 3.1 完整继承链

```
MetaMachine (GTCEu)
│
├── MultiblockPartMachine (GTCEu, 168行)
│   └── GoblinMultiblockPartMachine     ← 本次新建
│       ├── getBlockAppearance() → 返回 hullState（★ 核心：一个方法实现伪装+CTM）
│       ├── hullState / originalBlockStack 字段
│       └── TieredPartGoblinMachine     ← 改继承
│           └── TieredIOPartGoblinMachine ← 自动继承
│
├── WorkableTieredGoblinMachine implements IGoblinRecipeLogicMachine  ← 未来
│   （多供能单方块，无伪装特性）
│
└── MultiblockControllerMachine (GTCEu, 421行)
    └── MultiblockControllerGoblinMachine implements IGoblinRecipeLogicMachine  ← 未来
        ├── onLoad() → 不注册 asyncLogic
        ├── asyncCheckPattern() → 空实现
        └── （保留 Controller 类型身份通过 instanceof 检查）
```

> **已移除**：`implements IGhostMachine` — 不再需要接口，伪装外观通过直接覆写 `MetaMachine.getBlockAppearance()` 实现。
> **已移除**：`replacePartModelWhenFormed() → false` — 不再需要透明底座，因为渲染完全由原生管线处理。

### 3.2 为什么不创建 GoblinMetaMachine

三个分支的需求正交——Part 需要伪装+多方块，单方块需要多供能，Controller 需要阻止老逻辑。没有共同中介层需要抽取。每个分支直接从 GTCEu 对应基类继承。

---

## 4. 伪装外观实现（getBlockAppearance 覆写）

### 4.1 核心方法

```java
// GoblinMultiblockPartMachine（继承 MultiblockPartMachine）
public class GoblinMultiblockPartMachine extends MultiblockPartMachine {
    @SaveField @SyncToClient @RerenderOnChanged
    private BlockState hullState;        // 伪装方块外观

    @SaveField(nbtKey = "originalBlock")
    private ItemStack originalBlockStack;

    /**
     * ★ 核心：覆写此方法即可实现伪装 + CTM。
     * 无需 IGhostMachine 接口、无需 DynamicRender、无需透明底座。
     */
    @Override
    public BlockState getBlockAppearance(BlockState state, BlockAndTintGetter level,
                                         BlockPos pos, Direction side,
                                         @Nullable BlockState sourceState,
                                         @Nullable BlockPos sourcePos) {
        // 1. Cover 优先（GTCEu 原生逻辑）
        var coverAppearance = getCoverContainer()
            .getBlockAppearance(state, level, pos, side, sourceState, sourcePos);
        if (coverAppearance != null) return coverAppearance;

        // 2. 返回伪装外观（含 CTM 支持）
        BlockState hull = getHullState();
        if (hull != null && !hull.isAir()) return hull;

        // 3. 兜底：维持 GTCEu 原生行为
        return super.getBlockAppearance(state, level, pos, side, sourceState, sourcePos);
    }

    // 扳手循环伪装方块属性
    public boolean cycleHullProperty() {
        BlockState hull = getHullState();
        // 循环 FACING / AXIS / LIT 等属性
        // ...
    }

    public void dropOriginalBlock() {
        // 破坏时掉落被替换的原方块
        // ...
    }
}
```

### 4.2 为什么它能工作

| 步骤 | 谁调用的 | 发生了什么 |
|------|---------|-----------|
| 1 | Minecraft 渲染器 | 调用 `Block.getAppearance()` 获取方块外观 |
| 2 | `MetaMachineBlock.getAppearance()` | 转发给机器实例的 `getBlockAppearance()` |
| 3 | 我们的覆写 | 返回存储的 `hullState`（伪装方块的 BlockState） |
| 4 | Minecraft 模型系统 | 加载该 BlockState 的完整 BakedModel（含 Create CTM） |
| 5 | Create CTM | 通过步骤 1 查询相邻方块，发现我们的方块返回了匹配的外壳 → 纹理连接 |

### 4.3 与旧方案的对比

| | 旧方案（IGhostMachine + DynamicRender） | 新方案（getBlockAppearance 覆写） |
|------|--------------------------------------|-----------------------------------|
| 新增类 | IGhostMachine、GhostPartHullRender、RenderType、Model、transparent.png | **0 个新类** |
| 渲染方式 | DynamicRender 接管，底座透明 | Minecraft 原生渲染 |
| CTM 支持 | 需要额外注册 + renderType 配合 | 原生支持（通过 getAppearance 查询） |
| 方块属性 | 需手动处理 FACING 等 | 天然支持（因为渲染的是完整 BlockState） |
| 破坏动画 | 需要同步 | 自动显示 hullState 的破坏动画 |
| 维护成本 | 高（多个类相互关联） | **极低**（一个方法覆写） |

### 4.4 从现有实现中的清理

由于新方案完全不需要以下文件，可以从项目中移除：

| 文件 | 说明 |
|------|------|
| `IGhostMachine.java` | 不再需要接口 |
| `GhostPartHullRender.java` | 不再需要 DynamicRender |
| `GhostPartHullRenderType.java` | 不再需要渲染类型注册 |
| `GhostPartHullModel.java` | 不再需要透明底座模型 |
| `transparent.png` | 不再需要透明纹理占位符 |
| GTCEu 注册代码 | 不再需要在 GTCEu CommonProxy 中注册 DynamicRender |

---

## 5. GoblinMultiblockPartMachine

### 5.1 基本结构

```java
public class GoblinMultiblockPartMachine
    extends MultiblockPartMachine {      // 继承 GTCEu 168行全部功能

    @SaveField @SyncToClient @RerenderOnChanged
    private BlockState hullState;        // 伪装方块外观

    @SaveField(nbtKey = "originalBlock")
    private ItemStack originalBlockStack;

    /**
     * ★ 唯一需要覆写的方法。
     * 伪装外观 + CTM 全部由 Minecraft 原生渲染管线处理。
     */
    @Override
    public BlockState getBlockAppearance(BlockState state, BlockAndTintGetter level,
                                         BlockPos pos, Direction side,
                                         @Nullable BlockState sourceState,
                                         @Nullable BlockPos sourcePos) {
        var coverAppearance = getCoverContainer()
            .getBlockAppearance(state, level, pos, side, sourceState, sourcePos);
        if (coverAppearance != null) return coverAppearance;

        BlockState hull = getHullState();
        if (hull != null && !hull.isAir()) return hull;

        return super.getBlockAppearance(state, level, pos, side, sourceState, sourcePos);
    }

    @Override public void onMachineDestroyed() { super.onMachineDestroyed(); dropOriginalBlock(); }
}
```

### 5.2 继承的 MultiblockPartMachine 功能（全部保留）

| 功能 | 说明 |
|------|------|
| 控制器追踪 | controllerPositions + controllers |
| 归属查询 | hasController / isFormed |
| 同步回调 | onControllersUpdated (ClientFieldChangeListener) |
| getControllers | IMultiPart 要求 |
| 配方聚合 | getRecipeHandlers + getHandlerList |
| 卸载清理 | onUnload + CME 防护 |
| 生命周期 | addedToController / removedFromController |

> **注意**：`replacePartModelWhenFormed()` 保留 GTCEu 默认行为（根据 IS_FORMED 状态决定）。不再需要返回 `false`，因为不再需要透明底座。

### 5.3 渲染管线对比

```
旧方案：
  MachineModel.renderMachine()
    ├── renderBaseModel()           → 底座透明（textureOverrides 替换）
    ├── DynamicRender.getRenderQuads()  ← 额外开销
    │   └── GhostPartHullRender     → 手动渲染 hullState
    └── CustomBakedModel.reBakeCustomQuads()

新方案（简化）：
  Minecraft 原生渲染管线
    ├── Block.getAppearance() → MetaMachineBlock.getAppearance()
    │   └── GoblinMultiblockPartMachine.getBlockAppearance()
    │       └── return hullState      ← 直接返回，天然支持 CTM
    └── 剩余由 Minecraft + Create CTM 自动处理
```

**零额外类、零额外渲染步骤、零额外纹理资源。**

---

---

## ~~6. 渲染系统~~（已移除）

> 此章节描述的 `GhostPartHullRender` / `GhostPartHullRenderType` / `GhostPartHullModel` / `transparent.png` 已不再需要。
> 伪装外观现在通过直接覆写 `GoblinMultiblockPartMachine.getBlockAppearance()` 实现，Minecraft 原生渲染管线（含 Create CTM）自动处理一切。
> 详见 [§4 伪装外观实现](#4-伪装外观实现getblockappearance-覆写)。

---

## 7. MultiblockControllerGoblinMachine（计划）

### 7.1 设计动机

`MultiblockControllerMachine` 有 47 处 `instanceof` 检查散落 GTCEu 各子系统（BlockPattern、RecipeLogic、GTRecipeModifiers、Jade、CleanroomCondition 等），不可重做继承链。但需要阻止以下老旧逻辑：

### 7.2 需要阻止的行为

| 行为 | 来源 | 阻止方式 |
|------|------|---------|
| 定时结构检查（每 5 tick） | `onLoad()` → `MultiblockWorldSavedData.addAsyncLogic()` | override onLoad() 不调用 addAsyncLogic |
| asyncCheckPattern | 调度器回调 | override 为空 |
| shift+右键结构预览 | `onUse()` → `MultiblockInWorldPreviewRenderer.showPreview()` | override onUse() |
| 终端自动建造 | `TerminalBehavior` | 不注册终端物品（"哥布林不会用终端"） |
| 转动触发 pattern 检查 | `onRotated()` / `setFrontFacing()` | override 去掉 checkPattern 调用 |

### 7.3 替代方案

- **结构建造**：Create 蓝图 + 大炮，由继承 Create `SchematicItem` 的 `ShamanItem` 驱动
- **结构验证**：MultiblockControllerGoblinMachine 内部维护三维 bitmap，在方块放置/破坏时 O(1) 更新 + O(n/64) 比对（n=方块数），瞬时完成结构验证
- **GUI 预览**：复用 Create 蓝图预览系统（含移动/旋转/翻转），第二排添加 JEI 风格的多方块预览按钮

### 7.4 保留的继承功能

- `isFormed` / `onStructureFormed()` / `onStructureInvalid()` — Part 添加/移除仍需要
- `getParts()` / `partPositions` — 配方系统依赖
- `getMultiblockState()` — 结构状态管理
- `getParallelHatch()` — 并行处理仓

---

## 8. 蓝图与 Part 交互系统（计划）

### 8.1 设计概览

三种物品、三种仪式、一种神明：

```
物品层：
  MultiblockControllerGoblinMachine（方块物品，可放置）—— 机器的"肉身"
  ShamanItem（Item，extends Create SchematicItem）—— "蓝图腾"，通神的萨满
  TieredPartGoblinMachine（Item，非方块——不可单独放置在世界中）

世界外层：
  GoblinMachineDeity —— "多方块机器之神"，以 Controller 为键管理成型判定缓存
     ├─ IDeityOracle（神谕）—— 三种判定方式：固定 / 可延展 / 围合
     └─ 方块变更 → onBlockChanged() → 返回哪些 Controller 的成型状态变了

仪式一「拜拜」— 从已放置的 Controller 获取蓝图：
  ShamanItem 对 MultiblockControllerGoblinMachine 下蹲右键（鞠躬拜一下）
    → 蓝图内容 = 该 Controller 定义的结构（锚定于此位置，不可移动）
    → Controller 位置非法方块 → 红色闪烁（机器在告诉哥布林哪里不对）
    → 输入输出限制 → Outliner 标注（机器在告诉哥布林 IO 方向）
    → 哥布林知道该怎么摆了
    → 重置神明厌烦状态（重新获得神明关注）
    → 立即触发一次结构验证（重新获得神的认可）

仪式二「祈福」— 自由放置蓝图：
  ShamanItem 副手放 MultiblockControllerGoblinMachine 物品，主手对空气右键
    → 蓝图内容 = Controller 定义的结构 + 可移动/旋转/翻转
    → 支持 Create 蓝图全部工具（MOVE / ROTATE / FLIP / DEPLOY）
    → 放入蓝图加农炮 → 火药 + 动画批量建造
    → Controller 方块随蓝图一起放置到加农炮范围内

仪式三「显灵」— 蓝图加农炮打印完成后：
  ShamanItem 就是 Create 蓝图（extends SchematicItem）→ 加农炮正常使用
    → 加农炮打印全部 Part 方块 + Controller 方块
    → 打印完成后 → GoblinMachineDeity.registerController(controller)
    → deity.checkFormation() → 如果通过 → controller.onStructureFormed()
    → 如果未通过 → 等待后续手动补 Part

仪式四「僭越」— 手动 Part 放置：
  TieredPartGoblinMachine 物品
    └─ 右键 MultiblockControllerGoblinMachine → 绑定（哥布林将灵魂出卖给神）
         └─ 右键交集中的方块位置 → 放置 + 伪装（僭越神权）
              └─ 每次放置都会惊扰 GoblinMachineDeity（调用成型检查）
                   ├─ 神明宽恕 → 机器成型（僭越行为被认可）
                   └─ 神明震怒 → 机器失效（神罚）
```

### 8.2 TieredPartGoblinMachine 行为状态机

TieredPartGoblinMachine 是物品（Item），不是可直接放置的方块物品。

```
TieredPartGoblinMachine（未绑定）
  │
  ├─ 右键 MultiblockControllerGoblinMachine
  │   ├─ Part 支持被当前 Controller 蓝图使用（类型匹配）
  │   │   └─ 绑定 → 状态变为「已绑定」
  │   └─ Part 不支持 → 不做任何事
  │
  └─（右键非 Controller）→ 不做任何事

TieredPartGoblinMachine（已绑定，持有中）
  │
  ├─ Outliner 标记绑定的 Controller 位置（绿色高亮）
  ├─ Outliner 标记该 Part 在当前 Controller 蓝图中可放置的位置（黄色高亮）
  │
  ├─ 右键另一个 MultiblockControllerGoblinMachine（不支持共享）
  │   ├─ 原 Controller 标记变红色 + 闪烁
  │   └─ 所有标记淡出 → 放弃绑定
  │
  ├─ 右键另一个 MultiblockControllerGoblinMachine（支持共享）
  │   ├─ 取两 Controller 蓝图可放置位置的交集
  │   ├─ 交集为空/已被占 → 放弃绑定新 Controller
  │   └─ 交集非空 → 绑定新旧两 Controller
  │       ├─ Outliner 标记两 Controller 位置
  │       └─ Outliner 标记交集位置
  │
  └─ 右键交集位置的方法
      ├─ 目标位置已被 Part 占据 → 放弃放置
      └─ 目标位置可替换 → 放置 GoblinMultiblockPartMachine 方块
          ├─ hullState = 目标位置的原方块 BlockState
          ├─ originalBlockStack = 目标位置的原方块掉落物
          ├─ 替换世界中的方块
          └─ addedToController() → 注册到绑定的 Controller
```

### 8.3 ShamanItem（蓝图腾）行为

ShamanItem 继承 Create 的 `SchematicItem`（S-h-a-m-a-n / S-c-h-e-m-a-t-i-c，谐音梗），复用其完整蓝图系统。它是哥布林与"多方块机器之神"沟通的萨满——哥布林看不懂蓝图，只当它是通神的法器。

**仪式一「拜拜」— 下蹲右键已放置的 Controller**

```
ShamanItem 对 MultiblockControllerGoblinMachine 下蹲右键（鞠躬拜一下）
  ├─ 蓝图内容 = 该 Controller 定义的结构
  ├─ Controller 位置已锚定（不可移动/旋转/翻转）
  ├─ 非法方块检测：
  │   ├─ Controller 周围有不该在那里的方块 → 红色闪烁 Outliner
  │   └─ 缺少必须的 Part → 黄色虚线 Outliner 标记空位
  ├─ IO 限制标注：
  │   └─ 输入/输出面方向 → 箭头 Outliner（机器在告诉哥布林 IO 方向）
  ├─ 手持时 → Create SchematicHandler 渲染半透明预览
  └─ 仅启用第二排自定义工具（分层/变体/重复）
```

**仪式二「祈福」— 副手放 Controller 物品，主手对空气右键**

```
ShamanItem 副手放 MultiblockControllerGoblinMachine 物品，主手对空气右键
  ├─ 蓝图内容 = Controller 定义的结构（相对坐标 + Part 类型）
  ├─ Controller 方块将随蓝图一起放置
  ├─ 此时算作有内容的 Create 蓝图，支持全部 Create 蓝图工具：
  │   ├─ MOVE / MOVE_Y — 移动放置位置
  │   ├─ ROTATE — 旋转结构
  │   ├─ FLIP — 翻转结构
  │   └─ DEPLOY — 部署（选择放置位置）
  ├─ 第二排自定义工具：
  │   ├─ 分层显示（类似 JEI 预览，按 Y 层切换）
  │   ├─ 切换变体（线圈类型、玻璃种类等）
  │   └─ 重复次数设置
  └─ 放入 Create 蓝图加农炮：
      ├─ Controller 方块与 Part 方块一起打印到加农炮范围内
      ├─ 火药 + 动画批量建造
      ├─ 打印完成后 → GoblinMachineDeity.registerController(controller)
      └─ deity.checkFormation() → 如果通过 → onStructureFormed()
```

**仪式三「显灵」— 作为蓝图在加农炮中使用**

```
ShamanItem 放入 Create 蓝图加农炮（标准 Create 流程）
  ├─ 就是 Create 蓝图（extends SchematicItem），加农炮原生支持
  ├─ 消耗火药 + 动画搭建
  ├─ 每个 Part 位置 → 放置 GoblinMultiblockPartMachine
  ├─ Controller 方块放置到蓝图定义的坐标
  ├─ 打印完成后：
  │   ├─ GoblinMachineDeity.registerController(controller)
  │   └─ 额外主动触发 deity.checkFormation()
  │       ├─ 通过 → controller.onStructureFormed()
  │       └─ 未通过 → 等待后续手动补 Part
  └─ 注意：不再需要将 Controller 放入 ShamanItem 的槽位
      （祈福时副手持有即可，显灵时 ShamanItem 就是完整蓝图）
```

### 8.4 扳手交互（拆卸）

```
Create 扳手右键 TieredPartGoblinMachine 方块：
  ├─ 掉落 TieredPartGoblinMachine 物品
  ├─ 原位置替换回 originalBlockStack（伪装方块）
  ├─ removedFromController() — 注销
  └─ 同步客户端
```

### 8.5 Outliner 可视化汇总

| 场景 | Outliner 类型 | 颜色 | 说明 |
|------|-------------|------|------|
| 持有 TieredPartGoblinMachine | `showAABB` | `0x4D9162` 绿 | 绑定的 Controller 位置 |
| 持有 TieredPartGoblinMachine | `showCluster` | `0xC5B548` 黄 | 可放置位置集合 |
| 不能共享时右键新 Controller | `showAABB` | `0xC54848` 红→淡出 | 原 Controller 变红闪烁 |
| 支持共享，取交集后 | `showCluster` | `0xC5B548` 黄 | 两 Controller 的交集位置 |
| 方块已放置 | `remove` | — | 清除 Outliner 标记 |

### 8.6 Create 蓝图系统复用清单

| Create 类 | 复用以求 | 在你的场景 |
|-----------|---------|-----------|
| `SchematicItem` | `extends` | `ShamanItem` — "蓝图腾"，S-h-a-m-a-n / S-c-h-e-m-a-t-i-c 谐音梗 |
| `SchematicHandler` | 直接使用 | 客户端蓝图预览 + 工具选择 |
| `SchematicTransformation` | 直接使用 | 移动/旋转/翻转定位 |
| `SchematicRenderer` | 直接使用 | 3D 结构半透明渲染 |
| `ToolType` / `ISchematicTool` | 扩展 | 第二排自定义工具 |
| `SchematicannonBlockEntity` | 直接使用 | 蓝图大炮搭建动画 |
| `SchematicPrinter` | 使用 + 扩展 | 放置时替换方块 + 注册 Part |
| `StructurePlaceSettings` | 直接使用 | 旋转/镜像参数 |
| `StructureTemplate` | 使用 | NBT 蓝图序列化 |

### 8.7 结构验证：三维 Bitmap 方案

#### 8.7.1 设计目标

GTCEu 原生 `BlockPattern.checkPatternAt()` 存在实际使用时延迟严重的问题：每 5 tick（250ms）触发一次 O(m×n) 的完整遍历（m=朝向尝试次数，n=结构方块数），对于大型结构可达数百毫秒甚至更久。Bitmap 方案通过事件驱动，在方块放置/破坏时瞬时完成验证，完全消除轮询延迟。

#### 8.7.2 为什么许多 BlockPattern 功能我们不需要

| BlockPattern 功能 | 为什么不需要 | 替代 |
|---|---|---|
| `aisleRepetitions`（动态层数） | 蓝图定义固定结构，无可变重复层 | — |
| `TraceabilityPredicate` 复杂匹配 | 每个位置有唯一确定的 Part 类型，不需要谓词匹配 | 简单的类型 ID 比对 |
| `isAny()` 任意方块匹配 | 所有非 Part 方块由伪装系统处理，不在验证范围内 | — |
| `addCache()` 特殊位置缓存 | 蓝图已经提供了完整坐标列表 | `StructureBitmap` 本身就是缓存 |
| `autoBuild` 自动搭建 | Create 蓝图大炮负责搭建 | 蓝图大炮 |
| `getPreview` JEI/Jade 预览 | 可保留 BlockPattern 定义仅用于预览生成 | 保留 patternBuilder DSL（预览专用） |

**真正需要新增的能力**：类型计数约束、详细错误报告、多朝向支持。

#### 8.7.3 增强版数据结构

```java
public class StructureBitmap {
    // === 位置层：坐标映射 ===
    private final BlockPos origin;       // Controller 世界坐标
    private final int sizeX, sizeY, sizeZ;
    
    public int posToIndex(BlockPos worldPos) {
        int dx = worldPos.getX() - origin.getX();
        int dy = worldPos.getY() - origin.getY();
        int dz = worldPos.getZ() - origin.getZ();
        if (dx < 0 || dy < 0 || dz < 0 || dx >= sizeX || dy >= sizeY || dz >= sizeZ)
            return -1;
        return (dy * sizeX * sizeZ) + (dz * sizeX) + dx;
    }
    
    // === 位置层：每个位置的类型约束 ===
    // 每个位置可以有多个候选 Part 类型（位掩码）
    private final byte[] allowedTypes;   // [index] = bitmask of allowed PartType IDs
    private final BitSet requiredMask;   // [index] = true if this position must be filled
    
    // === 位置层：当前放置状态 ===
    private final BitSet occupiedMask;   // [index] = true if currently occupied
    private final byte[] currentTypes;   // [index] = the PartType ID actually placed
    
    // === 计数层：全局类型数量约束 ===
    // 例如："总共需要 2 个输入仓，最多 4 个"
    public record CountConstraint(int partTypeId, int minCount, int maxCount) {}
    private final List<CountConstraint> countConstraints;
    private final int[] currentCounts;   // [partTypeId] = how many of this type exist

    // === 多朝向支持 ===
    private final Direction facing;      // 此 bitmap 对应的朝向（null=不关心朝向）
    
    // === O(1) — 放置 Part ===
    public PlacementResult markOccupied(BlockPos worldPos, int partTypeId) {
        int idx = posToIndex(worldPos);
        if (idx < 0) 
            return PlacementResult.outOfBounds();
        if (!requiredMask.get(idx)) 
            return PlacementResult.notAPartPosition();
        if ((allowedTypes[idx] & (1 << partTypeId)) == 0) 
            return PlacementResult.typeMismatch(allowedTypes[idx]);
        if (occupiedMask.get(idx)) 
            return PlacementResult.alreadyOccupied();
        
        // 检查计数约束是否有空间
        for (var constraint : countConstraints) {
            if (constraint.partTypeId == partTypeId) {
                if (currentCounts[partTypeId] >= constraint.maxCount)
                    return PlacementResult.countExceeded(constraint);
            }
        }
        
        occupiedMask.set(idx);
        currentTypes[idx] = (byte) partTypeId;
        currentCounts[partTypeId]++;
        return PlacementResult.ok();
    }
    
    // === O(1) — 移除 Part ===
    public void markVacant(BlockPos worldPos) {
        int idx = posToIndex(worldPos);
        if (idx < 0 || !occupiedMask.get(idx)) return;
        int type = currentTypes[idx];
        occupiedMask.clear(idx);
        currentTypes[idx] = 0;
        currentCounts[type]--;
    }
    
    // === O(n/64) + O(c) — 验证是否成型 ===
    public FormationStatus checkFormation() {
        // 1. 位置完整性：所有必须位置是否都有 Part
        BitSet missing = (BitSet) requiredMask.clone();
        missing.andNot(occupiedMask);
        if (!missing.isEmpty()) {
            return FormationStatus.incomplete(missing.stream()
                .mapToObj(this::indexToPos).toList());
        }
        
        // 2. 计数约束：是否满足最小数量
        for (var constraint : countConstraints) {
            if (currentCounts[constraint.partTypeId] < constraint.minCount) {
                return FormationStatus.countUnderMin(constraint);
            }
        }
        
        return FormationStatus.formed();
    }
    
    // 位置索引转回世界坐标（用于错误报告）
    public BlockPos indexToPos(int idx) {
        int y = idx / (sizeX * sizeZ);
        int remainder = idx % (sizeX * sizeZ);
        int z = remainder / sizeX;
        int x = remainder % sizeX;
        return origin.offset(x, y, z);
    }
}

// === 返回值类型 ===
public sealed interface PlacementResult {
    record Ok() implements PlacementResult {}
    record OutOfBounds() implements PlacementResult {}
    record NotAPartPosition() implements PlacementResult {}
    record TypeMismatch(int allowedTypeMask) implements PlacementResult {}
    record AlreadyOccupied() implements PlacementResult {}
    record CountExceeded(CountConstraint constraint) implements PlacementResult {}
    
    static PlacementResult ok() { return new Ok(); }
    // ... other factory methods
}

public sealed interface FormationStatus {
    record Formed() implements FormationStatus {}
    record Incomplete(List<BlockPos> missingPositions) implements FormationStatus {}
    record CountUnderMin(CountConstraint constraint) implements FormationStatus {}
}
```

#### 8.7.4 即时验证流程

```
=== 放置路径 ===
EntityPlaceEvent / 蓝图大炮打印完成：
  1. 新方块是 GoblinMultiblockPartMachine？
     ├─ 否 → 忽略
     └─ 是 → 继续
  2. 遍历 Part 声明的每个 Controller 的 bitmap：
     ├─ bitmap.markOccupied(worldPos, partType)
     │   ├─ OutOfBounds / NotAPartPosition → 继续下一个 Controller
     │   ├─ TypeMismatch → 阻止放置，向玩家提示允许的类型
     │   ├─ CountExceeded → 阻止放置，提示已达到上限
     │   └─ Ok → 至少一个 Controller 接受 → 允许放置
  3. 对所有通过该 Part 的 Controller：
     └─ if (bitmap.checkFormation() is Formed) → onStructureFormed()

=== 移除路径 ===
BlockEvent.BreakEvent / 扳手拆卸：
  1. 旧方块是 GoblinMultiblockPartMachine？
     └─ 是 → 对每个关联的 Controller：bitmap.markVacant(worldPos)
  2. 如果之前是 formed → onStructureInvalid()
```

#### 8.7.5 多朝向处理

```java
public class StructureBitmapSet {
    // Controller 朝向 → 对应 bitmap
    private final Map<Direction, StructureBitmap> perFacing;
    
    public StructureBitmap forFacing(Direction facing) {
        return perFacing.get(facing);
    }
    
    public static StructureBitmapSet fromDefinition(
            GoblinMultiblockDefinition def, Direction[] validFacings,
            BlockPos origin) {
        Map<Direction, StructureBitmap> map = new EnumMap<>(Direction.class);
        for (Direction facing : validFacings) {
            map.put(facing, StructureBitmap.fromDefinition(def, facing, origin));
        }
        return new StructureBitmapSet(map);
    }
}
```

每个 `StructureBitmap` 内部的坐标映射已经按朝向做了旋转变换，所以 `posToIndex` 返回的 index 对每个朝向是不一样的——但对外部调用方完全透明。

#### 8.7.6 Bitmap 来源

Bitmap 可以由两种方式生成：

**方式 1 — 从定义数据生成（推荐）**

```java
public StructureBitmap(int sizeX, int sizeY, int sizeZ, BlockPos origin, 
        List<SlotDefinition> slots, Direction facing) {
    this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
    this.origin = origin;
    this.allowedTypes = new byte[sizeX * sizeY * sizeZ];
    this.currentTypes = new byte[sizeX * sizeY * sizeZ];
    this.requiredMask = new BitSet(sizeX * sizeY * sizeZ);
    this.occupiedMask = new BitSet(sizeX * sizeY * sizeZ);
    
    for (var slot : slots) {
        BlockPos relPos = rotateByFacing(slot.relativePos(), facing);
        int idx = (relPos.getY() * sizeX * sizeZ) + (relPos.getZ() * sizeX) + relPos.getX();
        this.requiredMask.set(idx);
        this.allowedTypes[idx] = slot.allowedTypesMask();
    }
    
    this.countConstraints = slots.stream()
        .filter(s -> s.minCount() > 0)
        .map(s -> new CountConstraint(s.partTypeId(), s.minCount(), s.maxCount()))
        .distinct().toList();
    this.currentCounts = new int[256]; // 最多 256 种 Part 类型
}
```

**方式 2 — 从 GoblinMultiblockDefinition 自动生成**

```java
// MultiblockControllerGoblinMachine
public StructureBitmapSet createBitmapSet() {
    var def = getDefinitionData();  // 抽象方法，每个 Controller 实现
    return StructureBitmapSet.fromDefinition(def, getValidFacings(), self().getPos());
}
```

#### 8.7.7 复杂度分析

| 操作 | 复杂度 | 说明 |
|------|--------|------|
| Part 放置 (`markOccupied`) | O(1) + O(c) | 一次数组查找 + BitSet.set + 遍历 c 个计数约束（c 通常 ≤ 5） |
| Part 移除 (`markVacant`) | O(1) | 一次 BitSet.clear |
| 成型检查 (`checkFormation`) | O(n/64) + O(c) | BitSet 逐 long 比较 + 遍历计数约束 |
| 30 方块结构 | ~1 次 long 比较 + ~3 次 int 比较 | < 1 微秒 |
| 128 方块结构 | ~2 次 long 比较 + ~5 次 int 比较 | < 1 微秒 |
| 256 方块结构 | ~4 次 long 比较 + ~8 次 int 比较 | < 1 微秒 |

#### 8.7.8 对比 GTCEu 原生方案（校正版）

下表中的 BlockPattern 劣势是 **GoblinTech 实际使用场景下的核心问题**，新方案针对性地解决：

| 维度 | GTCEu BlockPattern（实际体验） | Bitmap 方案 | 结论 |
|------|-------------------------------|------------|------|
| 检查周期 | 每 5 tick（250ms）异步轮询 | 即时（事件驱动） | ✅ Bitmap 消除轮询延迟 |
| 实际延迟 | 大型结构可达 500ms-2s（含遍历+谓词匹配+重复检测） | < 1μs | ✅ Bitmap 快 5 个数量级 |
| 线程开销 | ScheduledExecutorService 常驻 | 零后台线程 | ✅ Bitmap 无开销 |
| aisles 重复 | GTCEu 需要 | 蓝图固定结构，不需要 | ⬜ 无关 |
| 复杂谓词匹配 | GTCEu 需要 | 固定 Part 类型，不需要 | ⬜ 无关 |
| 计数约束 | ✅ 原生支持 | ✅ 新增 CountConstraint | ⬜ 已补齐 |
| 错误报告 | ✅ PatternError | ✅ PlacementResult + FormationStatus | ⬜ 已补齐 |
| 多朝向 | ✅ 运行时遍历 | ✅ 预生成 per-facing Bitmap | ⬜ 已补齐 |
| 自动搭建 | ✅ autoBuild | 蓝图大炮替代 | ⬜ 替代方案更好 |
| JEI/Jade 预览 | ✅ getPreview | 保留 BlockPattern DSL 仅用于预览 | ⬜ 两全其美 |
| 内存 | MultiblockState + cache (~KB) | BitSet + byte[] (~几十字节) | ✅ Bitmap 更省 |

#### 8.7.9 动态结构处理：三种模式

前面的 `StructureBitmap` 假设了蓝图定义的固定结构。但在以下场景中结构不固定：

- **未使用蓝图**：只是放了个 Controller，Part 逐个手动放置，尺寸未定
- **可延展方向**：沿一个轴可以重复中间层（如 3~5 层可选）
- **超净间式自由形状**：完全由墙壁/门围合决定

对于不同场景，需要不同的处理策略。

#### 8.7.10 模式 A：分段 Bitmap（沿轴重复）

适用：沿一个轴可重复中间层的结构（如大型锅炉、裂化塔）。

**设计思路**：将结构切成三段，通过"两固定端 + 可重复段"组合生成最终 Bitmap。

```java
public class SectionedBitmap {
    /** 沿哪个轴重复（X/Y/Z） */
    private final Direction.Axis repeatAxis;
    /** 重复次数范围 */
    private final int minRepeat, maxRepeat;
    /** 当前实际重复次数（由已放置的 Part 决定，初始为 -1 表示未确定） */
    private int currentRepeat = -1;

    /** 固定端 A（重复段之前的部分） */
    private final BitmapSection capA;
    /** 可重复段的一份 */
    private final BitmapSection middle;
    /** 固定端 B（重复段之后的部分） */
    private final BitmapSection capB;

    /** 当前活跃的完整 Bitmap（由 capA + N*middle + capB 拼合） */
    private StructureBitmap activeBitmap;

    /**
     * 当放置一个 Part 时，判断它落在哪个段
     */
    public PlacementResult markOccupied(BlockPos worldPos, int partTypeId) {
        int axisOffset = getAxisOffset(worldPos); // 沿 repeatAxis 的偏移量
        
        // 如果当前重复次数未确定 → 尝试推断
        if (currentRepeat < 0) {
            // 检查是否在 capA 范围内
            if (capA.contains(axisOffset)) {
                // 还在 capA，不需要推断 N
            } else if (middle.contains(axisOffset - capA.width)) {
                // 第一个 middle 段的位置被放了 → N >= 1
                currentRepeat = 1;
                activeBitmap = buildActiveBitmap(currentRepeat);
            } else {
                // 超出所有可能范围
                return PlacementResult.outOfBounds();
            }
        }
        
        // 如果结构在变长（新放了一个临近的 middle slot）
        int newN = inferRepeatCount(axisOffset + 1); // +1 因为刚放置后结构可能变长
        if (newN > currentRepeat && newN <= maxRepeat) {
            currentRepeat = newN;
            activeBitmap = buildActiveBitmap(currentRepeat);
        }
        
        return activeBitmap.markOccupied(worldPos, partTypeId);
    }
    
    /** 从世界当前状态推断实际使用的重复次数 */
    private int inferRepeatCount(int hintMax) {
        // 从 capB 的位置逆推：如果 capB 已经有 Part → N 确定
        // 否则从最远的 middle 层推断
        for (int n = Math.min(hintMax, maxRepeat); n >= minRepeat; n--) {
            if (anySlotOccupiedInSection(middle, n)) return n;
        }
        return -1; // 还没放 middle 层
    }
    
    /** 拼合最终 Bitmap */
    private StructureBitmap buildActiveBitmap(int n) {
        // capA + n * middle + capB + 旋转 + 朝向 → 一个完整的 StructureBitmap
        int totalWidth = capA.width + n * middle.width + capB.width;
        // ... 按 repeatAxis 拼合三个段的 slot 列表
    }
}
```

**性能分析**：`buildActiveBitmap` 只在结构尺寸变化时调用一次（O(n) 遍历所有 slot），后续所有放置/验证都是 O(1) + O(n/64)。由于重复次数通常很小（3~10），重建成本可忽略。

**对称变体**：当前结构有镜像对称的两个合法方案时，`StructureBitmapSet` 维护两组：`bitmapSetA` 和 `bitmapSetB`（镜像），Part 放置时同时检查两组，哪组先完整就成型。

#### 8.7.11 模式 B：延迟包围盒检测（超净间式）

适用：结构由"给定方块类型的围合"定义，无固定形状模板（如超净间）。

**为什么 Bitmap 不适用**：这类结构不需要特定方块在特定位置，而是需要"封闭空间"——墙壁围住任意形状的区域。Bit 的本意是"每个位置有固定预期"，而这里没有。

**替代方案：事件驱动 + 缓存 BFS**

```
┌─ 当唯一定义的"边界方块"（墙壁/门）被放置或破坏时：
│   1. invalidate(controller) → 标记成型状态待验证
│   2. 不立即检查（可能还在建造中）
│
├─ 当 Part（机器组件）被放置或破坏时：
│   1. Part 注册到 Controller（正常流程）
│   2. 如果之前是 formed → onStructureInvalid()
│   3. 如果缓存标记为待验证 → 异步触发 verify()
│
└─ verify() 执行：
    1. BFS 从 Controller 向外遍历
       ├─ 穿过属于该 Controller 的 Part 方块 — 计入封闭空间
       ├─ 碰到边界方块 — 计入边界
       ├─ 碰到非边界方块 — 非封闭，验证失败
       └─ 超出最大范围 — 非封闭，验证失败
    2. 验证边界完整性（所有 Part 被边界封闭，无缺口）
    3. 验证尺寸约束（min/max 以内空间体积）
    4. 缓存结果 → 如果通过 → onStructureFormed()
```

**关键优化**：
- BFS 只用世界坐标，不需要 Bitmap 3D 数组
- 用 `LongOpenHashSet` 记录已访问 BlockPos（fastutil 原始 long 集合，零装箱）
- 缓存 formed 结果，只有边界方块变化时失效
- 每个 Part 放置/破坏时只做 O(1) 的计数更新，不成型不触发 BFS

```java
public class EnclosureValidator {
    private final LongOpenHashSet boundaryPositions = new LongOpenHashSet();
    private final LongOpenHashSet partPositions = new LongOpenHashSet();
    private boolean validated = false;
    
    /** 边界方块变更时调用 */
    public void onBoundaryChanged() { validated = false; }
    
    /** Part 放上去时调用 */
    public void markPart(BlockPos pos) {
        partPositions.add(pos.asLong());
        validated = false;
    }
    
    /** 需要知道成型状态时调用 */
    public boolean isEnclosed(Level level, BlockPos controllerPos, int maxRadius) {
        if (validated) return partPositions.size() >= minParts;
        
        LongOpenHashSet visited = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(controllerPos);
        
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            long key = pos.asLong();
            if (!visited.add(key)) continue;
            
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                long nKey = neighbor.asLong();
                if (visited.contains(nKey)) continue;
                
                if (partPositions.contains(nKey)) {
                    queue.add(neighbor); // Part 内部，继续遍历
                } else if (isBoundaryBlock(level, neighbor)) {
                    visited.add(nKey);   // 边界，计入但不穿越
                } else {
                    // 不是边界也不是 Part → 有缺口
                    validated = false;
                    return false;
                }
            }
        }
        
        validated = true;
        return partPositions.size() >= minParts;
    }
}
```

#### 8.7.12 三种模式的适用对照

| 结构类型 | 使用模式 | 成型条件 |
|---------|----------|---------|
| 固定形状（蓝图中定义） | `StructureBitmap` | `checkFormation().isFormed()` |
| 沿轴可延展（min~max 重复层） | `SectionedBitmap` | `activeBitmap.checkFormation().isFormed()` |
| 自由围合（超净间等） | `EnclosureValidator` | `isEnclosed()` |

MultiblockControllerGoblinMachine 通过抽象方法声明自己属于哪种模式：

```java
public abstract GoblinMultiblockDefinition getDefinitionData();
// 返回的 definition 指定了 verifyMode: FIXED / SECTIONED / ENCLOSURE
```

#### 8.7.13 多方块机器之神：GoblinMachineDeity

##### 设计动机

前面的三种神谕（`StructureBitmap`、`SectionedBitmap`、`EnclosureValidator`）各自独立工作，但如果直接嵌入每个 Controller 实例中，存在三个问题：

1. **耦合**：Controller 需要知道自己用的是哪种神谕，并直接调用其 API
2. **重复遍历**：同一个方块变更可能影响多个 Controller（共享 Part），各自独立判断浪费计算
3. **不可替换**：神谕判定逻辑与 Controller 绑定，无法在外部注入不同实现（测试 mock、性能分析等）

##### 解决方案：以 Controller 为键的集中式神谕缓存

```java
public class GoblinMachineDeity {
    // 单例（或按 Level 分实例——每个世界有自己的神）
    private static final Map<Level, GoblinMachineDeity> BY_LEVEL = new HashMap<>();
    
    // === 核心数据结构 ===
    // Controller → 其神谕实例
    private final Map<MultiblockControllerGoblinMachine, IDeityOracle> oracles;
    // 世界坐标 → 覆盖该坐标的所有 Controller（用于快速查找受影响的 Controller）
    private final Long2ObjectOpenHashMap<Set<MultiblockControllerGoblinMachine>> posToControllers;
    // 缓存的神谕判定（避免重复计算）
    private final Map<MultiblockControllerGoblinMachine, FormationStatus> cachedStatus;
    // 粗略范围索引：AABB → 覆盖此范围的 Controller（用于快速预筛选）
    private final List<AABBControllerPair> roughAABBIndex;
    
    // === 公开 API ===
    
    /**
     * 方块变更时调用（哥布林僭越神权，惊扰神明）。
     * 先进行粗略范围检查，如果变更不在任何机器的检查范围内就直接跳过（神明懒得理会）。
     * @return 受影响的 Controller → 新的成型状态（仅返回状态有变化的）
     */
    public Map<MultiblockControllerGoblinMachine, FormationStatus> onBlockChanged(
            Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        
        // 快速路径：方块变更不在任何机器的粗略范围内 → 直接跳过
        Set<MultiblockControllerGoblinMachine> candidates = roughAABBCheck(level, pos);
        if (candidates == null || candidates.isEmpty()) return Collections.emptyMap();
        
        // 精细路径：命中范围 → 精确检查每个候选 Controller
        Map<MultiblockControllerGoblinMachine, FormationStatus> changes = new HashMap<>();
        
        for (var controller : candidates) {
            // 再用精确的 posToControllers 索引验证一次（双重保险）
            Set<MultiblockControllerGoblinMachine> affected = posToControllers.get(pos.asLong());
            if (affected == null || !affected.contains(controller)) continue;
            
            IDeityOracle oracle = oracles.get(controller);
            if (oracle == null) continue;
            
            // 输入方块变更 → 神谕内部更新
            oracle.onBlockChanged(pos, oldState, newState);
            
            // 检查成型状态是否变化
            FormationStatus oldStatus = cachedStatus.getOrDefault(controller, 
                FormationStatus.INCOMPLETE);
            FormationStatus newStatus = oracle.checkFormation();
            
            if (!oldStatus.equals(newStatus)) {
                cachedStatus.put(controller, newStatus);
                changes.put(controller, newStatus);
            }
        }
        
        return changes; // 调用方据此触发 onStructureFormed/onStructureInvalid
    }
    
    /**
     * 粗略 AABB 范围检查（第一道筛选）
     * 只有变更位置在某个机器的粗略包围盒内才继续检查
     * 同时实现神明厌烦机制：频繁触发会导致神明厌弃（暂时取消检测）
     */
    private Set<MultiblockControllerGoblinMachine> roughAABBCheck(Level level, BlockPos pos) {
        Set<MultiblockControllerGoblinMachine> candidates = null;
        long currentTime = System.currentTimeMillis();
        
        // 从配置获取阈值
        int triggerThreshold = ConfigHolder.INSTANCE.machines.multiblockAnnoyanceThreshold;
        long minIntervalMs = ConfigHolder.INSTANCE.machines.multiblockAnnoyanceInterval * 50L; // tick → ms
        
        for (var pair : roughAABBIndex) {
            if (pair.aabb.contains(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))) {
                // 检查是否被神明厌弃
                if (pair.annoyanceTracker().isAbandoned()) {
                    // 神明已厌弃：不仅跳过检测，还要强制标记结构为不完整
                    // 并触发失效回调（如果之前是成型状态）
                    FormationStatus currentStatus = cachedStatus.get(pair.controller());
                    if (currentStatus == FormationStatus.FORMED) {
                        cachedStatus.put(pair.controller(), FormationStatus.INCOMPLETE);
                        // 触发失效回调（神罚）
                        changes.put(pair.controller(), FormationStatus.INCOMPLETE);
                    }
                    continue; // 跳过检测，拒绝承认结构完整
                }
                
                // 记录触发并检查是否触发厌弃
                boolean annoyed = pair.annoyanceTracker().recordTrigger(currentTime, minIntervalMs);
                if (annoyed) {
                    // 神明厌弃！向附近玩家发送警告消息
                    sendAnnoyanceWarning(level, pair.controller());
                    
                    // 立即强制失效（神罚）
                    cachedStatus.put(pair.controller(), FormationStatus.INCOMPLETE);
                    changes.put(pair.controller(), FormationStatus.INCOMPLETE);
                }
                
                if (candidates == null) candidates = new HashSet<>();
                candidates.add(pair.controller);
            }
        }
        
        return candidates;
    }
    
    /**
     * 向多方块结构所在区块内的所有玩家发送神明厌弃警告
     */
    private void sendAnnoyanceWarning(Level level, MultiblockControllerGoblinMachine controller) {
        BlockPos controllerPos = controller.getPos();
        int radius = 64; // 警告范围
        
        for (Player player : level.players()) {
            if (player.blockPosition().distSqr(controllerPos) <= radius * radius) {
                // 发送聊天消息警告
                player.sendSystemMessage(Component.translatable(
                    "message.goblintech.deity_annoyed",
                    controller.getDefinition().getLocalizedName()
                ));
            }
        }
    }
    
    // 辅助类：AABB 与 Controller 的配对（含厌烦机制）
    private record AABBControllerPair(
            AABB aabb, 
            MultiblockControllerGoblinMachine controller,
            AnnoyanceTracker annoyanceTracker) {}
    
    /**
     * 厌烦追踪器 - 记录神明被惊扰的频率
     */
    private static class AnnoyanceTracker {
        /** 最近被触发的时刻列表（长度固定为阈值） */
        private final long[] recentTriggerTimes;
        /** 当前列表索引 */
        private int currentIndex = 0;
        /** 是否已被神明厌弃 */
        private boolean abandoned = false;
        /** 厌弃时间戳（用于冷却） */
        private long abandonmentTime = 0;
        
        public AnnoyanceTracker(int threshold) {
            this.recentTriggerTimes = new long[threshold];
            Arrays.fill(recentTriggerTimes, -1); // -1 表示未使用
        }
        
        /**
         * 记录一次触发，返回是否触发厌弃
         */
        public boolean recordTrigger(long currentTime, long minIntervalMs) {
            if (abandoned) {
                // 检查是否过了冷却期
                if (currentTime - abandonmentTime > ConfigHolder.INSTANCE.machines.multiblockAnnoyanceCooldown * 1000L) {
                    abandoned = false; // 冷却结束，恢复检测
                }
                return false;
            }
            
            // 检查与上一次触发的间隔
            long lastTime = recentTriggerTimes[(currentIndex - 1 + recentTriggerTimes.length) % recentTriggerTimes.length];
            if (lastTime != -1 && currentTime - lastTime < minIntervalMs) {
                // 间隔过短，记录这次触发
                recentTriggerTimes[currentIndex] = currentTime;
                currentIndex = (currentIndex + 1) % recentTriggerTimes.length;
                
                // 检查是否填满（连续触发次数达到阈值）
                if (recentTriggerTimes[(currentIndex - 1 + recentTriggerTimes.length) % recentTriggerTimes.length] != -1) {
                    abandoned = true;
                    abandonmentTime = currentTime;
                    return true; // 触发厌弃
                }
            } else {
                // 间隔足够长，重置列表
                Arrays.fill(recentTriggerTimes, -1);
                recentTriggerTimes[0] = currentTime;
                currentIndex = 1;
            }
            
            return false;
        }
        
        public boolean isAbandoned() {
            return abandoned;
        }
        
        /**
         * 重置厌弃状态（通过拜拜重新获得神明关注）
         */
        public void reset() {
            abandoned = false;
            abandonmentTime = 0;
            Arrays.fill(recentTriggerTimes, -1);
            currentIndex = 0;
        }
    }
    
    /**
     * 查询指定 Controller 的当前状态（不触发神谕计算）
     */
    public FormationStatus getStatus(MultiblockControllerGoblinMachine controller) {
        return cachedStatus.getOrDefault(controller, FormationStatus.INCOMPLETE);
    }
    
    /**
     * 查询指定位置期望放置什么 Part（神告诉哥布林该放什么）
     */
    public ExpectedPartQuery queryExpectedPart(
            MultiblockControllerGoblinMachine controller, BlockPos pos) {
        IDeityOracle oracle = oracles.get(controller);
        if (oracle == null) return ExpectedPartQuery.NONE;
        return oracle.queryExpectedPart(pos);
    }
    
    // === 注册/注销（Controller 的"皈依"与"叛离"） ===
    
    public void registerController(MultiblockControllerGoblinMachine controller) {
        IDeityOracle oracle = createOracle(controller);
        oracles.put(controller, oracle);
        
        // 注册精确空间索引：所有可能被该 Controller 覆盖的坐标
        for (BlockPos coveredPos : oracle.getCoveredPositions()) {
            posToControllers.computeIfAbsent(coveredPos.asLong(), 
                k -> new HashSet<>()).add(controller);
        }
        
        // 注册粗略范围索引：用于快速预筛选（含厌烦追踪器）
        int threshold = ConfigHolder.INSTANCE.machines.multiblockAnnoyanceThreshold;
        roughAABBIndex.add(new AABBControllerPair(
            oracle.getRoughAABB(), 
            controller, 
            new AnnoyanceTracker(threshold)
        ));
        
        cachedStatus.put(controller, FormationStatus.INCOMPLETE);
    }
    
    public void unregisterController(MultiblockControllerGoblinMachine controller) {
        IDeityOracle oracle = oracles.remove(controller);
        if (oracle != null) {
            // 注销精确空间索引
            for (BlockPos pos : oracle.getCoveredPositions()) {
                var set = posToControllers.get(pos.asLong());
                if (set != null) {
                    set.remove(controller);
                    if (set.isEmpty()) posToControllers.remove(pos.asLong());
                }
            }
            
            // 注销粗略范围索引
            roughAABBIndex.removeIf(pair -> pair.controller() == controller);
        }
        cachedStatus.remove(controller);
    }
    
    private IDeityOracle createOracle(MultiblockControllerGoblinMachine ctrl) {
        return switch (ctrl.getDefinitionData().verifyMode()) {
            case FIXED     -> new StructureBitmap(...);
            case SECTIONED -> new SectionedBitmap(...);
            case ENCLOSURE -> new EnclosureValidator(...);
        };
    }
}
```

##### 神谕接口（IDeityOracle）

```java
public interface IDeityOracle {
    /** 方块变更通知 */
    void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState);
    
    /** 神谕判定：当前是否成型 */
    FormationStatus checkFormation();
    
    /** 查询某个位置期望的 Part 类型（神告诉哥布林该放什么） */
    ExpectedPartQuery queryExpectedPart(BlockPos pos);
    
    /** 该神谕覆盖的所有坐标（用于精确空间索引注册） */
    Collection<BlockPos> getCoveredPositions();
    
    /** 获取粗略包围盒（用于快速预筛选，神明懒得理会范围外的僭越） */
    AABB getRoughAABB();
}
```

##### 事件驱动流程（完整）

```
BlockEvent.EntityPlace / BlockEvent.Break（哥布林僭越神权）：
  └─ GoblinMachineDeity.onBlockChanged(level, pos, oldState, newState)
       ├─ roughAABBCheck(pos) —— 粗略范围检查（神明懒得理会范围外的僭越）
       │   └─ 不在任何机器范围内 → 直接返回空（神忽略）
       ├─ 通过 posToControllers 精确查找受影响的 Controller
       ├─ 调用每个 Controller 的 oracle.onBlockChanged()
       ├─ 调用 oracle.checkFormation()
       └─ 返回状态变化的 Controller 列表
            │
            ├─ 新成型 → controller.onStructureFormed()（取悦神明）
            └─ 新失效 → controller.onStructureInvalid()（神罚）
```

##### 设计优势

| 维度 | 传统（神谕嵌入 Controller） | 集中神（GoblinMachineDeity） |
|------|-------------------------------|---------|
| 查找受影响的 Controller | O(k) 遍历所有 Controller | O(1) 空间哈希查找 |
| 共享 Part 的多个 Controller | 每个独立检查 | 一次神谕调用批量处理 |
| 状态缓存 | 各自维护 | 神统一缓存 |
| 可替换性 | Controller 硬编码 | `IDeityOracle` 接口，注入不同实现 |
| Mixin 友好 | 需 mixin Controller | 可 mixin 神的 `onBlockChanged` 或替换整个神实例 |
| 查询期望 Part | 需持有 Controller 引用 | `queryExpectedPart()` 一站式查询 |

##### AOT 可行性分析

前面提到的 AOT 库加速在此场景中**不必要**，原因：

| 操作 | Java 成本 | JNI/Native 成本 | 结论 |
|------|----------|----------------|------|
| `BitSet.set(idx)` | ~5ns（JIT 内联为 `BTS` 指令） | ~20-50ns（JNI 边界穿越） | JNI 更慢 |
| `BitSet.andNot()` | ~3ns/long（JIT 向量化） | 同上 + 堆外内存拷贝 | 拷贝开销 > 计算 |
| 成型检查（256 方块） | ~20ns（4 次 long 比较） | ~200ns+ | 10 倍劣化 |
| BFS（超净间，1000 方块） | ~5μs（`LongOpenHashSet`） | 可能持平但需数据拷贝 | 收益不抵维护成本 |

`BitSet` 是 JIT 一等公民——HotSpot 内联到单条 x86 指令。JNI 边界穿越 20-50ns，而一次 `BitSet.set` 只要 5ns。Native 在这个场景中是净劣化。

但**接口抽象**确实为未来留了后路：
- 如果 Java 实现被证明是瓶颈（几乎不可能，Minecraft 本身的开销远大于此），可以实现 `NativeDeityOracle implements IDeityOracle`
- 配置文件控制：`deity.backend = java | native`（默认 java）
- 关键价值是**架构的开放封闭原则**，而非实际性能收益

#### 8.7.14 与 ShamanItem 的整合

```
ShamanItem（蓝图腾）创建时：
  ├─ 从 Controller 的 getDefinitionData() 序列化结构定义
  └─ 蓝图 NBT 中包含 verifyMode + 结构参数

蓝图大炮打印完成后（显灵）：
  ├─ 所有 Part 方块 + Controller 方块放置完毕
  └─ GoblinMachineDeity.registerController(controller)
       └─ oracle.checkFormation() → 如果通过 → onStructureFormed()
            └─ 如果未通过 → 等待后续手动补 Part

手动 Part 放置时（仪式四）：
  ├─ GoblinMachineDeity.onBlockChanged(pos, oldState, newState)
  └─ 如果返回 Formed → 客户端 Outliner 清除 + onStructureFormed()

---

## 9. 改动文件清单

### 9.1 Phase 1 — GoblinMultiblockPartMachine（当前可执行）

| 文件 | 操作 | 说明 |
|------|------|------|
| `GoblinMultiblockPartMachine.java` | **新建** | `extends MultiblockPartMachine`，覆写 `getBlockAppearance()` |
| `TieredPartGoblinMachine.java` | **修改**（改继承为 GoblinMultiblockPartMachine） | ~5 行改动 |
| `TieredIOPartGoblinMachine.java` | **不修改**（继承链自动变更） | 0 行 |

> **不需要**：IGhostMachine、GhostPartHullRender、GhostPartHullRenderType、GhostPartHullModel、transparent.png、GTCEu DynamicRender 注册。
> **移除现有文件**：`GhostPartHullRender.java`、`GhostPartHullRenderType.java`。

### 9.2 Phase 2 — Part 放置交互系统

| 文件 | 操作 | 说明 |
|------|------|------|
| `TieredPartGoblinMachine` 改为 Item | **修改** | 不可直接放置，改为交互物品 |
| `GoblinPartItemHandler.java` | **新建** | 客户端 tick + Outliner 渲染（绑定/放置/共享） |
| `GoblinPartBindPacket.java` | **新建** | 绑定确认网络包 |
| `GoblinPartPlacePacket.java` | **新建** | 放置 Part 网络包 |
| `GoblinPartUnbindPacket.java` | **新建** | 取消绑定网络包 |
| `GoblinMultiblockDefinition.java` | **新建** | 蓝图位置定义数据模型 |

### 9.3 Phase 3 — Controller 改造

| 文件 | 操作 | 说明 |
|------|------|------|
| `MultiblockControllerGoblinMachine.java` | **新建** | 继承 MultiblockControllerMachine |
| override `onLoad()` | 不注册 asyncLogic | 阻止定时结构检查 |
| override `asyncCheckPattern()` | 空实现 | 双重保险 |
| override `onUse()` | 不调用 showPreview | 阻止世界内预览 |
| override `onRotated()` / `setFrontFacing()` | 去掉 checkPattern | 阻止转动触发的检查 |
| 添加 `getDefinitionData()` | **新增** | 返回结构定义（供 ShamanItem 序列化） |

### 9.4 Phase 4 — 多方块机器之神（GoblinMachineDeity）

| 文件 | 操作 | 说明 |
|------|------|------|
| `IDeityOracle.java` | **新建** | 神谕接口（`onBlockChanged` / `checkFormation` / `queryExpectedPart` / `getCoveredPositions`） |
| `GoblinMachineDeity.java` | **新建** | 集中式神，以 Controller 为键管理神谕缓存，空间哈希 O(1) 查找 |
| `StructureBitmap.java` | **新建** | FIXED 模式神谕，BitSet + byte[] + 计数约束 |
| `SectionedBitmap.java` | **新建** | SECTIONED 模式，capA + N×middle + capB 动态推断 |
| `EnclosureValidator.java` | **新建** | ENCLOSURE 模式，缓存 BFS 围合检测 |
| `PlacementResult.java` | **新建** | 密封接口：Ok / OutOfBounds / TypeMismatch / CountExceeded |
| `FormationStatus.java` | **新建** | 密封接口：Formed / Incomplete / CountUnderMin |
| `ExpectedPartQuery.java` | **新建** | 查询期望的 Part 类型（供 Outliner 用） |

#### Sable Companion 兼容

**核心设计**：确保多方块机器在被物理化（physicify）时能正确迁移并判断成型状态。

**兼容策略**：

| 场景 | 处理方式 |
|------|---------|
| **坐标投影** | 使用 `SableCompanion.INSTANCE.projectOutOfSubLevel()` 将子世界坐标转换为主世界坐标 |
| **子世界检测** | 使用 `SableCompanion.INSTANCE.getContaining()` 判断位置是否在子世界内 |
| **空间索引** | 所有空间索引存储主世界坐标，查询时先投影再查找 |
| **结构验证** | 验证时使用投影后的坐标进行检查 |

**关键代码示例**：

```java
public class GoblinMachineDeity {
    // ...
    
    /**
     * 获取位置所在的主世界坐标（处理子世界情况）
     */
    private long getWorldPositionKey(Level level, BlockPos pos) {
        // 使用 Sable Companion 投影坐标
        Vec3 projected = SableCompanion.INSTANCE.projectOutOfSubLevel(level, pos);
        return BlockPos.asLong(
            Mth.floor(projected.x()),
            Mth.floor(projected.y()),
            Mth.floor(projected.z())
        );
    }
    
    /**
     * 检查位置是否在某个 Controller 的检测范围内（考虑子世界）
     */
    private boolean isInControllerRange(Level level, BlockPos pos, 
                                        MultiblockControllerGoblinMachine controller) {
        // 获取控制器位置的主世界坐标
        Vec3 controllerWorldPos = SableCompanion.INSTANCE.projectOutOfSubLevel(
            level, controller.getPos()
        );
        
        // 获取变更位置的主世界坐标
        Vec3 posWorldPos = SableCompanion.INSTANCE.projectOutOfSubLevel(level, pos);
        
        // 计算距离
        double distance = controllerWorldPos.distanceTo(posWorldPos);
        return distance <= controller.getDefinitionData().getMaxRange();
    }
    
    /**
     * 注册 Controller（处理子世界情况）
     */
    public void registerController(MultiblockControllerGoblinMachine controller) {
        Level level = controller.getLevel();
        
        // 获取控制器所在的子世界（如果有的话）
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(controller);
        
        IDeityOracle oracle = createOracle(controller);
        oracles.put(controller, oracle);
        
        // 注册空间索引（使用主世界坐标）
        for (BlockPos coveredPos : oracle.getCoveredPositions()) {
            // 如果控制器在子世界中，需要将相对坐标转换为主世界坐标
            BlockPos worldPos = subLevel != null 
                ? convertToWorldPos(subLevel, coveredPos) 
                : coveredPos;
            
            posToControllers.computeIfAbsent(worldPos.asLong(), 
                k -> new HashSet<>()).add(controller);
        }
        
        // ... 其他注册逻辑
    }
    
    private BlockPos convertToWorldPos(SubLevelAccess subLevel, BlockPos localPos) {
        // 使用子世界的 pose 将本地坐标转换为主世界坐标
        Vector3dc worldPos = subLevel.getPlot().getPose().transform(localPos.getX(), 
            localPos.getY(), localPos.getZ());
        return new BlockPos(
            Mth.floor(worldPos.x()),
            Mth.floor(worldPos.y()),
            Mth.floor(worldPos.z())
        );
    }
}
```

**依赖声明**（在 `libs.versions.toml` 中）：

```toml
[sable-companion]
version = "1.6.0"
```

```gradle
// build.gradle
dependencies {
    modImplementation "dev.ryanhcode:sable-companion:${libs.versions.sable-companion.get()}"
}
```

#### 神明不厌其烦机制

**核心设计**：频繁触发检测范围会导致神明厌弃，**不仅暂停检测，还会拒绝承认结构完整**，强制触发神罚（机器失效），必须通过"拜拜"仪式重新验证结构。

**实现逻辑**：

| 组件 | 说明 |
|------|------|
| `AnnoyanceTracker` | 每个 Controller 的厌烦追踪器，记录最近触发时刻 |
| `recentTriggerTimes[]` | 固定长度的时间戳列表（长度 = 阈值） |
| `minIntervalMs` | 触发间隔阈值（低于此值视为频繁） |
| `abandoned` | 是否已被厌弃 |
| `abandonmentTime` | 厌弃时间戳（用于冷却） |

**触发条件**：
1. 方块变更命中检测范围
2. 与上一次触发间隔 < `minIntervalMs`
3. 连续触发次数达到阈值 → **神明厌弃（神罚）**

**神罚效果**：
- **立即强制失效**：如果机器之前是成型状态，立即标记为不完整
- **拒绝承认结构**：厌弃期间任何方块变更都不会触发成型检查
- **持续惩罚**：每次方块变更都会再次确认失效状态

**恢复机制**：
- **拜拜仪式**：直接重置厌烦状态，重新获得神明关注（并触发一次结构验证）
- **自动冷却**：经过 `multiblockAnnoyanceCooldown` 秒后自动恢复

**警告消息**：神明厌弃时向附近玩家发送警告：
```
"<机器名称> 的神明已对你感到厌烦！请使用蓝图腾拜拜重新获得神的认可。"
```

**配置项**（在 `ConfigHolder.java` 的 `MachineConfigs` 中添加）：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `multiblockAnnoyanceThreshold` | int | 5 | 连续频繁触发多少次会导致厌弃 |
| `multiblockAnnoyanceInterval` | int | 20 | 触发间隔阈值（tick，20 tick = 1 秒） |
| `multiblockAnnoyanceCooldown` | int | 60 | 厌弃冷却时间（秒） |

### 9.5 Phase 5 — ShamanItem（蓝图腾）

| 文件 | 操作 | 说明 |
|------|------|------|
| `ShamanItem.java` | **新建** | `extends SchematicItem`，S-h-a-m-a-n / S-c-h-e-m-a-t-i-c 谐音梗 |
| `ShamanItemHandler.java` | **新建** | 客户端预览 Handler（扩展 SchematicHandler） |
| `ShamanTool.java` | **新建** | 自定义第二排工具（分层/变体/重复） |
| `ShamanDataComponent.java` | **新建** | 存储 Controller 定义引用 |

### 9.6 Phase 6 — WorkableTieredGoblinMachine

| 文件 | 操作 |
|------|------|
| `WorkableTieredGoblinMachine.java` | **新建**，`extends MetaMachine implements IGoblinRecipeLogicMachine` |

---

## 10. 技术决策记录

### 10.1 为什么不创建 GoblinMetaMachine 中介类

Part（伪装+多方块）、单方块（多供能）、Controller（阻止老逻辑）三个方向需求正交，无共同中介层可抽取。各自直接从 GTCEu 基类继承更干净。

### 10.2 为什么 GoblinMultiblockPartMachine 继承 MultiblockPartMachine

GTCEu 的 `MultiblockPartMachine` 168 行代码零 `final` 方法、零 `final` 类，完全可继承。直接继承免去搬运 5 大功能模块的维护成本。

### 10.3 为什么 MultiblockControllerGoblinMachine 继承 MultiblockControllerMachine

GTCEu 内部有 47 处 `instanceof MultiblockControllerMachine` 检查（BlockPattern、RecipeLogic、GTRecipeModifiers、Jade 等）。不继承则多方块成型、配方处理、Jade 显示全部崩溃。

### 10.4 为什么 DynamicRender 泛型用 IGhostMachine 而非具体类

`IGhostMachine` 是接口，任何实现它的机器类（不仅是 `GoblinMultiblockPartMachine`）都能自动获得幽灵外壳渲染能力。

### 10.5 为什么 GhostPartHullRender 不偏移 quad

底座模型已被 `textureOverrides` 替换为透明，覆盖层无需内偏移防 z-fighting。`GhostPartHullRender` 直接在方块空间渲染目标模型即可。

### 10.6 为什么 TieredPartGoblinMachine 不是方块而是物品

TieredPartGoblinMachine 是一个"幽灵 Part"——它只有在放置到 Controller 蓝图定义的位置后才能变成方块。单独存在时只是物品。这与 Create 蓝图系统的"先定义结构再放置"理念一致，也与 Cover 操作类似但不是在可放置 Cover 的方块上进行。

### 10.7 为什么复用 Create 蓝图系统而非自建

- **免去适配工作**：Create 蓝图已有完整的客户端预览（SchematicHandler + SchematicRenderer）、蓝图大炮搭建（Schematicannon）、序列化（StructureTemplate）、变换系统（SchematicTransformation）
- **原生支持动画**：蓝图大炮搭建有完整的粒子+音效动画
- **原生支持移动/旋转/翻转**：这些是 Create 蓝图工具的标配功能
- **天然兼容**：作为 Create 附属 mod，无需额外添加依赖

### 10.8 为什么采用集中式神谕（GoblinMachineDeity）设计

| 方案 | 问题 | 后果 |
|------|------|------|
| 每个机器自己监听 | 100个机器 = 100个监听器 | 事件总线拥堵，性能差 |
| 每个机器自己检查 | 每次变更都遍历所有机器 | O(n) 复杂度，n=机器数 |
| 分散状态管理 | 每个机器维护自己的状态 | 难以协调，容易不一致 |

**集中式方案的优势**：
- **单一监听器**：只有 `GoblinMachineDeity` 监听方块变更事件
- **双层筛选**：先 AABB 粗略筛选，再精确哈希查找，O(1) 找到受影响机器
- **统一状态缓存**：所有机器的成型状态由神统一管理
- **易于扩展**：新增神谕模式只需实现 `IDeityOracle` 接口

### 10.9 三种神谕模式的适用场景

#### FIXED（固定形状）
- **适用**：结构固定、不可扩展的机器（如基础机器、特定配方机器）
- **特点**：使用 `StructureBitmap`，BitSet 精确匹配每个位置
- **示例**：3x3x3 标准多方块结构

#### SECTIONED（沿轴可延展）
- **适用**：沿某一轴可重复扩展的机器（如传送带、管道阵列）
- **特点**：`capA + N×middle + capB` 动态组合，自动推断重复次数
- **示例**：长度可变的能量传输管道

#### ENCLOSURE（自由围合）
- **适用**：无固定形状、只需边界围合的机器（如超净间、反应堆腔室）
- **特点**：事件驱动 + 缓存 BFS，验证封闭空间
- **示例**：需要封闭环境的特殊加工室

### 10.10 性能优化考虑

| 优化点 | 实现方式 | 收益 |
|--------|---------|------|
| 粗略范围预筛选 | `roughAABBIndex` 快速跳过无关变更 | 99% 无效变更直接跳过 |
| 精确空间哈希 | `posToControllers` Long2ObjectOpenHashMap | O(1) 查找受影响机器 |
| 状态缓存 | `cachedStatus` Map | 避免重复计算成型状态 |
| 事件驱动 | 仅在方块变更时触发检查 | 消除定时轮询开销 |
| FastUtil 集合 | `LongOpenHashSet`、`Long2ObjectOpenHashMap` | 避免装箱，提升缓存友好性 |

### 10.11 与 GTCEu 原生多方块系统的对比

| 维度 | GTCEu 原生 | GoblinMachineDeity |
|------|-----------|-------------------|
| **触发时机** | 定时轮询（每 tick 或每 125ms） | 事件驱动（方块变更时） |
| **延迟** | 最高可达数百毫秒 | 瞬时（<1ms） |
| **复杂度** | O(n) 遍历所有机器 | O(1) 哈希查找 |
| **状态管理** | 分散在每个 Controller | 集中缓存 |
| **动态结构** | 不支持 | 支持（SECTIONED / ENCLOSURE） |
| **错误提示** | 仅支持 JEI 预览 | 支持实时 Outliner 标记 |
| **扩展性** | 固定模式 | 可扩展神谕模式 |

### 10.12 三种神谕模式的实现细节

#### StructureBitmap（FIXED 模式）

```java
public class StructureBitmap implements IDeityOracle {
    // 三维 BitSet：每个位置一个 bit
    private final BitSet expected;      // 期望的位置
    private final BitSet actual;        // 实际放置的位置
    private final byte[] expectedTypes; // 每个位置期望的 Part 类型
    private final byte[] actualTypes;   // 每个位置实际的 Part 类型
    private final int[] countConstraints; // 每种类型的数量约束
    
    @Override
    public FormationStatus checkFormation() {
        // 快速路径：位运算比较
        BitSet missing = (BitSet) expected.clone();
        missing.andNot(actual);
        
        if (!missing.isEmpty()) {
            return FormationStatus.incomplete(missing);
        }
        
        // 检查数量约束
        for (int type = 0; type < countConstraints.length; type++) {
            int expectedCount = countConstraints[type];
            int actualCount = countType(actualTypes, type);
            if (actualCount != expectedCount) {
                return FormationStatus.countMismatch(type, expectedCount, actualCount);
            }
        }
        
        return FormationStatus.FORMED;
    }
}
```

#### SectionedBitmap（SECTIONED 模式）

```java
public class SectionedBitmap implements IDeityOracle {
    private final Direction.Axis repeatAxis;
    private final int minRepeat, maxRepeat;
    private int currentRepeat = -1; // 由已放置的 Part 动态推断
    
    private final BitmapSection capA;      // 固定端盖 A
    private final BitmapSection middle;    // 可重复段
    private final BitmapSection capB;      // 固定端盖 B
    
    private StructureBitmap activeBitmap;  // 当前活跃的完整 bitmap
    
    @Override
    public void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        // 根据新放置的 Part 位置推断重复次数
        currentRepeat = inferRepeatCount(pos);
        // 重建活跃 bitmap
        activeBitmap = buildActiveBitmap(currentRepeat);
    }
    
    @Override
    public FormationStatus checkFormation() {
        if (currentRepeat < minRepeat) {
            return FormationStatus.countUnderMin(minRepeat, currentRepeat);
        }
        if (currentRepeat > maxRepeat) {
            return FormationStatus.countExceeded(maxRepeat, currentRepeat);
        }
        return activeBitmap.checkFormation();
    }
}
```

#### EnclosureValidator（ENCLOSURE 模式）

```java
public class EnclosureValidator implements IDeityOracle {
    private final LongOpenHashSet boundaryPositions = new LongOpenHashSet();
    private final LongOpenHashSet partPositions = new LongOpenHashSet();
    private boolean validated = false;
    private boolean lastResult = false;
    
    @Override
    public void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        validated = false; // 标记待验证
        
        if (isBoundaryBlock(newState)) {
            boundaryPositions.add(pos.asLong());
        } else {
            boundaryPositions.remove(pos.asLong());
        }
        
        if (isPartBlock(newState)) {
            partPositions.add(pos.asLong());
        } else {
            partPositions.remove(pos.asLong());
        }
    }
    
    @Override
    public FormationStatus checkFormation() {
        if (validated) {
            return lastResult ? FormationStatus.FORMED : FormationStatus.INCOMPLETE;
        }
        
        // BFS 验证围合
        lastResult = isEnclosed();
        validated = true;
        
        return lastResult ? FormationStatus.FORMED : FormationStatus.INCOMPLETE;
    }
    
    private boolean isEnclosed() {
        // BFS 从 Controller 向外遍历，验证所有 Part 被边界包围
        // ...
    }
}
```

### 10.13 为什么放弃 DynamicRender 方案改用 getBlockAppearance()

| 维度 | DynamicRender 方案 | getBlockAppearance() 方案 |
|------|-------------------|--------------------------|
| 新增类 | IGhostMachine + Render + RenderType + Model + transparent.png ≈ 5 个 | **0 个** |
| 渲染方式 | 底座透明 + DynamicRender 接管 | Minecraft 原生渲染 |
| CTM | 需要在 getRenderQuads 中手动获取 CTM model | 原生支持（通过 getAppearance 查询链） |
| 方块属性 (FACING等) | 需在 DynamicRender 中手动处理 | 自动支持（渲染完整 BlockState） |
| 破坏动画 | 需额外同步逻辑 | 自动显示伪装方块的破坏动画 |
| 注册需求 | 需在 GTCEu CommonProxy 注册 DynamicRenderType | 无需任何注册 |
| 端口(port)显示 | 需额外叠加层渲染 | 保留 GTCEu 默认端口渲染 |
| 维护成本 | 多条渲染路径相互关联 | 一个方法覆写 |
| Phase 数量 | 7 个 Phase | 6 个 Phase |

**结论**：GTCEu 的 `appearanceBlock()` 本就设计为"让 Part 看起来像另一个方块"。我们只需要绕过"成型后才生效"的限制，直接在 Part 中覆写 `getBlockAppearance()` 返回存储的 hullState。Minecraft 原生渲染管线（含 Create CTM）自动处理后续一切。

---

## 11. 相关文件

### 11.1 GoblinTech 项目文件

| 文件 | 说明 |
|------|------|
| **多方块系统** | |
| `src/main/java/com/goblincoders/goblintech/api/machine/multiblock/part/GoblinMultiblockPartMachine.java` | Part 基类（★ 覆写 `getBlockAppearance()` 实现伪装） |
| `src/main/java/com/goblincoders/goblintech/api/machine/multiblock/part/TieredPartGoblinMachine.java` | 分级 Part |
| `src/main/java/com/goblincoders/goblintech/api/machine/multiblock/part/TieredIOPartGoblinMachine.java` | IO Part |
| `src/main/java/com/goblincoders/goblintech/api/machine/multiblock/MultiblockControllerGoblinMachine.java` | Controller 基类 |
| **神谕系统（Phase 4 新增）** | |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/GoblinMachineDeity.java` | 多方块机器之神（集中式验证引擎） |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/IDeityOracle.java` | 神谕接口 |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/StructureBitmap.java` | FIXED 模式神谕实现 |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/SectionedBitmap.java` | SECTIONED 模式神谕实现 |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/EnclosureValidator.java` | ENCLOSURE 模式神谕实现 |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/FormationStatus.java` | 成型状态枚举 |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/ExpectedPartQuery.java` | 期望 Part 查询结果 |
| **蓝图腾系统** | |
| `src/main/java/com/goblincoders/goblintech/api/item/ShamanItem.java` | 蓝图腾物品（谐音 Create Schematic） |
| **其他** | |
| `src/main/java/com/goblincoders/goblintech/GoblinTech.java` | Mod 入口 |

> **已从列表中移除**：`IGhostMachine`、`GhostPartHullRender`、`GhostPartHullRenderType`、`GhostPartHullModel`、`transparent.png` — 不再需要。

### 11.2 GTCEu 基类文件

| 文件 | 说明 |
|------|------|
| `src/main/java/com/gregtechceu/gtceu/api/machine/multiblock/part/MultiblockPartMachine.java` | GTCEu Part 基类（GoblinMultiblockPartMachine 的继承源） |
| `src/main/java/com/gregtechceu/gtceu/api/machine/multiblock/MultiblockControllerMachine.java` | GTCEu Controller 基类（MultiblockControllerGoblinMachine 的继承源） |
| `src/main/java/com/gregtechceu/gtceu/client/renderer/machine/DynamicRender.java` | GTCEu 动态渲染基类 |
| `src/main/java/com/gregtechceu/gtceu/client/model/machine/MachineModel.java` | GTCEu 机器模型渲染管线 |

### 11.3 Create 参考文件

| 文件 | 说明 |
|------|------|
| `Create-mc1.21.1-6.0.10/.../schematics/SchematicItem.java` | 蓝图物品（ShamanItem 的继承目标，S-h-a-m-a-n / S-c-h-e-m-a-t-i-c 谐音梗） |
| `Create-mc1.21.1-6.0.10/.../schematics/client/SchematicHandler.java` | 蓝图客户端 Handler |
| `Create-mc1.21.1-6.0.10/.../schematics/client/SchematicTransformation.java` | 蓝图变换系统 |
| `Create-mc1.21.1-6.0.10/.../schematics/client/SchematicRenderer.java` | 蓝图 3D 渲染 |
| `Create-mc1.21.1-6.0.10/.../schematics/client/tools/ToolType.java` | 蓝图工具枚举 |
| `Create-mc1.21.1-6.0.10/.../schematics/SchematicPrinter.java` | 蓝图打印机（批量方块替换） |
| `Create-mc1.21.1-6.0.10/.../schematics/cannon/SchematicannonBlockEntity.java` | 蓝图大炮 |
| `Create-mc1.21.1-6.0.10/.../glue/SuperGlueSelectionHandler.java` | Create 强力胶可视化参考 |
| `Create-mc1.21.1-6.0.10/.../glue/SuperGlueEntity.java` | Create 强力胶实体（AABB 检测） |
| `Create-mc1.21.1-6.0.10/.../glue/SuperGlueSelectionHelper.java` | Create 强力胶 BFS 漫水填充 |
| `Create-mc1.21.1-6.0.10/.../chassis/ChassisRangeDisplay.java` | Create 底盘范围显示（Outliner 参考） |
