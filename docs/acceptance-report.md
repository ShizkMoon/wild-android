# SZKM-52 验收报告 — Wild 安卓原生复刻

- 验收对象：`ShizkMoon/wild-android` `main` @ `65cbb36`（PR #1/#2/#3 全部合入）
- APK：`app-debug.apk`，`app.wild.android` v0.1.0 (versionCode 1)，debug 签名，27,208,104 B，SHA256 前缀 `3141b8e7`，minSdk 26 / target 37
- 验收日期：2026-09-23；执行：质量与交付核验（独立核验，未参与开发）
- 设备矩阵：Pixel 7 AVD (1080×2400, API 35, 干净 `-wipe-data` 安装)；Pixel Tablet AVD (2560×1600，卸载重装)；Pixel Fold AVD (CLOSED 1080×2092 / HALF / OPENED 2208×1840)
- 环境声明：本机网络下 wenku8.net 的 Cloudflare `cf_clearance` 无法端到端取得（挑战域名仅 IPv6）；站点对 `modules/article/*` 的匿名请求一律 302/403 至登录页。因此登录态功能与列表类页面按「受阻-环境」口径核验，直接路径（详情/正文/目录）用真实数据核验。此为站点与环境限制，非 App 缺陷；相应功能在有登录态/IPv6 网络下需复验。

## 一、四句结论

1. **功能：匿名可达路径完全可用**——启动/登录表单/书籍详情目录/双模式阅读器/进度记录/选章下载/离线阅读/历史/设置/关于共 24 项实测通过；登录态功能（书架簇、账户、签到）与 CF 列表页（推荐/分类内容/排行/完结/评论数据）本机环境受阻，机制代码与错误态已验证。**存在 1 个高严重度缺陷：详情页字段解析错位（作者/状态/更新/简介）**。
2. **界面：达到复刻基准**——与 Stage 3 基准对比图及 spec §2 逐屏结构一致（顶栏/四 Tab/三列网格/底栏、阅读器排版与页码、弹层结构、深浅色双主题）；差异项见 §3.3，均为可忽略或低优先级。
3. **三类形态均正常**——手机底栏/平板与折叠展开 NavigationRail + ListDetailPane 双栏/折叠态底栏单栏全部正确；折叠↔展开↔半展开↔旋转全程无崩溃、无重启、阅读进度按字数锚点保持（2/27→2/28→2/27）。
4. **三类优化有证据支撑**——架构：`docs/architecture.md` 与代码结构逐项一致（MVVM+Koin+Room/DataStore+OkHttp/Jsoup+Coil，决策 A/B/C 均落地）；性能：冷启动实测 1756–2893ms（均值 ≈2.3s，Stage 4 自测 1443ms 同量级），接口缓存/章节缓存/图片磁盘缓存/字数锚点进度均在代码与运行中确认；界面：MD3 双主题+动态取色+宽屏限宽阅读器+宽屏详情窗格回调已接通。**未验证项**：无账号导致书架写操作、签到、账户字段无法端到端；CF 拦截下网格真实数据渲染未验证（错误态已验证）。

## 二、功能清单逐项核验（F1–F42）

