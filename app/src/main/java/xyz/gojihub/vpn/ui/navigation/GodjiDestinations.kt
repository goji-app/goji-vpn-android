package xyz.gojihub.vpn.ui.navigation

object GodjiDestinations {
    const val LOGIN = "login"
    const val VERIFY_EMAIL = "verify_email/{email}"
    // До редизайна "Apple Glass" (2026-09-28) это были 4 независимых NavHost-назначения —
    // теперь это одна страница MAIN, внутри которой HorizontalPager листает те же 4 экрана как
    // страницы (см. MainActivity.GodjiApp/MainTabsScreen). CONNECT/SERVERS/PLANS/SETTINGS
    // оставлены как есть (не route, а просто стабильные ID вкладок) — используются как индексы
    // в GodjiTab и там, где раньше был navController.navigate(SERVERS) и т.п., теперь
    // pagerState.animateScrollToPage(GodjiTab.entries.indexOf(...)).
    const val MAIN = "main"
    const val CONNECT = "connect"
    const val SERVERS = "servers"
    const val PLANS = "plans"
    const val SETTINGS = "settings"
    const val PING_SETTINGS = "ping_settings"
    const val LOG_LEVEL = "log_level"
    const val APP_TUNNELING = "app_tunneling"
    const val SUPPORT_LIST = "support_list"
    const val SUPPORT_NEW = "support_new"
    const val SUPPORT_TICKET = "support_ticket/{ticketId}"
    const val SUPPORT_FAQ = "support_faq"

    fun verifyEmail(email: String) = "verify_email/$email"
    fun supportTicket(ticketId: Long) = "support_ticket/$ticketId"
}
