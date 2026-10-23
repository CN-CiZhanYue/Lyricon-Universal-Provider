# 词幕通用播放器Provider（universal-provider-lyricon）

面向 [词幕（Lyricon）](https://github.com/tomakino/lyricon) 的通用播放器 Provider 适配版，基于
[ColorOS-Live-Lyrics-Providers](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers)
中的 `universal-provider` 模块修改。

- 包名：`io.github.cn_cizhanyue.lyricon.universal.provider`
- 项目地址：https://github.com/CN-CiZhanYue/Lyricon-Universal-Provider/
- 应用名：词幕通用播放器Provider
- 最低系统：Android 8.1（minSdk 27）
- 许可证：Apache-2.0（沿用上游）

## 上游项目

| 项目 | 地址 | 用途 |
| --- | --- | --- |
| ColorOS-Live-Lyrics-Providers | https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers | 通用播放器 Provider 源码、解析器、在线词源、ColorOS 会话 Hook |
| Lyricon | https://github.com/tomakino/lyricon | 词幕歌词框架、Provider SDK、状态栏歌词渲染 |
| Lyricon Provider SDK | `io.github.proify.lyricon:provider:0.1.70` | 向词幕注册 Provider、推送 Song / PlaybackState |

本模块保留上游 `universal-provider` 的取词、缓存、来源优先级、播放器绑定和设置页，只把输出目标改为词幕。

## 功能

- 监听 ColorOS `MediaSessionRecord` 的 metadata / playback state；
- 在线词源：QQ 音乐、网易云音乐、Apple Music；
- 歌词缓存、来源优先级、手动绑定、纯音乐/无词策略；
- 将解析后的歌词同步推送到词幕：`Song`、`PlaybackState`、翻译开关；
- 行级歌词会按行起止时间合成逐字时间轴（中文逐字、英文逐词），真实 QRC/YRC 优先；
- 切歌时用占位 Song 刷新歌名，旧 payload 按 generation / session / 标题校验丢弃；
- 不写入 `MediaMetadata["lyricInfo"]`，可与官方通用播放器 Provider 同时运行；
- 「和官方版共用（缓存）」词源：读取 `/data/system/universal_lyric_cache.json`；
- 联网开关：关闭后不再联网，只使用共享缓存；官方版未安装时关闭会提示；
- AI 翻译：歌词源没有翻译时，使用用户自己的 OpenAI 兼容接口补翻译（设置中配置地址/Key/模型/目标语言），默认关闭；
- 关于弹窗：说明这是适配版本，非官方版本，并提供本项目与上游项目链接；
- Liquid Glass UI：极光背景、半透明玻璃卡片、浮动玻璃导航；
- 3 套 UI 主题（Liquid Glass / 纸张 / 霓虹），每套 5 种配色，共 15 种；
- 主题模式：跟随系统/浅色/深色；支持自定义背景图片；
- 分组设置页、AI 设置分区、独立自适应图标；
- 完整中英文文案对齐。
- About 中可打开本项目或上游项目。

## 与上游的差异

1. `applicationId`、应用名、`lyricon_module` 元数据；
2. 新增 `lyricon/` 包：`LyriconBridge`、`LyriconPublisher`、歌词解析与逐字合成；
3. `UniversalModuleEntry` 在取词完成点推送到词幕，同时移除锁屏 `lyricInfo` 写入；
4. 新增共享缓存词源、联网开关、关于弹窗、首次启动说明；
5. 逐字默认开启，使用独立配置文件 `/data/system/lyricon_word_timing.enabled`；
6. 设置页/主页 UI 文案与入口调整。

## 安装

1. 安装 APK；
2. LSPosed 启用「词幕通用播放器Provider」，作用域勾选 **系统**；
3. 打开 App，绑定需要取词的播放器、选择歌词来源；
4. 词幕本体启用，作用域勾选 **系统界面**；
5. 官方通用播放器 Provider 可以保留（负责锁屏），本模块只负责词幕，不写锁屏。

> 新包名与原版不共享设置，首次使用需要重新绑定播放器；`/data/system/universal_*` 缓存与来源配置仍共用。

## 已知限制

- 逐字合成是按行时间均分，不是逐字真实演唱时间；有 QRC/YRC 时使用真实时间；
- 无歌词歌曲的标题依赖播放器上报的 `MediaMetadata`；播放器不上报标题时无法推断；
- 不同播放器的 `PlaybackState` 上报频率/位置精度不同，已做抖动过滤；
- 未在所有 ColorOS 版本/播放器上验证，问题请附 `LyriconUniversal` 日志。

## 致谢

- [Andrea-lyz](https://github.com/Andrea-lyz)：ColorOS-Live-Lyrics-Providers 上游项目；
- [tomakino / Proify](https://github.com/tomakino)：Lyricon 与 Provider SDK；
- 辞盏月：词幕适配与维护。

## 许可证

Apache License 2.0，见仓库根目录 `LICENSE` 与 `NOTICE`。上游代码版权归原作者所有。
