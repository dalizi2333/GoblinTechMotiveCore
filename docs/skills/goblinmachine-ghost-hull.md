# GoblinMachine 幽灵外壳架构

## 1. 概述

### 1.1 设计目标

为 GoblinTechMotive 的多方块 Part 机器提供"幽灵外壳/伪装方块"能力——Part 机器可以在运行时替换外观纹理（包括 Create 的 CTM 连接纹理），同时保持机器原有功能。外部看是普通方块，内部是 GTCEu 多方块部件。

核心需求：
1. **伪装外观**：Part 机壳纹理即时变化，使用物品操作后替换为目标方块的完整外观（含 CTM）
2. **CTM 连接**：相邻方块正确识别本机外观，实现连续纹理
3. **蓝图建造**：参考 Create 强力胶 + 蓝图机制，先定义结构再放置/替换
4. **多供能单方块**：`GoblinWorkableMachine` 原生支持电能/动能/蒸汽等
5. **控制器改造**：`GoblinControllerMachine` 阻止老旧定时检查，改用事件驱动

### 1.2 设计原则

- **最小侵入**：`GoblinPartMachine` 继承 GTCEu 的 `MultiblockPartMachine`，只覆写一个方法
- **原生渲染**：覆写 `MetaMachine.getBlockAppearance()` 返回 `hullState`，Minecraft 原生渲染管线处理一切（含 Create CTM）
- **无需 DynamicRender**：不需要透明底座 + 动态渲染来绘制外壳
- **无需 IGhostMachine 渲染层**：伪装外观通过 GTCEu 原生外观系统实现
- **事件驱动**：结构验证不再定时轮询，改为参考 Create `EntityPlaceEvent` 事件驱动 + BFS 漫水填充
- **★ 绑定放置**：`GoblinPartMachine` 方块不可直接放置，必须通过物品形式先绑定 Controller，再放到 Deity 缓存的可用位置

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

**关键发现**：在 `GoblinPartMachine` 中直接覆写 `getBlockAppearance()` 返回存储的 `hullState`，就能让 Minecraft 原生渲染管线渲染目标方块的完整模型——包括 Create 的 CTM。因为 CTM 通过 NeoForge 的 `Block.getAppearance()` 查询相邻方块外观，而我们的覆写恰好在这个入口处返回了伪装方块。

**结论**：不需要 `DynamicRender`、不需要透明底座模型、不需要 `IGhostMachine` 的渲染层面。只需要一个方法覆写。

---

## 2. 包名架构

### 2.1 包结构

| 包名 | 层级 | 职责 |
|------|------|------|
| `com.goblincoders.goblintech.api.machine.multiblock.part` | API | `GoblinPartMachine`、`GoblinTieredPartMachine`、`GoblinTieredIOPartMachine` |
| `com.goblincoders.goblintech.api.machine.multiblock` | API | `GoblinControllerMachine`（未来） |
| `com.goblincoders.goblintech.api.machine.deity` | API | `GoblinMachineDeity`、`IDeityOracle`、`StructureBitmap`、`SectionedBitmap`、`EnclosureValidator` |
| `com.goblincoders.goblintech.api.machine` | API | `GoblinWorkableMachine`（未来） |
| `com.goblincoders.goblintech.api.item` | API | `GoblinShamanItem`（蓝图腾） |
| `com.goblincoders.goblintech.client.shaman` | 客户端 | `GoblinShamanItemHandler`（Outliner 可视化 + 仪式交互） |

> **已移除**：`com.goblincoders.goblintech.api.machine.feature.multiblock` — `GhostPartHullRender`/`RenderType`/`Model` 不再需要，`IGhostMachine` 接口也不再需要。伪装外观通过直接覆写 `MetaMachine.getBlockAppearance()` 实现。

### 2.2 与现有包的关系

```
goblintech（主包）
├── api.machine.feature.multiblock   ← 本次新增：幽灵外壳 API
├── api.machine.multiblock.part      ← 本次新增：Part 机器层次
├── api.machine.multiblock           ← 未来：Controller 改造
├── api.machine                      ← 未来：GoblinWorkableMachine
├── recipe                           ← 已有：配方系统（`GoblinOracleOfScripture`、`GoblinScripture`）
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
│   └── GoblinPartMachine     ← 本次新建
│       ├── getBlockAppearance() → 返回 hullState（★ 核心：一个方法实现伪装+CTM）
│       ├── hullState / originalBlockStack 字段
│       └── GoblinTieredPartMachine     ← 改继承
│           └── GoblinTieredIOPartMachine ← 自动继承
│
├── GoblinWorkableMachine implements IBelieverOfScripture  ← 未来
│   （多供能单方块，无伪装特性）
│
└── MultiblockControllerMachine (GTCEu, 421行)
    └── GoblinControllerMachine implements IBelieverOfScripture  ← 未来
        ├── onLoad() → 不注册 asyncLogic
        ├── asyncCheckPattern() → 空实现
        └── （保留 Controller 类型身份通过 instanceof 检查）
```

> 伪装外观通过直接覆写 `MetaMachine.getBlockAppearance()` 实现，不再需要 `IGhostMachine` 接口或透明底座。

### 3.2 为什么不创建 GoblinMetaMachine

三个分支的需求正交——Part 需要伪装+多方块，单方块需要多供能，Controller 需要阻止老逻辑。没有共同中介层需要抽取。每个分支直接从 GTCEu 对应基类继承。

---

## 4. 伪装外观实现（getBlockAppearance 覆写）

### 4.1 核心方法

```java
// GoblinPartMachine.getBlockAppearance() — ★ 伪装全貌，一个方法足矣
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
    // ★ 直接返回绝对 BlockState，不做旋转

    return super.getBlockAppearance(state, level, pos, side, sourceState, sourcePos);
}
```

