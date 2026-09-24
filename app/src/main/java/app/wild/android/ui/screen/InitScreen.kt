package app.wild.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.wild.android.R
import app.wild.android.ui.vm.SessionViewModel
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * 启动页 `/init`（spec §2.1）：全屏启动图（铺满裁切、底部 5% 渐隐）
 * + 底部主色进度圈；无 AppBar。
 * Stage 4：initSession（种 cookie/检测 CF）+ 登录态分流 —— 已登录进主页，否则去登录页。
 * SZKM-67：M-8 开屏 reveal 动效替代「delay(400) 后硬切」的观感；
 * G-8 进度圈避让导航条、图片 fillMaxSize 不留 letterbox。
 */
@Composable
fun InitScreen(
    onFinished: () -> Unit,
    onNeedLogin: () -> Unit = onFinished,
    vm: SessionViewModel = koinViewModel(),
) {
    val done by vm.initDone.collectAsState()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.init()
        visible = true
    }
    LaunchedEffect(done) {
        val d = done ?: return@LaunchedEffect
        delay(350) // 启动图至少可见一瞬（配合 reveal 动效）
        if (d) onFinished() else onNeedLogin()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(280)) + scaleIn(
                initialScale = 1.06f,
                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
            ),
        ) {
            Image(
                painter = painterResource(R.drawable.wild_startup),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize() // G-8：铺满，宽屏不露裸窗口背景
                    // 底部 5% 线性渐隐（spec：LinearGradient stops [0,0.95,1] dstIn）
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black,
                                0.95f to Color.Black,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding() // G-8：不压手势条
                .padding(bottom = 48.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InitScreenPreview() {
    app.wild.android.ui.theme.WildTheme { InitScreen({}) }
}
