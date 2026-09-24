# SZKM-67 界面重构规格：几何修复 + M3E Surface 层级 + Spring 动效

> 适用仓库：`ShizkMoon/wild-android`（Kotlin + Compose BOM 2026.09.00 + material3 1.4.0 + adaptive 1.3.0）
> 性质：**实施规格**。本文不改代码；所有条款给出可在现有依赖内编译的 API 依据，主力编码按 §5 逐屏清单执行即可。
> 引用行号以 main `4db3082` 为准。

## §1 API 基线（已按 Gradle 缓存实际解析版本核对）

| 依赖 | 实际解析 | 关键事实 |
|---|---|---|
| `compose-bom:2026.09.00` | animation/foundation/ui **1.12.1**，material3-android **1.4.0** | material3 与目录 pin 一致，非降级 |
| `SharedTransitionLayout` / `Modifier.sharedElement` / `sharedBounds` / `animateBounds` | `androidx.compose.animation:1.12.1` | **稳定，无 ExperimentalSharedTransitionApi opt-in**；`SharedTransitionScope` 即 `LookaheadScope` |
| navigation-compose | **2.9.4** | `composable()` **无** sharedElement 参数；集成方式：NavHost 外包 `SharedTransitionLayout`，在 `composable { … }` 内用 `Modifier.sharedElement(rememberSharedContentState(key), animatedVisibilityScope = this)`（`AnimatedContentScope` 即 `AnimatedVisibilityScope`） |
| 预测性返回 | navigation-compose 2.9.4 + targetSdk 37 | NavHost 内部已接 `PredictiveBackHandler` + `SeekableTransitionState`；targetSdk ≥36 时系统侧默认开启，**无需也不应再加** `enableOnBackInvokedCallback`（该 flag 现在是 opt-out）。约束：自定义转场必须是可被手势搓动的动画（见 §4.3） |
| M3 Expressive | material3 1.4.0 | `MaterialExpressiveTheme(colorScheme, motionScheme, shapes, typography)`、`MaterialTheme.motionScheme`、`MotionScheme.expressive()/standard()`、`WideNavigationRail`、`ListItem`（`ListItemType`）全部**稳定**；⚠️ `FloatingActionButtonMenu`、`FloatingToolbar`、`LoadingIndicator` **不在 1.4.0 构件里**（只有 token 类），规格中不得引用 |
| Spring | animation-core 1.12.1 | `spring(dampingRatio, stiffness, visibilityThreshold)`；预设 `Spring.DampingRatio{NoBouncy,LowBouncy,MediumBouncy,HighBouncy}` × `Stiffness{VeryLow,Low,MediumLow,Medium,High}` |
| 列表项动画 | foundation 1.12.1 | 统一 API 为 `Modifier.animateItem(fadeInSpec, placementSpec, fadeOutSpec)`；**`animateItemPlacement`/`animateItemFade` 已不存在** |
| `NavigationSuiteScaffold` | adaptive-navigation-suite 1.4.0 | 稳定；`NavigationSuiteType` 为 String value class，含 `ShortNavigationBar{Compact,Medium}`、`WideNavigationRail{Collapsed,Expanded}`；可传 `navigationSuiteColors`/`containerColor` |
| `PullToRefreshBox` | material3 1.4.0 | 稳定，无需 opt-in |

## §2 缺陷清单（按严重度聚合）

严重度：**P0** 视觉可见的功能/显示错误；**P1** 明显不达 M3/M3E 水准；**P2** 打磨项。

### P0 — 显示错误（必须修）

