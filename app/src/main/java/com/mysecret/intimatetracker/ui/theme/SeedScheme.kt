package com.mysecret.intimatetracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 由**一个颜色**推导出完整的 Material 3 配色方案。
 *
 * 移植自 LSPatch / Vector（JingMatrix，GPL-3.0）的 `org.matrix.vector.ui.theme.SeedScheme`，
 * 保留其取色逻辑，只替换品牌种子色与预设色板。原实现的取舍在这里同样成立：
 *
 * Android 自带的生成器只通过 `dynamicColorScheme` 暴露，且读的是**壁纸**，没有「给一个颜色、
 * 还一套方案」的公开 API；Android 12 以下更是完全不存在动态取色。所以用户想要自己的颜色时，
 * 过去只能在手写色板里挑一个。
 *
 * 计算在 **CIE LCh** 中进行，思路与 Google 的 HCT 一致：固定种子的色相（hue）与彩度（chroma），
 * 只沿**明度**上下取值，得到一条色调梯（tonal ramp）。这正是「一套方案看起来像同一个颜色」的原因
 * ——所有角色都是同一色相在不同明度上的切片。这里的明度是 L\\*，感知均匀，所以「tone 40」真的是
 * 肉眼从黑到白走了 40%，也正是 Material 对比度配对成立的前提。
 *
 * 唯一的细节是色域。多数 (明度, 色相) 组合在 sRGB 里承载不了种子的全部彩度 —— 鲜艳的黄在 tone 20
 * 上根本不存在 —— 因此每个色调都会二分查找「转换前还能保住的最大彩度」。图省事的做法是直接裁剪
 * 通道，但那会改变色相，也正是朴素生成器的色阶越暗越从蓝漂到紫的原因。
 */
object SeedScheme {

    /**
     * 品牌种子色：应用原本的薄荷绿强调色 `#6FB6A6`。
     *
     * 它是默认身份色，也是「恢复默认」回到的地方。
     */
    const val DEFAULT_SEED: Int = 0xFF6FB6A6.toInt()

    /**
     * 色轮之外值得先摆出来的预设。
     *
     * 挑选标准是色相彼此拉得够开、且在明暗两端都能当强调色用 —— 一排邻居色会看起来像 bug。
     */
    val PRESETS: List<Int> =
        listOf(
            DEFAULT_SEED, // 薄荷
            0xFF4C6FFF.toInt(), // 靛蓝
            0xFF7C4DFF.toInt(), // 紫
            0xFFD4467A.toInt(), // 玫红
            0xFFE0603A.toInt(), // 珊瑚
            0xFFD9A227.toInt(), // 琥珀
            0xFF3F9B54.toInt(), // 苔绿
            0xFF5B6570.toInt(), // 石墨
        )

    /**
     * 强调色梯子的彩度下限。
     *
     * Material 自己的 tonal spot 配方取 36；上游 Vector 用的是 48，那会让低彩度的种子（本应用
     * 的品牌薄荷 `#6FB6A6` 只有 C\\*=26）被强行推成霓虹青。36 仍然能保证「洗白的种子也有可用
     * 的强调色」，又保住了品牌的那种灰调薄荷 —— 彩度高于 36 的种子（靛蓝、紫、琥珀）不受影响，
     * 它们本来就在色域边缘。
     */
    private const val PRIMARY_CHROMA = 36f

