package xyz.gojihub.vpn.ui.theme

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Liquid Glass — оптика Glass-HQ/liquid-glass (MIT; packages/liquid-glass: core/optics.ts,
 * core/materials.ts, gpu/maps.wgsl), перенесённая с WebGPU/SVG-фильтров на Android:
 * фон приложения записывается в GraphicsLayer (LiquidBackdrop), а каждая стеклянная
 * поверхность рисует свой участок этого слоя через RenderEffect: размытие материала +
 * AGSL-шейдер, который повторяет исходную модель —
 *  • преломление у края: смещение amount·(1 − √((2−depth)·depth)), depth = −d·0.05 (d в dp);
 *  • материалы Clear/Regular: тоновые кривые и насыщенность из materials.ts;
 *  • блик сверху/снизу (spread 1.08) и мягкая подсветка у кромки, тень боковой кромки (0.98).
 * Работает на Android 13+ (RuntimeShader). Ниже, во всплывающих окнах (другое окно — другой
 * слой) или при выключенной настройке остаётся прежнее стекло (см. CardStyle.glassSurface).
 * Ради производительности фон снимается ~8 раз в секунду, а размывается в 1/4 разрешения
 * (см. SourceNode и GlassNode).
 */
object LiquidGlass {
    /** Настройка "Жидкое стекло" (Настройки → Внешний вид); читается в композиции. */
    var enabled by mutableStateOf(true)

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

enum class LiquidMaterial { Clear, Regular }

/** Слой с тем, что лежит ПОД стеклом, и его положение в окне. */
class LiquidBackdrop internal constructor(internal val view: View) {
    internal var layer: GraphicsLayer? = null
    internal var origin: Offset = Offset.Zero
}

/** Фон приложения (кольца, точки, пятна) — его преломляют карточки, плашки, кнопки, таб-бар. */
val LocalLiquidBackdrop = staticCompositionLocalOf<LiquidBackdrop?> { null }

@Composable
fun rememberLiquidBackdrop(): LiquidBackdrop {
    val view = LocalView.current
    return remember(view) { LiquidBackdrop(view) }
}

/** Пишет содержимое этого элемента в слой [backdrop] и рисует его как обычно. */
fun Modifier.liquidBackdropSource(backdrop: LiquidBackdrop): Modifier =
    if (!LiquidGlass.supported) this else this then SourceElement(backdrop)

private data class SourceElement(val backdrop: LiquidBackdrop) : ModifierNodeElement<SourceNode>() {
    override fun create() = SourceNode(backdrop)
    override fun update(node: SourceNode) { node.backdrop = backdrop }
    override fun InspectorInfo.inspectableProperties() { name = "liquidBackdropSource" }
}

/**
 * Пятна фона дрейфуют непрерывно. Если переписывать слой каждый кадр, каждое стекло на экране
 * заново размывает и преломляет свой участок с частотой экрана (120 Гц) — в 1.0.110 это
 * заметно тормозило всё приложение. Поэтому слой обновляется не чаще раза в
 * [SNAPSHOT_INTERVAL_MS] (пятна за это время сдвигаются на доли dp — разницы не видно), а между
 * обновлениями фон рисуется напрямую и стекло берёт готовую картинку из кэша HWUI.
 */
private class SourceNode(var backdrop: LiquidBackdrop) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var layer: GraphicsLayer? = null
    private var recordedAt = 0L
    private var recordedSize = IntSize.Zero
    private var trailing: Job? = null

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer().also { backdrop.layer = it }
        recordedAt = 0L
    }

    override fun onDetach() {
        trailing = null
        layer?.let {
            if (backdrop.layer === it) backdrop.layer = null
            requireGraphicsContext().releaseGraphicsLayer(it)
        }
        layer = null
    }

    override fun ContentDrawScope.draw() {
        val l = layer ?: return drawContent()
        val now = SystemClock.uptimeMillis()
        val sz = IntSize(size.width.roundToInt(), size.height.roundToInt())
        val wait = SNAPSHOT_INTERVAL_MS - (now - recordedAt)
        if (wait <= 0 || sz != recordedSize) {
            recordedAt = now
            recordedSize = sz
            l.record { this@draw.drawContent() }
            drawLayer(l)
        } else {
            drawContent()
            // Последнее изменение внутри окна тоже должно попасть в слой.
            if (trailing == null) trailing = coroutineScope.launch {
                delay(wait)
                trailing = null
                invalidateDraw()
            }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        backdrop.origin = coordinates.positionInWindow()
    }

    private companion object {
        const val SNAPSHOT_INTERVAL_MS = 125L
    }
}

/**
 * Рисует под содержимым элемента жидкое стекло формы [shape] из слоя [backdrop].
 * [overlay] — цветной оттенок поверх стекла (как раньше tint у godjiCard).
 */
internal fun Modifier.liquidGlass(
    backdrop: LiquidBackdrop,
    shape: Shape,
    material: LiquidMaterial,
    overlay: Color?,
    dark: Boolean
): Modifier = this then GlassElement(backdrop, shape, material, overlay, dark)

