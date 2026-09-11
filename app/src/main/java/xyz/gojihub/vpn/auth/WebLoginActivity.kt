package xyz.gojihub.vpn.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import xyz.gojihub.vpn.MainActivity
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.ui.theme.GodjiVpnTheme
import javax.inject.Inject

/**
 * Нативный обмен кода на токен (POST /api/auth/native/exchange) на бэкенде 7.1.0 сломан —
 * возвращает 400 "invalid request" всегда, независимо от провайдера (проверено на Google и
 * Yandex живыми запросами с логами OkHttp). Обходной путь: показать пользователю ту же самую
 * веб-версию входа, что работает у реальных пользователей сайта каждый день (свой отдельный
 * эндпоинт /api/auth/session/exchange, наш баг не затрагивает), и забрать сессионный токен прямо
 * из куки rw_session_token в CookieManager этого WebView — Chrome Custom Tabs так не дал бы
 * (кука осела бы в его собственном изолированном профиле), а обычный android.webkit.WebView
 * делит CookieManager с самим приложением.
 */
@AndroidEntryPoint
class WebLoginActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)

        setContent {
            GodjiVpnTheme {
                WebLoginScreen(
                    onSessionCookie = { token ->
                        lifecycleScope.launch {
                            authRepository.completeWebLogin(token)
                            startActivity(
                                Intent(this@WebLoginActivity, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            )
                            finish()
                        }
                    },
                    onClose = { finish() }
                )
            }
        }
    }
}

private const val SITE_URL = "https://gojihub.xyz/"
private const val SESSION_COOKIE_NAME = "rw_session_token"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebLoginScreen(onSessionCookie: (String) -> Unit, onClose: () -> Unit) {
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var handled by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = canGoBack) { webViewRef?.goBack() }

    Column(Modifier.fillMaxSize().background(GodjiColors.Background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "‹",
                color = GodjiColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                modifier = Modifier.clickable(onClick = onClose)
            )
            Text(Loc.s.webLoginTitle, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (progress in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = GodjiColors.Teal
            )
        }

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                canGoBack = view.canGoBack()
                                if (handled) return
                                val cookies = CookieManager.getInstance().getCookie(SITE_URL)
                                val token = cookies
                                    ?.split("; ")
                                    ?.firstOrNull { it.startsWith("$SESSION_COOKIE_NAME=") }
                                    ?.substringAfter("$SESSION_COOKIE_NAME=")
                                if (!token.isNullOrBlank()) {
                                    handled = true
                                    CookieManager.getInstance().flush()
                                    onSessionCookie(token)
                                }
                            }
                        }

                        webViewRef = this
                        loadUrl(SITE_URL)
                    }
                }
            )
            if (progress == 0f) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    color = GodjiColors.Teal
                )
            }
        }
    }
}
