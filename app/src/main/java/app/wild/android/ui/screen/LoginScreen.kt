package app.wild.android.ui.screen

import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.wild.android.R
import app.wild.android.ui.components.CaptchaImage
import app.wild.android.ui.vm.SessionViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * 登录页 `/login`（spec §2.2）：AppBar「登录轻小说文库」+ 垂直居中表单。
 * Stage 4：验证码 = `checkcode.php` 真实 PNG（点击刷新，拉取失败落回程序化占位图）；
 * 登录 = POST `/login.php`（checkcode 字段照发，服务端不校验也无害），
 * 失败文案取服务器返回（如「密码错误」）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    vm: SessionViewModel = koinViewModel(),
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var captchaText by rememberSaveable { mutableStateOf("") }
    var captchaSeed by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var showRegisterDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val captchaBytes by vm.captcha.collectAsState()
    val captchaLoading by vm.captchaLoading.collectAsState()
    val loggingIn by vm.loggingIn.collectAsState()

    LaunchedEffect(Unit) { vm.refreshCaptcha() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("登录轻小说文库") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            Image(
                painter = painterResource(R.drawable.wild_icon),
                contentDescription = null,
                modifier = Modifier
                    .padding(bottom = 50.dp)
                    .size(96.dp),
            )
            Column(modifier = Modifier.widthIn(max = 480.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                // 验证码图：200×50，点击刷新（spec §2.2）
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val bytes = captchaBytes
                    when {
                        captchaLoading -> Box(
                            Modifier.size(200.dp, 50.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                        bytes != null -> {
                            val bmp = remember(bytes) {
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "验证码，点击刷新",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(200.dp, 50.dp)
                                        .clickable { vm.refreshCaptcha() },
                                )
                            } else {
                                CaptchaImage(
                                    seed = captchaSeed,
                                    modifier = Modifier
                                        .size(200.dp, 50.dp)
                                        .clickable { captchaSeed++; vm.refreshCaptcha() },
                                )
                            }
                        }
                        else -> CaptchaImage(
                            seed = captchaSeed,
                            modifier = Modifier
                                .size(200.dp, 50.dp)
                                .clickable { captchaSeed++; vm.refreshCaptcha() },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = captchaText,
                    onValueChange = { captchaText = it },
                    label = { Text("验证码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showRegisterDialog = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("注册") }
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = {
                            when {
                                username.isBlank() ->
                                    scope.launch { snackbar.showSnackbar("请输入用户名") }
                                password.isBlank() ->
                                    scope.launch { snackbar.showSnackbar("请输入密码") }
                                else -> {
                                    vm.login(username, password, captchaText) { r ->
                                        r.onSuccess { onLoginSuccess() }
                                            .onFailure {
                                                scope.launch {
                                                    snackbar.showSnackbar(
                                                        it.message ?: "登录失败"
                                                    )
                                                }
                                            }
                                    }
                                }
                            }
                        },
                        enabled = !loggingIn,
                        modifier = Modifier.weight(2f),
                    ) {
                        if (loggingIn) CircularProgressIndicator(Modifier.size(24.dp))
                        else Text("登录")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRegisterDialog) {
        AlertDialog(
            onDismissRequest = { showRegisterDialog = false },
            title = { Text("注册提示") },
            text = { Text("注册需要在网页端进行，是否跳转到注册页面？") },
            confirmButton = {
                TextButton(onClick = {
                    showRegisterDialog = false
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wenku8.net/register.php"))
                        )
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false }) { Text("取消") }
            },
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun LoginScreenPreview() {
    app.wild.android.ui.theme.WildTheme { LoginScreen(onLoginSuccess = {}) }
}
