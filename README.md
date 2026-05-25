# GoblinTechMotive

<p align="center">
    <strong>GoblinTechMotive</strong> - 整合包专用 GregTech CEu 核心模组
</p>

<p align="center">
    基于 GregTech CEu Modern，专为整合包设计的核心模组
</p>

---

## 📋 模组信息

| 项目 | 值 |
|------|-----|
| **模组 ID** | `goblintechmotive` |
| **命名空间** | `gtceu` (保持与 GregTech CEu 兼容) |
| **Minecraft** | 1.21.1 |
| **模组加载器** | NeoForge |
| **版本** | 0.0.1-gtmotive |

---

## ⚙️ 强制依赖

本模组**必须在**以下模组存在的情况下才能加载：

| 模组 | 说明 |
|------|------|
| **Create** | 动力学系统、多方块结构 |
| **Applied Energistics 2** | 存储系统、自动化 |
| **NeoEcoAE** | AE2 高性能扩展组件 |

---

## 🔧 与 GregTech-Modern 的关系

- **GoblinTechMotive** 是 **GregTech-Modern** 的一个分支版本
- 保持 `gtceu` 命名空间以兼容现有生态系统
- 代码层面大量复用 GregTech-CEU Modern 的实现
- 通过独立 `modId` (`goblintechmotive`) 与官方版本区分

---
## ⚠️ 兼容性说明

- **不能与官方 GregTech-Modern 同时加载**
- 已通过还原 `MOD_ID` 为 `gtceu` 实现冲突检测，无法与官方版共存

---

## 🐛 已知问题

| 问题 | 状态 |
|------|------|
| **ModernUI** 与创造模式物品栏搜索框冲突，导致搜索时文字消失。仅影响搜索框显示，不影响游戏功能 | 暂时不考虑修复，如有必要可使用 JEI 作弊模式在创造模式更高效地获取物品 |
| **Create Propulsion Simulated** 物品未能成功注册到创造模式独立物品栏中，但可通过创造模式物品栏搜索找到，功能逻辑本身没有问题 | 暂时不考虑修复，如有必要可使用 JEI 作弊模式在创造模式更高效地获取物品 |
| **FirmaLife** 物品同样未能成功注册到创造模式独立物品栏中，但可通过创造模式物品栏搜索找到，功能逻辑本身没有问题 | 暂时不考虑修复，如有必要可使用 JEI 作弊模式在创造模式更高效地获取物品 |

---

## 📦 开发相关 *(计划中)*

### 添加为依赖

> Maven 仓库配置正在计划中，暂不支持外部依赖

```groovy
// 计划中 - 等待 Maven 仓库配置完成
repositories {
    maven {
        name = 'GoblinTechMotive Maven'
        url = 'https://maven.your-server.com' // TODO: 待配置
    }
}

dependencies {
    implementation "com.gregtechceu.gtceu:goblintechmotive-1.21.1:${version}" // TODO: 待发布
}
```

### 编译要求

- Java 21
- Gradle 8.x
- IntelliJ IDEA + Lombok Plugin + Minecraft Development Plugin

---

## 📝 致谢

本模组基于以下项目：

- [GregTech CEu Modern](https://github.com/GregTechCEu/GregTech-Modern) - 核心代码
- [Create](https://www.curseforge.com/minecraft/mc-mods/create) - 动力学系统
- [Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2) - 存储系统
- [NeoEcoAE](https://modrinth.com/mod/neoecoae) - AE2 高性能扩展

---

## 📄 许可证

LGPL-3.0 License

---

## 🔗 相关链接

- GitHub: https://github.com/dalizi2333/GoblinTechMotiveCore
- Issues: https://github.com/dalizi2333/GoblinTechMotiveCore/issues