| # | 位置 | 缺陷 |
|---|---|---|
| P0-1 | `ui/screen/HomeScreen.kt:262` | 推荐网格 `take(6).chunked(3)` 硬编码 3 列，忽略 `adaptiveGridColumns()`；宽屏双栏里封面被拉稀，与全站密度不一致 |
| P0-2 | `ui/screen/Screens.kt:324-327` | 历史页下拉刷新 `onRefresh = { refreshing = true; refreshing = false }` 同步置回——纯摆设，不触发加载 |
| P0-3 | `ui/screen/LoginScreen.kt:96-99` | `verticalScroll` + `Arrangement.Center` 组合：内容高于视口时顶部被推到负偏移且不可达（矮屏/横屏/大字体下表单顶部永久裁掉） |
| P0-4 | `ui/screen/DownloadsScreen.kt:78-82` | 下载状态色整套硬编码 Material2 色板（0xFF2196F3/4CAF50/F44336/FF9800），绕开 `colorScheme`，深色下对比不达标 |
| P0-5 | `ui/reader/ReaderSheets.kt:317-321` | 所有 `SliderRow` 把 `steps` 当"区间个数"传（14..24 steps 10 等），实际语义是"端点间分段数"——滑杆停在分数值上，永远选不中标签显示的整数；多处默认值不在自己的刻度上，首次拖动必跳变 |

### P1 — 几何 / insets

| # | 位置 | 缺陷 |
|---|---|---|
| G-1 | `ui/reader/PagedReaderScreen.kt:381,390,422,433` | `rightPad` 参数死代码：`padding(horizontal = leftPad)` 左右同值；分页测量用 `w-L-R`、渲染用 `w-2L`——`L>R` 时文本右缘被裁 |
| G-2 | `PagedReaderScreen.kt:393-399` vs `Pagination.kt` | 页一首段渲染时 `fontSize+2`+Bold，分页测量按正文字号——页一实际更高，末行可溢入页码区 |
| G-3 | `PagedReaderScreen.kt:327` | 覆盖层顶栏 `.statusBarsPadding()` 叠在 Scaffold `padding` 之上，状态栏 inset 被消费两次，黑条 ~2×statusbar+64dp；且与 `topBarHeight` 预留的 56dp 不匹配，正文首行会被压进覆盖栏下 |
| G-4 | `PagedReaderScreen.kt:148` vs `407-409` | `counterPx` 预留估算比实际页码高度多 ~4-5dp → 提前翻页，页底留白不均 |
| G-5 | `ui/reader/ScrollReaderScreen.kt:261-266` | `contentPadding(bottom = bottomBarHeight)` 覆盖丢弃了 `padding.calculateBottomPadding()` 的导航条 inset——正文与翻页行滚到系统导航条下 |
| G-6 | `ui/reader/Pagination.kt:51-58` | 空页放不下单段时强行整段写入 → 横屏/矮视口下整页溢出版心 |
| G-7 | `LoginScreen.kt:92-97`、`SettingsScreens.kt:192-200`、`SearchScreen.kt:108-126` | 无 `imePadding()`，edge-to-edge 下输入框/按钮可被键盘遮挡 |
| G-8 | `ui/screen/InitScreen.kt:78` | 进度指示 `padding(bottom=48.dp)` 无导航条 inset 感知；`:55-74` Image 无 `fillMaxSize`/尺寸约束，宽屏 letterbox 露裸窗口背景 |
| G-9 | `ui/components/Common.kt:201-209` | `ErrorBlock` 无 `modifier` 参数——全站 5 处调用（Screens.kt:169、NovelInfoScreen.kt:138、SearchScreen.kt:169、ReviewsScreen.kt:97、DownloadSelectScreen.kt:127、SettingsScreens.kt:366）错误块画进 TopAppBar 之下 |
| G-10 | `HomeScreen.kt:400-403`、`Screens.kt:146-149` | `LazyRow` 把 `padding(horizontal=16)` 打在容器而非 `contentPadding`，首尾 chip 滚不到屏幕边、被硬裁（复制粘贴缺陷两处） |
| G-11 | `HomeScreen.kt:324-330` | 分类选择器自绘 Row+clip+border，`vertical=8dp` → 触控高约 36dp <48dp |
| G-12 | `Screens.kt:204-213` | 书架多选圆勾 `.size(24dp).clickable` → 触控 24dp，远低于 48dp 下限 |
| G-13 | `NovelInfoScreen.kt:293-301,315-325,262` | 统计项/标签 chip/作者链接全是 ~20-30dp 高的裸 `clickable` 文字行 |
| G-14 | `ReaderSheets.kt:368-374` | 色板 `.size(48dp).padding(4dp)` 后接 `clickable` → 实际触控 40dp；无色名 contentDescription |
| G-15 | `Screens.kt:331-334`、`DownloadsScreen.kt:136-139` | 历史/下载列表无 `contentPadding` 底部余量且 `items()` 无 key，末卡贴导航条 |
| G-16 | `ReviewsScreen.kt:84`、`NovelInfoScreen.kt:398`、`DownloadsScreen.kt:106-116,158` | 长标题/作者名/章节名无 `maxLines`+ellipsis，溢出或任意撑高 |
| G-17 | `PagedReaderScreen.kt:455-459` | `MockIllustration`：`background(RoundedCornerShape(8dp))` 只裁剪背景层，Canvas 的渐变矩形直角绘制盖过圆角——形状与底色双重失效；`:454` seed 计算后未使用（死代码） |
| G-18 | `ui/reader/ReaderSheets.kt:155-160,284-285` | 设置/目录 sheet 仅 `padding(bottom=16)`，无 `navigationBarsPadding`——重置按钮/末项可压手势条 |
| G-19 | `ui/screen/SettingsScreens.kt:309`（+`Common.kt:309`）| `InfoRow` 固定 80dp 标签列，服务器下发的长 key 会换行/顶值 |

