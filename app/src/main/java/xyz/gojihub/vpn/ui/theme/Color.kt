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
 * Токены "Tactical Sand & Void" — взяты из макета редизайна Stitch (goji_vpn /
 * tactical_sand_void_dual_system), адаптированы под существующую структуру полей ниже: светлая
 * тема — тёплый "песочный" холст с глубоким изумрудным акцентом, тёмная — AMOLED-чернота с
 * неоновым изумрудом/коралом. Имена и назначение полей не менялись, чтобы не трогать все экраны
 * — обновлены только сами значения.
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

    /** Текущий режим темы (см. ThemeMode) — читается в MainActivity.GodjiApp, чтобы при
     *  SYSTEM живо реагировать на смену системной темы через isSystemInDarkTheme(), а не
     *  только на явный выбор в Настройках. Сам isDark выше остаётся источником истины для
     *  цветов — themeMode лишь определяет, кто им управляет: пользователь или система. */
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    /** См. FontSizePreset — читается реактивно в Theme.kt (GodjiVpnTheme), как и isDark. */
    var fontSizePreset by mutableStateOf(FontSizePreset.NORMAL)

    var Background by mutableStateOf(Color(0xFFF4EFE6))
    var Surface by mutableStateOf(Color(0xFFFFFFFF))
    var SurfaceGlass by mutableStateOf(Color(0xFFFAF7F2))

    var Ink by mutableStateOf(Color(0xFF152220))
    var InkShadow by mutableStateOf(Color(0xFF0B1512))
    var TextPrimary by mutableStateOf(Color(0xFF152220))
    var TextSecondary by mutableStateOf(Color(0xFF4E5D59))
    var TextMuted by mutableStateOf(Color(0xFF7B8A85))

    var Teal by mutableStateOf(Color(0xFF00875A))
    var TealBright by mutableStateOf(Color(0xFF00875A))
    var TealDeep by mutableStateOf(Color(0xFF005235))
    var TealTint by mutableStateOf(Color(0xFFE3F2EA))
    var TealTintBorder by mutableStateOf(Color(0xFFB8DECB))

    var Terracotta by mutableStateOf(Color(0xFFD84A2A))
    var TerracottaDeep by mutableStateOf(Color(0xFF8B1A00))
    var TerracottaTint by mutableStateOf(Color(0xFFFBE4DC))
    var TerracottaTintBorder by mutableStateOf(Color(0xFFF0C0AE))

    var Warning by mutableStateOf(Color(0xFFC9911F))
    var Danger by mutableStateOf(Color(0xFFD84A2A))
    var JamBg by mutableStateOf(Color(0xFFFBE4DC))
    var JamBorder by mutableStateOf(Color(0xFFF0C0AE))
    var JamText by mutableStateOf(Color(0xFF8B1A00))

    var CardBorder by mutableStateOf(Color(0xFFECE5DA))
    var CardBorderStrong by mutableStateOf(Color(0xFFE4DCC8))
    var ButtonBorder by mutableStateOf(Color(0xFFD8CFB8))
    var Chip by mutableStateOf(Color(0xFFECE5DA))
    var TrackBg by mutableStateOf(Color(0xFFECE5DA))

    var BorderTeal by mutableStateOf(Color(0x2600875A))
    var Purple by mutableStateOf(Color(0xFF00838F))

    fun applyLight() {
        isDark = false
        Background = Color(0xFFF4EFE6); Surface = Color(0xFFFFFFFF); SurfaceGlass = Color(0xFFFAF7F2)
        Ink = Color(0xFF152220); InkShadow = Color(0xFF0B1512)
        TextPrimary = Color(0xFF152220); TextSecondary = Color(0xFF4E5D59); TextMuted = Color(0xFF7B8A85)
        Teal = Color(0xFF00875A); TealBright = Color(0xFF00875A); TealDeep = Color(0xFF005235)
        TealTint = Color(0xFFE3F2EA); TealTintBorder = Color(0xFFB8DECB)
        Terracotta = Color(0xFFD84A2A); TerracottaDeep = Color(0xFF8B1A00)
        TerracottaTint = Color(0xFFFBE4DC); TerracottaTintBorder = Color(0xFFF0C0AE)
        Warning = Color(0xFFC9911F); Danger = Color(0xFFD84A2A)
        JamBg = Color(0xFFFBE4DC); JamBorder = Color(0xFFF0C0AE); JamText = Color(0xFF8B1A00)
        CardBorder = Color(0xFFECE5DA); CardBorderStrong = Color(0xFFE4DCC8)
        ButtonBorder = Color(0xFFD8CFB8); Chip = Color(0xFFECE5DA); TrackBg = Color(0xFFECE5DA)
        BorderTeal = Color(0x2600875A); Purple = Color(0xFF00838F)
    }

    fun applyDark() {
        isDark = true
        Background = Color(0xFF0A0E13); Surface = Color(0xFF101419); SurfaceGlass = Color(0xFF181C21)
        // Ink/Surface работают как инвертируемая пара (тёмная кнопка со светлым текстом на
        // светлой теме → светлая кнопка с тёмным текстом на тёмной, ничего в экранах менять
        // не пришлось: и там, и там текст "Surface", фон "Ink").
        Ink = Color(0xFFE0E2EA); InkShadow = Color(0xFFC7CBD4)
        TextPrimary = Color(0xFFE0E2EA); TextSecondary = Color(0xFF849588); TextMuted = Color(0xFF849588)
        Teal = Color(0xFF00F5A0); TealBright = Color(0xFF00F5A0); TealDeep = Color(0xFF00B87A)
        TealTint = Color(0xFF14332A); TealTintBorder = Color(0xFF1F4A3B)
        Terracotta = Color(0xFFFF5E3A); TerracottaDeep = Color(0xFFFF8562)
        TerracottaTint = Color(0xFF2E1D16); TerracottaTintBorder = Color(0xFF5C3323)
        Warning = Color(0xFFE0A63C); Danger = Color(0xFFFF5E3A)
        JamBg = Color(0xFF2E1D16); JamBorder = Color(0xFF5C3323); JamText = Color(0xFFFF8562)
        CardBorder = Color(0xFF262C31); CardBorderStrong = Color(0xFF303840)
        ButtonBorder = Color(0xFF2E363B); Chip = Color(0xFF181C21); TrackBg = Color(0xFF262A30)
        BorderTeal = Color(0x2600F5A0); Purple = Color(0xFF00D2FF)
    }
}
