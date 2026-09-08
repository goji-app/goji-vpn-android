package xyz.gojihub.vpn.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import xyz.gojihub.vpn.network.RemnawaveApi
import xyz.gojihub.vpn.network.models.ConsentRequest
import xyz.gojihub.vpn.network.models.NativeExchangeRequest
import xyz.gojihub.vpn.network.models.SendOtpRequest
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.network.models.VerifyOtpRequest
import xyz.gojihub.vpn.util.AppLogger
import xyz.gojihub.vpn.util.LogCategory
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/** Deeplink, на который сервер редиректит браузер после провайдерского OAuth-callback.
 *  Схему нужно один раз добавить в панели: Настройки → Мобильные приложения →
 *  "Разрешённые схемы app_redirect". Без этого сервер откажется на неё редиректить. */
private const val OAUTH_REDIRECT_URI = "godjivpn://oauth2redirect"

@Singleton
class AuthRepository @Inject constructor(
    private val api: RemnawaveApi,
    private val tokenManager: TokenManager,
    @ApplicationContext private val appContext: Context
) {
    // PKCE verifier/provider живут между стартом OAuth (открытие Custom Tabs) и возвратом
    // deeplink'ом — раньше держались только в памяти, и это было сознательно принятым риском
    // ("если процесс убьют посреди авторизации в браузере, придётся начать заново"). На практике
    // это ломает именно вход через Telegram: подтверждение там требует переключиться в другое
    // приложение и вручную найти уведомление — команда занимает намного больше времени, чем
    // "нажать один аккаунт" у Google/Яндекса, и Android с гораздо большей вероятностью успевает
    // убить наш процесс в фоне за это время. После этого callback приходит без pendingProvider(),
    // и OAuthCallbackActivity молча завершается — пользователь видит "зависание" вместо ошибки.
    // Переживает смерть процесса — обычный SharedPreferences, не Encrypted: verifier одноразовый,
    // короткоживущий и бесполезен без code, привязанного к нему на сервере.
    private val pendingPrefs = appContext.getSharedPreferences("godji_pending_oauth", Context.MODE_PRIVATE)

    private var pendingCodeVerifier: String?
        get() = pendingPrefs.getString(KEY_VERIFIER, null)
        set(value) { pendingPrefs.edit().putString(KEY_VERIFIER, value).apply() }

    private var pendingProviderValue: String?
        get() = pendingPrefs.getString(KEY_PROVIDER, null)
        set(value) { pendingPrefs.edit().putString(KEY_PROVIDER, value).apply() }

    suspend fun sendOtp(email: String): AuthResult = runCatching {
        api.sendOtp(SendOtpRequest(email))
        AuthResult.Success
    }.getOrElse {
        AppLogger.e(appContext, LogCategory.MAIN, TAG, "sendOtp failed", it)
        AuthResult.Error(it.message ?: Loc.s.errorOtpFailed)
    }

    suspend fun verifyOtp(email: String, code: String): AuthResult = runCatching {
        val response = api.verifyOtp(VerifyOtpRequest(email, code))
        onAuthenticated(response.token, response.expiresIn)
        AuthResult.Success
    }.getOrElse {
        AppLogger.e(appContext, LogCategory.MAIN, TAG, "verifyOtp failed", it)
        AuthResult.Error(it.message ?: Loc.s.errorWrongCode)
    }

    /** provider: "google" | "yandex" | "telegram-oidc". Возвращает URL для Custom Tabs.
     *  (Проверено: /start всегда отдаёт голый JSON, даже при прямой навигации браузером —
     *  открывать его напрямую бессмысленно, дёргаем через API как и раньше.) */
    suspend fun startOAuth(provider: String): Result<String> = runCatching {
        val verifier = Pkce.generateVerifier()
        val response = api.startOAuth(
            provider = provider,
            appRedirect = OAUTH_REDIRECT_URI,
            codeChallenge = Pkce.challengeFor(verifier)
        )
        pendingCodeVerifier = verifier
        pendingProviderValue = provider
        response.authUrl
    }

    fun pendingProvider(): String? = pendingProviderValue

    /** Вызывается из OAuthCallbackActivity после возврата deeplink'ом из браузера. */
    suspend fun completeOAuth(provider: String, code: String): AuthResult {
        val verifier = pendingCodeVerifier
            ?: return AuthResult.Error(Loc.s.errorOAuthSessionExpired)
        return runCatching {
            val response = api.exchangeNativeOAuth(NativeExchangeRequest(code, verifier, provider))
            onAuthenticated(response.accessToken, response.expiresIn)
            pendingCodeVerifier = null
            pendingProviderValue = null
            AuthResult.Success
        }.getOrElse {
            AppLogger.e(appContext, LogCategory.MAIN, TAG, "completeOAuth($provider) failed", it)
            AuthResult.Error(it.message ?: Loc.s.errorOAuthCompleteFailed)
        }
    }

    private suspend fun onAuthenticated(token: String, expiresInSeconds: Long) {
        tokenManager.save(token, expiresInSeconds)
        // Согласия на обработку данных / условия использования — требуются один раз после
        // первой регистрации. Если бэкенд их не запрашивал, вызов просто ни на что не влияет.
        runCatching {
            if (api.getMe().consentRequired) {
                api.consent(ConsentRequest())
            }
        }
    }

    fun logout() = tokenManager.clear()
    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    private companion object {
        const val KEY_VERIFIER = "verifier"
        const val KEY_PROVIDER = "provider"
        const val TAG = "GodjiAuth"
    }
}
