package app.wild.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/**
 * 验证码占位图（Stage 3 不联网）：随机 4 位字符 + 干扰线，点击由调用方处理刷新。
 * Stage 4 换成 `checkcode.php` 的真实字节图。
 * SZKM-67 S-5：底板/字形/干扰线全部走 colorScheme（surfaceContainer + 描边），
 * 深色主题下不再是一块悬浮亮块。
 */
@Composable
fun CaptchaImage(seed: Int, modifier: Modifier = Modifier) {
    val code = remember(seed) { randomCode(seed) }
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.colorScheme.surfaceContainer
    val onBase = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(base)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val rnd = Random(seed)
            // 干扰线
            repeat(4) {
                drawLine(
                    lineColor.copy(alpha = 0.6f + rnd.nextFloat() * 0.4f),
                    Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                    Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                    strokeWidth = 2f,
                )
            }
            // 字符：主色与 onSurface 交替，保持可读性
            val style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
            val totalW = size.width
            val cellW = totalW / code.length
            code.forEachIndexed { i, c ->
                val m = measurer.measure(
                    c.toString(),
                    style.copy(color = if (i % 2 == 0) accent else onBase),
                )
                drawText(
                    m,
                    topLeft = Offset(
                        cellW * i + (cellW - m.size.width) / 2 + (rnd.nextFloat() - 0.5f) * 8f,
                        (size.height - m.size.height) / 2 + (rnd.nextFloat() - 0.5f) * 10f,
                    ),
                )
            }
        }
    }
}

private fun randomCode(seed: Int): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val rnd = Random(seed)
    return (1..4).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
}
