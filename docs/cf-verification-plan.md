# SZKM-66 · Cloudflare 验证打通与会话持久化 — 根因审计与实施方案

> 状态：方案冻结候选（v1）。产出方：需求与技术方案。实现由后续编码任务按本文执行。
> 审计基准：`main @ 2ad5f04`（2026-09-24 拉取；issue 描述中的 `4db3082` 是其父提交）。
> 本文所有「实测」均为 2026-09-24 经本机代理对 `https://www.wenku8.net` 的真实探测结果。

## 0. 一页结论

现状的 CF 链路（`CfBypass` 隐藏 WebView 解 `cf_clearance` → 回填 OkHttp → 重试一次）**机制本身成立**（上一轮验收中 tags.php/search.php 曾实测求解成功），但有五类缺陷叠加导致「不可用」：

1. **判定器把「拦截」误报成「解成功」**：`challengeGone` 检查不含 `Attention Required` 硬阻断页，且对「挑战过后被 302 到 login.php」的情形判为未过——两个方向的误判都有（§2 RC-1/RC-2）。
2. **站点当前对部分出口 IP 直接回硬阻断页（Attention Required，无 JS 挑战可解）**，隐藏 WebView 再转也拿不到 cookie；此时必须走「可见验证页人工过」降级——**现有代码根本没有这条路**（RC-3）。
3. **CF 守卫覆盖不全**：`login()`、`bookshelfAdd/Remove/Move`、`checkcodeImage()`、`initSession()` 全部绕过 `guard`，命中挑战直接裸抛（RC-4）。
4. **`302→login.php` 语义被错误消费**：写操作把「未登录被踢到登录页」当成功；登录 POST 命中挑战时 UI 只弹错误条——这正是「登录按钮点了没反应/书架页一直弹验证」的直接来源（RC-5/RC-6）。
5. **Cookie 单写点不一致**：内存 map 异步更新、过期不过滤、退出登录不清 WebView 侧 cookie（RC-7）。

方案核心：把 CF 通行升级为一个有明确状态机的 `CfSession` 组件——**隐藏求解 → 可见验证页 → 已验证/失败**三态，`cf_clearance`/session cookie/UA 三者在 WebView↔OkHttp 间双向同步，原请求透明重试；cookie 表补齐 domain/path/expiry 语义并在冷启动恢复。

## 1. 链路现状（代码地图）

```
UI (VM) ──► Wenku8HtmlSource.guard{} ──► CfBypass.guard ──► Wenku8Client.get/post
                                              │ CfChallengeException
                                              ▼
                                     ensureClearance()：1px WebView loadUrl
                                     → 轮询 cookie/JS 标记（45s）
                                     → importWebViewCookies → 重试一次
```

| 组件 | 文件 | 关键行 |
|---|---|---|
| CF 判定 | `data/remote/Wenku8Client.kt` | `isCfChallenge()` L329、`handle()` L294 |
| 隐藏求解 | `data/remote/CfBypass.kt` | `guard` L107、`ensureClearance` L118、`challengeGone` L162 |
| Cookie 库 | `data/remote/Wenku8Client.kt` | `cookieJar` L63、`importWebViewCookies` L185、`clearCookies` L199 |
| UA 分流 | `data/remote/Wenku8Client.kt` | `uaFor` L229（`/index.php`、`/modules/`、`/` → 固定浏览器 UA；其余 → 随机 Dalvik UA） |
| 会话预热 | `data/remote/Wenku8Client.kt` | `initSession` L144（**未过 guard**） |
| 持久化 | `data/local/Entities.kt` `CookieEntity` L8（domain/name/value/expiry，**无 path、无 hostOnly 语义**） |
| 登录 | `data/remote/Wenku8HtmlSource.kt` | `login` L63（**未过 guard**）、`checkcodeImage` L88（未过 guard） |
| 书架写操作 | `data/remote/Wenku8HtmlSource.kt` | `bookshelfAdd` L203、`bookshelfRemove` L231、`bookshelfMove` L242（**全部未过 guard**） |
| 启动链路 | `WildApp.kt` L46（init+initSession 重复于 `SessionViewModel.init`）、`MainActivity.kt` L34（attach WebView） |
| 错误文案 | `ui/vm/ViewModels.kt` | `friendlyError` L33（CfChallenge → 「站点防护验证未通过」） |

