# 词幕通用播放器 Provider（universal-provider-lyricon）

> 普通用户的安装与排错指南请回到 [根目录 README](../README.md)；本文档面向开发者与想了解实现细节的用户。

将“通用播放器”歌词能力接入词幕的 LSPosed Provider 模块。本模块基于上游 `ColorOS-Live-Lyrics-Providers / universal-provider` 适配，保留上游的取词、缓存、来源优先级与播放器绑定逻辑，只把输出目标改为词幕。

## 模块信息

| 项目 | 值 |
|---|---|
| 应用名 | 词幕通用播放器 Provider |
| 包名 | `io.github.cn_cizhanyue.lyricon.universal.provider` |
| 最低系统 | Android 8.1（minSdk 27） |
| 许可证 | Apache-2.0（沿用上游） |
| 上游模块 | `ColorOS-Live-Lyrics-Providers / universal-provider` |
| 输出目标 | 词幕 Lyricon |

## 上游项目

本模块保留上游的取词、缓存、来源优先级与播放器绑定逻辑，只把输出目标改为词幕。

| 项目 | 用途 |
|---|---|
| [Andrea-lyz/ColorOS-Live-Lyrics-Providers](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers) | 上游 `universal-provider` 与解析层 |
| [tomakino/lyricon](https://github.com/tomakino/lyricon) | 词幕本体 |

## 功能

### 歌词能力

- **在线词源**：QQ音乐、网易云音乐、Apple Music。
- **来源优先级**：歌词来源优先级可配置；支持自动回退与在线搜索。
- **本地缓存**：本地缓存与离线复用；网络异常时回退到缓存。
- **逐字歌词合成**：LRC / QRC / KRC / YRC / TTML / LYS 等格式解析与合成。
- **AI 翻译**：可接入 OpenAI 兼容接口，生成翻译行。

### 同步与兼容

- **播放器绑定**：自动发现媒体会话，支持手动选择与包名过滤。
- **状态栏歌词推送**：向词幕输出逐字歌词、翻译行与对唱信息。

### 界面与设置

- **UI**：Material 3 主题、动态取色、深浅色模式、多语言。
- **设置项**：歌词来源优先级、缓存策略、翻译开关、日志级别等。
- **日志与诊断**：可导出日志，便于反馈问题。

## 构建

本地构建请运行 `./gradlew assembleRelease`。仓库的 GitHub Actions 工作流会在推送 tag 时自动出包（如已配置）。

## 相关链接

- 源码与问题反馈：[CN-CiZhanYue/Lyricon-Universal-Provider](https://github.com/CN-CiZhanYue/Lyricon-Universal-Provider)
- 上游项目：[ColorOS-Live-Lyrics-Providers](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers)
- 词幕本体：[tomakino/lyricon](https://github.com/tomakino/lyricon)

## 许可证

Apache License 2.0（沿用上游）
