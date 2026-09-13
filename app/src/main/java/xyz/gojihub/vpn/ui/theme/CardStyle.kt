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
 * Единая стилизация "карточки-модуля" (rounded-xl, см. design.md редизайна Tactical Sand &
 * Void) — раньше каждый экран сам повторял .background(Surface, shape).border(CardBorder,
 * shape), из-за чего светлая и тёмная тема выглядели одинаково "плоско". По спеку светлая тема
 * держит глубину через физический слой (сплошная белая карточка + мягкая тень), а тёмная — через
 * "матовое стекло" (полупрозрачная поверхность + тонкий градиентный блик по верхней кромке,
 * вместо реального backdrop-blur, которому нужен RenderEffect API 31+ — упрощение того же рода,
 * что уже применяется к halo вокруг кнопки подключения).
 */
fun Modifier.godjiCard(
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = GodjiColors.Surface,
    borderColor: Color = GodjiColors.CardBorder
): Modifier = if (GodjiColors.isDark) {
    this
        .clip(shape)
        .background(tint.copy(alpha = 0.82f))
        .border(
            BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.02f)))),
            shape
        )
} else {
    this
        .shadow(elevation = 8.dp, shape = shape, ambientColor = Color(0x14503320), spotColor = Color(0x14503320))
        .clip(shape)
        .background(tint)
        .border(BorderStroke(1.5.dp, borderColor), shape)
}