    /** 由种子构造某一明暗模式下的完整方案。 */
    fun of(seed: Int, dark: Boolean): ColorScheme {
        val (_, chroma, hue) = Color(seed).toLch()

        // 标准 Material「tonal spot」配方：强调色有彩度下限，避免洗白的种子产生不可用的主色；
        // 辅助角色刻意更安静；中性色保留一丝色相，让表面与强调色同源，而不是并置的一块灰。
        val primary = Ramp(hue, chroma.coerceAtLeast(PRIMARY_CHROMA))
        val secondary = Ramp(hue, 16f)
        val tertiary = Ramp(hue + 60f, 24f)
        val neutral = Ramp(hue, 4f)
        val variant = Ramp(hue, 8f)
        // 错误色恒为红：破坏性操作不该因为有人选了绿主题就变得可以商量。
        val error = Ramp(25f, 84f)

        return if (dark) {
            darkColorScheme(
                primary = primary[80],
                onPrimary = primary[20],
                primaryContainer = primary[30],
                onPrimaryContainer = primary[90],
                inversePrimary = primary[40],
                secondary = secondary[80],
                onSecondary = secondary[20],
                secondaryContainer = secondary[30],
                onSecondaryContainer = secondary[90],
                tertiary = tertiary[80],
                onTertiary = tertiary[20],
                tertiaryContainer = tertiary[30],
                onTertiaryContainer = tertiary[90],
                error = error[80],
                onError = error[20],
                errorContainer = error[30],
                onErrorContainer = error[90],
                background = neutral[6],
                onBackground = neutral[90],
                surface = neutral[6],
                onSurface = neutral[90],
                surfaceVariant = variant[30],
                onSurfaceVariant = variant[80],
                surfaceTint = primary[80],
                inverseSurface = neutral[90],
                inverseOnSurface = neutral[20],
                outline = variant[60],
                outlineVariant = variant[30],
                scrim = Color.Black,
                surfaceBright = neutral[24],
                surfaceDim = neutral[6],
                surfaceContainerLowest = neutral[4],
                surfaceContainerLow = neutral[10],
                surfaceContainer = neutral[12],
                surfaceContainerHigh = neutral[17],
                surfaceContainerHighest = neutral[22],
            )
        } else {
            lightColorScheme(
                primary = primary[40],
                onPrimary = primary[100],
                primaryContainer = primary[90],
                onPrimaryContainer = primary[10],
                inversePrimary = primary[80],
                secondary = secondary[40],
                onSecondary = secondary[100],
                secondaryContainer = secondary[90],
                onSecondaryContainer = secondary[10],
                tertiary = tertiary[40],
                onTertiary = tertiary[100],
                tertiaryContainer = tertiary[90],
                onTertiaryContainer = tertiary[10],
                error = error[40],
                onError = error[100],
                errorContainer = error[90],
                onErrorContainer = error[10],
                background = neutral[98],
                onBackground = neutral[10],
                surface = neutral[98],
                onSurface = neutral[10],
                surfaceVariant = variant[90],
                onSurfaceVariant = variant[30],
                surfaceTint = primary[40],
                inverseSurface = neutral[20],
                inverseOnSurface = neutral[95],
                outline = variant[50],
                outlineVariant = variant[80],
                scrim = Color.Black,
                surfaceBright = neutral[98],
                surfaceDim = neutral[87],
                surfaceContainerLowest = neutral[100],
                surfaceContainerLow = neutral[99],
                // 卡片底色取纯白，而不是 M3 默认的 tone 94：深色下卡片比页面亮一档，浅色下
                // 卡片就该是白纸压在灰白页面上 —— 同一条规则「卡片浮在页面上」，两边都成立。
                // 嵌套元素（tone 92/90）因此落在白色卡片上，反而比灰底更清楚。
                surfaceContainer = neutral[100],
                surfaceContainerHigh = neutral[92],
                surfaceContainerHighest = neutral[90],
            )
        }
    }

    /** 色板预览展示的色调，由浅到深。 */
    private val PREVIEW_TONES = intArrayOf(95, 90, 80, 70, 60, 50, 40, 30, 20, 10)

    /** 预览条要画的那十个颜色，彩度与强调色梯子一致 —— 展示的就是方案真正发出去的值。 */
    fun previewTones(seed: Int): List<Color> {
        val (_, _, hue) = Color(seed).toLch()
        val ramp = Ramp(hue, PRIMARY_CHROMA)
        return PREVIEW_TONES.map { ramp[it] }
    }

    /**
     * 种子在某明暗下对应的强调色（就是方案里的 `primary`）。
     *
     * 与 [of] 用的是同一条梯子，只是不把另外四十个角色也算出来 —— 色板上的八个小圆点
     * 每个都要一个颜色，没必要为此生成八套完整方案。
     */
    fun accentOf(seed: Int, dark: Boolean): Color {
        val (_, chroma, hue) = Color(seed).toLch()
        return Ramp(hue, chroma.coerceAtLeast(PRIMARY_CHROMA))[if (dark) 80 else 40]
    }

    /** 某个色相在全部色调上的取色。带缓存，因为一套方案会要 ~40 个。 */
    class Ramp internal constructor(private val hue: Float, private val chroma: Float) {
        private val cache = HashMap<Int, Color>(16)

        operator fun get(tone: Int): Color = cache.getOrPut(tone) { lchToColor(tone.toFloat(), chroma, hue) }
    }

    /** 由色轮上的一个点直接取色，供选择器自身显示。 */
    fun wheelColor(hue: Float, chroma: Float, tone: Float = 60f): Color = lchToColor(tone, chroma, hue)

    /** 颜色在色轮上的位置：彩度为半径，色相为角度。 */
    fun Color.toWheel(): Pair<Float, Float> {
        val (_, c, h) = toLch()
        return c to h
    }

    // ---- 色彩空间转换 -------------------------------------------------------------------------
    //
    // sRGB <-> 线性 RGB <-> CIE XYZ (D65) <-> CIE Lab <-> LCh。标准公式；常数是 sRGB 三原色
    // 与 D65 白点。

    private const val WHITE_X = 95.047f
    private const val WHITE_Y = 100.0f
    private const val WHITE_Z = 108.883f

