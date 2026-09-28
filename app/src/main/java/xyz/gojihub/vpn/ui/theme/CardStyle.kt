package xyz.gojihub.vpn.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * "Apple Glass" (Liquid Glass) v5 — редизайн 2026-09-28. Настоящий backdrop-blur через Haze
 * (см. GlassBackdrop.kt): на API 31+ каждая стеклянная поверхность реально сэмплирует то, что
 * нарисовано на фоне-подложке под ней (градиент + плавающие пятна + сетка точек), на 24-30
 * Haze сам откатывается на полупрозрачную заливку без блюра — minSdk 24 не нарушается.
 * Поверх блюра — кромка-градиент (яркая сверху-слева → отсвет снизу-справа), внутренние тени
 * (светлая сверху / тёмная снизу — имитация объёма выпуклой поверхности) и бегающий блик,
 * медленно гуляющий по экрану (см. rememberGlassLight ниже) — тот же приём, что и в макете v5.
 */

/** Один HazeState на экран: GlassBackdrop кладёт его сюда, стеклянные поверхности читают. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Общий "источник света" — медленно гуляет по экрану (как бегающий блик в макете v5). */
val LocalGlassLight = compositionLocalOf { Offset(0.72f, 0.12f) }

@Composable
fun rememberGlassLight(): Offset {
    val t = rememberInfiniteTransition(label = "glassLight")
    val x by t.animateFloat(0.15f, 0.85f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lx")
    val y by t.animateFloat(0.05f, 0.30f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ly")
    return Offset(x, y)
}

/** Ядро стекла v5: блюр 24dp + насыщение фона, тинт, кромка-градиент, внутренние тени, блик. */
private fun Modifier.glassCore(shape: Shape, tintAlpha: Float, blur: Dp, withBlur: Boolean): Modifier = composed {
    val haze = LocalHazeState.current
    val light = LocalGlassLight.current
    val tint = GodjiColors.GlassTint
    this
        .clip(shape)
        .then(
            if (withBlur && haze != null) Modifier.hazeEffect(
                state = haze,
                style = HazeStyle(
                    backgroundColor = GodjiColors.Background,
                    tints = listOf(HazeTint(tint.copy(alpha = tintAlpha))),
                    blurRadius = blur,
                    noiseFactor = 0f
                )
            ) else Modifier.drawBehind { drawRect(tint.copy(alpha = (tintAlpha + 0.25f).coerceAtMost(0.92f))) }
        )
        .drawWithContent {
            // бегающий блик
            drawRect(
                Brush.radialGradient(
                    listOf(GodjiColors.GlassSpot, Color.Transparent),
                    center = Offset(size.width * light.x, size.height * light.y),
                    radius = 240.dp.toPx()
                )
            )
            // диагональный глянец
            drawRect(Brush.linearGradient(0f to GodjiColors.GlassHighlight, 0.45f to GodjiColors.GlassHighlightFade))
            // внутренние тени: светлая сверху, тёмная снизу → объём
            val h = 16.dp.toPx()
            drawRect(Brush.verticalGradient(listOf(GodjiColors.GlassInnerTop.copy(alpha = GodjiColors.GlassInnerTop.alpha * 0.5f), Color.Transparent), 0f, h))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, GodjiColors.GlassInnerBottom), size.height - h * 1.4f, size.height))
            // тонкие контурные линии (hl сверху, edge по бокам) — из референса это отдельные
            // "inset 0 1px 0 hl" / "inset ±1px 0 0 edge", раньше не рисовались вовсе, из-за
            // чего стекло выглядело площе, чем в макете.
            val edgeW = 1.dp.toPx()
            drawRect(GodjiColors.GlassHl, size = Size(size.width, edgeW))
            drawRect(GodjiColors.GlassEdge, size = Size(edgeW, size.height))
            drawRect(GodjiColors.GlassEdge, topLeft = Offset(size.width - edgeW, 0f), size = Size(edgeW, size.height))
            drawContent()
        }
        .border(
            BorderStroke(
                1.dp,
                Brush.linearGradient(
                    0f to GodjiColors.GlassRimTop,
                    0.35f to GodjiColors.GlassRimTop.copy(alpha = 0.25f),
                    0.62f to GodjiColors.GlassRimTop.copy(alpha = 0.06f),
                    1f to GodjiColors.GlassRimBottom
                )
            ),
            shape
        )
}

/** Многослойная тень v5: контактная + глубокая мягкая. */
private fun Modifier.glassShadow(shape: Shape, elevation: Dp) = this
    .shadow(2.dp, shape, ambientColor = GodjiColors.GlassShadow, spotColor = GodjiColors.GlassShadow)
    .shadow(elevation, shape, ambientColor = GodjiColors.GlassShadow, spotColor = GodjiColors.GlassShadow)

fun Modifier.godjiCard(
    shape: Shape = RoundedCornerShape(26.dp),
    tint: Color = GodjiColors.GlassTint,        // оставлено ради совместимости сигнатуры
    borderColor: Color = GodjiColors.CardBorder // accent-обводка (текущий тариф) рисуется поверх
): Modifier = this
    .glassShadow(shape, 18.dp)
    .glassCore(shape, GodjiColors.GlassTintAlpha, 24.dp, withBlur = true)
    .then(if (borderColor != GodjiColors.CardBorder) Modifier.border(1.dp, borderColor, shape) else Modifier)

/** Строки внутри карточки — без блюра (фон уже размыт карточкой) и без тени. */
fun Modifier.godjiGlassFlat(
    shape: Shape = RoundedCornerShape(20.dp),
    tint: Color = GodjiColors.GlassTint
): Modifier = this.glassCore(shape, GodjiColors.GlassTintAlpha * 0.6f, 0.dp, withBlur = false)

fun Modifier.godjiGlassBar(shape: Shape = RoundedCornerShape(34.dp)): Modifier = this
    .glassShadow(shape, 24.dp)
    .glassCore(shape, (GodjiColors.GlassTintAlpha + 0.14f).coerceAtMost(0.9f), 30.dp, withBlur = true)

fun Modifier.godjiGlassPill(
    shape: Shape = RoundedCornerShape(50),
    tint: Color = GodjiColors.GlassTint
): Modifier = this
    .glassShadow(shape, 8.dp)
    .glassCore(shape, GodjiColors.GlassTintAlpha, 24.dp, withBlur = true)