| # | 结果 | 证据 | 备注 |
|---|---|---|---|
| F1 启动初始化 | 通过 | `phone/launch_init.png` `launch_landed.png` | 启动图+进度圈→未登录正确落 /login |
| F2 登录+验证码 | 通过 | `login_captcha.png` `login_captcha_refresh.png` `login_error.png` | 验证码渲染+点击刷新换图；错误凭据返回服务端文案「用户名不存在」 |
| F3 登录态持久化 | 部分通过 | Room `cookie` 表 | jieqiVisitId/PHPSESSID 写入正常；登录成功态无账号未验 |
| F4 退出登录 | 通过 | `logout_result.png` | 确认框→cookie 表清空→回 /login |
| F5 注册跳转 | 通过 | `register_result.png` | 弹窗→拉起外部浏览器打开注册页 |
| F6 账户信息页 | 受阻-需登录 | `account.png` | 「未登录」空态正确；20 字段未验 |
| F7 自动签到 | 受阻-需登录 | — | 无法触发 |
| F8 推荐页 | 受阻-站点CF | `home_recommend_final.png` `cfbypass_evidence.txt` | 错误块+下拉重试正常；index.php challenge 84 轮超时 |
| F9 分类浏览 | 受阻-需登录+CF | `home_category.png` | SegmentedButton+标签选择器渲染；数据被拦 |
| F10 排行榜 | 受阻-站点CF | `home_toplist.png` | 13 个 FilterChip 渲染正常 |
| F11 完结列表 | 受阻-站点CF | `home_finished.png` | 同上 |
| F12 无限滚动 | 未验证 | — | 网格无数据无法触发；逻辑在代码 |
| F13 搜索 | 部分通过 | `search_landing.png` `search_result.png` `search_history.png` | 深链带参直达+自动搜；历史写入+点击回填再搜；结果被拦 |
| F14 书籍详情 | **失败** | `novel_2304.png` `intro_zoom.png` | 标题/封面/标签/卷章目录真实数据；**作者显示「[举报/报错]」（应为「镜游」）、状态/更新/简介为空**——解析缺陷，传染历史卡与下载表 |
| F15 评论页 | 受阻-需登录 | `reviews.png` `reviews_result.png` | 标题正确；列表被拦 |
| F16 普通阅读器 | 通过 | `reader_p1.png` `reader_p2.png` | 真实正文、水平分页、n/24 |
| F17 HTML 阅读器 | 通过 | `reader_html.png` `html_tail_shot.png` | 整章滚动+尾部上/下章 |
| F18 点击翻页分区 | 通过 | `reader_p1→p2` | 左/右/中三区行为全部正确 |
| F19 阅读进度 | 通过 | Room `reading_history` | progress=1371 字数锚点+progressPage=4；「继续阅读」恢复正确 |
| F20 目录弹层 | 通过 | `reader_toc.png` | 卷名+章列表+当前章高亮 |
| F21 阅读设置面板 | 通过 | `reader_settings*.png` | 全部滑杆/分段钮/配色/背景图/透明度/重置在且即时生效 |
| F22 自动滚动 | 通过 | `as2_t0.png` vs `as2_t8.png` 像素差分 | 持续滚动+强制全屏 |
| F23 音量键翻页 | 通过 | logcat | VOLUME_DOWN 1/24→2/24 |
| F24 保持亮屏 | 部分通过 | `settings.png` | 两开关在；**滚屏常亮默认 off，spec 要求 on** |
| F25 自定义背景 | 未验证 | `reader_settings3.png` | 设置行存在，未选图实测 |
| F26 全屏阅读 | 通过 | `html_appbar.png` | 点正文切全屏正常 |
| F27–F31 书架簇 | 受阻-需登录 | `bookshelf.png` | 「书架需要登录后使用」正确门控；详情页书架钮返回 403 反馈 |
| F32 CF 绕过 | 部分通过 | `cfbypass_evidence.txt` | 机制在跑：clearance 对 tags.php/search.php 求解成功；index.php 超时+站点仍要登录；**错误文案误标 CF**（实为需登录） |
| F33 选章下载 | 通过 | `download_select.png` | 已下载绿勾不可选/全选/计数正确 |
| F34 后台下载 | 通过 | `files/download/2304/chapter_84800.txt` 18.6KB + DB status=1 | 单章秒下；指数退避+节流在代码 |
| F35 下载列表 | 通过 | `downloads_list.png` | 封面+1/1+「下载完成」 |
| F36 下载详情+离线 | 通过 | `download_detail.png` `offline_reader.png` | 关 wifi+data 后已下载章全文可读 |
| F37 删除下载 | 通过 | `download_delete_dialog.png` `download_deleted.png` | DB+文件双清 |
| F38 主题模式 | 部分通过 | `settings_dark*.png` `dark_*.png` | App 三选生效；**阅读器 auto 跟系统而非 App 主题** |
| F39 API Host | 通过 | `settings.png` | 输入框+重置在 |
| F40 清除缓存 | 通过 | `clear_cache*.png` | web_cache/chapter_cache 表清零+SnackBar |
| F41 更新检查 | 未验证 | `about_update.png` | 点击无任何反馈/无日志（api.github.com 可达） |
| F42 关于页 | 通过 | `about.png` | 图标+版本 0.1.0+更新钮+GPL |

附加实测：历史页卡片+长按删除+继续阅读 ✓；更多页 4 项 ✓；旋转重排按字数锚点（5/24→5/56→5/24）✓；全程 crash buffer 为空。

## 三、界面核验

### 3.1 基准对比
Stage 3 已交付 `cmp_home/cmp_history/cmp_reader/cmp_reader_img` 基准对比（左原 App 右实现），本轮用真实数据版本复核手机/平板/折叠三形态截图（`verify2/`）：顶栏「轻小说文库」+搜索 icon+四 Tab、三列封面网格、详情页封面+统计行+标签 chip+卷章卡、阅读器顶栏+页码+控制栏显隐、登录页验证码结构——均与 spec §2 逐屏规格一致。

