# wild-android 架构（Stage 4 · 全量接线）

本文档描述 Stage 4 之后的分层结构、数据通道、缓存层级与下载/阅读器的核心流程。
基准规格：SZKM-48 `wild-spec.md`；本文按"现状"写，决策点与原 App 行为对齐。

## 分层总览

```
ui/screen + ui/reader + ui/components   Compose 屏与组件（无 mock）
        │  collectAsState / 参数化 koinViewModel(aid, cid)
ui/vm/ViewModels.kt                     每屏一个 ViewModel，UiState/Paged/各自 State
        │
data/repository/                        LibraryRepository（历史/搜索/书架标记/缓存清理）
        │                               SessionRepository（登录态/签到）
        │                               ReaderContentSource（阅读器三级取文）
        │                               DownloadEngine（下载队列 + 章级状态机）
        │
data/remote/                            Wenku8DataSource 接口（Repository/VM 只见它）
        │                               Wenku8HtmlSource（Jsoup 抓取实现）
        │                               Wenku8Client（OkHttp + cookie 持久化 + 接口缓存）
        │                               CfSession（状态机：隐藏 WebView 解 cf_clearance → 可见验证页降级）
        │
data/local/  Room（cookie / web_cache / chapter_cache / image_cache /
             reading_history / search_history / sign_log /
             novel_download / download_chapter / bookshelf_local）
data/prefs/  DataStore SettingsStore（主题/API Host/UA；阅读器设置键同原 App property 表）
filesDir/download/{aid}/               下载正文 chapter_{cid}.txt + cover
```

DI：`di/AppModule.kt` 单 Koin 模块。普通屏 `single` ViewModel；
带参屏 `viewModel { (aid: Int) -> … }`、阅读器 `viewModel { (aid: Int, cid: Int) -> … }`，
Compose 侧 `koinViewModel(parameters = { parametersOf(aid, cid) })`。

## 数据通道（决策 A 落地）

官方 XML API（`app.wenku8.com/api.php`）实测已死（恒回 `0`），
全部数据走 `https://www.wenku8.net` 桌面网页 HTML 抓取（GBK 解码）：

| 功能 | 路径 | CF 拦截 |
|---|---|---|
| initSession | `/` + `/login.php` | 否 |
| 首页推荐 | `/index.php` | 否 |
| 书籍详情 | `/book/{aid}.htm` | 否 |
| 目录 | `/novel/{aid/1000}/{aid}/index.htm`（cid = `{cid}.htm` 文件名） | 否 |
| 章节正文 | `/novel/{aid/1000}/{aid}/{cid}.htm`（`#content` + `img[src]`→`<!--image-->`） | 否 |
| 分类/排行/完结/搜索/书评/书架/账户 | `/modules/article/…` | 是 |

`Wenku8HtmlSource` 的每个方法（含登录/验证码/书架写操作）都被 `guard{}` 包住：
命中 CF 挑战页 → `Wenku8Client` 抛 `CfChallengeException` → `CfSession.guard`
用挂进 decorView 的 1px WebView 加载同一 URL，轮询 `cf_clearance` cookie 落地后
导入 OkHttp cookie 库并透明重试；隐藏求解失败/`Attention Required` 硬阻断时
状态推进 `NeedsUser`，NavShell 弹出全屏 `CfVerifyScreen`（可见 WebView）让用户
手动过一次，通过判定=CookieManager 出现 cf_clearance 或挑战页标记消失。
clearance 与 UA/IP 绑定：wenku8 域内 OkHttp 请求与两侧 WebView 一律
`SettingsStore.DEFAULT_UA`。重试仍被拦时 `fetchHtmlViaWebView` 兜底直接取 HTML。
`CfSession.attach(this)`/`detachFrom` 由 MainActivity 驱动；
302→login.php 判定为 `NeedLoginException`（不再被当成写操作成功）。

## 缓存层级

```
请求 ──► web_cache（Room，接口级，TTL：index 10min / 其余 1h）
           │ 网络失败且非 CF → 降级返回旧缓存；CF 异常一律上抛
章节正文 ──► filesDir/download/{aid}/chapter_{cid}.txt（已下载）
           ──► chapter_cache（Room，7 天）
           ──► 网络（成功后写 chapter_cache）
图片 ──► Coil → OkHttp 磁盘缓存（imageClient 自带 UA+Referer）
           下载插图在入队时预热同一缓存，离线命中
阅读进度 ──► reading_history.progress（累计文本字数锚点，决策 C；
             progressPage 仅展示，不作恢复依据）
```

「清除缓存」（设置页）= `client.evictHttpCache()`（OkHttp 缓存 + web_cache）
+ `library.clearCaches()`（chapter_cache / image_cache）。

## 下载引擎

`DownloadEngine`：单发循环（`while` + 轮询 pending），`start()` 于
`WildApp.onCreate`；`wake()` 保证 loop 活着。

- `enqueue(aid, novelName, author, coverUrl, chapters: List<TocChapter>, cids: Set<Int>?)`
  整本（null）或选中章；已有章行保留状态 = 断点续传。
- 章级状态机：0 等待 → 1 成功 / 2 失败（attempts ≤ 3，指数退避）；
  本级状态 0 等待 / 1 完成 / 2 失败 / 3 删除中（`delete` 置 3 → 清文件 → 清行）。
- 文件：`filesDir/download/{aid}/chapter_{cid}.txt` + `cover`。
- 入队时以 `client.imageClient()` 预热章内插图 URL 进 OkHttp 缓存；
  正文 URL 不改写，离线可读性 = 磁盘缓存命中。

## 阅读器

路由 `reader/{aid}/{cid}` 按 `ReaderSettings.readerType` 分流：
`NORMAL` → `PagedReaderScreen`（分页算法 `paginate()`，测高用 TextMeasurer）；
`HTML` → `ScrollReaderScreen`（整章 LazyColumn，`parseBlocks()` 拆文本/插图块）。

- `ReaderViewModel.load()`：先拉目录+详情+历史；目录拉不下来时降级用
  `download_chapter`（status=1）构「已下载章节」单卷目录——离线可读。
- `restoreProgress`：进入章后按累计字数锚点定位（paged 求页、scroll 求块），消费一次清零。
- `record()`：页码/首个可见项变化 → 写 `reading_history`。
- 插图：`<!--image-->URL<!--image-->` → `SubcomposeAsyncImage`，失败回落渐变占位。

## 登录 / 启动

`InitScreen` → `SessionViewModel.init()`：`client.init()`（恢复持久化 cookie + UA）
+ `initSession()`（GET `/` + `/login.php` 种 session/clearance）→
`cookieDao.isLoggedIn()` 分流：已登录 → home（清栈）；未登录 → login。
`LoginScreen`：`/checkcode.php` 字节 → bitmap（可点按刷新）+ `POST /login.php`；
`onLoginSuccess` → home 清栈。书架/账户需登录态，未登录给明确文案。

## 设置持久化

`ReaderSettings`：每属性 `persistedXxx` 委托（mutableState + 防抖 400ms 写
DataStore），`attach(store)` 幂等装载；键名沿用原 App property 表
（`reader_type`/`font_size`/`reader_*_color`/`_volumeControlProperty`…）。
`MainActivity` onCreate `ReaderSettings.attach(settingsStore)`；
音量键经 `ReaderSettings.volumeKeyHandler` 分发到当前阅读器。
