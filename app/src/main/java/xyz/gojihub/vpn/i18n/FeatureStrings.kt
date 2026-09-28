package xyz.gojihub.vpn.i18n

/**
 * Строки функций, добавленных после редизайна 1.0.105 (плитка в шторке, ярлыки, сортировка
 * серверов, проверка утечек, правила сетей и т.д.) — отдельным классом, как SupportStrings,
 * чтобы не раздувать и без того огромный Strings. Доступ — Loc.f.xxx.
 */
data class FeatureStrings(
    // ── Вибрация ──
    val hapticsTitle: String,
    val hapticsDesc: String,
    // ── Сортировка серверов ──
    val sortFavorites: String,
    val sortPing: String,
    val sortName: String,
) {
    companion object {
        fun forLang(lang: AppLanguage): FeatureStrings = when (lang) {
            AppLanguage.RU -> RU
            AppLanguage.EN -> EN
            AppLanguage.ZH -> ZH
        }

        private val RU = FeatureStrings(
            hapticsTitle = "Вибрация",
            hapticsDesc = "Короткий отклик при подключении и отключении VPN",
            sortFavorites = "Избранные",
            sortPing = "Пинг",
            sortName = "А–Я",
        )

        private val EN = FeatureStrings(
            hapticsTitle = "Vibration",
            hapticsDesc = "A short buzz when the VPN connects or disconnects",
            sortFavorites = "Favorites",
            sortPing = "Ping",
            sortName = "A–Z",
        )

        private val ZH = FeatureStrings(
            hapticsTitle = "振动",
            hapticsDesc = "VPN 连接或断开时短暂振动",
            sortFavorites = "收藏",
            sortPing = "延迟",
            sortName = "名称",
        )
    }
}
