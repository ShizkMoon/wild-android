package app.wild.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/**
 * 验证码占位图（Stage 3 不联网）：随机 4 位字符 + 干扰线，点击由调用方处理刷新。
 * Stage 4 换成 `checkcode.php` 的真实字节图。
 */
@Composable
fun CaptchaImage(seed: Int, modifier: Modifier = Modifier) {
    val code = remember(seed) { randomCode(seed) }
    val measurer = rememberTextMeasurer()
    Box(modifier = modifier.background(Color(0xFFF5F5F5))) {
        Canvas(Modifier.fillMaxSize()) {
            val rnd = Random(seed)
            // 干扰线
            repeat(4) {
                drawLine(
                    Color(0x33000000 + rnd.nextInt(0x555555)),
                    Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                    Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                    strokeWidth = 2f,
                )
            }
            // 字符
            val style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E5F8A))
            val totalW = size.width
            val cellW = totalW / code.length
            code.forEachIndexed { i, c ->
                val m = measurer.measure(c.toString(), style)
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