## 2. 实测证据（2026-09-24，本机代理出口）

| 探测 | 结果 | 含义 |
|---|---|---|
| `GET /`（Dalvik UA，匿名） | 302 → `http://www.wenku8.net/login.php?jumpurl=…` | 站点把匿名根访问踢向登录页 |
| `GET /login.php`（Dalvik UA） | 200 | 登录页本身不吃 CF |
| `POST /login.php`（测试账号，Dalvik UA） | 200 + `jieqiUserInfo`/`jieqiVisitInfo`/`PHPSESSID` 三个 Set-Cookie | **登录请求本身不被 CF 拦**；`isLoggedIn()` 的 `jieqiUserInfo` 判据成立 |
| `GET /checkcode.php` | 200 image/png 61×18 | 验证码图直连可取 |
| `GET /index.php`（浏览器 UA，匿名） | 403，body 含 `cf_chl`/`Just a moment` | 真·JS 挑战页（可解型） |
| `GET /modules/article/toplist.php`（浏览器 UA 与 Dalvik UA 各一次） | 403 挑战页 | modules 族被拦，与 UA 无关 |
| `GET /modules/article/bookcase.php`（**带登录 session**，浏览器 UA ×1 / Dalvik UA ×3） | **403「Attention Required!」页，body 无 `cf_chl`/`_cf_chl_opt`** | 当前对本出口 IP 是**硬阻断**：无挑战可解，隐藏 WebView 必然超时——与验收报告「index.php 84 轮超时」吻合 |
| `GET /modules/article/addbookcase.php?bid=1`（匿名） | 302 → `login.php` | 匿名写操作被踢登录，不是 302=成功 |
| `GET /modules/article/addbookcase.php?bid=1`（带 session） | 200（未 302） | 已登录写操作可通 |
| DNS | A: `104.21.95.119`,`172.67.144.191`；AAAA: `2606:4700:3031::…` | **域名双栈**，验收报告「挑战域名仅 IPv6」并非 DNS 事实——但模拟器 IPv6 出网仍不可靠，见 §7 风险 |

凭据用法（实现/验收统一）：`U=$(head -1 ~/.config/multica/wenku8-account); P=$(tail -1 ~/.config/multica/wenku8-account)`，凭据不进代码/文档/提交。

## 3. 根因清单

### RC-1 解决判定误判（两个方向都有）

`CfBypass.kt:162-190` `challengeGone` 判定：

```kotlin
"(typeof _cf_chl_opt==='undefined')" +
" && !document.title.includes('Just a moment')" +
" && !document.title.includes('请稍候')" +
" && location.pathname.indexOf('login')<0"
```

- **误判为已解决**：`Attention Required!` 硬阻断页没有 `_cf_chl_opt`、标题不在排除表、路径不含 login → `challengeGone=true` → `solved=true` → 重试仍 403 → 抛错。**表现：一次拦截 ≈ 等满 45s 轮询或立刻假成功再失败，UI 只看到「验证未通过」。**
- **误判为未解决**：匿名身份过了挑战后被站点 302 到 `login.php`（modules 页匿名即如此），`pathname.indexOf('login')<0` 让 `challengeGone` 永假；若 `cf_clearance` cookie 恰未在轮询窗口写入 → 45s 超时。验收报告「index.php challenge 84 轮超时」即此路径。

### RC-2 硬阻断页无可解挑战，却无降级出口

`Attention Required` 是 IP/指纹级阻断，无 JS 挑战对象。现有代码对它：判定 `isCfChallenge` 命中（`Attention Required` 在 403 检查表内）→ 走 WebView 求解 → RC-1 假成功/超时。**从未出现「让用户手动过一次」的可见验证页**——父 issue 明确要求这条降级链路，当前缺失。

### RC-3 guard 覆盖缺口（登录/写操作裸奔）

`Wenku8HtmlSource` 内不经 `guard{}`/`fetch` 的调用：

