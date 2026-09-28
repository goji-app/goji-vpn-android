package xyz.gojihub.vpn.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * "Apple Glass" (Liquid Glass) — редизайн 2026-09-28. Единая стилизация "карточки-модуля":
 * полупрозрачная заливка (GlassTint/GlassTintAlpha) + верхний specular-блик диагональным
 * градиентом (имитация того, как свет ложится на выпуклую стеклянную поверхность) + тонкая
 * окантовка, тоже градиентом от яркой к почти прозрачной. Раньше светлая и тёмная темы были
 * визуально разными приёмами (сплошная карточка+тень против матового стекла) — теперь это один
 * и тот же приём в обеих, отличаются только сами значения токенов (см. Color.kt).
 *
 * Настоящего backdrop-blur (сэмплинга контента позади поверхности) здесь нет — см. комментарий
 * у токенов Glass* в Color.kt, почему сознательно не стали тащить RenderEffect ради
 * стабильности на minSdk 24. Тень оставлена и в стекле — без неё плавающая поверхность на
 * пёстром фоне (глобус, скругления вкладок) визуально "отклеивается" от макета.
 */
fun Modifier.godjiCard(
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = GodjiColors.GlassTint,
    borderColor: Color = GodjiColors.CardBorder
): Modifier = this
    .shadow(elevation = 18.dp, shape = shape, ambientColor = GodjiColors.GlassShadow, spotColor = GodjiColors.GlassShadow)
    .clip(shape)
    .background(tint.copy(alpha = GodjiColors.GlassTintAlpha))
    .background(
        Brush.linearGradient(
            colorStops = arrayOf(0f to GodjiColors.GlassHighlight, 0.4f to GodjiColors.GlassHighlightFade),
        )
    )
    .border(
        BorderStroke(1.dp, Brush.verticalGradient(listOf(GodjiColors.GlassStroke, GodjiColors.GlassStrokeFade))),
        shape
    )

/** Та же поверхность, но без внешней тени — для плашек внутри уже "приподнятой" карточки
 *  (например строка списка внутри godjiCard), где вторая тень поверх первой смотрелась бы
 *  грязно, а стеклянность (блик+окантовка) всё равно нужна для консистентности. */
fun Modifier.godjiGlassFlat(
    shape: Shape = RoundedCornerShape(20.dp),
    tint: Color = GodjiColors.GlassTint
): Modifier = this
    .clip(shape)
    .background(tint.copy(alpha = GodjiColors.GlassTintAlpha * 0.7f))
    .background(
        Brush.linearGradient(
            colorStops = arrayOf(0f to GodjiColors.GlassHighlight, 0.4f to GodjiColors.GlassHighlightFade),
        )
    )
    .border(
        BorderStroke(1.dp, Brush.verticalGradient(listOf(GodjiColors.GlassStroke, GodjiColors.GlassStrokeFade))),
        shape
    )

/** Плавающая стеклянная панель (нижняя навигация, всплывающие тулбары) — та же оптика, что
 *  и godjiCard, но с более выраженным бликом и полностью скруглённой (капсула, а не rounded-xl)
 *  формой — визуальный язык iOS 26 "остров" вместо панели во всю ширину экрана впритык к краям. */
fun Modifier.godjiGlassBar(
    shape: Shape = RoundedCornerShape(32.dp)
): Modifier = this
    .shadow(elevation = 24.dp, shape = shape, ambientColor = GodjiColors.GlassShadow, spotColor = GodjiColors.GlassShadow)
    .clip(shape)
    .background(GodjiColors.GlassTint.copy(alpha = GodjiColors.GlassTintAlpha + 0.1f))
    .background(
        Brush.verticalGradient(
            colorStops = arrayOf(0f to GodjiColors.GlassHighlight, 0.5f to GodjiColors.GlassHighlightFade),
        )
    )
    .border(
        BorderStroke(1.dp, Brush.verticalGradient(listOf(GodjiColors.GlassStroke, GodjiColors.GlassStrokeFade))),
        shape
    )

/** Стеклянная пилюля для кнопок/чипов — компактнее, без внешней тени (живёт обычно уже внутри
 *  godjiCard/godjiGlassBar), но с тем же бликом+окантовкой, чтобы не выпадать из общего языка. */
fun Modifier.godjiGlassPill(
    shape: Shape = RoundedCornerShape(50),
    tint: Color = GodjiColors.GlassTint
): Modifier = this
    .clip(shape)
    .background(tint.copy(alpha = GodjiColors.GlassTintAlpha * 0.85f))
    .background(
        Brush.linearGradient(
            colorStops = arrayOf(0f to GodjiColors.GlassHighlight, 0.5f to GodjiColors.GlassHighlightFade),
        )
    )
    .border(
        BorderStroke(1.dp, Brush.verticalGradient(listOf(GodjiColors.GlassStroke, GodjiColors.GlassStrokeFade))),
        shape
    )
