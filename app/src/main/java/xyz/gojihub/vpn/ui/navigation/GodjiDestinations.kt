package xyz.gojihub.vpn.ui.navigation

object GodjiDestinations {
    const val LOGIN = "login"
    const val VERIFY_EMAIL = "verify_email/{email}"
    const val CONNECT = "connect"
    const val SERVERS = "servers"
    const val PLANS = "plans"
    const val SETTINGS = "settings"
    const val PING_SETTINGS = "ping_settings"
    const val LOG_LEVEL = "log_level"

    fun verifyEmail(email: String) = "verify_email/$email"
}
