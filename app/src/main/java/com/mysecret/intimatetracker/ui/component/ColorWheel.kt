package com.mysecret.intimatetracker.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mysecret.intimatetracker.ui.theme.SeedScheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 色轮边缘代表的彩度。再往外，几乎没有任何颜色还在色域内。 */
private const val MAX_CHROMA = 110f

/** 渲染色轮的边长（像素），之后再缩放到实际尺寸。 */
private const val WHEEL_PIXELS = 320

/**
 * 色轮，画在主题真正生成所用的色彩空间里。
 *
 * 角度是色相，到圆心的距离是彩度 —— 这不是装饰，正是 [SeedScheme] 用来构造方案里每个角色的两个
 * 数字。点一个位置，就是字面意义上「指」到了种子，所以色轮展示的就是它选中的东西，而不是一个
 * HSV 取色器、其输出还要再翻译一次。
 *
 * 圆心是灰、边缘是 sRGB 允许的最饱和，于是「我想要多鲜艳」只是一次径向手势。显示器显示不出的
 * 颜色就画它最接近的样子，所以边缘在黄色和绿色一带看起来是平的 —— 那是 sRGB 色域的形状，
 * 不是渲染错误。
 */
@Composable
fun ColorWheel(
    hue: Float,
    chroma: Float,
    dark: Boolean,
    onChange: (hue: Float, chroma: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 画在强调色实际所处的色调上，于是色轮是「预览」而不只是一张通用彩虹：切到深色模式，
    // 它会跟着变亮，就像强调色本身一样。
    val tone = if (dark) 80f else 45f
    var wheel by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(tone) { wheel = withContext(Dispatchers.Default) { renderWheel(tone) } }

    val haptics = LocalHapticFeedback.current

    fun report(position: Offset, canvas: Size) {
        val radius = minOf(canvas.width, canvas.height) / 2f
        if (radius <= 0f) return
        val dx = position.x - canvas.width / 2f
        val dy = position.y - canvas.height / 2f
        val distance = hypot(dx, dy)

        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (angle < 0f) angle += 360f
        // 越过边缘的手势仍然算数，钉在全彩度上 —— 手指滑出圆外不该让选择掉回灰色。
        onChange(angle, (distance / radius * MAX_CHROMA).coerceIn(0f, MAX_CHROMA))
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        report(offset, this.size.toSize())
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> report(offset, this.size.toSize()) }
                    ) { change, _ ->
                        report(change.position, this.size.toSize())
                    }
                }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val image = wheel ?: return@Canvas
            drawImage(
                image = image,
                dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
            )
            drawThumb(hue, chroma, dark)
        }
    }
}

/** 选择标记：一个显示当前颜色的圆环，而不是指向它的箭头。 */
private fun DrawScope.drawThumb(hue: Float, chroma: Float, dark: Boolean) {
    val radius = minOf(size.width, size.height) / 2f
    val angle = Math.toRadians(hue.toDouble())
    val distance = (chroma / MAX_CHROMA).coerceIn(0f, 1f) * radius
    val centre =
        Offset(
            size.width / 2f + (cos(angle) * distance).toFloat(),
            size.height / 2f + (sin(angle) * distance).toFloat(),
        )

    val swatch = SeedScheme.wheelColor(hue, chroma, if (dark) 80f else 45f)
    // 两层描边，深色在下浅色在上，于是无论在苍白的圆心还是饱和的边缘上都看得见，
    // 而不需要知道背后是什么颜色。
    drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = 17.dp.toPx(), center = centre)
    drawCircle(color = Color.White, radius = 15.dp.toPx(), center = centre)
    drawCircle(color = swatch, radius = 12.dp.toPx(), center = centre)
}

/**
 * 逐个色调画一次圆盘。
 *
 * 每个像素都是一次独立的 LCh 转换，所以它在主线程之外运行并被缓存 —— 320² 就是十万次转换，
 * 做一次可以，每帧都做没戏。
 */
private fun renderWheel(tone: Float): ImageBitmap {
    val n = WHEEL_PIXELS
    val pixels = IntArray(n * n)
    val centre = n / 2f
    val radius = n / 2f

    for (y in 0 until n) {
        val dy = y + 0.5f - centre
        for (x in 0 until n) {
            val dx = x + 0.5f - centre
            val distance = hypot(dx, dy)
            if (distance > radius) continue // 保持透明，留下一个干净的圆

            var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            if (angle < 0f) angle += 360f

            val colour = SeedScheme.wheelColor(angle, distance / radius * MAX_CHROMA, tone)
            // 把边缘最后一个像素羽化，否则放大后这个圆在低密度屏上看起来是锯齿状的。
            val edge = ((radius - distance) / 1.5f).coerceIn(0f, 1f)
            pixels[y * n + x] = colour.copy(alpha = edge).toArgb()
        }
    }

    return Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun IntSize.toSize(): Size = Size(width.toFloat(), height.toFloat())
