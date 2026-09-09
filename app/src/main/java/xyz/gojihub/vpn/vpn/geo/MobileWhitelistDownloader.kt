package xyz.gojihub.vpn.vpn.geo

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * hxehex/russia-mobile-internet-whitelist — список доменов/IP, живьём собранный
 * сообществом во время вайтлистинга мобильного интернета в РФ (см. MobileWhitelist для
 * формата и точку подстановки в GodjiVpnService.resolveGeoDataRules). В отличие от
 * category-ru (runetfreedom/russia-v2ray-rules-dat, обновляется каждые 6 часов), этот
 * репозиторий пополняется вручную по мере новых замеров — раз в неделю (см.
 * MobileWhitelistRefreshWorker) достаточно, чтобы не отставать от реальных изменений.
 */
object MobileWhitelistDownloader {
    private const val TAG = "GodjiVpn"
    private const val DOMAINS_URL =
        "https://raw.githubusercontent.com/hxehex/russia-mobile-internet-whitelist/main/whitelist.txt"
    private const val CIDR_URL =
        "https://raw.githubusercontent.com/hxehex/russia-mobile-internet-whitelist/main/cidrwhitelist.txt"

    // Оборванная/ошибочная закачка (HTML-страница вместо текста) не должна вытеснять уже
    // рабочую версию — реальные файлы весят по крайней мере несколько КБ.
    private const val MIN_VALID_SIZE = 1_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun refresh(context: Context): Boolean {
        val dir = File(context.filesDir, "geoassets").apply { mkdirs() }
        val okDomains = downloadOne(DOMAINS_URL, File(dir, "mobile_whitelist_domains.txt"))
        val okCidr = downloadOne(CIDR_URL, File(dir, "mobile_whitelist_cidr.txt"))
        return okDomains && okCidr
    }

    private fun downloadOne(url: String, dest: File): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "MobileWhitelistDownloader: HTTP ${response.code} для $url")
                    return false
                }
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.size < MIN_VALID_SIZE) {
                    Log.w(TAG, "MobileWhitelistDownloader: подозрительно маленький ответ ($url, ${bytes?.size ?: 0} байт) — отброшен")
                    return false
                }
                val tmp = File(dest.parentFile, "${dest.name}.tmp")
                tmp.writeBytes(bytes)
                tmp.renameTo(dest)
                Log.d(TAG, "MobileWhitelistDownloader: $url -> ${dest.name} (${bytes.size} байт)")
                true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "MobileWhitelistDownloader: скачивание $url провалилось", t)
            false
        }
    }
}
