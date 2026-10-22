# Lyricon-Universal-Provider

> 为 [词幕 Lyricon](https://github.com/tomakino/lyricon) 提供**通用播放器歌词源**的 Provider 模块。一个模块，补上状态栏歌词的最后一公里。

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)]()
[![LSPosed](https://img.shields.io/badge/framework-LSPosed-orange.svg)]()
[![Build](https://img.shields.io/badge/build-GitHub%20Actions-success.svg)](../../actions)

---

## 简介

**词幕（Lyricon）** 是 Android 上口碑极佳的状态栏歌词增强工具，支持逐字歌词、翻译显示与对唱模式，并通过 Provider 插件系统扩展不同播放器的歌词源。

但词幕的歌词源长期依赖**专门的 Provider 插件**——你常用的播放器如果没有对应适配，状态栏歌词就是一片空白。

**Lyricon-Universal-Provider** 基于 [ColorOS-Live-Lyrics-Providers](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers) 的 `universal-provider` 适配而来，把“通用播放器”能力接入词幕生态。**你不再需要等待某个播放器被单独适配，一个通用 Provider 就能把歌词推给词幕。**

---

## 特性

| 特性 | 说明 |
|---|---|
| 🎵 **通用适配** | 不绑定特定播放器，走通用 Provider 路径，大幅拓宽词幕可用歌词来源 |
| ✨ **逐字歌词** | 与词幕本体联动，支持逐字点亮、翻译行、对唱模式 |
| 🧩 **职责单一** | **只向词幕推送歌词，不写入 `lyricInfo`**，不与锁屏歌词模块抢通道 |
| 🤝 **共存友好** | 与官方通用播放器 Provider 可同时存在，锁屏歌词与状态栏歌词互不干扰 |
| 🏗️ **结构清晰** | 主模块独立为 `universal-provider-lyricon/`，上游解析层与公共层作为编译依赖，便于跟随上游更新 |
| 🔧 **自动构建** | GitHub Actions 已配置，push 或手动触发即自动构建并上传 APK |

---

## 安装

### 前置条件

- Android 设备，已 root 并安装 **LSPosed**
- 已安装 **词幕（Lyricon）** 本体
- 建议先备份当前 LSPosed 配置

### 步骤

1. **安装 APK**：从 [Releases](../../releases) 下载最新 APK 并安装。
2. **启用模块**：在 LSPosed 中启用 `Lyricon-Universal-Provider`，**作用域勾选「系统」**。
3. **绑定播放器**：打开 Provider App，绑定你要使用的播放器，并选择歌词来源。
4. **设置词幕作用域**：将**词幕本体**的作用域设为 **「系统界面」**。
5. **重启生效**：重启 System UI（或直接重启设备），播放歌曲即可看到状态栏歌词。

> ⚠️ **作用域别搞反**：Provider 模块选「系统」，词幕本体选「系统界面」。两个都配好后重启 System UI。

---

## 构建

### 环境要求

- **JDK 21**
- **compileSdk 37**
- Android SDK / Build Tools 按 `build.gradle` 配置

### 命令行构建

```bash
./gradlew :universal-provider-lyricon:assembleDebug
```

产物位于 `universal-provider-lyricon/build/outputs/apk/`。

### CI 构建

仓库已配置 GitHub Actions。向主分支 push，或在 Actions 页面手动触发 `workflow_dispatch`，即可自动构建并上传 APK 产物，无需本地搭环境。

---

## 项目结构

```
Lyricon-Universal-Provider/
├── universal-provider-lyricon/   # 主模块：面向词幕的通用 Provider
├── ...                           # 上游解析层（LRC / QRC / YRC）与 Provider 公共层（编译依赖）
└── .github/workflows/            # 自动构建
```

- 解析层（LRC / QRC / YRC）与 Provider 公共层（诊断、缓存）来自上游，作为**编译依赖**引入，不作为独立应用。
- 本模块**只负责向词幕推送歌词**，不写入 `lyricInfo`。

---

## 与上游的关系

| 项目 | 关系 |
|---|---|
| [ColorOS-Live-Lyrics-Providers](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers) | 上游，本项目基于其 `universal-provider` 适配 |
| [词幕 Lyricon](https://github.com/tomakino/lyricon) | 目标生态，本模块为其提供通用歌词源 |

上游代码版权归原作者所有。本项目在上游基础上做面向词幕的适配与维护。

---

## 常见问题

**Q：状态栏没有歌词？**
A：依次检查——Provider 模块作用域是否为「系统」、词幕本体作用域是否为「系统界面」、App 内是否已绑定播放器并选择歌词来源，最后重启 System UI。

**Q：和锁屏歌词模块冲突吗？**
A：不冲突。本模块**不写入 `lyricInfo`**，只向词幕推送歌词，可与官方通用播放器 Provider 共存。

**Q：支持哪些播放器？**
A：走通用 Provider 路径，不绑定特定播放器。具体可用性取决于播放器自身的媒体会话与歌词暴露方式。

**Q：需要 root 吗？**
A：需要。本模块依赖 LSPosed 框架。

---

## 致谢

- [Andrea-lyz/ColorOS-Live-Lyrics-Providers](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers) — 上游 `universal-provider` 与解析层
- [tomakino/lyricon](https://github.com/tomakino/lyricon) — 词幕本体

---

## 许可证

本项目以 **Apache License 2.0** 开源，详见 [LICENSE](LICENSE)。

上游代码版权归原作者所有，本项目在其基础上进行适配与维护。