| 方法 | 行 | 调用 | 后果 |
|---|---|---|---|
| `login` | L68 | `client.post("/login.php")` | login.php 若被挑战 → `CfChallengeException` 穿透 `runCatching` → 上层只弹错误条；叠加 RC-6 表现为「点了没反应」 |
| `checkcodeImage` | L89 | `client.getBytes` | 验证码被挑战时静默回落占位图，用户拿不到真验证码 |
| `bookshelfAdd` | L204 | `client.getResponse` | 403/挑战直接失败，永不求解 |
| `bookshelfRemove` | L232 | `client.getResponse` | 同上 |
| `bookshelfMove` | L250 | `client.postMultiValue` | 同上 |
| `initSession` | L60→`Wenku8Client:144` | `client.get("/")` + `get("/login.php")` | `/` 走浏览器 UA，若被挑战，`runCatching` 吞掉——**session 未种上且无人知道** |

### RC-4 `302→login.php` 被当成成功

`bookshelfAdd`（L209）与 `bookshelfRemove`（L237）/`bookshelfMove`（L262）把 `r.code==302` 一律视为成功——实测匿名 `addbookcase.php` 返回的正是 `302→login.php`。结果：**未登录/掉登录态时点「加入书架」提示「已加入书架」的假成功**。同理 `handle()` 的 302 直通（L303）。

### RC-5 「登录按钮无响应」的直接成因

`SessionViewModel.login` → `source.login` → `client.post`：
- 若 `login.php` 被挑战（RC-3）：异常经 `runCatching` → `friendlyError` → snackbar「站点防护验证未通过」。用户看到按钮转圈结束后只弹一句文案，观感=「点了没反应/登录不了」。
- 若出口 IP 被硬阻断：`post` 返回 403 挑战页 → `body.contains("登录成功")` 为假 → 「登录失败：…」文案同样误导。
- 真正的修复=login 纳入 CF 守卫 + 挑战时先解再 POST（见 §5 D-3）。

### RC-6 Cookie 单写点不一致

- `saveFromResponse`（`Wenku8Client:74`）在 `scope.launch` 里**异步**更新内存 map——紧跟着的请求可能读不到刚下发的 `PHPSESSID`/`jieqiUserInfo`（登录后立刻 `userDetail` 易踩）。
- `loadForRequest` 不查 `expiryEpochMs`：`cf_clearance`（站点给 ~15min-2h）过期后仍被发送 → 反复挑战。
- `importWebViewCookies` 把 WebView cookie 以 `domain=host`、expiry=0 入库，`cf_clearance` 实际可能带 `.wenku8.net` 主域；且 `expiry=0` 与「session cookie」语义混淆。
- `clearCookies()`（L199）只清 Room+内存，**不清 `CookieManager`**——退出登录后 WebView 仍带旧 `jieqiUserInfo`/`cf_clearance`（跨账号泄漏 + 旧 clearance 污染下一次求解）。
- 种 cookie 到 WebView（`CfBypass:134`）用 `cm.setCookie(host,"n=v")` 后 `cm.flush()` 不保证与随后的 `loadUrl` 有序（flush 异步落盘，但内存态一般即时；建议保留 flush 且给 setCookie 带 domain/path 显式参数）。

### RC-7 UA 一致性缺口

`cf_clearance` 按 UA+IP 绑定。`uaFor` 只让 `/index.php`、`/modules/`、`/` 用固定浏览器 UA；若 CF 升级对 `/book/`、`/novel/` 也上墙（Dalvik UA 请求），手持的 clearance 立即失效且 WebView 解出的新 clearance 仍绑浏览器 UA → 死循环。需要「同域一律同一 UA」的单一事实源（见 §5 D-2）。

### RC-8 启动链路小毛病（非阻断，一并修）

- `WildApp.onCreate` 与 `SessionViewModel.init` 重复 `client.init()`+`initSession()`（幂等但浪费）。
- `ensureClearance` 45s 超时偏短：真实 Turnstile/Managed Challenge 在弱网模拟器上可 >45s。
- WebView 尺寸 1px：`evaluateJavascript` 在纯软件渲染/无 GPU 的 AVD 上有不执行风险（罕见，纳入降级考虑）。

## 4. 目标链路

