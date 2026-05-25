# Create Electro Energetics 自动更新技能

## 概述

本项目计划将 GTCEu（GregTech CEu Modern）的电力系统迁移到 Create Electro Energetics 模组上。由于该模组仍处于开发阶段，尚未发布正式版，因此使用自动构建版本（autobuild）进行开发和测试。

本技能提供了一个 Python 脚本，用于半自动获取 Create Electro Energetics 的最新 autobuild 版本，确保开发环境始终使用最新的可用构建。

## 使用方式

### 手动更新

```bash
# Windows
python scripts/update_electroenergetics.py

# Linux
python3 scripts/update_electroenergetics.py
```

### 脚本功能

1. 从 GitHub API 获取项目 `george8188625/Create-Electro-Energetics` 的最新 autobuild release
2. 自动下载对应的 `.jar` 文件
3. 将 jar 保存为固定名称 `libs/electroenergetics-latest.jar`
4. 更新 `gradle/forge.versions.toml` 中的版本号记录

### 依赖处理

- Gradle 通过 `flatDir` 仓库引用 `libs/` 目录下的 `electroenergetics-latest.jar`
- `libs/` 目录已加入 `.gitignore`，不会提交到版本控制
- 在服务器或新开发环境上运行一次脚本即可获取最新版本

## 迁移计划

当 Create Electro Energetics 发布正式版后，将：

### 完整切换回标准依赖方式

1. 移除 `libs/` 目录和 `scripts/update_electroenergetics.py`
2. 移除 `repositories.gradle` 中的 `flatDir` 仓库
3. 将 `dependencies.gradle` 中 `':electroenergetics-latest:'` 替换为 `forge.cee` 版本目录引用
4. 添加正式版 mod 的 Maven 依赖声明到 `gradle/forge.versions.toml`（Modrinth Maven 或 Curse Maven）

### 注册为正式的必要依赖

版本稳定后需在以下位置注册，使其获得与其他核心依赖同等的检查权限：

1. `gradle.properties` — 添加 `cee_mod_version = <正式版本号>` 属性
2. `gradle/scripts/resources.gradle` — 在 `replaceProperties` 中添加 `cee_mod_version` 变量映射
3. `src/main/templates/META-INF/neoforge.mods.toml` — 添加依赖声明：
   ```toml
   [[dependencies.${mod_id}]]
       modId = "electroenergetics"
       type = "required"
       versionRange = "[${cee_mod_version},)"
       ordering = "AFTER"
       side = "BOTH"
   ```

这样一来，NeoForge 在启动阶段即可检测 CEE 是否缺失，不再依赖运行时 `GoblinTech.isCEELoaded()` 的 Java 代码检查。

## 相关文件

| 文件 | 说明 |
|------|------|
| `scripts/update_electroenergetics.py` | 自动更新脚本 |
| `libs/electroenergetics-latest.jar` | 下载后的 jar 文件（被 .gitignore 排除） |
| `gradle/forge.versions.toml` | 版本号和依赖声明 |
| `gradle/scripts/repositories.gradle` | flatDir 仓库配置 |
