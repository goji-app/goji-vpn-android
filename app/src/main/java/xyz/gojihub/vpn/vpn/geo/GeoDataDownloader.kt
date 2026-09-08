package xyz.gojihub.vpn.vpn.geo

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * geoip.dat/geosite.dat, встроенные в assets, — это обычный (не российский) набор, с которым
 * приложение когда-то собиралось, и Xray-core их всё равно никогда не открывал напрямую (см.
 * GeoAssets.kt — переменная окружения до Go-рантайма не доходит). Теперь эти файлы читает сам
 * GeoDataParser, в обход Xray, поэтому их актуальность и охват российских сервисов/блокировок —
 * целиком на нас. runetfreedom/russia-v2ray-rules-dat — тот же протобуф-формат (совместим с
 * GeoDataParser), с российской спецификой, обновляется на их стороне каждые 6 часов; здесь
 * просто периодически подтягиваем свежую версию (см. GeoDataRefreshWorker) поверх тех, что
 * скопированы из assets при первом запуске.
 */
object GeoDataDownloader {
    private const val TAG = "GodjiVpn"
    private const val GEOIP_URL =
        "https://raw.githubusercontent.com/runetfreedom/russia-v2ray-rules-dat/release/geoip.dat"
    private const val GEOSITE_URL =
        "https://raw.githubusercontent.com/runetfreedom/russia-v2ray-rules-dat/release/geosite.dat"

    // Оборванная на середине или ошибочная (HTML-страница вместо .dat) закачка не должна
    // вытеснять уже рабочую версию — настоящие geoip.dat/geosite.dat весят от сотен КБ.
    private const val MIN_VALID_SIZE = 50_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun refresh(context: Context): Boolean {
        val dir = File(context.filesDir, "geoassets").apply { mkdirs() }
        val okIp = downloadOne(GEOIP_URL, File(dir, "geoip.dat"))
        val okSite = downloadOne(GEOSITE_URL, File(dir, "geosite.dat"))
        return okIp && okSite
    }

    private fun downloadOne(url: String, dest: File): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GeoDataDownloader: HTTP ${response.code} для $url")
                    return false
                }
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.size < MIN_VALID_SIZE) {
                    Log.w(TAG, "GeoDataDownloader: подозрительно маленький ответ ($url, ${bytes?.size ?: 0} байт) — отброшен")
                    return false
                }
                val tmp = File(dest.parentFile, "${dest.name}.tmp")
                tmp.writeBytes(bytes)
                tmp.renameTo(dest)
                Log.d(TAG, "GeoDataDownloader: $url -> ${dest.name} (${bytes.size} байт)")
                true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "GeoDataDownloader: скачивание $url провалилось", t)
            false
        }
    }
}