### P1 — Surface / 层级

| # | 位置 | 缺陷 |
|---|---|---|
| S-1 | `PagedReaderScreen.kt:326-347` | 阅读器顶栏 `Color.Black.copy(alpha=0.7)` + 硬编码白图标——不随阅读器主题（白/米/深）变化，浅色主题下黑条突兀 |
| S-2 | `ScrollReaderScreen.kt:221-226` | AppBar `containerColor = bg.copy(alpha=0.8)` 任意 alpha，非 tonal 层级，且静止/滚动同色 |
| S-3 | `DownloadsScreen.kt:247`、`Screens.kt:375` | `color.copy(alpha=0.1f/0.3f)` 自调透明度当容器色——应映射 `*Container`/`on*Container` 配对 |
| S-4 | `Screens.kt:215-217` | 多选圆勾 `Color.White` + `Color.Gray` 边框硬编码，深色下失控 |
| S-5 | `CaptchaImage.kt:28,41,34` | 底板 0xFFF5F5F5、字形 0xFF3E5F8A、干扰线 `0x33000000+rnd` 全硬编码；深色主题下亮块悬浮 |
| S-6 | `Theme.kt:20-64` | 自定义 scheme 未声明 `surfaceContainer*`/`outline*`/`surfaceTint`，且 `dynamicColor=true` 默认使整套 WildSeed 配色在 API31+ 为死代码 |
| S-7 | `Theme.kt:96-99` | `MaterialTheme` 只传 colorScheme——无 typography/shapes/motion 令牌，全站默认形状 |
| S-8 | `ui/theme/Theme.kt:89-92` | 状态栏图标色只随 App 明暗：阅读器自有深色主题 + App 浅色时，深图标画在 `#1A1A1A` 上不可见（需 per-screen 控制钩子） |
| S-9 | `Common.kt:117-144` | 封面占位：楔形 Path 左缘收成 0 高度读作任意多边形；白字压在浅金/浅青等亮调色板上对比不及格 |
| S-10 | `DownloadsScreen.kt:162-167` | 下载进度只有「x/y 章节」文字，无 `LinearProgressIndicator`——核心状态无视觉载体 |