### 3.2 深浅色
`settings_dark*.png`/`dark_home.png`/`dark_reader2.png`：App 三选主题（跟随系统/浅色/深色）实测生效，MD3 深色 scheme 正确；阅读器有独立明暗配色，但 auto 档跟随系统 uimode 而非 App 内设置（缺陷 #4）。

### 3.3 差异评级
- **可忽略**：基准图为旧 Flutter 界面截图（内容数据不同属正常）；个别换行差为文案长度差异。
- **需跟进**：详情页字段错位（高）；页码 `n/m` 在满页被挤出屏幕（低，`PagedReaderScreen.kt` `Spacer(weight)` 溢出）；宽屏次级页未限宽（低，仅下载页有 720dp cap）；展开态详情路由无双栏（低）。

## 四、设备矩阵

| 形态 | 导航 | 布局 | 连续性 | 崩溃 |
|---|---|---|---|---|
| 手机 Pixel 7 1080×2400 | 底栏 ✓ | 单栏 ✓ | 旋转重排按字数锚点 ✓ | 0 |
| 平板 Pixel Tablet 2560×1600 | NavigationRail ✓ | ListDetail 双栏（回调已接通）✓ 阅读器 ~640dp 限宽居中 ✓ | 旋转无丢态 ✓ | 0 |
| 折叠 Pixel Fold | 展开 rail/折叠底栏 ✓ | 半展开正常 ✓ | 折叠→展开页码保持（2/27→2/28→2/27），Activity 不重启 ✓ | 0 |

干净实例安装→启动→主流程：手机（wipe-data）与平板（卸载重装）各一次通过。

## 五、优化核验

| 方向 | 证据 | 结论 |
|---|---|---|
| 架构 | `docs/architecture.md` 与代码逐项核对一致：分层/决策 A（HTML 抓取，`img→<!--image-->` 提取在 `Wenku8HtmlSource:406-429`）/B（302+CF 兜底接口）/C（字数锚点 `reading_history.progress`）均落地；MVVM+Koin+Room+DataStore | 成立 |
| 性能 | 冷启动实测 1756/2227/2893ms（均值≈2.3s，含 init 网络）；`web_cache` TTL（index 10min/其余 1h）、`chapter_cache` 7 天、OkHttp 32MB 图片缓存、下载节流 300ms+指数退避 ≤3 次均在代码确认；`assembleDebug`/`lintDebug` 均 0 error | 成立 |
| 界面 | MD3+动态取色（`Theme.kt:79`）、深浅色、宽屏 rail/双栏/限宽阅读器 | 成立 |

残留：`data/mock/WildMock.kt` 文件仍打包在产物中但已无任何引用（死代码，建议清理）。

## 六、已知问题（建议后续 issue）

**高**
1. 详情页字段解析错位：作者显示「[举报/报错]」（实为镜游）、状态/更新/简介为空；传染历史卡/下载卡/`novel_download.author`。复现：`wild://app/novel/2304`。

**中**
2. 无阅读历史时详情页出现幽灵「继续阅读 - 插图」钮（清数据→novel/2304 复现）。
3. 模块页未登录被 302 至 login.php 时错误文案误标「Cloudflare 验证未通过」，掩盖真实语义「需要登录」。
4. 阅读器 auto 主题跟随系统而非 App 主题设置（App 深色+系统浅色时阅读器仍浅色）。
5. 正文插图链路：spec §4.1 已知线上缺陷，抓取层虽补了 `img→<!--image-->` 提取（代码可见），但下载章节文件与运行中未见 image 标记生效，需在有图章节复验。

**低**
6. 深链进入的下载完成/删除后回退栈落到登录页（栈底是 /login）。
7. 「滚屏时保持亮屏」默认 off（spec：on）。
8. 检查更新点击完全静默（无 Toast/日志）。
9. 登录页 IME 顶起时验证码输入框不可见。
10. 宽屏次级页（设置/关于/账户）未限宽；页码 n/m 在满页被挤出（`PagedReaderScreen.kt`）；详情路由展开态无双栏。

## 七、环境受阻项与复验条件

以下项因无 wenku8 账号 + cf_clearance 不可取得而标记「受阻」，非「失败」：F6/F7（账户/签到）、F27–F31（书架簇）、F8–F11 数据区、F15 数据、F12 滚动、搜索数据结果。复验条件：有登录态账号 + 可解析 IPv6 或站点不加 CF 的网络。

## 附：证据索引

`verify2/phone/`（96+ 截图+XML+logcat+DB dump）、`verify2/tablet/`（34 文件）、`verify2/fold/`（9 截图+9 XML+crash.log）；各目录含 `REPORT.md` 明细。
