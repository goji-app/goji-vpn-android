package xyz.gojihub.vpn.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Стекло v5 — БЕЗ библиотек и без RenderEffect: одинаково работает на API 24…37.
 * Эффект «стекла» даёт фон GlassBackdrop (уже мягкий и размытый) + полупрозрачная заливка
 * + блик + глянец + внутренние тени + градиентная кромка. Так же устроен эталон (HTML):
 * см. handoff-1.0.77/SPEC.md §2.
 *
 * Сигнатура godjiCard(shape, tint, borderColor) — как в 1.0.77, экраны не меняются:
 *  • tint == Surface/Chip/SurfaceGlass (дефолтные) → чистое стекло;
 *  • любой другой tint (TealTint, TerracottaTint, JamBg…) → стекло + этот цвет поверх;
 *  • borderColor == Teal/TealTintBorder/TealDeep → дополнительная акцентная обводка 1.5dp
 *    (выбранный сервер, текущий тариф); остальные цвета обводки (Ink, CardBorder…) игнорируются.
 */

/** Позиция «источника света» 0..1 относительно каждой стеклянной поверхности. */
val LocalGlassLight = staticCompositionLocalOf<State<Offset>> { derivedStateOf { Offset(0.72f, 0.12f) } }

@Composable
fun rememberGlassLight(): State<Offset> {
    val t = rememberInfiniteTransition(label = "glassLight")
    val x = t.animateFloat(0.15f, 0.85f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lx")
    val y = t.animateFloat(0.05f, 0.30f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ly")
    return remember { derivedStateOf { Offset(x.value, y.value) } }
}

enum class GlassLevel { Normal, Strong, Faint }

/** Мягкая многослойная тень ТОЛЬКО снаружи формы (под полупрозрачным стеклом тени не видно). */
private fun Modifier.outerShadow(shape: Shape, color: Color, blur: Dp, dy: Dp): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val paint = Paint()
    paint.asFrameworkPaint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        setShadowLayer(blur.toPx(), 0f, dy.toPx(), color.toArgb())
    }
    val contact = Paint()
    contact.asFrameworkPaint().apply {
        isAntiAlias = true
        this.color = color.copy(alpha = color.alpha * 0.6f).toArgb()
        setShadowLayer(2.dp.toPx(), 0f, 1.dp.toPx(), color.copy(alpha = color.alpha * 0.6f).toArgb())
    }
    onDrawBehind {
        clipPath(path, ClipOp.Difference) {
            drawIntoCanvas { c ->
                c.drawPath(path, contact)
                c.drawPath(path, paint)
            }
        }
    }
}

private fun Modifier.glassSurface(shape: Shape, level: GlassLevel, overlay: Color?, accentBorder: Boolean): Modifier = composed {
    val light = LocalGlassLight.current
    drawWithCache {
        val outline: Outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        val fill = when (level) {
            GlassLevel.Normal -> GodjiColors.Glass
            GlassLevel.Strong -> GodjiColors.GlassStrong
            GlassLevel.Faint -> GodjiColors.GlassFaint
        }
        val gloss = Brush.linearGradient(
            0f to GodjiColors.GlassGloss, 0.45f to Color.Transparent,
            start = Offset.Zero, end = Offset(size.width, size.height)
        )
        val innerH = 14.dp.toPx()
        val innerTop = Brush.verticalGradient(listOf(GodjiColors.InnerTop.copy(alpha = GodjiColors.InnerTop.alpha * 0.45f), Color.Transparent), 0f, innerH)
        val innerBottom = Brush.verticalGradient(listOf(Color.Transparent, GodjiColors.InnerBottom), size.height - innerH * 1.3f, size.height)
        val rim = Brush.linearGradient(
            0f to GodjiColors.RimA, 0.35f to GodjiColors.RimB, 0.62f to GodjiColors.RimC, 1f to GodjiColors.RimD,
            start = Offset.Zero, end = Offset(size.width * 0.82f, size.height)
        )
        val rimW = 1.dp.toPx()
        val spotR = 240.dp.toPx()
        onDrawWithContent {
            clipPath(path) {
                drawRect(fill)
                if (overlay != null) drawRect(overlay)
                val l = light.value
                drawRect(Brush.radialGradient(listOf(GodjiColors.GlassSpot, Color.Transparent), Offset(size.width * l.x, size.height * l.y), spotR))
                drawRect(gloss)
                drawRect(innerTop)
                drawRect(innerBottom)
            }
            drawContent()
            drawOutline(outline, rim, style = Stroke(rimW))
            if (accentBorder) drawOutline(outline, GodjiColors.Teal, style = Stroke(1.5.dp.toPx()))
        }
    }
}

private fun isNeutralTint(c: Color) =
    c == GodjiColors.Surface || c == GodjiColors.Chip || c == GodjiColors.SurfaceGlass || c == Color.Transparent

private fun isAccentBorder(c: Color) =
    c == GodjiColors.Teal || c == GodjiColors.TealTintBorder || c == GodjiColors.TealDeep || c == GodjiColors.TealBright

/** Карточка-модуль (26dp). Сигнатура совместима с 1.0.77. */
fun Modifier.godjiCard(
    shape: Shape = RoundedCornerShape(26.dp),
    tint: Color = GodjiColors.Surface,
    borderColor: Color = GodjiColors.CardBorder
): Modifier = this
    .outerShadow(shape, GodjiColors.GlassShadow, 22.dp, 10.dp)
    .glassSurface(shape, GlassLevel.Normal, if (isNeutralTint(tint)) null else tint, isAccentBorder(borderColor))

/** Лист входа, таб-бар, тост — более плотное стекло. */
fun Modifier.godjiGlassStrong(shape: Shape = RoundedCornerShape(32.dp)): Modifier = this
    .outerShadow(shape, GodjiColors.GlassShadow, 26.dp, 12.dp)
    .glassSurface(shape, GlassLevel.Strong, null, false)

/** Круглые кнопки 40dp, плашка «Goji», сетевой статус, чипы. */
fun Modifier.godjiGlassPill(shape: Shape = RoundedCornerShape(50), tint: Color? = null): Modifier = this
    .outerShadow(shape, GodjiColors.GlassShadow, 12.dp, 5.dp)
    .glassSurface(shape, GlassLevel.Normal, tint, false)

/** Строка/чип ВНУТРИ карточки — без тени, слабое стекло. */
fun Modifier.godjiGlassFlat(shape: Shape = RoundedCornerShape(20.dp)): Modifier =
    this.glassSurface(shape, GlassLevel.Faint, null, false)