### P1 — 动效 / 交互

| # | 位置 | 缺陷 |
|---|---|---|
| M-1 | `ui/navigation/WildNavShell.kt:155-158` | NavHost 未声明任何 enter/exit/pop 转场——全站默认 fade，无 M3 空间感 |
| M-2 | 全站 | 无任何 shared element：封面图列表→详情→阅读器的主视觉链路是纯切换 |
| M-3 | `PagedReaderScreen.kt:239,252,213,219,180` | 点按翻页/音量键/章跳全走 `scrollToPage` 瞬跳——除手指拖拽外零翻页动画 |
| M-4 | `PagedReaderScreen.kt:321`、`ScrollReaderScreen.kt:200` | 阅读器控制栏/AppBar `if(show)` 硬挂载——瞬现瞬隐无 slide/fade |
| M-5 | 全站列表 | 零 `animateItem`/`AnimatedContent`/`animateContentSize`：tab 切换、增删、状态翻转全部瞬切 |
| M-6 | `HomeScreen.kt:236-240`、`Screens.kt:172` | 下拉刷新指示器"一闪即灭"/恒 false——同步置回或写死，刷新期无反馈 |
| M-7 | `ScrollReaderScreen.kt:152` | 自动滚动 `scrollBy(speed)` 每 8-32ms 离散跳像素——阶梯感，非平滑插值 |
| M-8 | `InitScreen.kt:48-52,63-73` | 开屏 `delay(400)` 后硬切；DstIn 底部羽化是静态遮罩——零开场动效 |
| M-9 | `ReaderSheets.kt:166-179` | sheet 内切 `readerType` 对已路由页面无即时效果且无反馈 |
| M-10 | `Screens.kt:185-229`、`DownloadsScreen.kt:140-172` | 多选圆勾、下载状态变化瞬现——无 `AnimatedVisibility`/颜色过渡/`animateItem` |

### P2 — 打磨项（抽样，实施时顺手修）

- `Screens.kt:139` 用 `SelectAll` 图标表示"进入多选"语义错位；`MoreScreen:462-467` `ChevronRight` 是 iOS 语汇
- `SearchScreen.kt:93-103` 类型选择器塞进 TopAppBar actions 挤压 title；`:97` 切类型清空关键词；无清除钮、历史无管理入口；`:137-147` 历史项整行染强调色层级过载、filled 图标与全站 outlined 不一致
- `NovelInfoScreen.kt:147-172` 双栏无分隔间距全靠内部 padding；`:361-376` 简介 `14.sp` 硬编码；`:390-400` 整卷平铺无折叠动画
- `ReviewsScreen.kt:66` 刷新指示绑定 loading 派生 var，loadMore 时误亮；`:118-126` 时间未降 `onSurfaceVariant`
- `PagedReaderScreen.kt:236-257` 不可见 L 形点按区无语义等价；`:408,445` 页码 alpha 0.3 远低于可读性；正文无 `SelectionContainer`
- `ReaderSheets.kt:315` 滑杆标签固定 110dp 会裁长标签；`:106` 当前章定位贴顶不居中；`:71,126` `Color.Transparent` 哨兵色 + `Gray 0.3` 高亮非 scheme 色
- `LoginScreen.kt:134-136` spinner 塞满 200×50 框；`:147` 验证码 PNG letterbox 无描边；`:113-125` 无 imeAction 链
- `Common.kt:158` `abs(MIN_VALUE)` 负数索引崩溃边界；`:172` `Modifier.clickable` 应用 `Card(onClick=)` 重载拿正确语义/涟漪；`:189` 标题 4dp 内边距过挤
- `ScrollReaderScreen.kt:301-309` 章导航 Text 显式 color 绕过 disabled alpha；`:284` 插图统一 4:3 letterbox；全屏无进度指示
- `SettingsScreens.kt:171-182` Row+RadioButton 双重可点语义；`:201-206` 有 onDone 无 `imeAction=Done`（死代码）；`:466` 版本号硬编码
- `Pagination.kt:39` 丢弃作者空行；`:33` 图文同行时文字丢失
- `ReaderSettings.kt:83-85` `volumeKeyPaging`/`keepOnReading`/`keepOnScroll` 已接线但 UI 无入口——死功能