```
                    ┌───────────────────────── CfSession（单例，Koin）─────────────────────────┐
                    │  状态机：UNKNOWN → SOLVING_HIDDEN → SOLVED │ NEEDS_USER → SOLVING_VISIBLE │
                    │            → BLOCKED(ip)                   │  → SOLVED / GAVE_UP          │
                    └──────────────────────────────────────────────────────────────────────────┘
 Wenku8HtmlSource 全部入口（读+写+login+checkcode）
        │ 统一走 guard{}
        ▼
 Wenku8Client.execute()
        │ isCfChallenge / 302→login 判定（统一在 ResponseClassifier）
        ▼ 命中挑战
 CfSession.solve(url)：Mutex 串行
   1) 隐藏 WebView 求解（60s 超时，判定=「cf_clearance cookie 在 CookieManager 出现」唯一标准）
   2) 成功 → syncCookies(W→O) → 原请求透明重试 → 仍被拦 → 进 NEEDS_USER
   3) 隐藏失败/超时/Attention Required → NEEDS_USER
        ▼ NEEDS_USER
 全屏 CfVerifySheet（可见 WebView + 顶栏说明 + 「我已通过」/重试）
   用户通过后 JS 回呼/轮询 cookie → syncCookies(W→O) → 重试原请求
   用户放弃 → GAVE_UP → 错误态（给「重试验证」入口）

 Cookie 同步（双向、domain/path/expiry 保真）：
   WebView CookieManager ◄── seed(host) ── SessionStore（Room cookie 表 + 内存 map）
        │                                        ▲
        └── harvest：getCookie(host)+getCookie(根域) ──┘  saveFromResponse 同步写内存
```

关键原则：

- **解决判据唯一化**：只认 `cf_clearance` 出现在 CookieManager（host 或父域），不再用 DOM 标记反推；挑战页 DOM 只用于「识别仍在挑战中」。
- **挑战分类**：`403+Attention Required` → `BLOCKED_IP`（隐藏无解，直接进可见页让用户试一次；仍败则报错说明）；`403/503+_cf_chl_opt|cf_chl|Just a moment` → 可解挑战；`302→login.php` → `NEED_LOGIN`（不再是 CF 语义）。
- **原请求透明重试上限 2 次**：第一次重试仍被拦说明 clearance 未被采信（UA/IP/指纹不匹），直接升级可见验证，不再空转。

## 5. 改动点清单（主力编码按此施工）

### D-1 新组件 `data/remote/CfSession.kt`（替代/重构 `CfBypass`）

```kotlin
sealed interface CfState { object Unknown; object SolvingHidden;
    object NeedsUser; object SolvingVisible; object Solved;
    data class Failed(val reason: String) : CfState }

class CfSession(client, sessionStore, dispatcher) {
    val state: StateFlow<CfState>
    suspend fun <T> guard(block: suspend () -> T): T   // 唯一入口，含 2 次透明重试
    suspend fun onUserVerified()                        // 可见页回调
    fun attach(activity); fun detachFrom(activity)      // 语义同今 CfBypass
}
```

- 隐藏 WebView：保留 1px attach 到 decorView，但**判定改为轮询 `CookieManager.getCookie(host)` 与 `getCookie(".wenku8.net")` 含 `cf_clearance=`**；同时轮询 `document.title` 仅作日志。超时 60s。
- 种子：`cm.setCookie("https://host/", "$n=$v; Domain=host; Path=/")` + `cm.flush()` 后 `loadUrl`。
- 可见降级：`ui/screen/CfVerifyScreen.kt` 新路由 `cf-verify`：全屏 WebView 加载触发页 + 顶部说明条 + 「我已完成验证」按钮；通过判定同上（cookie 出现）+ JS 注入轮询回调 `onUserVerified`；用户点「完成」且未拿到 cookie → 停留并提示「仍未检测到通过，请再试或稍候」。
- 触发可见页的途径：`state==NeedsUser` 时 UI 层统一弹（在 `WildNavShell` 监听 CfSession.state → navigate("cf-verify")，或在各错误块给「打开验证页」按钮——取前者，简单一致）。

### D-2 `Wenku8Client` 改造

