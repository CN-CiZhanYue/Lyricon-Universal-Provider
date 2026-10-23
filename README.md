# 词幕通用播放器 Provider

![LSPosed](https://img.shields.io/badge/LSPosed-Module-blue)
![Android](https://img.shields.io/badge/Android-8.1%2B-green)
![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey)

为 [词幕 Lyricon](https://github.com/tomakino/lyricon) 提供**通用播放器歌词源**的 Provider 模块。一个模块，补上状态栏歌词的最后一公里。

> 想了解实现细节、上游依赖或构建方式？请看 [universal-provider-lyricon/README.md](universal-provider-lyricon/README.md)。

## 简介

词幕通过 Provider 插件系统扩展歌词来源，但你常用的播放器如果不在已适配列表中，状态栏歌词就可能为空。本模块基于 [ColorOS-Live-Lyrics-Providers](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers) 的 `universal-provider` 适配，把“通用播放器”能力接入词幕生态。你不再需要等待某个播放器被单独适配，一个通用 Provider 就能把歌词推给词幕。

## 特性

| 特性 | 说明 |
|---|---|
| **通用适配** | 不绑定特定播放器，走通用 Provider 路径，大幅拓宽词幕可用歌词来源 |
| **逐字歌词** | 与词幕本体联动，支持逐字点亮、翻译行、对唱模式 |
| **职责单一** | 只向词幕推送歌词，不写入 `lyricInfo`，不与锁屏歌词模块抢通道 |
| **共存友好** | 与官方通用播放器 Provider 可同时存在，锁屏歌词与状态栏歌词互不干扰 |
| **适用场景** | 你用的播放器没有专门的词幕 Provider，但希望状态栏歌词正常显示 |

## 安装

1. 从本仓库 Releases 下载最新 APK 并安装。
2. 在 LSPosed 中启用本模块，**作用域勾选「系统」**。
3. 打开 Provider App，绑定播放器并选择歌词来源。
4. 将**词幕本体**的作用域设为 **「系统界面」**。
5. 重启 System UI（或重启设备），播放歌曲即可看到状态栏歌词。

> [!IMPORTANT]
> **作用域配置是生效的关键：**
> - 本模块 → 勾选 **「系统」**
> - 词幕本体 → 勾选 **「系统界面」**
>
> 两者**不要搞反**，否则歌词不会出现在状态栏。

## 常见问题

**状态栏没有歌词？** 按顺序检查：

1. 本模块在 LSPosed 中是否启用，作用域是否为「系统」；
2. 词幕本体作用域是否为「系统界面」；
3. Provider App 内是否已绑定当前播放器并选择了歌词来源；
4. 最后重启 System UI（或重启设备）。

**和官方通用播放器 Provider 冲突吗？** 不冲突。本模块不写入 `lyricInfo`，可与官方版同时存在，锁屏歌词与状态栏歌词互不影响。

## 源码与反馈

- 源码仓库：[CN-CiZhanYue/Lyricon-Universal-Provider](https://github.com/CN-CiZhanYue/Lyricon-Universal-Provider)
- 问题反馈：[Issues](https://github.com/CN-CiZhanYue/Lyricon-Universal-Provider/issues)
- 下载发布：[Releases](https://github.com/CN-CiZhanYue/Lyricon-Universal-Provider/releases)

## 致谢

- [Andrea-lyz/ColorOS-Live-Lyrics-Providers](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers) — 上游 `universal-provider` 与解析层
- [tomakino/lyricon](https://github.com/tomakino/lyricon) — 词幕本体

## 许可证

Apache License 2.0
