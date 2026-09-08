package xyz.gojihub.vpn.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import xyz.gojihub.vpn.R

// Manrope — статичные файлы на каждое начертание (вместо одного переменного шрифта
// с синтетическим bold) — Bold/SemiBold/ExtraBold и т.п. рисуются настоящими глифами
// шрифта, а не жирнее нарисованным системой Normal. Instrument Serif — заголовки.
val ManropeFamily = FontFamily(
    Font(R.font.manrope_extralight, FontWeight.ExtraLight),
    Font(R.font.manrope_light, FontWeight.Light),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold)
)
val InstrumentSerifFamily = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

// Ни один экран не берёт стиль текста из MaterialTheme.typography.* по имени — везде вызывают
// Text(...) напрямую со своими fontWeight/fontSize и (для заголовков) отдельно передают
// InstrumentSerifFamily. Но шрифт по умолчанию для любого такого Text() без своего fontFamily
// всё равно берётся из LocalTextStyle.current, а это typography.bodyLarge — то есть пока
// fontFamily не переопределён здесь на каждом стиле, весь обычный текст в приложении реально
// рисуется системным Roboto, а не Manrope. Переопределяем fontFamily у всех стилей Typography,
// чтобы Manrope был шрифтом по умолчанию для всего проекта.
private val base = Typography()
val GodjiTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = ManropeFamily),
    displayMedium = base.displayMedium.copy(fontFamily = ManropeFamily),
    displaySmall = base.displaySmall.copy(fontFamily = ManropeFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = ManropeFamily),
    headlineMedium = base.headlineMedium.copy(fontFamily = ManropeFamily),
    headlineSmall = TextStyle(fontFamily = InstrumentSerifFamily, fontWeight = FontWeight.Normal, fontSize = 27.sp),
    titleLarge = base.titleLarge.copy(fontFamily = ManropeFamily),
    titleMedium = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleSmall = base.titleSmall.copy(fontFamily = ManropeFamily),
    bodyLarge = base.bodyLarge.copy(fontFamily = ManropeFamily),
    bodyMedium = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Normal, fontSize = 13.5.sp),
    bodySmall = base.bodySmall.copy(fontFamily = ManropeFamily),
    labelLarge = base.labelLarge.copy(fontFamily = ManropeFamily),
    labelMedium = base.labelMedium.copy(fontFamily = ManropeFamily),
    labelSmall = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp),
)