## §3 设计令牌规格

### 3.1 形状方案（`WildShapes`）

声明 `Shapes` 并传入主题（见 §6.1），全站统一节奏——小件紧、大件圆：

| token | 值 | 本 App 映射 |
|---|---|---|
| `extraSmall` | 4dp | 状态胶囊/徽章（下载状态、书签标记） |
| `small` | 8dp | 验证码底板、chip/标签（改 AssistChip/InputChip 后取默认） |
| `medium` | 12dp | **封面卡、信息卡、设置卡、下载卡**（替换现 4dp） |
| `large` | 16dp | 大按钮（登录/立即阅读） |
| `extraLarge` | 28dp | ModalBottomSheet、AlertDialog（M3 默认，勿改） |
| 直边 | 0 | 阅读器正文版心、全屏插图 |

`NovelCoverCard` 现 `RoundedCornerShape(4dp)`（`Common.kt:173`）是旧 App 复刻残留——升到 `medium`；标题内边距 4dp→8dp。

### 3.2 留白栅格（间距一律取 4 的倍数）

| 场景 | 值 |
|---|---|
| 页面内容水平边距 | 16dp |
| 卡内边距 | 12dp（文本密集行 8dp） |
| 卡片/行项间距 | 8dp（分组标题前 24dp） |
| 网格间距 | 8dp（保持），网格外边距 8dp→**12dp** |
| 触控目标 | ≥48dp（硬性下限，修 G-11/12/13/14） |
| 列表底部余量 | `contentPadding` 末项 +导航条 inset（修 G-15） |

**Insets 统一方案**：① `ErrorBlock`/`LoadingBlock`/`EmptyBlock` 增加 `modifier` 参数，所有调用点传 `Modifier.padding(padding)`（修 G-9）；② 列表 `contentPadding` 合并 `padding`（`PaddingValues(bottom = padding.calculateBottomPadding() + N)`），不得覆盖丢弃（修 G-5）；③ 阅读器覆盖栏去掉叠加的 `statusBarsPadding()`，改为在 Scaffold 之外自绘并自行消费一次 inset（修 G-3）；④ 表单页统一 `imePadding()`（G-7）；⑤ sheet 内容加 `navigationBarsPadding()`（G-18）。

### 3.3 Surface 层级映射

页面底 = `surface`。容器一律走 tonal 层级 + `BorderStroke(1.dp, outlineVariant)` 细描边，**不用投影**：

| 层级 | token | 本 App 容器 |
|---|---|---|
| L1 | `surfaceContainerLowest` | 封面卡（内容图本身是主体，卡体尽量退后） |
| L2 | `surfaceContainerLow` | 设置卡/信息卡/下载卡/评论卡、ModalBottomSheet |
| L3 | `surfaceContainer` | NavigationBar/Rail、TopAppBar 滚动态（`TopAppBarDefaults.pinnedScrollBehavior`/`enterAlwaysScrollBehavior` 自带 scrolled container）、验证码底板、菜单 |
| L4 | `surfaceContainerHigh` | AlertDialog、FAB 类 |
| L5 | `surfaceContainerHighest` | 输入框填充态（M3 默认）、多选态高亮底 |

