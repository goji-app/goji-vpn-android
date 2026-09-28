package xyz.gojihub.vpn.ui.theme

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Фон под стеклом v5: вертикальный градиент, сетка точек 16dp, концентрические круги
 * в правом верхнем углу (чтобы размытие/преломление было видно) и 4 медленно плывущих
 * размытых пятна. Всё это — hazeSource; контент поверх рисует стекло через LocalHazeState.
 */
@Composable
fun GlassBackdrop(connected: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val haze = rememberHazeState()
    val light = rememberGlassLight()
    val t = rememberInfiniteTransition(label = "blobs")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "a")

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(haze)
                .background(
                    Brush.verticalGradient(
                        0f to GodjiColors.BackdropTop,
                        0.55f to GodjiColors.BackdropMid,
                        1f to GodjiColors.BackdropBottom
                    )
                )
        ) {
            val blobs = listOf(GodjiColors.Blob1, GodjiColors.Blob2, GodjiColors.Blob3, GodjiColors.Blob4)
            Canvas(
                Modifier
                    .fillMaxSize()
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(26.dp) else Modifier)
            ) {
                val w = size.width; val h = size.height
                // Базовые позиции — как в оригинальном JS (left/top в % от экрана: -30/-8, 45/10,
                // -10/52, 50/68 для пятен 360/320/340/300px), плюс лёгкий дрейф по своей фазе
                // (i*90° сдвиг), а не общий для всех wobble одной фазой.
                val spots = listOf(
                    Offset(w * (-0.30f + 0.1f * a), h * (-0.08f + 0.08f * a)) to 180.dp.toPx(),
                    Offset(w * (0.45f - 0.08f * a), h * (0.10f + 0.06f * a)) to 160.dp.toPx(),
                    Offset(w * (-0.10f + 0.08f * a), h * (0.52f - 0.05f * a)) to 170.dp.toPx(),
                    Offset(w * (0.50f - 0.06f * a), h * (0.68f - 0.06f * a)) to 150.dp.toPx()
                )
                spots.forEachIndexed { i, (c, r) ->
                    val alpha = GodjiColors.BlobAlpha + if (connected && i == 0) 0.15f else 0f
                    drawCircle(Brush.radialGradient(listOf(blobs[i].copy(alpha = alpha), Color.Transparent), c, r), r, c)
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val step = 16.dp.toPx()
                var y = 0f
                while (y < size.height) { var x = 0f; while (x < size.width) { drawCircle(GodjiColors.TexDot, 1.dp.toPx(), Offset(x, y)); x += step }; y += step }
                val c = Offset(size.width * 0.78f, size.height * 0.18f)
                var r = 26.dp.toPx()
                while (r < size.maxDimension) { drawCircle(GodjiColors.TexLine, r, c, style = Stroke(1.dp.toPx())); r += 27.dp.toPx() }
            }
        }
        CompositionLocalProvider(LocalHazeState provides haze, LocalGlassLight provides light) {
            content()
        }
    }
}