    private fun Color.toLch(): Triple<Float, Float, Float> {
        val r = linearize(red)
        val g = linearize(green)
        val b = linearize(blue)

        val x = (0.4124f * r + 0.3576f * g + 0.1805f * b) * 100f
        val y = (0.2126f * r + 0.7152f * g + 0.0722f * b) * 100f
        val z = (0.0193f * r + 0.1192f * g + 0.9505f * b) * 100f

        val fx = labF(x / WHITE_X)
        val fy = labF(y / WHITE_Y)
        val fz = labF(z / WHITE_Z)

        val l = 116f * fy - 16f
        val a = 500f * (fx - fy)
        val bb = 200f * (fy - fz)

        val chroma = hypot(a, bb)
        var hue = Math.toDegrees(atan2(bb, a).toDouble()).toFloat()
        if (hue < 0f) hue += 360f
        return Triple(l, chroma, hue)
    }

    /**
     * 该色相在该明度上，sRGB 真正能显示的最饱和颜色。
     *
     * 用二分而不是算术，是因为 sRGB 色域在 LCh 里不是个好看的形状 —— 边界同时取决于三个坐标。
     * 十二次迭代能收进四分之一个彩度单位，低于人眼分辨；一套方案每次换主题只要几十个，
     * 唯一会批量索取的调用方是色轮，它在后台渲染并缓存结果。
     */
    private fun lchToColor(lightness: Float, chroma: Float, hueDegrees: Float): Color {
        val l = lightness.coerceIn(0f, 100f)
        if (chroma <= 0.01f) return labToColor(l, 0f, 0f)

        val hue = Math.toRadians(hueDegrees.toDouble())
        val cosH = cos(hue).toFloat()
        val sinH = sin(hue).toFloat()

        fun fits(c: Float): Boolean = inGamut(l, c * cosH, c * sinH)

        if (fits(chroma)) return labToColor(l, chroma * cosH, chroma * sinH)

        var low = 0f
        var high = chroma
        repeat(12) {
            val mid = (low + high) / 2f
            if (fits(mid)) low = mid else high = mid
        }
        return labToColor(l, low * cosH, low * sinH)
    }

    private fun inGamut(l: Float, a: Float, b: Float): Boolean {
        val (r, g, bl) = labToLinear(l, a, b)
        // 留一丝容差：往返都是浮点运算，把一个「超出万分之一」的颜色判死会白白损失可见的彩度。
        return r >= -0.0001f && r <= 1.0001f &&
            g >= -0.0001f && g <= 1.0001f &&
            bl >= -0.0001f && bl <= 1.0001f
    }

    private fun labToColor(l: Float, a: Float, b: Float): Color {
        val (r, g, bl) = labToLinear(l, a, b)
        return Color(
            red = delinearize(r),
            green = delinearize(g),
            blue = delinearize(bl),
        )
    }

    private fun labToLinear(l: Float, a: Float, b: Float): Triple<Float, Float, Float> {
        val fy = (l + 16f) / 116f
        val fx = fy + a / 500f
        val fz = fy - b / 200f

        val x = labInverseF(fx) * WHITE_X / 100f
        val y = labInverseF(fy) * WHITE_Y / 100f
        val z = labInverseF(fz) * WHITE_Z / 100f

        val r = 3.2406f * x - 1.5372f * y - 0.4986f * z
        val g = -0.9689f * x + 1.8758f * y + 0.0415f * z
        val bl = 0.0557f * x - 0.2040f * y + 1.0570f * z
        return Triple(r, g, bl)
    }

    private fun labF(t: Float): Float =
        if (t > 0.008856f) cbrt(t.toDouble()).toFloat() else (7.787f * t + 16f / 116f)

    private fun labInverseF(t: Float): Float {
        val cubed = t * t * t
        return if (cubed > 0.008856f) cubed else (t - 16f / 116f) / 7.787f
    }

    private fun linearize(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f
        else ((channel + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    private fun delinearize(channel: Float): Float {
        val c = channel.coerceIn(0f, 1f)
        return if (c <= 0.0031308f) c * 12.92f
        else (1.055f * c.toDouble().pow(1.0 / 2.4) - 0.055f).toFloat()
    }

    /** `#RRGGBB`，让种子成为用户可以写下来、也可以再输回去的东西。 */
    fun Color.toHex(): String = String.format(
        Locale.US,
        "#%02X%02X%02X",
        (red * 255).roundToInt(),
        (green * 255).roundToInt(),
        (blue * 255).roundToInt(),
    )
}

/**
 * 把所有背景角色压成纯黑，供 OLED 屏使用。
 *
 * 叠加在当前使用的任意深色方案之上 —— 包括动态取色产生的方案 —— 因此壁纸带来的强调色得以保留。
 *
 * 生效中的色板从来不是应用能选择的：API 31 起由壁纸决定，之下、或用户关掉动态取色后，由用户
 * 选择的种子决定。所以没有任何地方可以依赖某个具体色相去承载含义 —— 状态、版本标记这类信息
 * 还要靠形状、图标、文字与动效一起表达。
 */
fun ColorScheme.toAmoled(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF101010),
        surfaceContainerHigh = Color(0xFF161616),
        surfaceContainerHighest = Color(0xFF1C1C1C),
    )
