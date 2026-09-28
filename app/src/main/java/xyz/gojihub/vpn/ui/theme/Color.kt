package xyz.gojihub.vpn.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class FontSizePreset(val multiplier: Float) { SMALL(0.9f), NORMAL(1f), LARGE(1.15f) }

/**
 * Токены дизайна v5 «Стекло» (эталон: handoff-1.0.77/screens/, файлы *.png).
 * Имена старых полей НЕ менялись — экраны 1.0.77 продолжают компилироваться как есть.
 *
 * ВАЖНО: Background = Transparent. Все экраны делают `.background(GodjiColors.Background)` —
 * теперь это no-op, и под экранами виден общий фон GlassBackdrop (см. MainActivity).
 * Для мест, где нужен сплошной цвет (отдельная Activity, диалог), есть BackgroundSolid.
 */
object GodjiColors {
    var isDark by mutableStateOf(false)
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var fontSizePreset by mutableStateOf(FontSizePreset.NORMAL)

    // ── старые поля (значения v5) ─────────────────────────────
    var Background by mutableStateOf(Color.Transparent)
    var BackgroundSolid by mutableStateOf(Color(0xFFE6ECF1))
    var Surface by mutableStateOf(Color(0xFFFFFFFF))          // текст на акценте / на Ink-кнопке
    var SurfaceGlass by mutableStateOf(Color(0x59FFFFFF))

    var Ink by mutableStateOf(Color(0xFF0B1F1C))
    var InkShadow by mutableStateOf(Color(0xFF06120F))
    var TextPrimary by mutableStateOf(Color(0xFF0B1F1C))
    var TextSecondary by mutableStateOf(Color(0x9E0B1F1C))    // rgba(11,31,28,.62)
    var TextMuted by mutableStateOf(Color(0x7A0B1F1C))

    var Teal by mutableStateOf(Color(0xFF00A79B))             // accent
    var TealBright by mutableStateOf(Color(0xFF2AC9BA))
    var TealDeep by mutableStateOf(Color(0xFF007A70))         // accentInk — акцентный ТЕКСТ
    var TealTint by mutableStateOf(Color(0x2400A79B))         // okBg .14
    var TealTintBorder by mutableStateOf(Color(0x5900A79B))

    var Terracotta by mutableStateOf(Color(0xFFE0693F))       // warm
    var TerracottaDeep by mutableStateOf(Color(0xFFB2482A))
    var TerracottaTint by mutableStateOf(Color(0x80FFD6C4))   // warmGlass
    var TerracottaTintBorder by mutableStateOf(Color(0x80FFFFFF))

    var Warning by mutableStateOf(Color(0xFFC98B12))
    var Danger by mutableStateOf(Color(0xFFD2432C))
    var JamBg by mutableStateOf(Color(0x8CFFC8B9))
    var JamBorder by mutableStateOf(Color(0x80FFFFFF))
    var JamText by mutableStateOf(Color(0xFFC2412A))

    var CardBorder by mutableStateOf(Color(0x80FFFFFF))       // stroke
    var CardBorderStrong by mutableStateOf(Color(0x80FFFFFF))
    var ButtonBorder by mutableStateOf(Color(0x80FFFFFF))
    var Chip by mutableStateOf(Color(0x59FFFFFF))             // chip .35
    var TrackBg by mutableStateOf(Color(0x170B1F1C))          // track .09

    var BorderTeal by mutableStateOf(Color(0x2600A79B))
    var Purple by mutableStateOf(Color(0xFF5B3BD6))

