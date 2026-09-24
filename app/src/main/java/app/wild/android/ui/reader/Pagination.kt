package app.wild.android.ui.reader

/**
 * 普通阅读器分页（spec §2.8 分页算法的 Kotlin 版）：
 * - 按 `\n` 切段；`<!--image-->URL<!--image-->` 段落独占一页。
 * - 文本段经 [measure] 测高：段高 > 剩余高度 → 用 `offsetAtHeight(freeHeight)`
 *   求断点，前半塞入当前页（不换行），剩余部分作为新段继续；
 *   放得下则整段写入并 `freeHeight -= height + paragraphSpacing`。
 * - [isTitle] 标记首个文本段（渲染侧按标题样式绘制，G-2：测量与渲染必须同一样式合同）。
 * - G-6：空页连最小行高都放不下时按 `minLineHeightPx` 截断，保证进度且不丢内容。
 *
 * 测量与断点由调用方传入（Compose 侧用 TextMeasurer，测试侧可注入假实现）。
 */
data class ReaderPage(val content: String, val isImage: Boolean)

val IMAGE_MARK_REGEX = Regex("<!--image-->([^<]+)<!--image-->")

fun paginate(
    content: String,
    canvasHeightPx: Float,
    minLineHeightPx: Float = 0f,
    measure: (isTitle: Boolean, text: String) -> ParagraphMeasure,
): List<ReaderPage> {
    if (canvasHeightPx <= 0f) return listOf(ReaderPage("", isImage = false))
    val pages = mutableListOf<ReaderPage>()
    var freeHeight = canvasHeightPx
    var textParaIndex = 0

    fun endPage(text: String) {
        pages += ReaderPage(text.trimEnd('\n'), isImage = false)
        freeHeight = canvasHeightPx
    }

    val buf = StringBuilder()
    for (raw in content.split("\n")) {
        val imgMatch = IMAGE_MARK_REGEX.find(raw)
        if (imgMatch != null) {
            if (buf.isNotEmpty()) { endPage(buf.toString()); buf.clear() }
            pages += ReaderPage(imgMatch.groupValues[1], isImage = true)
            freeHeight = canvasHeightPx
            continue
        }
        if (raw.isBlank()) continue

        val isTitle = textParaIndex == 0
        textParaIndex++
        var rest = raw
        while (rest.isNotEmpty()) {
            val m = measure(isTitle, rest)
            if (m.heightPx <= freeHeight) {
                if (buf.isNotEmpty()) buf.append('\n')
                buf.append(rest)
                freeHeight -= m.heightPx + m.paragraphSpacingPx
                rest = ""
            } else {
                val cut = m.offsetAtHeight(freeHeight)
                if (cut <= 0) {
                    // 当前页放不下任何字符
                    if (buf.isNotEmpty()) {
                        // 页内有内容：换页后重试本段
                        endPage(buf.toString()); buf.clear()
                    } else {
                        // G-6：空页连一行都放不下（横屏/矮视口）——按最小行高截断，
                        // 既不死循环也不整段溢出；minLineHeight 也放不下时保底写入。
                        val minCut = m.offsetAtHeight(minLineHeightPx)
                        if (minCut > 0) {
                            buf.append(rest.substring(0, minCut))
                            endPage(buf.toString()); buf.clear()
                            rest = rest.substring(minCut)
                        } else {
                            buf.append(rest); endPage(buf.toString()); buf.clear(); rest = ""
                        }
                    }
                } else {
                    buf.append(rest.substring(0, cut))
                    endPage(buf.toString()); buf.clear()
                    rest = rest.substring(cut)
                }
            }
        }
    }
    if (buf.isNotEmpty()) endPage(buf.toString())
    if (pages.isEmpty()) pages += ReaderPage("", isImage = false)
    return pages
}

data class ParagraphMeasure(
    /** 文本在当前排版参数下的总高度（px）。 */
    val heightPx: Float,
    /** 段落后追加的间距（px）。 */
    val paragraphSpacingPx: Float,
    /** 返回在 [heightPx] 处截断文本的字符偏移（可能为 0）。 */
    val offsetAtHeight: (Float) -> Int,
)
