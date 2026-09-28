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
    // ── Напоминания об окончании подписки ──
    val expiry3dTitle: String,
    val expiry1dTitle: String,
    val expiry3dText: (plan: String, date: String) -> String,
    val expiry1dText: (plan: String, date: String) -> String,
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
            expiry3dTitle = "Подписка закончится через 3 дня",
            expiry1dTitle = "Подписка закончится завтра",
            expiry3dText = { plan, date -> "Тариф «$plan» действует до $date. Продли заранее — VPN не отключится в самый неудобный момент." },
            expiry1dText = { plan, date -> "Тариф «$plan» заканчивается $date. Продли сейчас, чтобы не остаться без VPN." },
        )

        private val EN = FeatureStrings(
            hapticsTitle = "Vibration",
            hapticsDesc = "A short buzz when the VPN connects or disconnects",
            sortFavorites = "Favorites",
            sortPing = "Ping",
            sortName = "A–Z",
            expiry3dTitle = "Your plan ends in 3 days",
            expiry1dTitle = "Your plan ends tomorrow",
            expiry3dText = { plan, date -> "«$plan» is active until $date. Extend it in advance so the VPN doesn't stop at the worst moment." },
            expiry1dText = { plan, date -> "«$plan» ends on $date. Extend it now to keep your VPN working." },
        )

        private val ZH = FeatureStrings(
            hapticsTitle = "振动",
            hapticsDesc = "VPN 连接或断开时短暂振动",
            sortFavorites = "收藏",
            sortPing = "延迟",
            sortName = "名称",
            expiry3dTitle = "订阅将在 3 天后到期",
            expiry1dTitle = "订阅将于明天到期",
            expiry3dText = { plan, date -> "「$plan」有效期至 $date。请提前续费，以免 VPN 在关键时刻中断。" },
            expiry1dText = { plan, date -> "「$plan」将于 $date 到期。请立即续费，以免 VPN 停止工作。" },
        )
    }
}
