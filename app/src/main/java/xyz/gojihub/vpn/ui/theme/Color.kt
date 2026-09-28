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

    var Background by mutableStateOf(Color(0xFFEEF1F4))
    var Surface by mutableStateOf(Color(0xFFFFFFFF))
    var SurfaceGlass by mutableStateOf(Color(0xFFF7F9FA))

    var Ink by mutableStateOf(Color(0xFF0B1F1C))
    var InkShadow by mutableStateOf(Color(0xFF051210))
    var TextPrimary by mutableStateOf(Color(0xFF0B1F1C))
    var TextSecondary by mutableStateOf(Color(0xFF55615F))
    var TextMuted by mutableStateOf(Color(0xFF7D8886))

    var Teal by mutableStateOf(Color(0xFF00A79B))
    var TealBright by mutableStateOf(Color(0xFF2AC9BA))
    var TealDeep by mutableStateOf(Color(0xFF007A70))
    var TealTint by mutableStateOf(Color(0xFFDDF3F0))
    var TealTintBorder by mutableStateOf(Color(0xFFB5E5DF))

    var Terracotta by mutableStateOf(Color(0xFFE0693F))
    var TerracottaDeep by mutableStateOf(Color(0xFFB84F2A))
    var TerracottaTint by mutableStateOf(Color(0xFFFCE6DC))
    var TerracottaTintBorder by mutableStateOf(Color(0xFFF3C7B3))

    var Warning by mutableStateOf(Color(0xFFC98B12))
    var Danger by mutableStateOf(Color(0xFFD2432C))
    var JamBg by mutableStateOf(Color(0xFFFFE3D8))
    var JamBorder by mutableStateOf(Color(0xFFF3C7B3))
    var JamText by mutableStateOf(Color(0xFFC2412A))

    var CardBorder by mutableStateOf(Color(0x14000000))
    var CardBorderStrong by mutableStateOf(Color(0x1F000000))
    var ButtonBorder by mutableStateOf(Color(0x80FFFFFF))
    var Chip by mutableStateOf(Color(0x59FFFFFF))
    var TrackBg by mutableStateOf(Color(0x170B1F1C))

    var BorderTeal by mutableStateOf(Color(0x2600A79B))
    var Purple by mutableStateOf(Color(0xFF8EC3EA))

    // ── "Apple Glass" (Liquid Glass) v5 — редизайн 2026-09-28 ───────────────────────────
    // Настоящий backdrop-blur теперь есть (см. CardStyle.kt/GlassBackdrop.kt — Haze,
    // API 31+, на 24–30 фолбэк без блюра на полупрозрачную заливку) — токены ниже описывают
    // тинт стекла, кромку-градиент (яркая сверху-слева → отсвет снизу-справа), внутренние
    // тени (объём) и бегающий блик, плюс фон-подложку под стеклом (BackdropTop/Bottom,
    // Blob1-4, TexDot/TexLine — см. GlassBackdrop.kt).
    var GlassTint by mutableStateOf(Color(0xFFFFFFFF))
    var GlassTintAlpha by mutableStateOf(0.28f)
    var GlassHighlight by mutableStateOf(Color(0x40FFFFFF))
    var GlassHighlightFade by mutableStateOf(Color(0x00FFFFFF))
    var GlassStroke by mutableStateOf(Color(0xFFFFFFFF))
    var GlassStrokeFade by mutableStateOf(Color(0x0FFFFFFF))
    var GlassShadow by mutableStateOf(Color(0x2E3C1E78))
    var GlassRimTop by mutableStateOf(Color(0xFFFFFFFF))
    var GlassRimBottom by mutableStateOf(Color(0xB3FFFFFF))
    var GlassInnerTop by mutableStateOf(Color(0xF2FFFFFF))
    var GlassInnerBottom by mutableStateOf(Color(0x38283C5A))
    var GlassSpot by mutableStateOf(Color(0x8CFFFFFF))
    // Точные токены `hl` (тонкая яркая линия сверху) и `edge` (тонкие линии слева/справа) из
    // реального JS референса (themes.light/dark в GojiGlassFull.dc.html) — отдельные от
    // GlassHighlight (это `gloss`, диагональный широкий блик) и GlassInnerTop/Bottom (это
    // `inner`, широкая мягкая тень объёма). Раньше эти два тонких контурных слоя не рисовались
    // вовсе — стекло из-за этого выглядело площе, чем в макете.
    var GlassHl by mutableStateOf(Color(0xCCFFFFFF))
    var GlassEdge by mutableStateOf(Color(0x4DFFFFFF))
    var BackdropTop by mutableStateOf(Color(0xFFF2F4F7))
    var BackdropMid by mutableStateOf(Color(0xFFE6ECF1))
    var BackdropBottom by mutableStateOf(Color(0xFFDDE7EA))
    var Blob1 by mutableStateOf(Color(0xFF6FD1C4))
    var Blob2 by mutableStateOf(Color(0xFFAFA6F2))
    var Blob3 by mutableStateOf(Color(0xFFF2C2A5))
    var Blob4 by mutableStateOf(Color(0xFF8EC3EA))
    var BlobAlpha by mutableStateOf(0.6f)
    var TexDot by mutableStateOf(Color(0x21284659))
    var TexLine by mutableStateOf(Color(0x12284659))

    fun applyLight() {
        isDark = false
        Background = Color(0xFFEEF1F4); Surface = Color(0xFFFFFFFF); SurfaceGlass = Color(0xFFF7F9FA)
        Ink = Color(0xFF0B1F1C); InkShadow = Color(0xFF051210)
        TextPrimary = Color(0xFF0B1F1C); TextSecondary = Color(0xFF55615F); TextMuted = Color(0xFF7D8886)
        Teal = Color(0xFF00A79B); TealBright = Color(0xFF2AC9BA); TealDeep = Color(0xFF007A70)
        TealTint = Color(0xFFDDF3F0); TealTintBorder = Color(0xFFB5E5DF)
        Terracotta = Color(0xFFE0693F); TerracottaDeep = Color(0xFFB84F2A)
        TerracottaTint = Color(0xFFFCE6DC); TerracottaTintBorder = Color(0xFFF3C7B3)
        Warning = Color(0xFFC98B12); Danger = Color(0xFFD2432C)
        JamBg = Color(0xFFFFE3D8); JamBorder = Color(0xFFF3C7B3); JamText = Color(0xFFC2412A)
        CardBorder = Color(0x14000000); CardBorderStrong = Color(0x1F000000)
        ButtonBorder = Color(0x80FFFFFF); Chip = Color(0x59FFFFFF); TrackBg = Color(0x170B1F1C)
        BorderTeal = Color(0x2600A79B); Purple = Color(0xFF8EC3EA)
        GlassTint = Color(0xFFFFFFFF); GlassTintAlpha = 0.28f
        GlassHighlight = Color(0x40FFFFFF); GlassHighlightFade = Color(0x00FFFFFF)
        GlassStroke = Color(0xFFFFFFFF); GlassStrokeFade = Color(0x0FFFFFFF)
        GlassShadow = Color(0x2E3C1E78)
        GlassRimTop = Color(0xFFFFFFFF); GlassRimBottom = Color(0xB3FFFFFF)
        GlassInnerTop = Color(0xF2FFFFFF); GlassInnerBottom = Color(0x38283C5A); GlassSpot = Color(0x8CFFFFFF)
        GlassHl = Color(0xCCFFFFFF); GlassEdge = Color(0x4DFFFFFF)
        BackdropTop = Color(0xFFF2F4F7); BackdropMid = Color(0xFFE6ECF1); BackdropBottom = Color(0xFFDDE7EA)
        Blob1 = Color(0xFF6FD1C4); Blob2 = Color(0xFFAFA6F2); Blob3 = Color(0xFFF2C2A5); Blob4 = Color(0xFF8EC3EA); BlobAlpha = 0.6f
        TexDot = Color(0x21284659); TexLine = Color(0x12284659)
    }

    fun applyDark() {
        isDark = true
        Background = Color(0xFF080C12); Surface = Color(0xFF121820); SurfaceGlass = Color(0xFF161B24)
        Ink = Color(0xFFF5F7FA); InkShadow = Color(0xFFD9DEE4)
        // Контраст: основной текст почти белый, вторичный — 80% (было 60% → плохо читалось)
        TextPrimary = Color(0xFFF5F7FA); TextSecondary = Color(0xFFC9D1D8); TextMuted = Color(0xFF9AA4AD)
        // Teal/TealDeep — точные accent/accentInk из референса (#5FFFE6/#7FFFEA), а не
        // приблизительный #2FE0CF/#4BEADA, как было раньше: тот тон в реальном JS — это
        // окраска фона "okBg" (rgba(47,224,207,.14)), а не сам акцент.
        Teal = Color(0xFF5FFFE6); TealBright = Color(0xFF34E3D2); TealDeep = Color(0xFF7FFFEA)
        TealTint = Color(0xFF0F2E2C); TealTintBorder = Color(0xFF1B4B47)
        Terracotta = Color(0xFFFF8E62); TerracottaDeep = Color(0xFFFFA57F)
        TerracottaTint = Color(0xFF2E1B14); TerracottaTintBorder = Color(0xFF5A3122)
        Warning = Color(0xFFE0A526); Danger = Color(0xFFFF7A66)
        JamBg = Color(0xFF3A1A12); JamBorder = Color(0xFF5A3122); JamText = Color(0xFFFF8A6E)
        CardBorder = Color(0x1AFFFFFF); CardBorderStrong = Color(0x24FFFFFF)
        ButtonBorder = Color(0x24FFFFFF); Chip = Color(0x14FFFFFF); TrackBg = Color(0x1FFFFFFF)
        BorderTeal = Color(0x262FE0CF); Purple = Color(0xFF4332A8)
        GlassTint = Color(0xFF12161E); GlassTintAlpha = 0.62f
        GlassHighlight = Color(0x12FFFFFF); GlassHighlightFade = Color(0x00FFFFFF)
        GlassStroke = Color(0x24FFFFFF); GlassStrokeFade = Color(0x05FFFFFF)
        GlassShadow = Color(0x590A0528)
        GlassRimTop = Color(0x8CFFFFFF); GlassRimBottom = Color(0x47FFFFFF)
        GlassInnerTop = Color(0x2EFFFFFF); GlassInnerBottom = Color(0x80000000); GlassSpot = Color(0x12FFFFFF)
        GlassHl = Color(0x2EFFFFFF); GlassEdge = Color(0x14FFFFFF)
        BackdropTop = Color(0xFF080C12); BackdropMid = Color(0xFF0C1320); BackdropBottom = Color(0xFF06100F)
        Blob1 = Color(0xFF0F8C80); Blob2 = Color(0xFF4332A8); Blob3 = Color(0xFF8A3D2A); Blob4 = Color(0xFF1B4F8C); BlobAlpha = 0.32f
        TexDot = Color(0x0FC8E6F0); TexLine = Color(0x09A0DCE6)
    }
}