- **UA 单一事实源**：`uaFor(host, path)` → 同域（`wenku8.net` 及子域）一律 `DEFAULT_UA`（浏览器 UA）；仅图片域名（`img.wenku8.com` 等 CF 不拦的）可用 App UA。删除「按路径分 UA」逻辑——实测 UA 分流收益不确定，而 clearance 绑 UA 的代价是确定的。
- **CookieJar 同步写内存**：`saveFromResponse` 中 `cookies[key]=entity` 移出 `scope.launch`（内存先写，Room 落库仍异步）。
- **过期过滤**：`loadForRequest` 过滤 `expiryEpochMs>0 && <now`；`init()` 同样过滤并顺手删除过期行。
- **`CookieEntity` 增列 `path`（默认 `/`）与 `hostOnly`（Bool）**：DB `version 2→3`，`fallbackToDestructiveMigration` 已开，直接重建即可；`importWebViewCookies` 用 `Cookie.parse(HttpUrl, header)` 或显式记 `Domain=.wenku8.net` 语义。
- **`handle()` 判定顺序修正**：先判 `302→login.php`（`resp.code in 301..303 && location 含 login.php` → `NeedLoginException`），再判 CF，再 302=成功。`getResponse/postResponse/postMultiValue` 的调用方（书架写操作）按同一顺序检查 `Location`。
- **`clearCookies()` 追加 `CookieManager.removeAllCookies{}`+`flush()`**（主线程，包 `withContext(Main)`）。
- `initSession` 加守卫：**`/` 与 `/login.php` 均经 `guard{}`**（放 `Wenku8HtmlSource.initSession` 包一层即可，client 侧不变）。

### D-3 `Wenku8HtmlSource` 改造

- `login`/`checkcodeImage`/`bookshelfAdd`/`bookshelfRemove`/`bookshelfMove` 全部改经 `guard{}`/`fetch`；写操作先判 `Location` 含 `login.php` → `NeedLoginException`。
- `bookshelfClasses` 里的 `guard { client.initSession() }` 改为 `cfSession.ensureWarmed()`（幂等，已种过 session 直接返回）。

### D-4 会话持久化

- 维持 Room `cookie` 表为唯一持久面（已满足「冷启动不重复弹验证」的核心：cookie 在、UA 固定、IP 未变 → clearance 直接可用）。**不引入加密存储**：`jieqiUserInfo` 已含密码哈希，加密收益 vs 复杂度不划算；如需加固另开任务评估 Tink。
- DataStore 仅存 `cf_clearance_obtained_at`（调试用）与上次已知出口提示（可选）；`SettingsStore.userAgent` 已有，保留。
- 冷启动：`client.init()`（恢复 cookie+UA，过滤过期）→ `initSession`（经 guard）。UI 分流不变。

### D-5 UI/导航接线

- `WildNavShell`：`CfSession.state` collect → `NeedsUser` 时 `navigate("cf-verify")`（防重入：当前已是该路由则跳过）；`Solved` 时自动 `popBackStack`。
- `friendlyError`：`CfChallengeException` 文案细化——区分「已自动过验证仍被拦，点重试打开验证页」与 `NeedLoginException`。
- 书架/登录等错误块加「打开站点验证」按钮（deep link `wild://app/cf-verify`）。

### D-6 探测与日志

- 保留 `CfBypass` 现有 Log.d 标签体系迁到 `CfSession`；新增「solver 决策点」日志：challenge 分类、隐藏求解结果、可见页结果、重试成败——验收时 `adb logcat -s CfSession:*` 可逐条对。

## 6. 关键流程覆盖清单

| 流程 | 现状 | 改造点 | 验收判据 |
|---|---|---|---|
| 冷启动 | init/initSession 各跑两遍；`/` 挑战被吞 | D-2 guard 化 + 幂等 | 清数据装包 → 启动 → 书架/登录链路正常；logcat 见 `CfSession` 状态流 |
| 登录 | login/checkcode 无 guard；挑战=裸错误 | D-3 | 测试账号登录成功进主页；cookie 表有 `jieqiUserInfo`；杀进程重进免登录 |
| 书架同步 | 读走 guard 但判定误判；写无 guard | D-1/D-3/D-4 | 书架分类+列表真实数据；加/移/移动操作返回真语义（含「需要登录」而非假成功） |
| 详情/目录/正文 | `/book` `/novel` 直连，不走 CF | UA 统一后复查 | 详情字段、目录、正文正常；插图 `<!--image-->` 渲染 |
| 收藏/书签 | addbookcase 无 guard + 302 误判 | D-3 | 详情页书签两态往返，书架可见 |
| 在线阅读 | 经 `ReaderContentSource` → `source.chapterText`（fetch 已走 guard） | 无 | 正文加载 |
| 离线阅读 | 下载文件→chapter_cache 已在 | 无 | 断网开已下载章可读 |
| 搜索/排行/分类/书评 | fetch 走 guard | D-1 判定修正 | 列表返回真实数据或明确「打开验证页」 |
| 退出登录 | CookieManager 残留 | D-2 | 登出后 WebView cookie 清空 |

