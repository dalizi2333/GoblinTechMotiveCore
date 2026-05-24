# Minecraft 日志定位技能

## 概述

在 Minecraft 模组开发过程中，当游戏崩溃或出现异常时，需要查看运行日志来诊断问题。本技能说明了如何在 NeoForge/Gradle 开发环境中定位和读取运行日志。

## 日志位置

### 运行日志

在 Gradle 开发环境中，运行客户端或服务端产生的日志位于 `run/` 目录下：

| 文件 | 路径 | 说明 |
|------|------|------|
| 最新日志 | `run/logs/latest.log` | 最近一次运行的完整日志 |
| 调试日志 | `run/logs/debug.log` | 包含更详细的调试信息 |
| 崩溃报告 | `run/crash-reports/` | 崩溃时生成的报告文件 |

### 关于 `.gitignore`

`run/` 目录通常被 `.gitignore` 排除，不会提交到版本控制。这意味着：

- 日志文件**只存在于本地开发环境**中
- 需要通过 IDE 的文件浏览器或终端直接访问
- 无法通过代码仓库查看日志

### 日志查看方法

1. **IDE 文件浏览器**：在 IDE 中展开 `run/logs/` 目录直接查看
2. **终端命令**：使用 PowerShell 或命令行查看
   ```powershell
   # 查看最新日志末尾（最近的崩溃信息）
   Get-Content .\run\logs\latest.log -Tail 100

   # 实时跟踪日志输出
   Get-Content .\run\logs\latest.log -Wait
   ```
3. **文本编辑器**：直接用 VS Code 或记事本打开 `run/logs/latest.log`

## 常见日志分析

### 崩溃日志特征

- **关键字**：搜索 `ERROR`、`Exception`、`Caused by`、`Failed`、`Skipping`
- **模组加载错误**：搜索 `Mod ` 和 `failed to load`
- **Mixin 错误**：搜索 `Mixin` 和 `mixin`，常见于兼容性问题
- **类找不到**：搜索 `ClassNotFoundException` 或 `NoClassDefFoundError`

### 典型问题示例

```log
[ERROR] Skipping mod loading: borderless-window-1.7.5_1.jar
  - is for Minecraft Forge or an older version of NeoForge
```

这表明该模组的 JAR 不兼容当前的 NeoForge 版本，需要升级 NeoForge 或寻找更新的模组版本。

### 快速诊断流程

1. 打开 `run/logs/latest.log`
2. 搜索 `ERROR` 或 `Exception`
3. 关注最后出现的异常堆栈
4. 查看异常信息中的模组名称和版本号
5. 根据错误类型决定修复方案（升级版/更换模组/修改配置）

## 注意事项

- `clean runClient` 或 `clean runServer` 会清空旧的日志文件
- 多次运行的日志会被归档到 `run/logs/` 目录下带时间戳的文件中
- 如果日志文件为空，说明游戏可能没有真正启动就退出了
