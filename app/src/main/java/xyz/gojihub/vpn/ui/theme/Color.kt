package xyz.gojihub.vpn.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Настройка "Размер шрифта" (см. SettingsScreen, секция "Внешний вид") — множитель поверх
 *  системного fontScale (Theme.kt), трогает только sp (текст), а не dp (отступы/иконки/размеры
 *  карточек), в отличие от общего адаптивного масштаба под ширину экрана там же. */
enum class FontSizePreset(val multiplier: Float) { SMALL(0.9f), NORMAL(1f), LARGE(1.15f) }

/**
 * Токены "Daylight" v3 — взяты 1:1 из макета Godji VPN v3 Daylight.dc.html, плюс тёмная
 * версия той же палитры (роли те же, светлота инвертирована).
 *
 * Каждое поле — не val, а var по mutableStateOf: экраны как читали `GodjiColors.Background` и
 * т.п. напрямую (без единого изменения по всему проекту), так и продолжают — а поскольку это
 * теперь Compose State, чтение внутри любого @Composable само подписывает его на
 * перекомпоновку. applyDark()/applyLight() просто переприсваивают все поля разом, и все экраны,
 * что сейчас на экране, перерисовываются в новых цветах без какой-либо доп. проводки.
 */
object GodjiColors {
    /** Нужен отдельным флагом (не выводить "на глаз" из яркости Background) — Theme.kt строит
     *  по нему базовую Material3-схему (lightColorScheme/darkColorScheme), от которой берутся
     *  дефолты для ВСЕХ полей, что мы явно не переопределяем (например outline у
     *  OutlinedTextField) — без этого часть стандартных M3-компонентов держалась бы светлой
     *  палитры даже после переключения на тёмную тему. */
    var isDark by mutableStateOf(false)

    /** См. FontSizePreset — читается реактивно в Theme.kt (GodjiVpnTheme), как и isDark. */
    var fontSizePreset by mutableStateOf(FontSizePreset.NORMAL)

    var Background by mutableStateOf(Color(0xFFEFE9DA))
    var Surface by mutableStateOf(Color(0xFFFFFDF7))
    var SurfaceGlass by mutableStateOf(Color(0xFFFFFDF7))

    var Ink by mutableStateOf(Color(0xFF12312C))
    var InkShadow by mutableStateOf(Color(0xFF0A1F1C))
    var TextPrimary by mutableStateOf(Color(0xFF12312C))
    var TextSecondary by mutableStateOf(Color(0xFF5F736D))
    var TextMuted by mutableStateOf(Color(0xFF5F736D))

    var Teal by mutableStateOf(Color(0xFF00A79B))
    var TealBright by mutableStateOf(Color(0xFF00A79B))
    var TealDeep by mutableStateOf(Color(0xFF076A62))
    var TealTint by mutableStateOf(Color(0xFFE8F2EE))
    var TealTintBorder by mutableStateOf(Color(0xFFBFDCD5))

    var Terracotta by mutableStateOf(Color(0xFFD9714B))
    var TerracottaDeep by mutableStateOf(Color(0xFF8A4526))
    var TerracottaTint by mutableStateOf(Color(0xFFF2E0D5))
    var TerracottaTintBorder by mutableStateOf(Color(0xFFE4C9B8))

    var Warning by mutableStateOf(Color(0xFFC9911F))
    var Danger by mutableStateOf(Color(0xFFC4553C))
    var JamBg by mutableStateOf(Color(0xFFF7E2D8))
    var JamBorder by mutableStateOf(Color(0xFFE4B49B))
    var JamText by mutableStateOf(Color(0xFF8A3A1D))

    var CardBorder by mutableStateOf(Color(0xFFE7DFCA))
    var CardBorderStrong by mutableStateOf(Color(0xFFE2D9C4))
    var ButtonBorder by mutableStateOf(Color(0xFFD8CFB8))
    var Chip by mutableStateOf(Color(0xFFEFE7D4))
    var TrackBg by mutableStateOf(Color(0xFFE0D5BA))

    var BorderTeal by mutableStateOf(Color(0x2600A79B))
    var Purple by mutableStateOf(Color(0xFF8A4526))

    fun applyLight() {
        isDark = false
        Background = Color(0xFFEFE9DA); Surface = Color(0xFFFFFDF7); SurfaceGlass = Color(0xFFFFFDF7)
        Ink = Color(0xFF12312C); InkShadow = Color(0xFF0A1F1C)
        TextPrimary = Color(0xFF12312C); TextSecondary = Color(0xFF5F736D); TextMuted = Color(0xFF5F736D)
        Teal = Color(0xFF00A79B); TealBright = Color(0xFF00A79B); TealDeep = Color(0xFF076A62)
        TealTint = Color(0xFFE8F2EE); TealTintBorder = Color(0xFFBFDCD5)
        Terracotta = Color(0xFFD9714B); TerracottaDeep = Color(0xFF8A4526)
        TerracottaTint = Color(0xFFF2E0D5); TerracottaTintBorder = Color(0xFFE4C9B8)
        Warning = Color(0xFFC9911F); Danger = Color(0xFFC4553C)
        JamBg = Color(0xFFF7E2D8); JamBorder = Color(0xFFE4B49B); JamText = Color(0xFF8A3A1D)
        CardBorder = Color(0xFFE7DFCA); CardBorderStrong = Color(0xFFE2D9C4)
        ButtonBorder = Color(0xFFD8CFB8); Chip = Color(0xFFEFE7D4); TrackBg = Color(0xFFE0D5BA)
        BorderTeal = Color(0x2600A79B); Purple = Color(0xFF8A4526)
    }

    fun applyDark() {
        isDark = true
        Background = Color(0xFF121614); Surface = Color(0xFF1B211E); SurfaceGlass = Color(0xFF1B211E)
        // Ink/Surface работают как инвертируемая пара (тёмная кнопка со светлым текстом на
        // светлой теме → светлая кнопка с тёмным текстом на тёмной, ничего в экранах менять
        // не пришлось: и там, и там текст "Surface", фон "Ink").
        Ink = Color(0xFFE9EDEB); InkShadow = Color(0xFFD3DBD8)
        TextPrimary = Color(0xFFEAEFEC); TextSecondary = Color(0xFF93A39C); TextMuted = Color(0xFF93A39C)
        Teal = Color(0xFF2BC8B8); TealBright = Color(0xFF2BC8B8); TealDeep = Color(0xFF6BE0D2)
        TealTint = Color(0xFF17332E); TealTintBorder = Color(0xFF2C5049)
        Terracotta = Color(0xFFE98863); TerracottaDeep = Color(0xFFFFB294)
        TerracottaTint = Color(0xFF3A241C); TerracottaTintBorder = Color(0xFF5A3A2C)
        Warning = Color(0xFFE0A63C); Danger = Color(0xFFE2765F)
        JamBg = Color(0xFF3A2A20); JamBorder = Color(0xFF5C4230); JamText = Color(0xFFF0B294)
        CardBorder = Color(0xFF2A3230); CardBorderStrong = Color(0xFF333D3A)
        ButtonBorder = Color(0xFF3A433F); Chip = Color(0xFF232C29); TrackBg = Color(0xFF2C332E)
        BorderTeal = Color(0x262BC8B8); Purple = Color(0xFFFFB294)
    }
}
