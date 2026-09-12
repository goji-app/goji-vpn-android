package xyz.gojihub.vpn.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import xyz.gojihub.vpn.BuildConfig
import java.util.concurrent.TimeUnit

data class UpdateInfo(val version: String, val changelog: String, val apkUrl: String)

/**
 * Проверяет последний релиз в публичном GitHub-репозитории (goji-app/goji-vpn-android,
 * см. ARCHITECTURE.md/README — это тот же репозиторий, куда публикуются релизы с APK) и
 * сравнивает его версию с установленной. GitHub REST API отдаёт данные без авторизации, но
 * требует непустой User-Agent — без него отвечает 403 (обычная антибот-мера GitHub, не
 * специфика этого проекта). Используем тот же формат, что и для запросов к бэкенду.
 */
object AppUpdateChecker {
    private const val TAG = "GodjiUpdate"
    private const val RELEASES_URL = "https://api.github.com/repos/goji-app/goji-vpn-android/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** null — либо ошибка сети/API, либо установленная версия уже последняя.
     *  withContext(IO) обязателен именно здесь, а не полагается на диспетчер вызывающего —
     *  client.newCall(...).execute() блокирующий, а SettingsViewModel.checkForUpdate() запускает
     *  эту suspend-функцию через обычный viewModelScope.launch (Dispatchers.Main.immediate по
     *  умолчанию). Без этого ручная проверка по кнопке "Проверить обновления" валилась с
     *  NetworkOnMainThreadException, которое тихо проглатывалось runCatching ниже и снаружи
     *  выглядело как "у вас установлена последняя версия" даже когда на GitHub давно вышел
     *  новый релиз. */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("User-Agent", "Goji/${BuildConfig.VERSION_NAME}/Android")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                // Теги релизов — vX.Y.Z (см. workflow релизов), убираем префикс для сравнения
                // с BuildConfig.VERSION_NAME, где его нет.
                val remoteVersion = json.optString("tag_name").removePrefix("v")
                if (remoteVersion.isBlank() || !isNewer(remoteVersion, BuildConfig.VERSION_NAME)) return@use null

                val assets = json.optJSONArray("assets") ?: return@use null
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                if (apkUrl.isNullOrBlank()) return@use null

                UpdateInfo(
                    version = remoteVersion,
                    changelog = json.optString("body").ifBlank { json.optString("name") },
                    apkUrl = apkUrl
                )
            }
        }.onFailure { Log.w(TAG, "checkForUpdate failed", it) }.getOrNull()
    }

    /** Посегментное сравнение вида "1.0.29" > "1.0.28" — без поддержки суффиксов
     *  ("-beta" и т.п.), в тегах этого проекта их не бывает. */
    fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }
}