    // ── новые поля стекла ─────────────────────────────────────
    var Glass by mutableStateOf(Color(0x33FFFFFF))            // .20 — карточки
    var GlassStrong by mutableStateOf(Color(0x66FFFFFF))      // .40 — таб-бар, лист входа
    var GlassFaint by mutableStateOf(Color(0x29FFFFFF))       // .16
    var Hair by mutableStateOf(Color(0x140B1F1C))             // разделители .08
    var Hl by mutableStateOf(Color(0xCCFFFFFF))               // верхняя светлая кромка .8
    var GlassSpot by mutableStateOf(Color(0x8CFFFFFF))        // бегающий блик .55
    var GlassGloss by mutableStateOf(Color(0x40FFFFFF))       // диагональный глянец .25
    var RimA by mutableStateOf(Color(0xFFFFFFFF)); var RimB by mutableStateOf(Color(0x40FFFFFF))
    var RimC by mutableStateOf(Color(0x0FFFFFFF)); var RimD by mutableStateOf(Color(0xB3FFFFFF))
    var InnerTop by mutableStateOf(Color(0xF2FFFFFF))
    var InnerBottom by mutableStateOf(Color(0x38283C5A))      // rgba(40,60,90,.22)
    var GlassShadow by mutableStateOf(Color(0x2E3C1E78))      // rgba(60,30,120,.18)
    var AccentGradTop by mutableStateOf(Color(0xFF2AC9BA))
    var AccentGradMid by mutableStateOf(Color(0xFF00A79B))
    var AccentGradBottom by mutableStateOf(Color(0xFF008F84))
    var AccentGlow by mutableStateOf(Color(0x5900A79B))
    var SelBg by mutableStateOf(Color(0x1A00A79B))
    var Thumb by mutableStateOf(Color(0xEBFFFFFF))            // бегунок сегмент-контрола
    var Lens by mutableStateOf(Color(0xBFFFFFFF))             // линза выбранной вкладки
    var RingCore by mutableStateOf(Color(0xE0FFFFFF))
    var InkBtn by mutableStateOf(Color(0xFF0B1F1C)); var InkBtnText by mutableStateOf(Color(0xFFF4FAF9))
    // фон (GlassBackdrop)
    var BaseTop by mutableStateOf(Color(0xFFF2F4F7)); var BaseMid by mutableStateOf(Color(0xFFE6ECF1)); var BaseBottom by mutableStateOf(Color(0xFFDDE7EA))
    var Blob1 by mutableStateOf(Color(0xFF6FD1C4)); var Blob2 by mutableStateOf(Color(0xFFAFA6F2))
    var Blob3 by mutableStateOf(Color(0xFFF2C2A5)); var Blob4 by mutableStateOf(Color(0xFF8EC3EA))
    var BlobAlpha by mutableStateOf(0.6f)
    var TexLine by mutableStateOf(Color(0x12284660))          // rgba(40,70,90,.07)
    var TexDot by mutableStateOf(Color(0x21284660))           // rgba(40,70,90,.13)

    fun applyLight() {
        isDark = false
        Background = Color.Transparent; BackgroundSolid = Color(0xFFE6ECF1)
        Surface = Color(0xFFFFFFFF); SurfaceGlass = Color(0x59FFFFFF)
        Ink = Color(0xFF0B1F1C); InkShadow = Color(0xFF06120F)
        TextPrimary = Color(0xFF0B1F1C); TextSecondary = Color(0x9E0B1F1C); TextMuted = Color(0x7A0B1F1C)
        Teal = Color(0xFF00A79B); TealBright = Color(0xFF2AC9BA); TealDeep = Color(0xFF007A70)
        TealTint = Color(0x2400A79B); TealTintBorder = Color(0x5900A79B)
        Terracotta = Color(0xFFE0693F); TerracottaDeep = Color(0xFFB2482A)
        TerracottaTint = Color(0x80FFD6C4); TerracottaTintBorder = Color(0x80FFFFFF)
        Warning = Color(0xFFC98B12); Danger = Color(0xFFD2432C)
        JamBg = Color(0x8CFFC8B9); JamBorder = Color(0x80FFFFFF); JamText = Color(0xFFC2412A)
        CardBorder = Color(0x80FFFFFF); CardBorderStrong = Color(0x80FFFFFF); ButtonBorder = Color(0x80FFFFFF)
        Chip = Color(0x59FFFFFF); TrackBg = Color(0x170B1F1C)
        BorderTeal = Color(0x2600A79B); Purple = Color(0xFF5B3BD6)
        Glass = Color(0x33FFFFFF); GlassStrong = Color(0x66FFFFFF); GlassFaint = Color(0x29FFFFFF)
        Hair = Color(0x140B1F1C); Hl = Color(0xCCFFFFFF); GlassSpot = Color(0x8CFFFFFF); GlassGloss = Color(0x40FFFFFF)
        RimA = Color(0xFFFFFFFF); RimB = Color(0x40FFFFFF); RimC = Color(0x0FFFFFFF); RimD = Color(0xB3FFFFFF)
        InnerTop = Color(0xF2FFFFFF); InnerBottom = Color(0x38283C5A); GlassShadow = Color(0x2E3C1E78)
        AccentGradTop = Color(0xFF2AC9BA); AccentGradMid = Color(0xFF00A79B); AccentGradBottom = Color(0xFF008F84)
        AccentGlow = Color(0x5900A79B); SelBg = Color(0x1A00A79B)
        Thumb = Color(0xEBFFFFFF); Lens = Color(0xBFFFFFFF); RingCore = Color(0xE0FFFFFF)
        InkBtn = Color(0xFF0B1F1C); InkBtnText = Color(0xFFF4FAF9)
        BaseTop = Color(0xFFF2F4F7); BaseMid = Color(0xFFE6ECF1); BaseBottom = Color(0xFFDDE7EA)
        Blob1 = Color(0xFF6FD1C4); Blob2 = Color(0xFFAFA6F2); Blob3 = Color(0xFFF2C2A5); Blob4 = Color(0xFF8EC3EA)
        BlobAlpha = 0.6f; TexLine = Color(0x12284660); TexDot = Color(0x21284660)
    }

