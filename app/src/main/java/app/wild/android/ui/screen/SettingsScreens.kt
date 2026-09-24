package app.wild.android.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Html
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.wild.android.R
import app.wild.android.data.prefs.SettingsStore
import app.wild.android.data.prefs.ThemeMode
import app.wild.android.data.remote.Wenku8Client
import app.wild.android.data.repository.LibraryRepository
import app.wild.android.data.repository.SessionRepository
import app.wild.android.ui.components.EmptyBlock
import app.wild.android.ui.components.ErrorBlock
import app.wild.android.ui.components.InfoRow
import app.wild.android.ui.components.LoadingBlock
import app.wild.android.ui.components.contentColumnWidth
import app.wild.android.ui.reader.ReaderSettings
import app.wild.android.ui.reader.ReaderType
import app.wild.android.ui.vm.AccountViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * 设置页（spec §2.16）：5 张 Card = 阅读器设置 / 主题设置(3 Radio) / API Host
 * / 缓存设置 / 退出登录(红字)。
 * Stage 4：清缓存 = OkHttp 磁盘缓存 + web_cache + chapter_cache + image_cache；
 * 退出 = 清 cookie 回登录页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    settingsStore: SettingsStore = koinInject(),
    client: Wenku8Client = koinInject(),
    library: LibraryRepository = koinInject(),
    session: SessionRepository = koinInject(),
) {
    val themeMode by settingsStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val apiHost by settingsStore.apiHost.collectAsState(initial = "")
    var apiHostInput by rememberSaveable(apiHost) { mutableStateOf(apiHost) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(), // G-7：API Host 输入框不被键盘遮挡
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // 卡片1：阅读器设置
            item {
                SettingsCard(title = "阅读器设置") {
                    Text("阅读器类型", modifier = Modifier.padding(horizontal = 16.dp))
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        SegmentedButton(
                            selected = ReaderSettings.readerType == ReaderType.NORMAL,
                            onClick = { ReaderSettings.readerType = ReaderType.NORMAL },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = { Icon(Icons.Outlined.Book, null, Modifier.size(18.dp)) },
                        ) { Text("普通阅读器") }
                        SegmentedButton(
                            selected = ReaderSettings.readerType == ReaderType.HTML,
                            onClick = { ReaderSettings.readerType = ReaderType.HTML },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = { Icon(Icons.Outlined.Html, null, Modifier.size(18.dp)) },
                        ) { Text("HTML阅读器") }
                    }
                    SettingsSwitch(
                        label = "打开阅读器时保持亮屏",
                        checked = ReaderSettings.keepOnReading,
                        onChange = { ReaderSettings.keepOnReading = it },
                    )
                    SettingsSwitch(
                        label = "滚屏时保持亮屏",
                        checked = ReaderSettings.keepOnScroll,
                        onChange = { ReaderSettings.keepOnScroll = it },
                    )
                    SettingsSwitch(
                        label = "音量键翻页",
                        checked = ReaderSettings.volumeKeyPaging,
                        onChange = { ReaderSettings.volumeKeyPaging = it },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // 卡片2：主题设置（RadioListTile 三选；ListItem 行整行可点，Radio 不再重复挂 click）
            item {
                SettingsCard(title = "主题设置") {
                    listOf(
                        "跟随系统" to ThemeMode.SYSTEM,
                        "浅色" to ThemeMode.LIGHT,
                        "深色" to ThemeMode.DARK,
                    ).forEach { (label, mode) ->
                        ListItem(
                            leadingContent = {
                                RadioButton(selected = themeMode == mode, onClick = null)
                            },
                            headlineContent = { Text(label) },
                            modifier = Modifier.clickable {
                                scope.launch { settingsStore.setThemeMode(mode) }
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // 卡片3：API Host 设置
            item {
                SettingsCard(title = "API Host 设置") {
                    Text("API 主机地址", modifier = Modifier.padding(horizontal = 16.dp))
                    OutlinedTextField(
                        value = apiHostInput,
                        onValueChange = { apiHostInput = it },
                        placeholder = { Text("留空使用默认地址") },
                        leadingIcon = { Icon(Icons.Outlined.Link, null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                            scope.launch {
                                settingsStore.setApiHost(apiHostInput)
                                snackbar.showSnackbar("API Host 已更新")
                            }
                        }),
                    )
                    OutlinedButton(
                        onClick = {
                            apiHostInput = ""
                            scope.launch {
                                settingsStore.setApiHost("")
                                snackbar.showSnackbar("已重置为默认地址")
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重置为默认")
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // 卡片4：缓存设置
            item {
                SettingsCard(title = "缓存设置") {
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.CleaningServices, null) },
                        headlineContent = { Text("清除接口缓存") },
                        supportingContent = {
                            Text(if (clearing) "清除中…" else "清除所有网络请求与章节的缓存数据")
                        },
                        modifier = Modifier.clickable(enabled = !clearing) { showClearCacheDialog = true },
                    )
                }
            }

            // 卡片5：退出登录（红字）
            item {
                Card(
                    modifier = Modifier
                        .contentColumnWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                        headlineContent = { Text("退出登录", color = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable { showLogoutDialog = true },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清除缓存") },
            text = { Text("确定要清除所有接口缓存吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    clearing = true
                    scope.launch {
                        client.evictHttpCache()
                        library.clearCaches()
                        clearing = false
                        snackbar.showSnackbar("缓存已清除")
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showClearCacheDialog = false }) { Text("取消") } },
        )
    }
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出登录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    scope.launch {
                        session.signOut()
                        onLogout()
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .contentColumnWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

/**
 * 账户页（spec §2.16）：4 section Card（基本信息/联系方式/账户信息/个人签名），
 * 行 = 80dp 灰标签 + 值。Stage 4：`userdetail.php` 键值对驱动（站点字段直接映射为分区），
 * 顶部加签到卡（一天一次，sign_log 判重）；未登录给明确态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    vm: AccountViewModel = koinViewModel(),
) {
    val detail by vm.detail.collectAsState()
    val loggedIn by vm.loggedIn.collectAsState()
    val signedToday by vm.signedToday.collectAsState()
    val signResult by vm.signResult.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(signResult) {
        signResult?.let { snackbar.showSnackbar(it) }
    }

    // 站点键值 → 分区（保持原 App 的分组观感：按 key 归类）
    val sections = remember(detail.data) { groupUserDetail(detail.data.orEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账户") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            !loggedIn -> EmptyBlock("未登录", Modifier.padding(padding))
            detail.loading -> LoadingBlock(Modifier.padding(padding))
            detail.error != null && detail.data == null ->
                ErrorBlock(detail.error!!, onRefresh = { vm.load() }, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 签到卡
                item {
                    Card(Modifier.contentColumnWidth()) {
                        ListItem(
                            leadingContent = { Icon(Icons.Outlined.Book, null) },
                            headlineContent = { Text("每日签到") },
                            supportingContent = {
                                Text(if (signedToday) "今天已签到" else "每天可签到一次")
                            },
                            trailingContent = {
                                OutlinedButton(
                                    onClick = { vm.sign() },
                                    enabled = !signedToday,
                                ) { Text(if (signedToday) "已签" else "签到") }
                            },
                            modifier = Modifier.clickable(enabled = !signedToday) { vm.sign() },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                sections.forEach { (section, rows) ->
                    item {
                        Card(Modifier.contentColumnWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(section, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                rows.forEach { (label, value) -> InfoRow(label, value) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** userdetail 键值 → 分区（键名做归类，未匹配的全进「账户信息」）。 */
private fun groupUserDetail(map: Map<String, String>): List<Pair<String, List<Pair<String, String>>>> {
    if (map.isEmpty()) return emptyList()
    val buckets = linkedMapOf<String, MutableList<Pair<String, String>>>()
    fun bucketOf(key: String): String = when {
        key.contains("邮箱") || key.contains("Email", true) || key.contains("QQ", true) ||
            key.contains("MSN", true) || key.contains("网站") || key.contains("联系") -> "联系方式"
        key.contains("签名") || key.contains("描述") -> "个人签名"
        key.contains("用户名") || key.contains("昵称") || key.contains("ID", true) ||
            key.contains("等级") || key.contains("头衔") || key.contains("性别") -> "基本信息"
        else -> "账户信息"
    }
    map.forEach { (k, v) -> buckets.getOrPut(bucketOf(k)) { mutableListOf() }.add(k to v) }
    val order = listOf("基本信息", "联系方式", "账户信息", "个人签名")
    return order.mapNotNull { name -> buckets[name]?.let { name to it.toList() } }
}

/**
 * 关于页 `/about`（spec §2.16）：居中图标+名+版本 + 更新按钮 + GPL 说明 Card。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(Modifier.height(24.dp))
                Image(
                    painter = painterResource(R.drawable.wild_icon),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text("轻小说文库", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "版本 ${app.wild.android.BuildConfig.VERSION_NAME}+${app.wild.android.BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                OutlinedButton(onClick = {
                    scope.launch { snackbar.showSnackbar("已是最新版本") }
                }) {
                    Icon(Icons.Outlined.SystemUpdate, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("检查更新")
                }
                Spacer(Modifier.height(32.dp))
                Card(Modifier.contentColumnWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("关于 Wild", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Wild 是一个使用 Kotlin + Jetpack Compose 开发的轻小说文库客户端，提供流畅的阅读体验和丰富的功能。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("开源协议", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "本项目采用 GNU General Public License v3.0 (GPLv3) 协议开源。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun SettingsPreview() {
    app.wild.android.ui.theme.WildTheme {
        Text("预览需要 Koin 环境，见真机截图")
    }
}