private data class GlassElement(
    val backdrop: LiquidBackdrop,
    val shape: Shape,
    val material: LiquidMaterial,
    val overlay: Color?,
    val dark: Boolean
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(backdrop, shape, material, overlay, dark)
    override fun update(node: GlassNode) {
        node.backdrop = backdrop; node.shape = shape; node.material = material
        node.overlay = overlay; node.dark = dark
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() { name = "liquidGlass" }
}

/**
 * Два слоя. [frost] — участок фона в 1/[DOWNSCALE] разрешения с размытием материала: размытой
 * картинке полное разрешение не нужно, а пикселей в 16 раз меньше. [glass] — в полном
 * разрешении: растягивает frost обратно и пропускает через AGSL-шейдер (преломлению у кромки,
 * тону и бликам нужна чёткость). Поля вокруг элемента — только на сдвиг преломления и радиус
 * размытия (1.5σ, а не 3σ, как было).
 */
private class GlassNode(
    var backdrop: LiquidBackdrop,
    var shape: Shape,
    var material: LiquidMaterial,
    var overlay: Color?,
    var dark: Boolean
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var frost: GraphicsLayer? = null
    private var glass: GraphicsLayer? = null
    private var position = Offset.Zero
    private var shader: RuntimeShader? = null
    private var effectKey: Any? = null
    private var recordKey: Any? = null

    override fun onAttach() {
        val ctx = requireGraphicsContext()
        frost = ctx.createGraphicsLayer()
        glass = ctx.createGraphicsLayer()
    }

    override fun onDetach() {
        val ctx = requireGraphicsContext()
        frost?.let { ctx.releaseGraphicsLayer(it) }
        glass?.let { ctx.releaseGraphicsLayer(it) }
        frost = null
        glass = null
        effectKey = null
        recordKey = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val p = coordinates.positionInWindow()
        if (p != position) {
            position = p
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val f = frost
        val g = glass
        if (f == null || g == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || size.minDimension <= 0f) {
            drawContent(); return
        }
        drawGlass(f, g)
        drawContent()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun ContentDrawScope.drawGlass(f: GraphicsLayer, g: GraphicsLayer) {
        val src = backdrop.layer ?: return
        val radius = when (val o = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Rounded -> o.roundRect.topLeftCornerRadius.x
            is Outline.Rectangle -> 0f
            is Outline.Generic -> min(size.width, size.height) / 2f
        }
        val regular = material == LiquidMaterial.Regular
        val amount = (if (regular) REGULAR_REFRACTION else CLEAR_REFRACTION).dp.toPx()
        val sigma = if (regular) (if (dark) DARK_BLUR else LIGHT_BLUR).dp.toPx() else 0f
        val k = if (regular) 1f / DOWNSCALE else 1f
        val padG = ceil(amount).toInt()
        val padF = ceil(amount + sigma * 1.5f).toInt()
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()

        val key = listOf(w, h, radius, material, dark, overlay, density)
        if (key != effectKey) {
            effectKey = key
            val s = shader ?: RuntimeShader(GLASS_AGSL).also { shader = it }
            s.setFloatUniform("size", w.toFloat(), h.toFloat())
            s.setFloatUniform("pad", padG.toFloat())
            s.setFloatUniform("radius", radius)
            s.setFloatUniform("amount", amount)
            s.setFloatUniform("density", density)
            s.setFloatUniform("dark", if (dark) 1f else 0f)
            s.setFloatUniform("regular", if (regular) 1f else 0f)
            val o = overlay ?: Color.Transparent
            s.setFloatUniform("overlay", o.red, o.green, o.blue, o.alpha)
            g.renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
            f.renderEffect = if (sigma > 0f) {
                android.graphics.RenderEffect.createBlurEffect(sigma * k, sigma * k, Shader.TileMode.CLAMP).asComposeRenderEffect()
            } else null
        }

        // Перезаписываем слои, только если что-то сдвинулось: изменения самого фона доходят
        // по ссылке на его слой, а лишняя перезапись заставила бы HWUI заново прогнать
        // размытие и шейдер — например, на каждом кадре анимации внутри карточки.
        val rk = listOf(src, backdrop.origin, position, w, h, padF, padG, k)
        if (rk == recordKey) {
            translate(-padG.toFloat(), -padG.toFloat()) { drawLayer(g) }
            return
        }
        recordKey = rk

        // Участок фона под элементом (+ поля) в уменьшенном масштабе.
        val fw = ceil((w + 2 * padF) * k).toInt().coerceAtLeast(1)
        val fh = ceil((h + 2 * padF) * k).toInt().coerceAtLeast(1)
        f.record(IntSize(fw, fh)) {
            scale(k, k, pivot = Offset.Zero) {
                translate(backdrop.origin.x - position.x + padF, backdrop.origin.y - position.y + padF) { drawLayer(src) }
            }
        }
        g.record(IntSize(w + 2 * padG, h + 2 * padG)) {
            translate((padG - padF).toFloat(), (padG - padF).toFloat()) {
                scale(1f / k, 1f / k, pivot = Offset.Zero) { drawLayer(f) }
            }
        }
        translate(-padG.toFloat(), -padG.toFloat()) { drawLayer(g) }
    }

    private companion object {
        // materials.ts: refraction 60 (CSS px ≈ dp). Для крупных карточек 60 — слишком
        // сильный "линзовый" край; Regular чуть мягче, Clear (линза таб-бара) — как в оригинале.
        const val REGULAR_REFRACTION = 36f
        const val CLEAR_REFRACTION = 60f
        // Regular: frost 7.33 + fill 23/21.8 с непрозрачностью 0.34/0.65 — сведено к одному
        // размытию с эквивалентной силой (средневзвешенное по fillOpacity).
        const val LIGHT_BLUR = 12.7f
        const val DARK_BLUR = 16.7f
        const val DOWNSCALE = 4f
    }
}

/** AGSL-порт maps.wgsl (карты смещения/подсветки/кромки) + тоновых кривых materials.ts. */
private const val GLASS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float pad;
uniform float radius;
uniform float amount;
uniform float density;
uniform float dark;
uniform float regular;
uniform float4 overlay;

float sat(float x) { return clamp(x, 0.0, 1.0); }

float3 toneRegular(float3 v) {
    // tone = [a, b, q, chroma]; light [0.36,0.67,-0.1113,1.1031], dark [0.105,0.83,-0.2091,1.0648]
    float a = mix(0.36, 0.105, dark);
    float b = mix(0.67, 0.83, dark);
    float q = mix(-0.1113, -0.2091, dark);
    float chroma = mix(1.1031, 1.0648, dark);
    float w = mix(0.80, 0.90, dark);
    float3 t = w * (a + b * v + q * v * v) + (1.0 - w) * v;
    float lum = dot(t, float3(0.2126, 0.7152, 0.0722));
    return clamp(mix(float3(lum), t, chroma), 0.0, 1.0);
}

float3 toneClear(float3 v) {
    float lift = mix(0.18, 0.15, dark);
    float roll = mix(0.0, 0.20, dark);
    float3 inv = 1.0 - v;
    float3 t = v + lift * inv * inv * inv - roll * v * v * v;
    float3 tinted = dark > 0.5 ? (0.38 * v + 0.20) : (0.78 * v + 0.26);
    float tw = mix(0.06, 0.14, dark);
    return clamp((1.0 - tw) * t + tw * tinted, 0.0, 1.0);
}

half4 main(float2 fc) {
    float2 c = fc - pad - size * 0.5;
    float r = min(radius, min(size.x, size.y) * 0.5);
    float2 q = abs(c) - size * 0.5 + r;
    float dpx = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    float2 n;
    if (q.x > 0.0 && q.y > 0.0) n = normalize(q) * sign(c);
    else if (q.x > q.y) n = float2(sign(c.x), 0.0);
    else n = float2(0.0, sign(c.y));

    float d = dpx / density;          // расстояние до кромки в dp (CSS px оригинала)
    float aa = 1.0 / density;
    float coverage = sat(0.5 - d / aa);
    if (coverage <= 0.0) return half4(0.0);

    // optics.ts: edgeDisplacement
    float depth = sat(-d * 0.05);
    float edge = 1.0 - sat(sqrt((2.0 - depth) * depth));
    float4 src = content.eval(fc - n * edge * amount);
    float3 col = src.a > 0.0 ? src.rgb / src.a : float3(0.0);
    col = regular > 0.5 ? toneRegular(col) : toneClear(col);
    col = mix(col, overlay.rgb, overlay.a);

    // maps.wgsl, plane 2: блик сверху/снизу + мягкая подсветка у кромки
    float cs = cos(1.08);
    float cs2 = cos(1.08 * 0.65);
    float directional = pow(sat((abs(n.y) - cs) / (1.0 - cs)), 1.25);
    float diffuseDir = pow(sat((abs(n.y) - cs2) / (1.0 - cs2)), 1.25);
    float stroke = sat((d + 0.9) / aa + 0.5) * sat(0.5 - d / aa);
    float spec = directional * stroke;
    spec = spec / (1.0 + 0.5 * (1.0 - spec));
    float diffuse = diffuseDir * 0.18 * pow(1.0 - sat(-d / 4.5), 2.0) * coverage;
    float highlight = sat(spec * mix(1.0, 0.82, dark) + diffuse * mix(1.0, 0.40, dark));
    col = mix(col, float3(mix(1.0, 0.82, dark)), highlight);

    // plane 3: тень боковой кромки
    float adj = d - 0.5;
    float falloff = 1.0 - sat(-adj / 0.5);
    float ramp = mix(falloff > 0.0 ? 1.0 : 0.0, falloff, 0.75);
    float outline = ramp * sat((adj + 0.5) / aa + 0.5) * sat(-adj / aa + 0.5);
    float cside = cos(0.98);
    float2 wts = clamp((float2(n.x, -n.x) - cside) / (1.0 - cside), 0.0, 1.0) * outline;
    float shade = dot(wts / (1.0 + 0.4 * (1.0 - wts)), float2(1.0));
    col = mix(col, float3(0.0), sat(shade * mix(0.48, 0.60, dark)));

    return half4(half3(col) * coverage, coverage);
}
"""
