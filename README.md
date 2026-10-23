# Lyricon-Universal-Provider

> 为 [词幕 Lyricon](https://github.com/tomakino/lyricon) 提供**通用播放器歌词源**的 Provider 模块。一个模块，补上状态栏歌词的最后一公里。

[![Version](https://img.shields.io/badge/version-1.3.0-6C4DFF.svg)]()
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
| ✨ **逐字 / 逐词** | 真实 QRC/YRC 逐字优先；只有行级歌词时，按行起止时间合成中文逐字、英文逐词 |
| 🌐 **翻译 + AI 翻译** | 优先使用歌词源自带翻译；缺失时可用你自己的 OpenAI 兼容接口补翻译 |
| 💾 **共享缓存** | 新增「和官方版共用」词源，读取 `/data/system/universal_lyric_cache.json` |
| 📶 **联网开关** | 关闭后不再联网，只使用共享缓存；默认开启 |
| 🧩 **职责单一** | **只向词幕推送歌词，不写入 `lyricInfo`**，不与锁屏歌词模块抢通道 |
| 🤝 **共存友好** | 与官方通用播放器 Provider 可同时存在，锁屏歌词与状态栏歌词互不干扰 |
| 🪟 **Liquid Glass** | 极光背景、半透明玻璃卡片、浮动玻璃导航，完全独立于上游的视觉语言 |
| 🎨 **3 套 UI 主题 × 5 套配色** | Liquid Glass、纸张、霓虹三套界面；每套 5 种主题配色，共 15 种 |
| 🖼️ **自定义背景** | 可从相册选择一张图片作为全局背景，自动叠加可读性遮罩 |
| 🌗 **主题切换** | 跟随系统 / 浅色 / 深色；霓虹主题固定深色 |
| 🔧 **自动构建** | GitHub Actions 已配置，push 或手动触发即自动构建并上传 APK |

---

## 外观与主题

设置 → **外观**：

- **UI 主题**
  - **Liquid Glass**：极光光晕、玻璃面板、柔和模糊与浮动导航；
  - **纸张**：暖纸底色、衬线标题、细线分隔与扁平卡片；
  - **霓虹**：黑底网格、等宽标题、霓虹描边与锐利卡片。
- **主题配色**（每套 UI 主题 5 种，且各自至少有一种独有配色）
  - Liquid Glass：极光、余烬、潮汐、幽兰、冰川；
  - 纸张：墨、棕褐、鼠尾草、钴蓝、锈；
  - 霓虹：酸、赛博、电压、等离子、矩阵。
- **主题模式**：跟随系统 / 浅色 / 深色（霓虹固定深色）。
- **自定义背景**：选择图片作为全局背景，自动叠加可读性遮罩；可清除。
- **Liquid Glass 开关**：仅 Glass 主题生效，关闭后改用不透明面板，适合低端机或省电场景。

所有选择立即生效并持久化。

---

## AI 翻译

设置 → **AI 翻译**：

- 使用你自己的 **OpenAI 兼容**接口（如 `https://api.openai.com/v1`）；
- 配置接口地址、API Key、模型、目标语言；
- 仅在歌词源**没有翻译**、且「注入翻译歌词」与「允许联网」开启时调用；
- 按行切分，每 40 行一批请求 `/chat/completions`，结果按原时间轴写回；
- 歌词源已有翻译时优先使用源翻译，不覆盖；
- 失败后同一首歌 10 分钟内不重试，避免反复消耗 API；
- Key 与歌词会发送到你填写的接口，请确认可信；Key 保存在本机。

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
│   └── src/main/.../ui/          # Liquid Glass / 纸张 / 霓虹三套 UI 与主题系统
├── ...                           # 上游解析层（LRC / QRC / YRC）与 Provider 公共层（编译依赖）
├── 版本README.md                 # 版本与变更记录
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

**Q：逐字歌词为什么不完全准确？**
A：只有 QRC/YRC 才有真实逐字时间；只有行级歌词时，本模块按行起止时间合成逐字，属于显示增强，不是真实演唱时间。

**Q：AI 翻译必须用 OpenAI 官方吗？**
A：不需要。任何兼容 `/chat/completions` 的服务都可以，填入对应的接口地址、Key 和模型即可。

---

## 致谢

- [Andrea-lyz/ColorOS-Live-Lyrics-Providers](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers) — 上游 `universal-provider` 与解析层
- [tomakino/lyricon](https://github.com/tomakino/lyricon) — 词幕本体
- 辞盏月 — 词幕适配与维护

---

## 许可证

本项目以 **Apache License 2.0** 开源，详见 [LICENSE](LICENSE)。

上游代码版权归原作者所有，本项目在其基础上进行适配与维护。
