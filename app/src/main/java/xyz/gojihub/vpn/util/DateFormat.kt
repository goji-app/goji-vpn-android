package xyz.gojihub.vpn.util

import xyz.gojihub.vpn.i18n.AppLanguage
import xyz.gojihub.vpn.i18n.Loc
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "2026-09-05T16:11:51Z" → "5 сентября"/"September 5"/"9月5日" в зависимости от текущего языка
 *  интерфейса (Loc.lang). Возвращает исходную строку, если распарсить не удалось. */
fun formatDate(isoDateTime: String): String = runCatching {
    val zdt = Instant.parse(isoDateTime).atZone(ZoneId.systemDefault())
    val (locale, pattern) = when (Loc.lang) {
        AppLanguage.RU -> Locale("ru") to "d MMMM"
        AppLanguage.EN -> Locale.ENGLISH to "MMMM d"
        AppLanguage.ZH -> Locale.CHINESE to "M月d日"
    }
    zdt.format(DateTimeFormatter.ofPattern(pattern, locale))
}.getOrDefault(isoDateTime)