    fun applyDark() {
        isDark = true
        Background = Color.Transparent; BackgroundSolid = Color(0xFF0C1320)
        // Surface в экранах = цвет текста на акцентной/Ink-кнопке → на тёмной теме он тёмный.
        Surface = Color(0xFF0B1F1C); SurfaceGlass = Color(0x14FFFFFF)
        Ink = Color(0xFFF1F7F6); InkShadow = Color(0xFFC7CBD4)
        TextPrimary = Color(0xFFF5F7FA); TextSecondary = Color(0xCCE8EEF4); TextMuted = Color(0x99E8EEF4)
        Teal = Color(0xFF5FFFE6); TealBright = Color(0xFF34E3D2); TealDeep = Color(0xFF7FFFEA)
        TealTint = Color(0x242FE0CF); TealTintBorder = Color(0x595FFFE6)
        Terracotta = Color(0xFFFF8E62); TerracottaDeep = Color(0xFFFFB08E)
        TerracottaTint = Color(0x6178321C); TerracottaTintBorder = Color(0x24FFFFFF)
        Warning = Color(0xFFE0A63C); Danger = Color(0xFFFF7A66)
        JamBg = Color(0x66782819); JamBorder = Color(0x24FFFFFF); JamText = Color(0xFFFF8A6E)
        CardBorder = Color(0x24FFFFFF); CardBorderStrong = Color(0x24FFFFFF); ButtonBorder = Color(0x24FFFFFF)
        Chip = Color(0x14FFFFFF); TrackBg = Color(0x1FFFFFFF)
        BorderTeal = Color(0x262FE0CF); Purple = Color(0xFF8B5CFF)
        Glass = Color(0x9E12161E); GlassStrong = Color(0xBD161A24); GlassFaint = Color(0x6612161E)
        Hair = Color(0x1AFFFFFF); Hl = Color(0x2EFFFFFF); GlassSpot = Color(0x12FFFFFF); GlassGloss = Color(0x12FFFFFF)
        RimA = Color(0x8CFFFFFF); RimB = Color(0x14FFFFFF); RimC = Color(0x05FFFFFF); RimD = Color(0x47FFFFFF)
        InnerTop = Color(0x47FFFFFF); InnerBottom = Color(0x80000000); GlassShadow = Color(0x590A0528)
        AccentGradTop = Color(0xFF34E3D2); AccentGradMid = Color(0xFF10B8A9); AccentGradBottom = Color(0xFF079487)
        AccentGlow = Color(0x592FE0CF); SelBg = Color(0x172FE0CF)
        Thumb = Color(0x29FFFFFF); Lens = Color(0x2EFFFFFF); RingCore = Color(0xE610141C)
        InkBtn = Color(0xFFF1F7F6); InkBtnText = Color(0xFF0B1F1C)
        BaseTop = Color(0xFF080C12); BaseMid = Color(0xFF0C1320); BaseBottom = Color(0xFF06100F)
        Blob1 = Color(0xFF0F8C80); Blob2 = Color(0xFF4332A8); Blob3 = Color(0xFF8A3D2A); Blob4 = Color(0xFF1B4F8C)
        BlobAlpha = 0.32f; TexLine = Color(0x09A0DCE6); TexDot = Color(0x0FC8E6F0)
    }
}
