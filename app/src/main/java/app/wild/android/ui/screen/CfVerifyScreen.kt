package app.wild.android.ui.screen

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.wild.android.data.prefs.SettingsStore
import app.wild.android.data.remote.CfSession
import app.wild.android.data.remote.CfState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 站点验证页 `/cf-verify`（SZKM-66 方案 D-1）：
 * 隐藏 WebView 解不动 Cloudflare 时的可见降级——全屏 WebView 加载被拦页面，
 * 用户手动过一次；通过判定=CfSession 哨兵/onVerifyPageFinished/checkNow 三路。
 * 返回键/放弃 → cfSession.onUserGaveUp()，请求方拿到失败并呈现重试入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CfVerifyScreen(
    onBack: () -> Unit,
    cfSession: CfSession = koinInject(),
) {
    val cfState by cfSession.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }

    val verifyUrl = (cfState as? CfState.NeedsUser)?.url ?: cfSession.currentVerifyUrl()

    // 已通过 → 自动回退；返回键 = 放弃
    LaunchedEffect(cfState) {
        if (cfState is CfState.Verified) onBack()
    }
    BackHandler { cfSession.onUserGaveUp(); onBack() }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = SettingsStore.DEFAULT_UA // 与 OkHttp/求解器同 UA
        }
    }
    DisposableEffect(Unit) { onDispose { webView.destroy() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("站点安全验证") },
                navigationIcon = {
                    IconButton(onClick = { cfSession.onUserGaveUp(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = when (cfState) {
                            is CfState.Failed ->
                                "上次验证未完成或被站点拒绝，请再试一次；反复失败可能是出口 IP 被站点标记，建议切换网络后重试。"
                            else ->
                                "站点启用了 Cloudflare 防护，请在下方完成人机验证；通过后本页会自动关闭，刚才的请求会自动重发。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            AndroidView(
                factory = {
                    webView.apply {
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                scope.launch { cfSession.onVerifyPageFinished(view) }
                            }
                        }
                        val cm = android.webkit.CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)
                        loadUrl(verifyUrl)
                    }
                },
                update = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            checking = true
                            val ok = cfSession.checkNow()
                            checking = false
                            if (!ok) snackbar.showSnackbar("仍未检测到验证通过，请完成验证或稍候再试")
                        }
                    },
                    enabled = !checking,
                    modifier = Modifier.weight(1f),
                ) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text("我已完成验证")
                }
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = { cfSession.onUserGaveUp(); onBack() },
                    modifier = Modifier.weight(1f),
                ) { Text("暂不验证，返回") }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
