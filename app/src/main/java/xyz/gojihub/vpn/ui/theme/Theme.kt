package xyz.gojihub.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Читает ТЕКУЩИЕ (реактивные, см. GodjiColors) цвета при каждой перекомпоновке — раньше это
 *  был обычный val, посчитанный один раз при загрузке класса из тогдашних значений GodjiColors,
 *  поэтому переключение тёмной темы красило сами экраны (они читают GodjiColors.* напрямую), но
 *  НЕ трогало дефолты стандартных M3-компонентов (например обводку/подпись OutlinedTextField,
 *  где явного цвета нет) — снаружи это выглядело как "часть текста осталась в старом цвете".
 *  Базовая схема (light/dark ColorScheme) выбирается по GodjiColors.isDark, чтобы поля, которые
 *  мы сами не переопределяем ниже, тоже брали разумные тёмные/светлые дефолты. */
@Composable
private fun godjiColorScheme() = (if (GodjiColors.isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = GodjiColors.Teal,
    onPrimary = GodjiColors.Surface,
    secondary = GodjiColors.Terracotta,
    onSecondary = GodjiColors.Surface,
    background = GodjiColors.Background,
    onBackground = GodjiColors.TextPrimary,
    surface = GodjiColors.Surface,
    onSurface = GodjiColors.TextPrimary,
    surfaceVariant = GodjiColors.Chip,
    onSurfaceVariant = GodjiColors.TextSecondary,
    outline = GodjiColors.ButtonBorder,
    outlineVariant = GodjiColors.CardBorder,
    primaryContainer = GodjiColors.TealTint,
    onPrimaryContainer = GodjiColors.TealDeep,
    error = GodjiColors.Danger,
    onError = GodjiColors.Surface,
)

// Ширина экрана (в dp), под которую подобраны все .dp/.sp размеры во всех экранах —
// среднестатистический компактный телефон. Все Compose-экраны написаны с фиксированными
// значениями в dp/sp, которые сами по себе одинаковы в физических размерах на любом экране
// (это и есть смысл dp), но из-за этого на узких экранах вёрстка теснее и требует прокрутки
// (см. verticalScroll в ConnectScreen/PlansScreen/LoginScreen), а на широких — наоборот,
// выглядит мельче относительно доступного места. Подгоняем плотность экрана так, чтобы одна
// и та же вёрстка занимала одну и ту же ДОЛЮ ширины экрана на любом устройстве, а не
// фиксированное количество dp.
private const val REFERENCE_WIDTH_DP = 392f

// Не даём масштабу уйти в крайности: на совсем маленьких экранах текст не становится
// нечитаемо мелким, а на планшетах/очень широких экранах интерфейс не раздувается в разы.
private const val MIN_SCALE = 0.85f
private const val MAX_SCALE = 1.3f

@Composable
fun GodjiVpnTheme(
    // Тёплая "бумажная" дневная тема v3 (или тёмная — см. GodjiColors.isDark) — системную
    // тему устройства сознательно игнорируем, тема выбирается только тумблером в Настройках.
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val baseDensity = LocalDensity.current
    val scale = (configuration.screenWidthDp / REFERENCE_WIDTH_DP).coerceIn(MIN_SCALE, MAX_SCALE)
    val scaledDensity = Density(
        density = baseDensity.density * scale,
        // Только fontScale, не density — настройка "Размер шрифта" должна менять размер
        // текста, а не отступы/иконки/размеры карточек (те уже отмасштабированы под ширину
        // экрана выше через density).
        fontScale = baseDensity.fontScale * GodjiColors.fontSizePreset.multiplier
    )

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = godjiColorScheme(),
            typography = GodjiTypography,
            content = content
        )
    }
}
