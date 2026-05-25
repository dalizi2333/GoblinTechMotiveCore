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

1. 移除 `libs/` 目录和 `scripts/update_electroenergetics.py`
2. 移除 `repositories.gradle` 中的 `flatDir` 仓库
3. 改为使用 Modrinth Maven 或 Curse Maven 等标准依赖方式

## 相关文件

| 文件 | 说明 |
|------|------|
| `scripts/update_electroenergetics.py` | 自动更新脚本 |
| `libs/electroenergetics-latest.jar` | 下载后的 jar 文件（被 .gitignore 排除） |
| `gradle/forge.versions.toml` | 版本号和依赖声明 |
| `gradle/scripts/repositories.gradle` | flatDir 仓库配置 |