规则：① 嵌套容器差 ≥1 级；② 每处描边统一 `outlineVariant`，不叠加 shadow；③ 下载状态等语义色一律 `*Container`/`on*Container` 配对（`error`/`secondary`/`tertiary`/`primary`），禁止 `copy(alpha=)` 自调（修 P0-4、S-3、S-4、S-5）；④ 阅读器覆盖栏：底 = 阅读器 bg `copy(alpha=0.92f)` + 底缘 `outlineVariant` 细线，图标取阅读器 fg——这是阅读器自有配色体系的正确做法，替代硬编码黑条（修 S-1/S-2）；⑤ TopAppBar 配 `scrollBehavior` 获得"静止 surface → 滚动 surfaceContainer"的免费层级变化。

### 3.4 排版

默认 type scale 不动，只收敛离散值：卡标题 `12.sp` → `typography.bodyMedium`；简介 `14.sp` → `bodyMedium`；区块标题统一 `titleMedium`；统计数字 `titleLarge`+`FontWeight.SemiBold`。`MaterialExpressiveTheme` 可顺带启用其默认 typography。

## §4 动效规格

### 4.1 Spring 参数表

| 交互 | spec | 阻尼/刚度 |
|---|---|---|
| 按压回弹（卡片/列表项按下 scale 0.97→1） | `spring` | `DampingRatioLowBouncy` × `StiffnessMediumLow`（按下用 `NoBouncy`×`Medium` 快速到位，松手回弹用前者） |
| 列表项增删/重排 `animateItem` | fadeIn `spring(NoBouncy, Medium)`，placement `spring(LowBouncy, MediumLow)`，fadeOut `spring(NoBouncy, Medium)` | 见左 |
| 展开/收起 `animateContentSize` | `spring` | `NoBouncy` × `Medium`（尺寸动画禁 overshoot） |
| 覆盖栏/工具条进出 | `slideInVertically(spring(NoBouncy, Medium)) + fadeIn(tween(150))` | 位移 spring + 透明度短 tween（alpha 用 tween 可接受） |
| Tab/状态内容切换 `AnimatedContent` | `fadeIn(tween 220)+slideInHorizontally(spring NoBouncy MediumLow)` | M3 "shared axis X" 语义 |
| 点按/音量键翻页 | `pagerState.animateScrollToPage(spring(NoBouncy, Medium))` | 位移必须无 overshoot |
| 自动滚动 | `animateScrollBy`/`scroll { scrollBy(velocity*dt) }` 连续插值替换离散步进 | 修 M-7 |
| Sheet/Dialog | `ModalBottomSheet` 默认 spring 即可；传 `sheetState`+`skipPartiallyExpanded=false` 显式调优 | — |
| 下拉刷新 | 真异步：`isRefreshing` 绑定 `vm` 的 loading StateFlow，加载完成才落 | 修 M-6、P0-2 |

全局挂法：`MaterialExpressiveTheme` → `MaterialTheme.motionScheme`（`MotionScheme.expressive()`）；需要插值处优先 `motionScheme` 的 `defaultSpatialSpec`/`defaultEffectsSpec`/`fastSpatialSpec`/`fastEffectsSpec`，保证全站节奏一致。

### 4.2 共享元素清单（SharedTransitionLayout 包裹 NavHost）

| 转场 | 共享元素 | key 规则 | boundsTransform |
|---|---|---|---|
| 首页/书架/历史/搜索/分类/下载详情 → NovelInfo | 封面图 | `"cover-{aid}"` | `spring(NoBouncy, MediumLow)` |
| NovelInfo → 阅读器（Paged） | 首章插图页可用 `"cover-{aid}"` 延续 | 同上 | 同上 |
| 详情顶栏书名 → 阅读器顶栏标题 | `sharedBounds`（标题条） | `"title-{aid}"` | 同上 |
| Login → Home、Init → Login/Home | 不做共享元素，只做 fade+scale | — | — |

调用点：网格卡 `CoverImage` 加 `Modifier.sharedElement(rememberSharedContentState("cover-$aid"), animatedVisibilityScope)`；`NovelInfoScreen` 头图同 key。非共享内容在转场期间 fade，避免双重动画。

### 4.3 预测性返回

