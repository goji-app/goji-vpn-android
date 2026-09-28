package xyz.gojihub.vpn.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * Общий фон приложения v5 — рисуется ОДИН раз под NavHost (MainActivity) и под LoginScreen.
 * Слои (снизу вверх), 1:1 с эталоном GojiGlassFull.dc.html → wallpaperFor():
 *  1. Вертикальный градиент BaseTop → BaseMid (55%) → BaseBottom.
 *  2. Концентрические кольца: центр (78% w, 18% h), шаг 27dp, линия 1dp, TexLine.
 *  3. Сетка точек: шаг 16dp, радиус 1dp, TexDot.
 *  4. Четыре больших размытых пятна Blob1..4 (альфа BlobAlpha), медленно дрейфуют 14–19 с.
 * Координаты пятен заданы в dp для экрана 412×892 и масштабируются под реальный размер.
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    connected: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val light = rememberGlassLight()
    Box(modifier.fillMaxSize()) {
        StaticTexture()
        Blobs(connected)
        CompositionLocalProvider(LocalGlassLight provides light) { content() }
    }
}

@Composable
private fun StaticTexture() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(0f to GodjiColors.BaseTop, 0.55f to GodjiColors.BaseMid, 1f to GodjiColors.BaseBottom))
        val c = Offset(size.width * 0.78f, size.height * 0.18f)
        val step = 27.dp.toPx()
        val maxR = hypot(size.width, size.height)
        val ring = Stroke(1.dp.toPx())
        var r = step
        while (r < maxR) { drawCircle(GodjiColors.TexLine, r, c, style = ring); r += step }
        val g = 16.dp.toPx(); val dr = 1.dp.toPx()
        var y = g / 2
        while (y < size.height) {
            var x = g / 2
            while (x < size.width) { drawCircle(GodjiColors.TexDot, dr, Offset(x, y)); x += g }
            y += g
        }
    }
}

private class BlobSpec(val cx: Float, val cy: Float, val r: Float, val dx: Float, val dy: Float, val s0: Float, val s1: Float, val ms: Int)

// cx/cy/r — dp для макета 412×892; dx/dy — амплитуда дрейфа в dp; s0→s1 — масштаб.
private val blobSpecs = listOf(
    BlobSpec(56f, 109f, 180f, 60f, 40f, 1f, 1.18f, 14000),
    BlobSpec(345f, 249f, 160f, -50f, 70f, 1.1f, 0.9f, 17000),
    BlobSpec(129f, 634f, 170f, 40f, -60f, 0.95f, 1.15f, 19000),
    BlobSpec(356f, 757f, 150f, 60f, 40f, 1f, 1.18f, 15000),
)

@Composable
private fun Blobs(connected: Boolean) {
    val t = rememberInfiniteTransition(label = "blobs")
    val phases: List<State<Float>> = blobSpecs.mapIndexed { i, b ->
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(b.ms, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b$i")
    }
    val colors = listOf(GodjiColors.Blob1, GodjiColors.Blob2, GodjiColors.Blob3, GodjiColors.Blob4)
    val baseAlpha = GodjiColors.BlobAlpha
    Canvas(Modifier.fillMaxSize()) {
        val kx = size.width / 412.dp.toPx()
        val ky = size.height / 892.dp.toPx()
        blobSpecs.forEachIndexed { i, b ->
            val p = phases[i].value
            val scale = b.s0 + (b.s1 - b.s0) * p
            val center = Offset((b.cx + b.dx * p).dp.toPx() * kx, (b.cy + b.dy * p).dp.toPx() * ky)
            val alpha = (if (connected && i == 0) baseAlpha + 0.15f else baseAlpha).coerceAtMost(1f)
            softDisc(colors[i].copy(alpha = alpha), center, b.r.dp.toPx() * kx * scale)
        }
    }
}

/** Эквивалент CSS: круг + filter: blur(26px) — сплошное ядро и мягкий край ~26dp. */
private fun DrawScope.softDisc(color: Color, center: Offset, radius: Float) {
    val feather = 26.dp.toPx()
    val outer = radius + feather
    val core = ((radius - feather) / outer).coerceIn(0f, 1f)
    drawCircle(
        Brush.radialGradient(
            0f to color, core to color, (radius / outer) to color.copy(alpha = color.alpha * 0.5f), 1f to Color.Transparent,
            center = center, radius = outer
        ),
        radius = outer, center = center
    )
}