## 7. 验收口径（模拟器可复现）

环境：Pixel 7 AVD API 35（与上轮验收一致），**宿主网络需能出网到 `104.21.95.119`/`172.67.144.191`**；若 AVD 无 IPv6 或出口被 CF 硬阻断，至少「可见验证页」降级必须人工可走通——这本身就是验收项。

1. **冷启动恢复**：装包→登录→进书架（触发 CF 求解）→杀进程→重进→直接进主页且书架秒开（无二次验证）。
2. **隐藏求解**：书架/排行任一页首次命中挑战时，logcat `CfSession` 见 `SolvingHidden→Solved` ≤60s，页面出数据，无人工介入。
3. **可见降级**：制造硬阻断环境（出口 IP 被拦/代理切换后 IP 漂移）→ 命中挑战 → 自动弹出全屏验证页 → 用户过一次 → 页面自动回退且原请求成功。
4. **登录**：`U=$(head -1 …); P=$(tail -1 …)` 真账号登录 → 成功；故意错密码 → 「密码错误」类文案（非 CF 文案）。
5. **写操作真语义**：未登录态直接调 `addbookcase`（深链+清 cookie）→ 报「需要登录」而非「已加入书架」。
6. **过期 cookie**：手工把 `cf_clearance` 行 expiry 改为过去 → 下次请求自动重解一次即恢复，不死循环。
7. **回归**：`assembleDebug` + `lintDebug` 0 error；既有验收矩阵（F14 详情/F16-17 阅读器/F33-37 下载）不回归。

## 8. 风险与降级边界

| 风险 | 说明 | 对策 |
|---|---|---|
| CF 指纹校验升级（JA3/TLS 绑 clearance） | OkHttp 的 TLS 指纹≠WebView Chromium；若站点要求一致，cookie 重放失效 | 兜底降级：挑战页请求本身改经 WebView 取 HTML（`evaluateJavascript(document.documentElement.outerHTML)`），不依赖 cookie 重放；列为「若重试仍拦」的自动第三选择 |
| `Attention Required` 纯 IP 阻断 | 当前实测即此态 | 可见页人工过一次仍可能失败——失败文案如实说「出口 IP 被站点标记，建议切换网络」；不做无限重试 |
| 模拟器 IPv6 | 域名双栈，IPv4 可达；AVD IPv6 出网视宿主而定 | 文档化「验收需 IPv4 可达 wenku8.net」；`apiHost` 设置项保留以切镜像 |
| CF 策略变化（挑战页 DOM/标题改名） | 判定脚本会变脆 | 判据收敛到「cookie 出现」单点，DOM 只做日志；`isCfChallenge` 词表留可扩展位 |
| WebView 缺失/残疾设备 | 极少数设备无 System WebView | `attach` 检测 `webView==null` 时直接 `Failed("设备无 WebView")`，错误页给出外部浏览器打开链接的提示 |
| 验证码服务端未来真校验 | 当前 checkcode 不校验照发 | 保持字段发送；若回归校验，checkcode 已有真实图片链路 |

## 9. 不做的事（边界）

- 不引入 flaresolverr/外部代理解 CF 服务；不换 HTTP 栈（OkHttp 足够）。
- 不重构解析层/下载引擎；`data/mock/WildMock.kt` 死代码清理留给实现任务顺手做（不阻塞本方案）。
- 凭据不落任何文件/提交；测试账号仅经 `~/.config/multica/wenku8-account` 运行时读取。

## 10. 交接输入（实现者开工即可用）

- 目标文件：`data/remote/CfSession.kt`（新）、`data/remote/CfBypass.kt`（并入或删除）、`data/remote/Wenku8Client.kt`、`data/remote/Wenku8HtmlSource.kt`、`data/local/Entities.kt`+`Daos.kt`（cookie 表 +2 列）、`data/local/WildDatabase.kt`（v3）、`ui/screen/CfVerifyScreen.kt`（新）、`ui/navigation/WildNavShell.kt`、`ui/vm/ViewModels.kt`（friendlyError/登录错误分流）、`di/AppModule.kt`、`MainActivity.kt`、`WildApp.kt`。
- 接口契约：`CfSession.guard`/`state`/`onUserVerified`（§5 D-1）；判定分类 §4 原则；302→login 语义 §5 D-2。
- 验收：§7 七条。