> 完整类定义见 [§5 GoblinPartMachine](#5-goblinpartmachine)。

### 4.2 为什么它能工作

| 步骤 | 谁调用的 | 发生了什么 |
|------|---------|-----------|
| 1 | Minecraft 渲染器 | 调用 `Block.getAppearance()` 获取方块外观 |
| 2 | `MetaMachineBlock.getAppearance()` | 转发给机器实例的 `getBlockAppearance()` |
| 3 | 我们的覆写 | 返回存储的 `hullState`（伪装方块的 BlockState） |
| 4 | Minecraft 模型系统 | 加载该 BlockState 的完整 BakedModel（含 Create CTM） |
| 5 | Create CTM | 通过步骤 1 查询相邻方块，发现我们的方块返回了匹配的外壳 → 纹理连接 |

### 4.3 朝向处理

#### 核心原则：hullState 存绝对 BlockState

`hullState` 存储的是**伪装目标方块在世界中的绝对朝向**，不是相对 Part 的朝向。渲染时直接返回，不做旋转。

```
伪装时：原方块是熔炉朝东 → hullState = furnace[facing=east]
渲染时：直接返回 hullState → 熔炉始终朝东
扳手旋转 Part 从北到西：hullState 不变 → 熔炉仍然朝东 ✓
蓝图整体旋转 90°：hullState 随结构数据旋转 → 熔炉朝南 ✓（蓝图系统原生行为）
```

#### 两种旋转场景

| 场景 | 触发方式 | hullState 行为 | 原理 |
|------|---------|---------------|------|
| 扳手旋转 Part | 玩家单独旋转机器 | **不变**（伪装保持绝对朝向） | Part 转了但伪装没转，相对关系自动变化 |
| 蓝图/AE2 整体旋转 | 结构被整体旋转放置 | **随结构旋转** | 蓝图系统和 AE2 空间塔原生处理 BlockState 旋转 |

#### 为什么不需要四元数/相对旋转

- 伪装时记录的就是**世界坐标下的绝对 BlockState**
- 扳手旋转 Part 后，相对关系变了，但绝对 BlockState 没变，渲染正确
- 蓝图/AE2 旋转整个结构时，BlockState 的 FACING 会被结构系统自动旋转，无需我们干预

#### 简化后的 getBlockAppearance()

```java
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
    // ★ 直接返回，不做任何旋转。hullState 是绝对 BlockState。

    return super.getBlockAppearance(state, level, pos, side, sourceState, sourcePos);
}
```

#### 场景示例

```
初始状态：Part 朝北，伪装熔炉朝东
┌─────────────────────────────────────────┐
│  世界：                                  │
│    熔炉朝东 [hullState=furnace(east)]     │
│    Part 朝北                              │
│                                          │
│  扳手旋转 Part → 朝西：                    │
│    熔炉朝东 [hullState 不变]               │
│    Part 朝西                              │
│                                          │
│  蓝图整体旋转 90° 顺时针 → 放置：           │
│    熔炉朝南 [蓝图系统自动旋转 hullState]     │
│    Part 朝东 [蓝图系统自动旋转 Part]        │
└─────────────────────────────────────────┘
```

### 4.4 与旧方案的对比

| | 旧方案（IGhostMachine + DynamicRender） | 新方案（getBlockAppearance 覆写） |
|------|--------------------------------------|-----------------------------------|
| 新增类 | IGhostMachine、GhostPartHullRender、RenderType、Model、transparent.png | **0 个新类** |
| 渲染方式 | DynamicRender 接管，底座透明 | Minecraft 原生渲染 |
| CTM 支持 | 需要额外注册 + renderType 配合 | 原生支持（通过 getAppearance 查询） |
| 方块属性 | 需手动处理 FACING 等 | 直接存绝对 BlockState，扳手旋转不变，蓝图旋转由结构系统处理（见 §4.3） |
| 破坏动画 | 需要同步 | 自动显示 hullState 的破坏动画 |
| 维护成本 | 高（多个类相互关联） | **极低**（一个方法覆写） |

### 4.5 从现有实现中的清理

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

## 5. GoblinPartMachine

### 5.1 基本结构

```java
/**
 * 【神棍描述】幽灵 Part 机器 - 被多方块之神选中的伪装者，凡人不可直视其真身
 * 
 * <p>【工程描述】继承自 GTCEu {@link MultiblockPartMachine}，通过覆写
 * {@link #getBlockAppearance()} 实现伪装外观，原生支持 Create CTM 连接纹理。
 * 
 * <p><b>★ 放置限制</b>：此方块<b>不可被玩家直接从物品栏放置</b>。
 * 必须通过 {@code GoblinTieredPartMachine}（Item）先绑定 Controller，
 * 再从 Deity 缓存的可用位置中选择目标位置放置。
 * 直接放置（如 /setblock、蓝图大炮除外）将被拒绝。
 */
public class GoblinPartMachine extends MultiblockPartMachine {

    @SaveField @SyncToClient @RerenderOnChanged
    private BlockState hullState;        // 伪装方块外观

    @SaveField(nbtKey = "originalBlock")
    private ItemStack originalBlockStack; // 原方块的掉落物（直接破坏时掉落）

    @SaveField(nbtKey = "originalBlockTag")
    @Nullable
    private CompoundTag originalBlockTag; // 原方块的 TileEntity NBT（扳手拆卸时还原）

    /**
     * 【神棍描述】显露真身 - 返回伪装外观
     * 
     * <p>【工程描述】直接返回 hullState，不做旋转。
     * 优先级：Cover > 伪装外观 > GTCEu 原生行为。
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

    // 扳手循环伪装方块属性（FACING / AXIS / LIT 等）
    public boolean cycleHullProperty() {
        // ...
    }

    /**
     * 【神棍描述】还魂 — 拆卸时将伪装方块还原为真实方块
     * 
     * <p>【工程描述】根据拆卸方式不同，还原行为有差异：
     * <ul>
     * <li>扳手拆卸（asBlock=true）：在原位置放置真实方块（保留 TileEntity/NBT）</li>
     * <li>直接破坏（asBlock=false）：掉落 originalBlockStack 物品，不放置方块</li>
     * </ul>
     */
    public void restoreOriginalBlock(boolean asBlock) {
        if (asBlock) {
            getLevel().setBlock(getPos(), getHullState(), 3);
            if (originalBlockTag != null) {
                var be = getLevel().getBlockEntity(getPos());
                if (be != null) be.loadWithComponents(originalBlockTag, getLevel().registryAccess());
            }
        } else {
            Block.popResource(getLevel(), getPos(), originalBlockStack.copy());
        }
    }

    @Override 
    public void onMachineDestroyed() { 
        super.onMachineDestroyed(); 
        restoreOriginalBlock(false); // 直接破坏 → 掉落物品
    }
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
    │   └── GoblinPartMachine.getBlockAppearance()
    │       └── return hullState      ← 直接返回，天然支持 CTM
    └── 剩余由 Minecraft + Create CTM 自动处理
```

**零额外类、零额外渲染步骤、零额外纹理资源。**

---

## ~~6. 渲染系统~~（已移除）

> 此章节描述的 `GhostPartHullRender` / `GhostPartHullRenderType` / `GhostPartHullModel` / `transparent.png` 已不再需要。
> 伪装外观现在通过直接覆写 `GoblinPartMachine.getBlockAppearance()` 实现，Minecraft 原生渲染管线（含 Create CTM）自动处理一切。
> 详见 [§4 伪装外观实现](#4-伪装外观实现getblockappearance-覆写)。

---

## 7. GoblinControllerMachine（计划）

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

- **结构建造**：Create 蓝图 + 大炮，由继承 Create `SchematicItem` 的 `GoblinShamanItem` 驱动
- **结构验证**：`GoblinControllerMachine` 内部维护三维 bitmap，在方块放置/破坏时 O(1) 更新 + O(n/64) 比对（n=方块数），瞬时完成结构验证
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
  GoblinControllerMachine（方块物品，可放置）—— 机器的"肉身"
  GoblinShamanItem（Item，extends Create SchematicItem）—— "蓝图腾"，通神的萨满
  GoblinTieredPartMachine（Item，非方块——不可单独放置在世界中）

世界外层：
  GoblinMachineDeity —— "多方块机器之神"，以 Controller 为键管理成型判定缓存
     ├─ IDeityOracle（神谕）—— 三种判定方式：固定 / 可延展 / 围合
     └─ 方块变更 → onBlockChanged() → 返回哪些 Controller 的成型状态变了

仪式一「拜拜」— 从已放置的 Controller 获取蓝图：
  GoblinShamanItem 对 GoblinControllerMachine 下蹲右键（鞠躬拜一下）
    → 蓝图内容 = 该 Controller 定义的结构（锚定于此位置，不可移动）
    → Controller 位置非法方块 → 红色闪烁（机器在告诉哥布林哪里不对）
    → 输入输出限制 → Outliner 标注（机器在告诉哥布林 IO 方向）
    → 哥布林知道该怎么摆了
    → 重置神明厌烦状态（重新获得神明关注）
    → 立即触发一次结构验证（重新获得神的认可）

仪式二「祈福」— 自由放置蓝图：
  GoblinShamanItem 副手放 GoblinControllerMachine 物品，主手对空气右键
    → 蓝图内容 = Controller 定义的结构 + 可移动/旋转/翻转
    → 支持 Create 蓝图全部工具（MOVE / ROTATE / FLIP / DEPLOY）
    → 放入蓝图加农炮 → 火药 + 动画批量建造
    → Controller 方块随蓝图一起放置到加农炮范围内

仪式三「显灵」— 蓝图加农炮打印完成后：
  GoblinShamanItem 就是 Create 蓝图（extends SchematicItem）→ 加农炮正常使用
    → 加农炮打印全部 Part 方块 + Controller 方块
    → 打印完成后 → GoblinMachineDeity.registerController(controller)
    → deity.checkFormation() → 如果通过 → controller.onStructureFormed()
    → 如果未通过 → 等待后续手动补 Part

仪式四「僭越」— 手动 Part 放置：
  GoblinTieredPartMachine 物品
    └─ 右键 GoblinControllerMachine → 绑定（哥布林将灵魂出卖给神）
         └─ 右键交集中的方块位置 → 放置 + 伪装（僭越神权）
              └─ 每次放置都会惊扰 GoblinMachineDeity（调用成型检查）
                   ├─ 神明宽恕 → 机器成型（僭越行为被认可）
                   └─ 神明震怒 → 机器失效（神罚）
```

### 8.2 GoblinTieredPartMachine 行为状态机

`GoblinTieredPartMachine` 是物品（Item），不是可直接放置的方块物品。

```
GoblinTieredPartMachine（未绑定）
  │
  ├─ 右键 GoblinControllerMachine
  │   ├─ Part 支持被当前 Controller 蓝图使用（类型匹配）
  │   │   └─ 绑定 → 状态变为「已绑定」
  │   └─ Part 不支持 → 不做任何事
  │
  └─（右键非 Controller）→ 不做任何事

GoblinTieredPartMachine（已绑定，持有中）
  │
  ├─ Outliner 标记绑定的 Controller 位置（绿色高亮）
  ├─ Outliner 标记该 Part 在当前 Controller 蓝图中可放置的位置（黄色高亮）
  │
  ├─ 右键另一个 GoblinControllerMachine（不支持共享）
  │   ├─ 原 Controller 标记变红色 + 闪烁
  │   └─ 所有标记淡出 → 放弃绑定
  │
  ├─ 右键另一个 GoblinControllerMachine（支持共享）
  │   ├─ 取两 Controller 蓝图可放置位置的交集
  │   ├─ 交集为空/已被占 → 放弃绑定新 Controller
  │   └─ 交集非空 → 绑定新旧两 Controller
  │       ├─ Outliner 标记两 Controller 位置
  │       └─ Outliner 标记交集位置
  │
  └─ 右键交集位置的方法
      ├─ 目标位置已被 Part 占据 → 拒绝，提示玩家
      ├─ 目标位置不在 Deity 命中缓存中 → ★ 拒绝放置！只有绑定的位置才能放
      └─ 目标位置命中缓存且可替换 → 放置 GoblinPartMachine 方块
          ├─ hullState = 目标位置的原方块 BlockState
          ├─ originalBlockTag = 原方块的 TileEntity NBT（如有）
          ├─ originalBlockStack = 目标位置的原方块掉落物
          ├─ 替换世界中的方块
          ├─ Deity.onPartPlaced(pos, partTypeId) → 命中缓存直接通知 Controller
          └─ addedToController() → 注册到绑定的 Controller

  └─ 右键非缓存的位置 → ★ 禁止！（必须绑定 Controller 后才能放到特定位置）
  └─（尝试直接放置 GoblinPartMachine 方块物品）→ ★ 禁止！（BlockItem 不可用）
```

### 8.3 GoblinShamanItem（蓝图腾）行为

`GoblinShamanItem` 继承 Create 的 `SchematicItem`（S-h-a-m-a-n / S-c-h-e-m-a-t-i-c，谐音梗），复用其完整蓝图系统。它是哥布林与"多方块机器之神"沟通的萨满——哥布林看不懂蓝图，只当它是通神的法器。

**仪式一「拜拜」— 下蹲右键已放置的 Controller**

```
GoblinShamanItem 对 GoblinControllerMachine 下蹲右键（鞠躬拜一下）
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
GoblinShamanItem 副手放 GoblinControllerMachine 物品，主手对空气右键
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
GoblinShamanItem 放入 Create 蓝图加农炮（标准 Create 流程）
  ├─ 就是 Create 蓝图（extends SchematicItem），加农炮原生支持
  ├─ 消耗火药 + 动画搭建
  ├─ 每个 Part 位置 → 放置 GoblinPartMachine
  ├─ Controller 方块放置到蓝图定义的坐标
  ├─ 打印完成后：
  │   ├─ GoblinMachineDeity.registerController(controller)
  │   └─ 额外主动触发 deity.checkFormation()
  │       ├─ 通过 → controller.onStructureFormed()
  │       └─ 未通过 → 等待后续手动补 Part
  └─ 注意：不再需要将 Controller 放入 GoblinShamanItem 的槽位
      （祈福时副手持有即可，显灵时 GoblinShamanItem 就是完整蓝图）
```

### 8.4 扳手交互（拆卸）

```
Create 扳手右键 GoblinTieredPartMachine 方块：
  ├─ 调用 restoreOriginalBlock(true) — 原位置放置真实方块（保留 TileEntity/NBT）
  ├─ 掉落 GoblinTieredPartMachine 物品
  ├─ removedFromController() — 注销
  └─ 同步客户端

直接破坏方块：
  ├─ 调用 restoreOriginalBlock(false) — 掉落 originalBlockStack 物品
  ├─ 掉落 GoblinTieredPartMachine 物品
  ├─ removedFromController() — 注销
  └─ 同步客户端
```

### 8.5 Outliner 可视化汇总

| 场景 | Outliner 类型 | 颜色 | 说明 |
|------|-------------|------|------|
| 持有 GoblinTieredPartMachine | `showAABB` | `0x4D9162` 绿 | 绑定的 Controller 位置 |
| 持有 GoblinTieredPartMachine | `showCluster` | `0xC5B548` 黄 | 可放置位置集合 |
| 不能共享时右键新 Controller | `showAABB` | `0xC54848` 红→淡出 | 原 Controller 变红闪烁 |
| 支持共享，取交集后 | `showCluster` | `0xC5B548` 黄 | 两 Controller 的交集位置 |
| 方块已放置 | `remove` | — | 清除 Outliner 标记 |

### 8.6 Create 蓝图系统复用清单

| Create 类 | 复用以求 | 在你的场景 |
|-----------|---------|-----------|
| `SchematicItem` | `extends` | `GoblinShamanItem` — "蓝图腾"，S-h-a-m-a-n / S-c-h-e-m-a-t-i-c 谐音梗 |
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
  1. 新方块是 GoblinPartMachine？
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
  1. 旧方块是 GoblinPartMachine？
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
// GoblinControllerMachine
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
| JEI/Jade 预览 | ✅ getPreview | Deity Oracle 直出预览（§8.7.15） | ⬜ 更快更一致 |
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

GoblinControllerMachine 通过抽象方法声明自己属于哪种模式：

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
    private final Map<GoblinControllerMachine, IDeityOracle> oracles;
    // 缓存的神谕判定（避免重复计算）
    private final Map<GoblinControllerMachine, FormationStatus> cachedStatus;
    // 粗略范围索引：AABB → 覆盖此范围的 Controller（仅 Enclosure 模式用于边界方块变更感知）
    private final List<AABBControllerPair> roughAABBIndex;
    // ★ 命中缓存：玩家绑定 Part → 可用位置集合（放置时快速命中 Controller）
    //   放置前互动时写入，放置后清理，取消/切换物品时清理
    private final Map<Player, HitCache> playerHitCaches;
    
    // === 公开 API ===

    /**
     * 【神棍描述】神谕查询 — 玩家绑定 Part 时查询可用放置位置
     * @return 可用位置列表，同时缓存为命中区域
     */
    public List<BlockPos> queryAvailablePositions(
            GoblinControllerMachine controller, int partTypeId, Player player) {
        IDeityOracle oracle = oracles.get(controller);
        if (oracle == null) return List.of();
        var positions = oracle.getAvailablePositions(partTypeId);
        var hitCache = playerHitCaches.computeIfAbsent(player, k -> new HitCache());
        hitCache.addController(controller, positions);
        return positions;
    }

    /**
     * 【神棍描述】神谕交集 — 共享 Part 时取两 Controller 的交集
     */
    public List<BlockPos> queryIntersection(
            GoblinControllerMachine a, GoblinControllerMachine b,
            int partTypeId, Player player) {
        var posA = oracles.get(a).getAvailablePositions(partTypeId);
        var posB = oracles.get(b).getAvailablePositions(partTypeId);
        var intersection = new ArrayList<>(posA);
        intersection.retainAll(posB);
        var hitCache = playerHitCaches.get(player);
        if (hitCache != null) {
            hitCache.clear();
            hitCache.addController(a, intersection);
            hitCache.addController(b, intersection);
        }
        return intersection;
    }

    /** 取消放置 — 清理命中缓存 */
    public void clearHitCache(Player player) {
        playerHitCaches.remove(player);
    }

    /**
     * Part 放置 — 检查命中缓存，直接通知对应 Controller
     */
    public void onPartPlaced(BlockPos pos, int partTypeId) {
        for (var it = playerHitCaches.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            var hitCache = entry.getValue();
            if (hitCache.contains(pos)) {
                for (var ctrl : hitCache.getControllersFor(pos)) {
                    IDeityOracle oracle = oracles.get(ctrl);
                    if (oracle == null) continue;
                    oracle.markOccupied(pos, partTypeId);
                    FormationStatus status = oracle.checkFormation();
                    cachedStatus.put(ctrl, status);
                    if (status instanceof FormationStatus.Formed) ctrl.onStructureFormed();
                }
                it.remove();
            }
        }
    }

    /**
     * 【神棍描述】Part 拆除告知 — Part 主动告诉 Deity 自己属于哪个 Controller
     * 
     * <p>【工程描述】Part 自身携带 controllerPositions 元数据，
     * 拆除时直接传递给 Deity，无需空间哈希查找。
     */
    public void onPartRemoved(Set<BlockPos> ctrlPositions, BlockPos worldPos, int partTypeId) {
        for (BlockPos ctrlPos : ctrlPositions) {
            GoblinControllerMachine ctrl = findController(ctrlPos);
            if (ctrl == null) continue;
            IDeityOracle oracle = oracles.get(ctrl);
            if (oracle == null) continue;
            oracle.markVacant(worldPos);
            FormationStatus newStatus = oracle.checkFormation();
            FormationStatus oldStatus = cachedStatus.getOrDefault(ctrl, FormationStatus.INCOMPLETE);
            cachedStatus.put(ctrl, newStatus);
            if (oldStatus instanceof FormationStatus.Formed
                && newStatus instanceof FormationStatus.Incomplete) {
                ctrl.onStructureInvalid();
            }
        }
    }

    /** 边界方块变更 — 仅 Enclosure 模式感知非 Part 方块 */
    public void onBoundaryBlockChanged(Level level, BlockPos pos,
                                        BlockState oldState, BlockState newState) {
        for (var pair : roughAABBIndex) {
            if (!pair.aabb.contains(pos)) continue;
            if (pair.controller().getDefinitionData().verifyMode() != VerifyMode.ENCLOSURE) continue;
            IDeityOracle oracle = oracles.get(pair.controller());
            if (oracle != null) oracle.onBlockChanged(pos, oldState, newState);
        }
    }
    
    // === 注册/注销（Controller 的"皈依"与"叛离"） ===

    public void registerController(GoblinControllerMachine controller) {
        IDeityOracle oracle = createOracle(controller);
        oracles.put(controller, oracle);
        // 仅 Enclosure 模式注册粗略范围索引（边界方块变更感知）
        if (controller.getDefinitionData().verifyMode() == VerifyMode.ENCLOSURE) {
            int threshold = ConfigHolder.INSTANCE.machines.multiblockAnnoyanceThreshold;
            roughAABBIndex.add(new AABBControllerPair(
                oracle.getRoughAABB(), controller, new AnnoyanceTracker(threshold)));
        }
        cachedStatus.put(controller, FormationStatus.INCOMPLETE);
    }

    public void unregisterController(GoblinControllerMachine controller) {
        oracles.remove(controller);
        roughAABBIndex.removeIf(pair -> pair.controller() == controller);
        cachedStatus.remove(controller);
    }

    private GoblinControllerMachine findController(BlockPos pos) {
        return (GoblinControllerMachine) MetaMachine.getMachine(/* level */ null, pos);
    }

    private IDeityOracle createOracle(GoblinControllerMachine ctrl) {
        // 从工厂获取预计算模板，clone 为独立实例
        OracleTemplate template = GoblinMultiblockRegistrate.getTemplate(
            ctrl.getDefinition().getId());
        return template.cloneOracle();
    }

    // === 命中缓存内部类 ===
    private static class HitCache {
        private final Map<BlockPos, List<GoblinControllerMachine>> posToControllers = new HashMap<>();
        void addController(GoblinControllerMachine ctrl, List<BlockPos> positions) {
            for (BlockPos pos : positions)
                posToControllers.computeIfAbsent(pos, k -> new ArrayList<>()).add(ctrl);
        }
        boolean contains(BlockPos pos) { return posToControllers.containsKey(pos); }
        List<GoblinControllerMachine> getControllersFor(BlockPos pos) {
            return posToControllers.getOrDefault(pos, List.of());
        }
        void clear() { posToControllers.clear(); }
    }
}
```

##### 模板预计算（GoblinMultiblockRegistrate）

GTCEu 的机器注册是这样写的：

```java
// GTMultiMachines.java — GTCEu 原生写法
public static final MultiblockMachineDefinition COKE_OVEN = REGISTRATE
    .multiblock("coke_oven", CokeOvenMachine::new)
    .rotationState(RotationState.ALL)
    .pattern(def -> FactoryBlockPattern.start()
        .aisle("XXX", "XXX", "XXX")
        .aisle("XXX", "XCX", "XXX")
        .aisle("XXX", "XSX", "XXX")
        .where('S', controller(blocks(def.getBlock())))
        .where('X', blocks(GTBlocks.HIGH_POWER_CASING.get())
            .or(CokeOvenMachine.getHatchPredicates()))
        .where('C', blocks(COIL_BLOCK.get()))
        .build())
    .register();
```

问题：`.pattern()` 传入的 Lambda 只在 GTCEu 注册时被调用一次生成 BlockPattern，GoblinMachineDeity 注册 Controller 时还需要再次解析 BlockPattern 来构建 BitMap——同一份结构信息处理了两次。

**解决方案**：写一个 `GoblinMultiblockRegistrate`，包装 `GTRegistrate`，在 `.register()` 时拦截 BlockPattern 并预计算 OracleTemplate。

```java
// GoblinMultiblockRegistrate.java
public class GoblinMultiblockRegistrate {

    /**
     * GoblinTech 自己的 GTRegistrate — modId = "goblintech"。
     * 这意味着所有资源路径（方块/物品/模型/纹理/语言键/数据生成）
     * 都流向 goblintech: 命名空间，而非 gtceu:。
     */
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(GoblinTech.ID);

    /** OracleTemplate 注册表：方块名 → 预计算模板 */
    private static final Map<ResourceLocation, OracleTemplate> TEMPLATES = new HashMap<>();

    private GoblinMultiblockRegistrate() {}

    /**
     * 注册一个 Goblin 多方块机器。
     * 通过 GoblinTech 自己的 GTRegistrate，资源路径 = goblintech:xxx。
     */
    public static <T extends GoblinControllerMachine> GoblinMultiblockBuilder<T> goblinMultiblock(
            String name,
            Function<BlockEntityCreationInfo, T> machineFactory) {

        MultiblockMachineBuilder<MultiblockMachineDefinition, ?> builder =
            REGISTRATE.multiblock(name, machineFactory);

        return new GoblinMultiblockBuilder<>(name, builder);
    }

    public static OracleTemplate getTemplate(ResourceLocation id) {
        return TEMPLATES.get(id);
    }
}
```

> `GTRegistrate.create(modId)` 是 GTCEu 的公开 API，addon mod 每条产线都会调用。它按 modId 缓存实例，`create("goblintech")` 创建一个全新的 Registrate，方块/物品/数据生成全部自动归属 `goblintech:` 命名空间。无需额外子类。

**GoblinMultiblockBuilder — 对现有写法的零侵入扩展**：

```java
// GoblinMultiblockBuilder.java
public class GoblinMultiblockBuilder<T extends GoblinControllerMachine> {

    private final String name;
    private final MultiblockMachineBuilder<MultiblockMachineDefinition, ?> inner;
    private VerifyMode verifyMode;
    private Consumer<GoblinMultiblockDefinition.Builder> definitionBuilder;

    /**
     * 设定验证模式（FIXED / SECTIONED / ENCLOSURE）
     */
    public GoblinMultiblockBuilder<T> verifyMode(VerifyMode mode) {
        this.verifyMode = mode;
        return this;
    }

    /**
     * 设定机器结构定义。
     * 注意：pattern() 仍需通过 inner 调用（GTCEu 标准流程），
     * 此处 definition 管理额外的 Goblin 属性（Part 位置表、slotTypes 等）。
     */
    public GoblinMultiblockBuilder<T> definition(Consumer<GoblinMultiblockDefinition.Builder> builder) {
        this.definitionBuilder = builder;
        return this;
    }

    /**
     * 代理 GTCEu Builder 的标准方法（链式调用风格）
     */
    @SuppressWarnings("unchecked")
    public GoblinMultiblockBuilder<T> pattern(
            Function<MultiblockMachineDefinition, BlockPattern> p) {
        inner.pattern(p);
        return this;
    }

    // ... rotationState, appearanceBlock, tooltips 等代理方法 ...

    /**
     * ★ 关键：注册时拦截 BlockPattern 并预计算 OracleTemplate
     */
    public MultiblockMachineDefinition register() {
        // 1. 先走 GTCEu 注册 ← 此时 BlockPattern 已构建完毕
        MultiblockMachineDefinition def = inner.register();

        // 2. 从定义中提取 BlockPattern，预计算 OracleTemplate
        BlockPattern pattern = def.getPatternFactory().get();
        GoblinMultiblockDefinition goblinDef = GoblinMultiblockDefinition
            .builder(def.getId())
            .verifyMode(verifyMode)
            .blockPattern(pattern)  // ← 直接复用 GTCEu 已解析的 BlockPattern
            .apply(definitionBuilder)
            .build();

        // 3. 预计算并存入模板注册表
        OracleTemplate template = switch (verifyMode) {
            case FIXED     -> StructureBitmapTemplate.from(goblinDef);
            case SECTIONED -> SectionedBitmapTemplate.from(goblinDef);
            case ENCLOSURE -> EnclosureTemplate.from(goblinDef);
        };
        GoblinMultiblockRegistrate.TEMPLATES.put(def.getId(), template);

        return def;
    }
}
```

**实际使用对比**：

```java
// === GTCEu 原生写法（现有） ===
public static final MultiblockMachineDefinition COKE_OVEN = REGISTRATE
    .multiblock("coke_oven", CokeOvenMachine::new)
    .pattern(def -> FactoryBlockPattern.start()
        .aisle("XXX", "XXX", ...)
        .where('X', ...)
        .build())
    .register();

// === GoblinMultiblockRegistrate 写法（目标） ===
public static final MultiblockMachineDefinition STEAM_BOILER =
    GoblinMultiblockRegistrate.goblinMultiblock("steam_boiler", SteamBoilerMachine::new)
        .verifyMode(VerifyMode.FIXED)
        .pattern(def -> FactoryBlockPattern.start()
            .aisle("XXX", "XXX", "XXX")
            .aisle("XXX", "XCX", "XXX")
            .aisle("XXX", "XSX", "XXX")
            .where('S', controller(blocks(def.getBlock())))
            .where('X', blocks(GTBlocks.STEEL_CASING.get())
                .or(SteamBoilerMachine.getHatchPredicates()))
            .where('C', blocks(FIREBOX_BLOCK.get()))
            .build())
        .definition(b -> b
            .partSlot('X', new PartSlot("casing", PartSlotType.HULL))
            .partSlot('C', new PartSlot("firebox", PartSlotType.HULL))
            .hatchSlot(SteamBoilerMachine.getHatchPredicates()))
        .register();
```

**关键变化**：
- 多一行 `.verifyMode(VerifyMode.FIXED)` — 告诉 Factory 用什么模式预计算模板
- 多一段 `.definition(...)` — 声明额外的 Goblin 属性（Part 位置语义、IO 类型映射）
- `.pattern()` 仍然是 GTCEu 标准写法（`FactoryBlockPattern.start()...`），**零学习成本**
- `.register()` 调用后自动完成：GTM 注册 + OracleTemplate 预计算 → 存模板表

与上一版的区别：

| | 上一版（GoblinMultiblockFactory.register） | 现版（GoblinMultiblockRegistrate） |
|------|------------------------------------------|-----|
| 注册方式 | 额外调用 `Factory.register()` | 替换 `REGISTRATE.multiblock()` 为 `GoblinMultiblockRegistrate.goblinMultiblock()` |
| BlockPattern 复用 | 需自己解析 | 直接复用 GTCEu 已构建的 BlockPattern |
| 对现有代码侵入 | 需要加一行独立调用 | 仅替换第一行调用对象 |
| 学习成本 | 需要了解两套 API | 与 GTCEu 写法高度一致 |

##### 神谕接口（IDeityOracle）

```java
public interface IDeityOracle {
    /** Part 放置 — 标记位置被占用 */
    void markOccupied(BlockPos pos, int partTypeId);

    /** Part 移除 — 标记位置空闲 */
    void markVacant(BlockPos pos);

    /** 神谕判定：当前是否成型 */
    FormationStatus checkFormation();

    /** 查询某个位置期望的 Part 类型（供 Outliner 用） */
    ExpectedPartQuery queryExpectedPart(BlockPos pos);

    /** 查询指定 Part 类型的可用空位（供玩家绑定 Part 时返回可选位置） */
    List<BlockPos> getAvailablePositions(int partTypeId);

    /** 获取粗略包围盒（仅 Enclosure 模式用于边界方块变更感知） */
    AABB getRoughAABB();

    /** 边界方块变更通知（仅 Enclosure 模式） */
    void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState);
}
```

##### 事件驱动流程（优化后）

核心洞察：**Part 自身携带了"我属于哪个 Controller"的信息，不需要靠空间哈希来猜**。

```
═══════════════════════════════════════════════════════════════
放置路径 — 从 Part 物品交互 → 绑定 → 放置 → 结构验证
═══════════════════════════════════════════════════════════════

1. 持有 GoblinTieredPartMachine 物品，右键 GoblinControllerMachine（拜拜）
   └─ Deity.queryAvailablePositions(controller, partType)
        ├─ 返回该 Controller 蓝图中该 Part 可放置的位置列表
        └─ Deity 缓存：{Controller A} → {pos1, pos2, pos3}
             （命中缓存，用于快速判断后续方块变更是否"命中"此 Controller）

2. 右键另一个 GoblinControllerMachine（支持共享，取交集）
   └─ Deity.queryIntersection(controllerA, controllerB, partType)
        ├─ 返回两 Controller 的交集位置
        └─ Deity 缓存缩小：{Controller A, Controller B} → {pos2}

3. 右键交集位置 — 放置 GoblinPartMachine 方块
   └─ BlockEvent.EntityPlace 触发
        └─ Deity 命中缓存命中 pos2 → 直接通知 Controller A 和 Controller B
             ├─ Controller A.oracle.markOccupied(pos2, partType)
             ├─ Controller B.oracle.markOccupied(pos2, partType)
             └─ 各自 checkFormation() → Formed? onStructureFormed()

4. 取消放置（切物品/丢弃）
   └─ Deity.clearHitCache(player) → 清除命中缓存

═══════════════════════════════════════════════════════════════
移除路径 — Part 携带自身元数据，直接告知 Deity
═══════════════════════════════════════════════════════════════

扳手拆除 / 直接破坏：
  GoblinPartMachine 方块被移除
    ├─ ★ Part 自身知道属于哪些 Controller（controllerPositions）
    ├─ 调用 Deity.onPartRemoved(controllerPositions, worldPos, partType)
    │    ├─ 对每个 Controller：
    │    │    ├─ oracle.markVacant(worldPos)
    │    │    ├─ checkFormation() → 更新成型状态
    │    │    └─ 如果成型状态变更 → onStructureInvalid()
    │    └─ ★ 无需 posToControllers 空间哈希，Part 直接告诉 Deity 属于谁
    └─ restoreOriginalBlock(true/false)

═══════════════════════════════════════════════════════════════
边界方块变更 — 仅 Enclosure 模式需要
═══════════════════════════════════════════════════════════════

非 Part 方块变更（如墙壁/门被破坏）：
  └─ Deity.onBlockChanged(level, pos, oldState, newState)
       ├─ roughAABBIndex 粗略筛选 Enclosure 模式的 Controller
       └─ 命中 → oracle.onBoundaryChanged() → invalidate 缓存
```

##### 三种变更路径对比

| 变更类型 | 如何定位 Controller | 查找复杂度 | 原理 |
|---------|---------------------|-----------|------|
| Part 放置 | Deity 命中缓存（放置前已绑定） | O(1) 直接命中 | 放置前互交时已缓存可用位置 |
| Part 移除 | Part 自带 `controllerPositions` | O(1) 直接告知 | Part 知道自己属于谁 |
| 边界方块变更 | roughAABBIndex 粗略筛选 | O(n) 遍历 Enclosure 模式机器 | 只有围合模式需要感知非 Part 方块 |

##### design 优势（优化后）

| 维度 | GTCEu 轮询 | 旧集中神（纯空间哈希） | 新集中神（Part 主动告知） |
|------|-----------|---------------------|------------------------|
| Part 放置定位 | — | posToControllers O(1) 哈希 | Deity 命中缓存 O(1) 直接命中 |
| Part 移除定位 | — | posToControllers O(1) 哈希 | Part 带元数据 O(1) 直接告知 |
| 边界方块变更 | 每 5 tick 轮询 | posToControllers O(1) | roughAABBIndex O(n_enc) 仅枚举围合机器 |
| 空间索引维护 | — | 需维护 posToControllers 增删 | ★ 不再需要 posToControllers |
| 取消放置 | — | 不适用 | clearHitCache() 即时清理 |
| 信息流方向 | 拉（主动查） | 推（被动感知） | 拉 + 推混合（数据跟着数据走） |

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

#### 8.7.14 与 GoblinShamanItem 的整合

```
蓝图大炮打印完成后（显灵）：
  ├─ 所有 Part 方块 + Controller 方块放置完毕
  └─ GoblinMachineDeity.registerController(controller)
       └─ oracle.checkFormation() → 如果通过 → onStructureFormed()

手动 Part 放置时（仪式四，通过 Deity 命中缓存）：
  1. 玩家右键 Controller → Deity.queryAvailablePositions() 返回位置 + 缓存
  2. 玩家右键位置 → 放置方块 → Deity.onPartPlaced() 命中缓存直接通知 Controller
       └─ oracle.markOccupied → checkFormation → Formed? onStructureFormed()

拆卸 Part 时（Part 自带 Controller 元数据）：
  └─ onMachineDestroyed() → Deity.onPartRemoved(ctrlPositions, pos, partType)
       └─ oracle.markVacant → checkFormation → 失效? onStructureInvalid()

#### 8.7.15 GoblinMachineDeityShaman（仪式锻造机）

解决了"蓝图腾从哪来"的问题。仪式——只展示不消耗。原材料供奉给神明过目，神明回赐蓝图。

##### 数据流

```
GoblinMachineDeityShaman（GTM 单方块机器）
  ├─ 输入槽：展示架（供奉台）—— 所需材料（不消耗）
  │   └─ 自动配方：由 GoblinMultiblockRegistrate.register() 时从 OracleTemplate 生成
  ├─ 输出槽：GoblinShamanItem（附魔蓝图）
  └─ 仪式过程：
       ├─ 机器 GUI 中展示完整结构预览（Tier 1 JEI Scene widget 内嵌）
       ├─ 仪式进行：播放粒子 + 音效动画（哥布林跳大舞）
       ├─ 结束后：输出 GoblinShamanItem，输入物品不消耗
       └─ 等于"一键拜拜"，免去仪式一中逐个下蹲右键的操作
```

##### 配方自动生成

不需要单独写 JSON 配方。直接复用 `GoblinScripture`（已有的 `extends GTRecipe`），
在 `GoblinMultiblockRegistrate.register()` 时从 OracleTemplate 自动生成。

##### GoblinScripture 扩展

只需加两样东西：

```java
// GoblinScripture.java 新增：
public boolean consumeInputs = true;  // ★ DeityShaman 设为 false

/**
 * 从 OracleTemplate 自动生成 GoblinScripture。
 * @param template  预计算的模板（BitSet + blockPos 映射 + slotTypes）
 * @param recipeType 仪式配方类型
 * @param outputId   输出物品 ID（指向对应机器的 GoblinShamanItem）
 */
public static GoblinScripture fromOracleTemplate(
        OracleTemplate template, GTRecipeType recipeType, ResourceLocation outputId) {

    Map<Block, Integer> counts = template.countBlocks();
    int totalBlocks = counts.values().stream().mapToInt(i -> i).sum();

    var scripture = new GoblinScripture(
        recipeType,
        counts.entrySet().stream()
            .collect(Collectors.toMap(
                e -> ItemRecipeCapability.CAP,
                e -> List.of(new Content(e.getKey().asItem().getDefaultInstance(), e.getValue()))
            )),
        Map.of(ItemRecipeCapability.CAP, List.of(new Content(outputId, 1))),
        Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
        List.of(), new CompoundTag(),
        Math.max(20, totalBlocks * 20),  // 最少 1 秒
        recipeType.getCategory("default"), 0);

    scripture.mode = RecipeMode.TRANSFER;     // 反正不消耗，模式无所谓
    scripture.consumeInputs = false;          // ★ 关键：供奉品不扣
    return scripture;
}
```

> `fromOracleTemplate` 只需遍历 `blockTypes[]` 去重计数，然后构造 `inputs` Map。`GTRecipe` 构造函数参数较多但全是标准字段，无需理解 GTCEu 内部机制。

##### 不消耗机制

不再需要两套 inventory。`GoblinScripture.consumeInputs = false` 告诉 `GoblinMachineDeityShaman` 的工作逻辑：结束后不调用 `inventory.setStackInSlot(i, ItemStack.EMPTY)`。

```
GoblinMachineDeityShaman 单套 inventory（与普通 GTM 机器一致）
  ├─ 玩家放入材料 → 标准输入端
  ├─ 触发配方开始
  │   ├─ 仪式粒子 + 音效动画
  │   └─ 输入槽位被工作逻辑"锁定"（机器在跑，不可取出）
  ├─ 配方完成
  │   ├─ check scripture.consumeInputs
  │   │   ├─ true（正常配方）→ 清空输入端 → 输出产物
  │   │   └─ false（仪式配方）→ 不清空输入端！只输出产物
  │   └─ 输出槽产出 GoblinShamanItem
  └─ 输入材料原封不动留在槽位中（下次再触发还能用）
```

> `consumeInputs = false` 对 GoblinScripture 来说是天然设计——`RecipeMode.MODIFY` 本就有"输入物品不消耗"的理念，只是 MODIFY 修改原物品内容。仪式锻造是"输入展示、独立产出"，语义不同但机制共享。

##### 自动注册

在 `GoblinMultiblockRegistrate.register()` 末尾：

```java
public MultiblockMachineDefinition register() {
    MultiblockMachineDefinition def = inner.register();
    // ... 预计算 OracleTemplate ...

    // ★ 自动生成 GoblinScripture（配方类型和输出都是固定的）
    GoblinScripture scripture = GoblinScripture.fromOracleTemplate(
        template, def.getId());
    definition.recipeLogic()
        .addScripture(scripture);

    return def;
}
```

##### Package 输出扩展（待调研）

**问题**：DeityShaman 输出的蓝图腾 + 材料，能否直接打包为 Create Package 发送给蓝图大炮？

**Create 蓝图大炮的输入方式**：
- 蓝图大炮有 5 槽位：蓝图（Schematic）+ 书入 + 书出 + 材料列表出 + 火药
- **建筑材料**不进入大炮库存——大炮从相邻方块的 `ItemHandler` 拉取
- 蓝图大炮**不接受** Create Package 作为输入格式

**可行方案**：

```
方案 A（直接供奉）：
  材料 → DeityShaman → GoblinShamanItem（蓝图）
  蓝图 + 材料分别放入大炮（手动或自动化）

方案 B（Package + Repackager 拆包）：
  材料 → DeityShaman Wrapper Machine → Packager → Package（包裹）
    → PackagePort → 物流网络 → 大炮附近的 Frogport
      → Repackager 拆包 → 大炮从 Repackager 拉材料
  蓝图单独放入大炮

方案 C（DeityShaman 直接推送）：
  材料 → DeityShaman → GoblinShamanItem（蓝图）
  DeityShaman 通过配置的发送位置，将材料物品主动推出
    → 需实现 IItemHandler<N> 推送接口
    → 可配置默认发送坐标（类似 PackagePort 的 target 地址系统）
```

| 方案 | 自动化程度 | 实现复杂度 | 需要调研 |
|------|----------|----------|---------|
| A | 低（手动） | 低 | 无 |
| B | 高（全自动） | 高 | Repackager 是否可对接大炮邻近 `ItemHandler` |
| C | 中（半自动） | 中 | DeityShaman 直接推送到配置坐标的 `BlockEntity` |

> **待调研**：`Repackager` 拆包后是否能被邻近蓝图大炮通过 `IItemHandler` 拉取。如果可以，方案 B 可实现全自动蓝图打印流水线。

#### 8.7.16 JEI/EMI 多方块预览优化

##### 痛点

GTCEu 原生 JEI 预览依赖 `BlockPattern` DSL：加载大结构时需遍历所有 aisles、逐层展开谓词匹配、计算 bounds。结构越大，预览越慢（可达数百毫秒卡住 JEI 页面）。

**核心原因**：`BlockPattern` 是为了**运行时结构匹配**而设计的（丰富的谓词、沿轴重复、相对坐标系统），JEI 预览仅需**静态渲染**（同一套数据跑了两次）。

##### 两种参考实现

**参考 A：NeoECOAEExtension（LDLib2 Scene 方案）**

```
MultiBlockDefinition（Builder + BlockInstruction 链）
  → MultiBlockContext.createLevel() 执行指令
    → TrackedDummyWorld（LDLib2 虚拟世界）
      → MultiBlockInfoWrapper.createModularUI()
        → Scene widget（3D 渲染 + 拖拽/缩放/分层预览）
```

- 优势：绕开 BlockPattern，O(n) 直接写入虚拟世界；LDLib2 Scene 自带拖拽/缩放、按 Y 层切换(Formed 状态模拟
- 缺点：每次预览仍需完整执行 `BlockInstruction` 链（大结构仍是 O(n) 遍历）；自定义 DSL（`setBlock`/`setBlockRepeatable`），与 Deity 神谕非同一数据源

参考入口：`NeoECOAEExtension-1.21.1-1.3.4/src/main/java/cn/dancingsnow/neoecoae/integration/`

**参考 B：Simulated-Project（蓝图风格 + 正交投影）**

```
DiagramScreen（FBO 渲染 + Veil 后处理管线）
  → 正交投影（无近大远小透视）
  → 固定视角（不可拖动旋转，仅按钮切换）
  → 羊皮纸底纹 + 铅笔线条勾勒
  → 旋转/放缩按钮、便签注释
```

- 参考要点：**正交投影 + 固定视角**（这正是蓝图腾需要的效果）；Veil PostPipeline 勾勒、FBO 渲染管线
- **不需要**：去色/灰度（保留方块原色以标识 IO 类型）、力学矢量图部分
- 劣势：非 JEI/EMI 集成，需自建 GUI

参考入口：`Simulated-Project-main/simulated/common/src/main/java/dev/simulated_team/simulated/content/entities/diagram/`

##### 推荐方案：JEI 快速预览 + 蓝图腾详览，同源双视

核心思路：**一份 OracleTemplate，两个视图。**

```
OracleTemplate (BitSet + blockPos映射 + slotTypes)
  ├─ Tier 1: JEI/EMI 嵌入 → 单视口 Scene widget（快速浏览）
  └─ Tier 2: 四象限蓝图腾 → 独立 GUI（详细检视）← 从 JEI 点击打开 / 手持蓝图右键打开
```

##### Tier 1：JEI/EMI 嵌入预览

JEI 中直接嵌入一个 LDLib2 Scene widget，参考 NeoECOAEExtension 的做法。

```
JEI 面板中：
┌─────────────────────────────────────┐
│  Steam Boiler                  [详] │  ← "详"按钮 → 打开 Tier 2
│  ┌─────────────────────────────────┐│
│  │                                 ││
│  │    Scene widget (透视/正交可选)   ││  ← 可拖拽旋转（JEI 标准交互）
│  │                                 ││
│  └─────────────────────────────────┘│
│  E: 1  L: -1  F: false              │  ← 沿用 NeoECOAEExtension 的 E/L/F
│                                      │
│  所需材料: [钢块×12] [火箱×1] ...     │
└─────────────────────────────────────┘
```

- **可拖拽旋转**：JEI 标准交互（用户预期行为）
- **E/L/F 按钮**：Expand 切换 repeat 级别 / Layer 按 Y 层切换 / Formed 切换成型状态
- **"详"按钮**：点击打开 Tier 2 蓝图腾四象限视图
- 实现参考：`NeoECOAEExtension/integration/emi/recipe/MultiblockEmiRecipe.java` + `MultiBlockInfoWrapper.createModularUI()`
- 关键差异：数据源从 `MultiBlockDefinition` 换成 `OracleTemplate.exportPreviewBlocks()`

##### Tier 2：四象限蓝图腾（从 JEI "详"按钮或手持蓝图右键打开）

核心思路：**正交投影三视图 + 图例面板。**（与之前设计一致，但入口改为从 JEI 点击打开）

##### 布局

```
┌──────────────────────────┬──────────────────────────────┐
│  左上 · 图例              │  右上 · 三视图 ─ 正视图       │
│  ┌─────────────────────┐ │  (0° 俯仰, 0° 偏航)          │
│  │ 线形/颜色 = IO类型    │ │                              │
│  │ ██ 输入 ██ 输出      │ │  ┌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┐   │
│  │ ══ 能量 ══ 流体     │ │  ┊     正视图              ┊   │
│  │ ─ ─ 物品 ─ ─ 红石   │ │  └╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┘   │
│  └─────────────────────┘ │                              │
│                           │  ◄ 旋转按钮 / 方向切换        │
│  上半 · IO 标记 + Part 列表│  ─ ─ ─ ─ 剖面滑块 ─ ─ ─ ─  │
│  ┌─────────────────────┐ ├──────────────────────────────┤
│  │ 🔍 搜索Part名称...   │ │  右下 · 三视图 ─ 俯视图       │
│  │ ◄ 横向滚动 ────── ► │ │  (-90° 俯仰, 0° 偏航)        │
│  │                      │ │                              │
│  │ ██ 输入仓  [1,4]    │ │  ┌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┐   │
│  │ ██ 输出仓  [1,2]    │ │  ┊     俯视图              ┊   │
│  │ ══ 能量输入 [0,1]   │ │  └╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┘   │
│  │ ...                  │ │                              │
│  └─────────────────────┘ │  剖面滑块同步                  │
│                           ├──────────────────────────────┤
│  下半 · 方块放大预览       │  左下 · 三视图 ─ 侧视图       │
│  ┌─────────────────────┐ │  (0° 俯仰, 90° 偏航)         │
│  │                      │ │                              │
│  │  鼠标悬停/点击方块      │ │  ┌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┐   │
│  │  在此放大显示          │ │  ┊     侧视图              ┊   │
│  │                      │ │  └╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┘   │
│  └─────────────────────┘ │                              │
└──────────────────────────┴──────────────────────────────┘
```

##### 各区域详解

**左上 · 图例**
- 列出所有 IO 标记的线形/颜色对应关系
- 例：`█ 红色虚线框` = 物品输入、`█ 蓝色实线框` = 能量输出
- 固定内容，帮助玩家快速理解三视图中的标记含义

**上半 · IO 标记 + Part 列表**
- 每个 IO 标记颜色/线形右侧列出对应的 `GoblinPartMachine` 类型
- **搜索框**：Part 类型可能很多，支持搜索过滤
- **横向滚动条**：超出显示范围的 Part 项可横向滚动
- **`[min, max]` 区间**：每种 Part 的数量限制，如 `输入仓 [1, 4]` 表示至少 1 个最多 4 个

**下半 · 方块放大预览**
- **无操作时**：空白/闲置状态（图例预留位置）
- **悬停三视图** → 预览方块跟随鼠标所指位置（与跟随 Outliner 联动）
- **悬停 Part 列表项** → 若该 Part 类型只有一个位置，直接预览该方块；若有多个，预览第一个并提示"× N 个可用"
- **点击锁定** → 方块预览锁定（不再跟随鼠标），可旋转该单一方块视角

**右上 / 右下 / 左下 · 三视图**
- 三个独立渲染面板，分别以正交投影渲染同一结构
- 默认方向：**正视图**（俯仰 0°/偏航 0°）、**俯视图**（俯仰 -90°/偏航 0°）、**侧视图**（俯仰 0°/偏航 90°）
- **按钮切换方向**：每个视图独立可切换（如正视图可切为后视图），不可鼠标拖动旋转
- **剖面滑块**：锁定到整数位置的横向/纵向滑条，限制该轴的显示范围（如 Y ∈ [2, 5]）
  - 三视图的剖面滑块**同步联动**：调整正视图的 Y 剖面 → 俯视图和侧视图同步裁剪 Y 范围
  - 效果：可实现精确的**三视图剖面图**，方便查看内部结构

##### Outliner 交互模型

三视图、Part 列表、方块预览、IO 位置高亮之间存在四向联动，靠三套 Outliner 实现：

| Outliner | 触发 | 外观 | 行为 |
|----------|------|------|------|
| **跟随 Outliner** | 鼠标在三视图上移动 | 半透明闪烁框（α ≈ 0.4） | 实时跟随鼠标 blockPos，三视图同步位置标记 |
| **Part 高亮 Outliner** | 鼠标悬停 Part 列表 → 对应 IO 框类型 | 对应颜色高亮线框（α ≈ 0.7） | 同时在三视图标记该 Part 类型所有可用位置 |
| **锁定 Outliner** | 点击三视图方块 / 点击 Part 列表项 | 不透明实线框（α = 1.0） | 固定在点击位置，跟随 Outliner 降 α（变为半透明继续跟随但不覆盖锁定框） |

**交互流程**：

```
状态：无悬停、无锁定
  └─ 下方预览空白、无 Outliner

鼠标移入三视图
  ├─ 三视图同步：跟随 Outliner 出现在所有视图的对应 blockPos
  ├─ 图例联动：下方方块预览更新为 cursor 所指方块
  ├─ Part 列表联动：若 cursor 落在 IO 位置 → 列表区对应 Part 项轻微高亮
  └─ 跟随 Outliner α = 0.4

鼠标悬停 Part 列表项（如"输入仓"）
  ├─ Part 高亮 Outliner：三视图中所有"输入仓"位置同时点亮（α = 0.7）
  ├─ 跟随 Outliner 降低存在感（α = 0.2，退为浅色虚线）
  ├─ 下方方块预览：切换为预览鼠标所指 Part 类型对应的方块外观
  └─ 如果只有一个位置 → 跟随 Outliner 跳到该位置（snap）

鼠标离开 Part 列表
  ├─ Part 高亮 Outliner 消失
  └─ 跟随 Outliner 恢复 α = 0.4

点击三视图方块（锁定）
  ├─ 锁定 Outliner 锚定在点击位置（α = 1.0，不透明实线框）
  ├─ 跟随 Outliner 降低存在感（α = 0.2），继续跟随鼠标但不干扰锁定框
  ├─ 下方方块预览锁定为点击方块（不跟随鼠标变化）
  └─ Part 列表：当前悬停 Part 项保持轻微高亮，点击对应 Part 则高亮该类型所有位置

点击 Part 列表项（无悬停位置时）
  ├─ 若该类型只有一个可用位置 → 锁定 Outliner 直接跳到该位置
  ├─ 若该类型有多个可用位置 → Part 高亮 Outliner 标记全部（α = 0.7），锁定 Outliner 未触发
  ├─ 下方方块预览锁定为该 Part 外观
  └─ 再点击三视图某个位置 → 锁定 Outliner 锚定（同上）

切换到下一机器/配方
  └─ 全部重置：锁定 Outliner 清除、跟随 Outliner 清除、Part 高亮清除、预览重置为空白
```

##### 关键设计决策

| 决策 | 理由 |
|------|------|
| **正交投影** | 工程图标准做法，无透视变形可精确判断方块位置 |
| **不可拖动旋转** | 固定标准角度（0°/90°/180°/270°）保证三视图一致性（Tier 2 限定；Tier 1 JEI 内默认可拖拽） |
| **保留原色** | 去色会丢失 IO 类型标记信息，颜色本身就是功能标识 |
| **三视图联动剖面** | 与 Simulated-Project 的 E/L/F 按钮不同，三视图剖面滑块是在**空间维度**上裁剪而非在"层"上切换（Tier 2 限定） |
| **同源双视** | Tier 1（JEI 快速浏览）+ Tier 2（四象限详览），共享同一 OracleTemplate 数据源 |

##### 数据流

```
GoblinMultiblockRegistrate.register() 时：
  → OracleTemplate 预计算完成，存入 TEMPLATES

Tier 1（JEI 嵌入预览）：
  template = GoblinMultiblockRegistrate.getTemplate(id)
    → template.exportPreviewBlocks(repeatLevel)
      → TrackedDummyWorld → LDLib2 Scene widget（嵌入 JEI 面板）

Tier 2（四象限蓝图腾，从 JEI "详"按钮或手持蓝图右键打开）：
  同一 template
    → template.exportPreviewBlocks(repeatLevel)
      → 三个 TrackedDummyWorld → 各正交投影 Scene widget
    → template.exportSlotTypes(repeatLevel)
      → 图例面板 + IO 标记区 + Outliner 联动
```

```java
// oracle 新增方法
public interface IDeityOracle {
    // ... 已有方法 ...

    /** 导出预览方块（JEI/蓝图腾预览共用） */
    Map<BlockPos, BlockState> exportPreviewBlocks(int repeatLevel);

    /** 导出每个位置的 IO 标记类型（供图例面板用） */
    Map<BlockPos, PartSlotType> exportSlotTypes(int repeatLevel);
}

// StructureBitmap 实现
@Override
public Map<BlockPos, PartSlotType> exportSlotTypes(int repeatLevel) {
    Map<BlockPos, PartSlotType> result = new HashMap<>();
    for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
        PartSlotType type = slotTypes.get(i); // byte → PartSlotType
        if (type != PartSlotType.HULL) { // 外壳方块不需要标记
            result.put(indexToPos(i), type);
        }
    }
    return result;
}
```

##### 对比：预览方案演进

| 维度 | GTCEu BlockPattern | NeoECOAE LDLib2 | Deity 同源双视 |
|------|-------------------|-----------------|-------------|
| 数据源 | BlockPattern DSL | 独立 Builder DSL | OracleTemplate（与运行时一致） |
| 大结构性能 | 差（aisle展开） | 中（Instruction链遍历） | 好（BitSet/OracleTemplate 直接遍历） |
| 运行一致性 | 弱 | 弱 | **强**（同一数据源） |
| JEI 集成 | ✅ 内嵌 | ✅ 内嵌（LDLib2 Scene） | ✅ Tier 1 内嵌（LDLib2 Scene） |
| 详览模式 | 无 | 无 | ✅ Tier 2 四象限蓝图腾 |
| 视角控制 | 拖拽旋转（JEI内） | 拖拽旋转（JEI内） | **Tier 1 可拖拽 + Tier 2 固定方向** |
| IO 标记 | 无 | 无 | **图例面板 + 线形/颜色标记**（Tier 2） |
| 剖面图 | 无 | 按 Y 层切换 | **三视图联动剖面滑块**（Tier 2） |
| Part 数量限制 | 无 | 无 | **搜索框 + [min, max] 区间**（Tier 2） |
| 渲染管线 | GTCEu | LDLib2 Scene | LDLib2 Scene（Tier 1同NeoECOAE / Tier 2可接入Veil） |

---

## 9. 改动文件清单

### 9.1 Phase 1 — GoblinPartMachine（当前可执行）

| 文件 | 操作 | 说明 |
|------|------|------|
| `GoblinPartMachine.java` | **新建** | `extends MultiblockPartMachine`，覆写 `getBlockAppearance()` |
| `GoblinTieredPartMachine.java` | **修改**（改继承为 GoblinPartMachine） | ~5 行改动 |
| `GoblinTieredIOPartMachine.java` | **不修改**（继承链自动变更） | 0 行 |

> **不需要**：IGhostMachine、GhostPartHullRender、GhostPartHullRenderType、GhostPartHullModel、transparent.png、GTCEu DynamicRender 注册。
> **移除现有文件**：`GhostPartHullRender.java`、`GhostPartHullRenderType.java`。

### 9.2 Phase 2 — Part 放置交互系统

| 文件 | 操作 | 说明 |
|------|------|------|
| `GoblinTieredPartMachine` 改为 Item | **修改** | 不可直接放置，改为交互物品 |
| `GoblinPartItemHandler.java` | **新建** | 客户端 tick + Outliner 渲染（绑定/放置/共享） |
| `GoblinPartInteractPacket.java` | **新建** | Part 交互网络包（绑定/放置/取消统一入口） |
| `GoblinMultiblockDefinition.java` | **新建** | 蓝图位置定义数据模型 |

### 9.3 Phase 3 — Controller 改造

| 文件 | 操作 | 说明 |
|------|------|------|
| `GoblinControllerMachine.java` | **新建** | 继承 MultiblockControllerMachine |
| override `onLoad()` | 不注册 asyncLogic | 阻止定时结构检查 |
| override `asyncCheckPattern()` | 空实现 | 双重保险 |
| override `onUse()` | 不调用 showPreview | 阻止世界内预览 |
| override `onRotated()` / `setFrontFacing()` | 去掉 checkPattern | 阻止转动触发的检查 |
| 添加 `getDefinitionData()` | **新增** | 返回结构定义（供 GoblinShamanItem 序列化） |

### 9.4 Phase 4 — 多方块机器之神（GoblinMachineDeity）

| 文件 | 操作 | 说明 |
|------|------|------|
| `IDeityOracle.java` | **新建** | 神谕接口（`markOccupied`/`markVacant`/`checkFormation`/`queryExpectedPart`/`getAvailablePositions`/`exportPreviewBlocks`） |
| `GoblinMachineDeity.java` | **新建** | 集中式神，玩家命中缓存 + Part 自报位置，无需 `posToControllers` 空间哈希 |
| `StructureBitmap.java` | **新建** | FIXED 模式神谕，BitSet + byte[] + 计数约束 |
| `SectionedBitmap.java` | **新建** | SECTIONED 模式，capA + N×middle + capB 动态推断 |
| `EnclosureValidator.java` | **新建** | ENCLOSURE 模式，缓存 BFS 围合检测 |
| `PlacementResult.java` | **新建** | 密封接口：Ok / OutOfBounds / TypeMismatch / CountExceeded |
| `FormationStatus.java` | **新建** | 密封接口：Formed / Incomplete / CountUnderMin |
| `ExpectedPartQuery.java` | **新建** | 查询期望的 Part 类型（供 Outliner 用） |
| `MultiBlockJEICategory.java` | **新建** | JEI/EMI 预览 Category（基于 LDLib2 Scene + Oracle 直出） |
| `MultiBlockPreviewRender.java` | **新建** | 预览渲染桥接（Oracle → TrackedDummyWorld → Scene） |
| `GoblinMultiblockRegistrate.java` | **新建** | `GTRegistrate.create("goblintech")` + `goblinMultiblock()` 替代 `REGISTRATE.multiblock()`，所有资源路径归属 `goblintech:` 命名空间 |
| `GoblinMultiblockBuilder.java` | **新建** | Builder 中介，代理 GTCEu Builder + `.verifyMode()` / `.definition()` |
| `OracleTemplate.java` | **新建** | sealed interface + 三种实现（StructureBitmapTemplate / SectionedBitmapTemplate / EnclosureTemplate） |

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
                                        GoblinControllerMachine controller) {
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
    public void registerController(GoblinControllerMachine controller) {
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

### 9.5 Phase 5 — GoblinShamanItem（蓝图腾）

| 文件 | 操作 | 说明 |
|------|------|------|
| `GoblinShamanItem.java` | **新建** | `extends SchematicItem`，S-h-a-m-a-n / S-c-h-e-m-a-t-i-c 谐音梗 |
| `GoblinShamanItemHandler.java` | **新建** | 客户端预览 Handler（扩展 SchematicHandler） |
| `ShamanTool.java` | **新建** | 自定义第二排工具（分层/变体/重复） |
| `ShamanDataComponent.java` | **新建** | 存储 Controller 定义引用 |
| `GoblinScripture.java` | **修改** | 新增 `consumeInputs` 字段 + `fromOracleTemplate()` 工厂方法 |
| `RecipeMode.java` | **修改** | 新增 `TRANSFER` 枚举值（供奉展示型） |

### 9.6 Phase 6 — GoblinWorkableMachine

| 文件 | 操作 |
|------|------|
| `GoblinWorkableMachine.java` | **新建**，`extends MetaMachine implements IBelieverOfScripture` |

### 9.7 Phase 7 — GoblinMachineDeityShaman（仪式锻造机）

| 文件 | 操作 | 说明 |
|------|------|------|
| `GoblinMachineDeityShaman.java` | **新建** | GTM 单方块机器，供奉材料 → 产出蓝图。通过 GoblinScripture.consumeInputs=false 实现不消耗 |
| `ShamanRitualRenderer.java` | **新建** | 仪式粒子动画（图腾柱粒子 + 音效） |
| `ShamanRitualRecipeType.java` | **新建** | 仪式配方类型 |

---

## 10. 技术决策记录

### 10.1 为什么不创建 GoblinMetaMachine 中介类

Part（伪装+多方块）、单方块（多供能）、Controller（阻止老逻辑）三个方向需求正交，无共同中介层可抽取。各自直接从 GTCEu 基类继承更干净。

### 10.2 为什么 GoblinPartMachine 继承 MultiblockPartMachine

GTCEu 的 `MultiblockPartMachine` 168 行代码零 `final` 方法、零 `final` 类，完全可继承。直接继承免去搬运 5 大功能模块的维护成本。

### 10.3 为什么 GoblinControllerMachine 继承 MultiblockControllerMachine

GTCEu 内部有 47 处 `instanceof MultiblockControllerMachine` 检查（BlockPattern、RecipeLogic、GTRecipeModifiers、Jade 等）。不继承则多方块成型、配方处理、Jade 显示全部崩溃。

### 10.4 为什么 GoblinTieredPartMachine 不是方块而是物品

GoblinTieredPartMachine 是一个"幽灵 Part"——它只有在放置到 Controller 蓝图定义的位置后才能变成方块。单独存在时只是物品。这与 Create 蓝图系统的"先定义结构再放置"理念一致，也与 Cover 操作类似但不是在可放置 Cover 的方块上进行。

### 10.5 为什么复用 Create 蓝图系统而非自建

- **免去适配工作**：Create 蓝图已有完整的客户端预览（SchematicHandler + SchematicRenderer）、蓝图大炮搭建（Schematicannon）、序列化（StructureTemplate）、变换系统（SchematicTransformation）
- **原生支持动画**：蓝图大炮搭建有完整的粒子+音效动画
- **原生支持移动/旋转/翻转**：这些是 Create 蓝图工具的标配功能
- **天然兼容**：作为 Create 附属 mod，无需额外添加依赖

### 10.6 为什么采用集中式神谕（GoblinMachineDeity）设计

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

### 10.7 三种神谕模式的适用场景

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

### 10.8 性能优化考虑

| 优化点 | 实现方式 | 收益 |
|--------|---------|------|
| 粗略范围预筛选 | `roughAABBIndex` 快速跳过无关变更 | 99% 无效变更直接跳过 |
| 精确空间哈希 | `posToControllers` Long2ObjectOpenHashMap | O(1) 查找受影响机器 |
| 状态缓存 | `cachedStatus` Map | 避免重复计算成型状态 |
| 事件驱动 | 仅在方块变更时触发检查 | 消除定时轮询开销 |
| FastUtil 集合 | `LongOpenHashSet`、`Long2ObjectOpenHashMap` | 避免装箱，提升缓存友好性 |

### 10.9 与 GTCEu 原生多方块系统的对比

| 维度 | GTCEu 原生 | GoblinMachineDeity |
|------|-----------|-------------------|
| **触发时机** | 定时轮询（每 tick 或每 125ms） | 事件驱动（方块变更时） |
| **延迟** | 最高可达数百毫秒 | 瞬时（<1ms） |
| **复杂度** | O(n) 遍历所有机器 | O(1) 哈希查找 |
| **状态管理** | 分散在每个 Controller | 集中缓存 |
| **动态结构** | 不支持 | 支持（SECTIONED / ENCLOSURE） |
| **错误提示** | 仅支持 JEI 预览 | 支持实时 Outliner 标记 |
| **扩展性** | 固定模式 | 可扩展神谕模式 |

### 10.10 三种神谕模式的实现细节

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

### 10.11 为什么放弃 DynamicRender 方案改用 getBlockAppearance()

| 维度 | DynamicRender 方案 | getBlockAppearance() 方案 |
|------|-------------------|--------------------------|
| 新增类 | IGhostMachine + Render + RenderType + Model + transparent.png ≈ 5 个 | **0 个** |
| 渲染方式 | 底座透明 + DynamicRender 接管 | Minecraft 原生渲染 |
| CTM | 需要在 getRenderQuads 中手动获取 CTM model | 原生支持（通过 getAppearance 查询链） |
| 方块属性 (FACING等) | 需在 DynamicRender 中手动处理 | 直接存绝对 BlockState，扳手旋转不变，蓝图旋转由结构系统处理（见 §4.3） |
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
| `src/main/java/com/goblincoders/goblintech/api/machine/multiblock/part/GoblinPartMachine.java` | Part 基类（★ 覆写 `getBlockAppearance()` 实现伪装） |
| `src/main/java/com/goblincoders/goblintech/api/machine/multiblock/part/GoblinTieredPartMachine.java` | 分级 Part |
| `src/main/java/com/goblincoders/goblintech/api/machine/multiblock/part/GoblinTieredIOPartMachine.java` | IO Part |
| `src/main/java/com/goblincoders/goblintech/api/machine/multiblock/GoblinControllerMachine.java` | Controller 基类 |
| **神谕系统（Phase 4 新增）** | |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/GoblinMachineDeity.java` | 多方块机器之神（集中式验证引擎） |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/IDeityOracle.java` | 神谕接口 |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/StructureBitmap.java` | FIXED 模式神谕实现 |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/SectionedBitmap.java` | SECTIONED 模式神谕实现 |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/EnclosureValidator.java` | ENCLOSURE 模式神谕实现 |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/FormationStatus.java` | 成型状态枚举 |
| `src/main/java/com/goblincoders/goblintech/api/machine/deity/ExpectedPartQuery.java` | 期望 Part 查询结果 |
| **蓝图腾系统** | |
| `src/main/java/com/goblincoders/goblintech/api/item/GoblinShamanItem.java` | 蓝图腾物品（谐音 Create Schematic） |
| **其他** | |
| `src/main/java/com/goblincoders/goblintech/GoblinTech.java` | Mod 入口 |

> **已从列表中移除**：`IGhostMachine`、`GhostPartHullRender`、`GhostPartHullRenderType`、`GhostPartHullModel`、`transparent.png` — 不再需要。

### 11.2 GTCEu 基类文件

| 文件 | 说明 |
|------|------|
| `src/main/java/com/gregtechceu/gtceu/api/machine/multiblock/part/MultiblockPartMachine.java` | GTCEu Part 基类（GoblinPartMachine 的继承源） |
| `src/main/java/com/gregtechceu/gtceu/api/machine/multiblock/MultiblockControllerMachine.java` | GTCEu Controller 基类（GoblinControllerMachine 的继承源） |
| `src/main/java/com/gregtechceu/gtceu/client/renderer/machine/DynamicRender.java` | GTCEu 动态渲染基类 |
| `src/main/java/com/gregtechceu/gtceu/client/model/machine/MachineModel.java` | GTCEu 机器模型渲染管线 |

### 11.3 Create 参考文件

| 文件 | 说明 |
|------|------|
| `Create-mc1.21.1-6.0.10/.../schematics/SchematicItem.java` | 蓝图物品（GoblinShamanItem 的继承目标，S-h-a-m-a-n / S-c-h-e-m-a-t-i-c 谐音梗） |
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
