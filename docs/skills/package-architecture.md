# 包名架构规划

## 概述

本项目的包名和模块架构规划。基础包为 `com.goblincoders`，各功能按子包隔离开以便未来发展成独立附属模组。

---

## 包名规划

| 包名 | 所属顶层包 | 职责 | 说明 |
|------|-----------|------|------|
| `goblintech` | `com.goblincoders.goblintech` | **主游戏内容** | 配方链设计、电压限制到 IV、核心常量 |
| `goblintech.recipe` | `com.goblincoders.goblintech.recipe` | **配方系统扩展** | GoblinScripture、GoblinOracleOfScripture、IBelieverOfScripture、RecipeMode 等核心架构 |
| `goblintech.machine` | `com.goblincoders.goblintech.machine` | **机器逻辑** | 重写 GTM 机器底层逻辑（SlotMode、缓冲器、冰箱、提取机等） |
| `goblintech.power` | `com.goblincoders.goblintech.power` | **电力系统** | 重写电力系统，计划迁移到 CEE |
| `goblinmotive` | `com.goblincoders.goblinmotive` | **物理化支持** | 基- 于 SABLE 物理化引擎实现 Valkyrien Skies / Create Aeronautics 兼容 |
| `goblintfc` | `com.goblincoders.goblintfc` | **TFC 支持** | TerraFirmaCraft 兼容 |
| `goblintape` | `com.goblincoders.goblintape` | **联动配方** | 跨模组整合包的"胶带"式联-动配方 |
| `goblincanban` | `com.goblincoders.goblincanban` | **仓库管理** | AE2 存储与自动合成支持 |

> **⚠️ `goblincanban` 命名说明**：`canban` 是日文「看板」（kanban）的故意拼写错误。看板系统（Kanban）是一种生产管理和库存调度方法，与 AE2 的 ME 网络、自动合成、物品存储调度完美对应。哥布林程序员写错了——这就是 `canban` 的由来。

## 子包独立性

每个子包的设计都考虑了未来独立成附属模组的可能性。当需要剥离时：

1. 将该子包连同其资源文件复制到新模组项目中
2. 添加对 `com.goblincoders.goblintech` 的依赖（核心常量仍在主包中）
3. 通过 Mixin 注入回 GTM 主模组的对应逻辑

这种架构设计使得项目既适合当前整合包 CoreMod 的定位，又保留了未来分支发展的灵活性。

## 相关文件

| 文件 | 说明 |
|------|------|
| `src/main/java/com/goblincoders/goblintech/` | GoblinTech 主包 |
| `src/main/java/com/goblincoders/goblintech/GoblinTech.java` | 入口类 |
| `src/main/java/com/goblincoders/goblintech/api/GoblinTechValues.java` | 核心常量 |