- navigation-compose 2.9.4 + targetSdk 37：**开箱即用**，manifest 不加 flag；NavHost 内部 SeekableTransitionState 会随手势搓动转场。
- 约束实现：每个 `composable` 的 `popEnterTransition`/`popExitTransition` 用**位移+透明度**的 continuous spec（slide+fade/spring），不要用一次性 `AnimatedContent` 替换内容——手势搓动的是 transition 进度。
- 阅读器 `onBack` 与 Sheet dismiss 接入 `PredictiveBackHandler`（`androidx.activity:activity-compose` 已含）做取消/恢复，sheet 返回时手势搓动 sheet 下落。
- 校验：Android 14+ 模拟器开「预测性返回动画」开发者选项，回退时可见上一页预览缩放。

### 4.4 逐类动效接入点

- `NavHost`：四组 transition lambda（见 §5 逐屏表）+ SharedTransitionLayout 包装。
- 所有 `LazyColumn`/`LazyVerticalGrid`：`items(key=…)` + `Modifier.animateItem()`（M-5、G-15）。
- Tab 切换：`AnimatedContent` shared-axis-X（HomeScreen:113、CategoryTab、SearchScreen 三态）。
- 折叠区（详情页卷章、设置分组）：`animateContentSize` spring（NoBouncy, Medium）。
- 覆盖控件：`AnimatedVisibility(visible=showControls, enter=slideInVertically+fadeIn, exit=…)`（M-4）。
- 多选/勾选/状态翻转：`AnimatedVisibility`+`animateItem`（M-10）。

## §5 逐屏改造清单

格式：修（P0/G/S）→ 升层 → 加动效。

| 屏 | 修什么 | 升什么层级 | 加什么动效 |
|---|---|---|---|
| 导航壳 WildNavShell | — | 导航件显式 `navigationSuiteColors` 对齐 `surfaceContainer` | NavHost 四向转场 + SharedTransitionLayout 包裹（§4.2）；预测性返回（§4.3） |
| InitScreen | G-8（尺寸/inset）、S-1 无关 | 衬底 `surface` | `AnimatedVisibility`/`updateTransition` 做开屏 reveal 替代 `delay(400)` 硬切（M-8） |
| Login | P0-3（滚动+居中）、G-7 imePadding、S-5 验证码底板 token、G 项 chip/按钮触控 | 验证码 `surfaceContainer`+描边 | 验证码三态 `AnimatedContent` crossfade；按钮 loading size 动画 |
| Home | P0-1（列数）、G-10 chip 裁切、G-11 触控、区块标题对齐(256 vs 261)、末组 divider(373) | 卡 `surfaceContainerLowest`+outlineVariant；chip 行 `contentPadding` | PullToRefresh 真状态（M-6）；Tab `AnimatedContent`；网格 `animateItem`；封面→详情共享元素 |
| Bookshelf | G-10、G-12 多选触控 24dp、S-4 圆勾 token、刷新恒 false（M-6） | 多选高亮 `surfaceContainerHighest` | `animateItem` + 勾选 `AnimatedVisibility` |
| History | P0-2 假刷新、G-15 底边距+key | 卡同 Home | `animateItem`；长按删除 affordance |
| More/Account/About | G-9 ErrorBlock modifier、G-19 InfoRow、P2 版本号/图标语义 | 卡 `surfaceContainerLow`+描边 | — |
| NovelInfo | G-9、G-13 触控、双栏分隔(G-15 类)、章节名 ellipsis、简介 typography | 头图卡 L1、卷章卡 L2+描边 | 卷章 `animateContentSize` 折叠；actions 淡入；封面共享元素出/入 |
| Search | imePadding、appbar 布局（选择器下移）、历史彩色过载、无清除钮 | 历史项降层 | 三态 `AnimatedContent`；结果网格 `animateItem` |
| Category | 继承 Home 修复 | — | tag/排序切换 `AnimatedContent` |
| Reviews | G-9、G-16 标题 ellipsis、时间降层、key | 卡 L2 | `animateItem`；刷新指示语义修正 |
| Downloads | P0-4 状态色 token 化、S-3 胶囊 alpha、进度条缺失(S-10)、G-16 | 状态胶囊 `*Container` 配对 | 状态翻转 `animateItem`+颜色 tween |
| DownloadSelect | G-9、已下载项 enabled 语义、选中计数条 | 底部操作条 `surfaceContainer`+描边 | 勾选集 `animateItem` |
| 阅读器·Paged | **G-1 右边界死参数、G-2 页一测量、G-3 双 inset、G-4 页码预留、G-6 溢出、S-1 黑条、G-17 插图形状** | 覆盖栏 = 阅读器 bg×0.92+细线（§3.3④） | 点按/音量 `animateScrollToPage` spring；控制栏 `AnimatedVisibility` slide+fade；页码淡入；目录 sheet 预测性返回 |
| 阅读器·Scroll | G-5 底部 inset、S-2 AppBar alpha、章首图块标题缺失、disabled 色 | AppBar `surfaceContainer`+scrollBehavior | AppBar `enterAlwaysScrollBehavior` 折叠；自动滚动连续插值（M-7）；进度指示 |
| ReaderSheets | **P0-5 Slider steps**、G-14 色板触控、G-18 nav inset、当前章居中 | sheet `surfaceContainerLow` 默认即可；色板描边 | sheet 进出默认 spring；`skipPartiallyExpanded` 显式化 |
| ReaderSettings | volumeKeyPaging 等三项补 UI 入口（P2） | — | — |
| Settings | G-9、G-19、Row+Radio 双语义、imePadding | 卡 L2+描边 | 值变更行内淡入 |
| Category/Init 等静态屏 | 同上表 | — | — |

