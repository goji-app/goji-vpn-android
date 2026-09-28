package xyz.gojihub.vpn.network

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import xyz.gojihub.vpn.util.AppLogger
import xyz.gojihub.vpn.util.LogCategory
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * "Публичный IP и DNS сейчас" — не автоматический вердикт "утечки нет/есть" (для этого
 * понадобилась бы своя серверная инфраструктура вроде уникальных поддоменов на запрос), а
 * честная информация для самостоятельной проверки: приложение само исключено из собственного
 * VPN-туннеля (см. GodjiVpnService.establishTunnel — иначе оно не смогло бы достучаться до
 * своего же бэкенда через себя же), поэтому обычный запрос НЕ проходит через тоннель сам по
 * себе. tunnelAwareProxySelector() — тот же приём, что и у бэкенд-клиента (NetworkModule) —
 * заставляет именно этот запрос идти через локальный SOCKS Xray, когда туннель поднят, и
 * напрямую, когда нет: пользователь должен увидеть чужой IP/страну при включённом VPN и свой
 * провайдерский — при выключенном. Если при включённом VPN здесь всё ещё виден провайдер —
 * это и есть практический признак утечки.
 */
@Singleton
class NetworkDiagnostics @Inject constructor(
    @Named("diagnostics") private val client: OkHttpClient,
    @ApplicationContext private val appContext: Context
) {
    data class Result(
        val publicIp: String?,
        val country: String?,
        val dnsServers: List<String>,
        val ipError: Boolean
    )

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        val dns = readActiveDnsServers()
        val (ip, country, error) = fetchPublicIp()
        Result(publicIp = ip, country = country, dnsServers = dns, ipError = error)
    }

    private fun readActiveDnsServers(): List<String> = runCatching {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return@runCatching emptyList()
        val network = cm.activeNetwork ?: return@runCatching emptyList()
        val props = cm.getLinkProperties(network) ?: return@runCatching emptyList()
        props.dnsServers.mapNotNull { it.hostAddress }
    }.getOrElse {
        AppLogger.e(appContext, LogCategory.SUBSCRIPTION, TAG, "readActiveDnsServers failed", it)
        emptyList()
    }

    private fun fetchPublicIp(): Triple<String?, String?, Boolean> {
        return try {
            val request = Request.Builder().url("https://ipwho.is/").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Triple(null, null, true)
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (!json.optBoolean("success", true)) return Triple(null, null, true)
                val ip = json.optString("ip").takeIf { it.isNotBlank() }
                val country = json.optString("country").takeIf { it.isNotBlank() }
                Triple(ip, country, false)
            }
        } catch (e: IOException) {
            AppLogger.e(appContext, LogCategory.SUBSCRIPTION, TAG, "fetchPublicIp failed", e)
            Triple(null, null, true)
        }
    }

    private companion object {
        const val TAG = "GodjiDiag"
    }
}
