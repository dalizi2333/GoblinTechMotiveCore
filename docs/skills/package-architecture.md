# 包名架构规划

## 概述

本项目是 **GoblinTechMotive 整合包的专属 CoreMod**，采用单模块架构，完全重写底层系统，专注 ULV 到 IV 电压的游戏体验与世界探索。不考虑剥离成独立附属模组，因此所有代码都在 `com.goblincoders.goblintech` 下统一管理。

---

## 包名规划

| 包名 | 职责 | 说明 |
|------|------|------|
| `com.goblincoders.goblintech` | **核心入口与常量** | 模组主类、核心常量、电压定义（仅到 IV） |
| `com.goblincoders.goblintech.recipe` | **配方系统** | 完全重写配方系统，支持跨模组配方、自定义配方类型 |
| `com.goblincoders.goblintech.machine` | **机器系统** | 完全重写机器底层逻辑、方块实体、渲染系统 |
| `com.goblincoders.goblintech.power` | **电力系统** | 完全重写电力系统，未来可能迁移到 Electro Energetics |
| `com.goblincoders.goblintech.integration` | **跨模组联动** | Create、Create Aeronautics、TFC、AE2 等联动支持 |
| `com.goblincoders.goblintech.item` | **物品与方块** | 所有物品、方块、创造模式标签 |
| `com.goblincoders.goblintech.client` | **客户端内容** | 渲染、模型、GUI、按键绑定等客户端专属代码 |

---

## 设计原则

### 1. 整合包专属
- 所有设计只为 GoblinTechMotive 整合包服务
- 不考虑其他整合包或独立使用场景
- 可以大胆删除 GT(M) 中不需要的冗余代码

### 2. 专注 ULV 到 IV
- 电压仅定义到 IV（含）
- 超过 IV 的机器、配方、电力逻辑全部删除
- 专注早期到中期的游戏体验与世界探索

### 3. 完全重写
- 配方系统 → 完全重写
- 机器系统 → 完全重写
- 配方执行器 → 完全重写
- 电力系统 → 完全重写
- GT(M) 代码仅作参考灵感，实际全部重写

---

## 相关文件

| 文件 | 说明 |
|------|------|
| `src/main/java/com/goblincoders/goblintech/` | 主源码目录 |
| `src/main/java/com/goblincoders/goblintech/GoblinTech.java` | 模组入口类 |
| `src/main/java/com/goblincoders/goblintech/api/GoblinTechValues.java` | 核心常量与电压定义 |