## §6 实施约束与验收口径

### 6.1 全局改动锚点

1. `ui/theme/Theme.kt`：`MaterialTheme` → `MaterialExpressiveTheme(colorScheme, shapes = WildShapes, typography = WildTypography, motionScheme = MotionScheme.expressive())`；补 `surfaceContainer*`/`outline*`/`surfaceTint` 声明；状态栏图标色改为可 per-screen 覆盖（阅读器主题独立控制）。
2. `ui/components/Common.kt`：`ErrorBlock`/`EmptyBlock`/`LoadingBlock` 加 `modifier`；`NovelCoverCard` 用 `Card(onClick=)`+`medium` 形状+8dp 内边距；占位图重画楔形为柔和弧形色块 + 按调色板亮度选字形色。
3. `WildNavShell.kt`：`SharedTransitionLayout { NavHost(...) }`，每 `composable` 配转场。
4. `Pagination.kt`+`PagedReaderScreen.kt`：统一测量/渲染合同（同宽度、同学号、同预留），见 G-1/G-2/G-4。

### 6.2 验收口径

- **编译**：`./gradlew assembleDebug` 零警告级通过；不得新增依赖。
- **逐屏对比**：每个 §5 屏交 前后对比截图（light+dark），CF 拦截页用 mock 数据路径截；共 14 屏 ×2。
- **动效录屏**（`adb screenrecord`，≥60fps 设备或模拟器）：
  1. 封面→详情→阅读器共享元素往返；
  2. 点按翻页 + 控制栏呼出/收起；
  3. 预测性返回手势搓动/取消/完成三态；
  4. 列表增删（书架多选删除、下载勾选）`animateItem`；
  5. 设置 sheet Slider 停在整数刻度。
- **几何回归**：登录页在 480×800dp 矮屏+大字体下顶部不被裁；阅读器 L≠R 边距时右缘不裁字；列表末项不压导航条。
- **层级检查**：深色主题下下载状态色、验证码底板、多选圆勾对比 ≥3:1；页面无投影阴影。
