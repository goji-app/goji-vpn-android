package xyz.gojihub.vpn.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import xyz.gojihub.vpn.i18n.AppLanguage
import xyz.gojihub.vpn.ui.theme.FontSizePreset
import xyz.gojihub.vpn.util.LogLevel
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "godji_settings")

/** Способ замера пинга серверов — см. ServersScreen/PingRepository.
 *  PROXY_GET/PROXY_HEAD — через встроенный в libXray pingBatch (реальный HTTP-запрос через
 *  временный Xray-инстанс на кандидата, учитывает задержку самого прокси-протокола, не только
 *  сетевой RTT); PROXY_HEAD передаёт HTTP-метод в тот же payload — если конкретная сборка
 *  libXray его не читает, тихо ведёт себя как GET (не ломается, просто не даёт экономии).
 *  TCP — обычный TCP-connect до host:port узла, без прокси (быстрее, но не видит реальную
 *  задержку самого VLESS/Reality-протокола). ICMP — системная утилита ping (без root; работает,
 *  потому что бинарник /system/bin/ping имеет нужный capability на большинстве прошивок). */
enum class PingMethod { PROXY_GET, PROXY_HEAD, TCP, ICMP }

/** Per-app proxy (см. AppTunnelingScreen) — та же идея, что "Прокси для выбранных приложений"
 *  у Happ. OFF: без пользовательского списка (только встроенный RU_APPS_EXCLUDED_FROM_VPN в
 *  GodjiVpnService). ALLOW: через VPN идут ТОЛЬКО выбранные пакеты, остальное — напрямую
 *  (Android VpnService.Builder.addAllowedApplication). BYPASS: выбранные пакеты идут напрямую,
 *  остальное — через VPN (addDisallowedApplication, объединяется со встроенным RU-списком). */
enum class PerAppProxyMode { OFF, ALLOW, BYPASS }

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val preferredNodeKey = stringPreferencesKey("preferred_node_id")
    private val pinNotificationKey = booleanPreferencesKey("pin_notification")
    private val pingMethodKey = stringPreferencesKey("ping_method")
    private val pingTestUrlKey = stringPreferencesKey("ping_test_url")
    private val darkThemeKey = booleanPreferencesKey("dark_theme_enabled")
    private val appLanguageKey = stringPreferencesKey("app_language")
    private val logLevelKey = stringPreferencesKey("log_level")
    private val fontSizeKey = stringPreferencesKey("font_size_preset")
    private val autoConnectOnWifiKey = booleanPreferencesKey("auto_connect_on_wifi")
    private val killSwitchKey = booleanPreferencesKey("kill_switch_enabled")
    private val lastConnectPayloadKey = stringPreferencesKey("last_connect_payload")
    private val lastConnectLabelKey = stringPreferencesKey("last_connect_label")
    private val perAppProxyModeKey = stringPreferencesKey("per_app_proxy_mode")
    private val perAppProxyPackagesKey = stringSetPreferencesKey("per_app_proxy_packages")

    val preferredNodeId: Flow<String?> = context.dataStore.data.map { it[preferredNodeKey] }

    suspend fun setPreferredNodeId(nodeId: String?) {
        context.dataStore.edit {
            if (nodeId == null) it.remove(preferredNodeKey) else it[preferredNodeKey] = nodeId
        }
    }

    /** По умолчанию true — статус-уведомление закреплено (не смахивается), как и было всегда
     *  до появления этой настройки. */
    val pinNotification: Flow<Boolean> = context.dataStore.data.map { it[pinNotificationKey] ?: true }
    suspend fun setPinNotification(pinned: Boolean) {
        context.dataStore.edit { it[pinNotificationKey] = pinned }
    }
    suspend fun pinNotificationNow(): Boolean = pinNotification.first()

    val pingMethod: Flow<PingMethod> = context.dataStore.data.map {
        runCatching { PingMethod.valueOf(it[pingMethodKey] ?: PingMethod.PROXY_GET.name) }.getOrDefault(PingMethod.PROXY_GET)
    }
    suspend fun setPingMethod(method: PingMethod) {
        context.dataStore.edit { it[pingMethodKey] = method.name }
    }
    suspend fun pingMethodNow(): PingMethod = pingMethod.first()

    val pingTestUrl: Flow<String> = context.dataStore.data.map { it[pingTestUrlKey] ?: DEFAULT_PING_URL }
    suspend fun setPingTestUrl(url: String) {
        context.dataStore.edit { it[pingTestUrlKey] = url }
    }
    suspend fun pingTestUrlNow(): String = pingTestUrl.first()

    val darkThemeEnabled: Flow<Boolean> = context.dataStore.data.map { it[darkThemeKey] ?: false }
    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[darkThemeKey] = enabled }
    }

    val appLanguage: Flow<AppLanguage> = context.dataStore.data.map { AppLanguage.fromCode(it[appLanguageKey]) }
    suspend fun setAppLanguage(language: AppLanguage) {
        context.dataStore.edit { it[appLanguageKey] = language.code }
    }
    suspend fun appLanguageNow(): AppLanguage = appLanguage.first()

    val logLevel: Flow<LogLevel> = context.dataStore.data.map { LogLevel.fromName(it[logLevelKey]) }
    suspend fun setLogLevel(level: LogLevel) {
        context.dataStore.edit { it[logLevelKey] = level.name }
    }
    suspend fun logLevelNow(): LogLevel = logLevel.first()

    val fontSize: Flow<FontSizePreset> = context.dataStore.data.map {
        runCatching { FontSizePreset.valueOf(it[fontSizeKey] ?: FontSizePreset.NORMAL.name) }.getOrDefault(FontSizePreset.NORMAL)
    }
    suspend fun setFontSize(preset: FontSizePreset) {
        context.dataStore.edit { it[fontSizeKey] = preset.name }
    }
    suspend fun fontSizeNow(): FontSizePreset = fontSize.first()

    /** По умолчанию выключено — авто-старт VPN на пользователя без явного согласия был бы
     *  неожиданным поведением, особенно на первом Wi-Fi после установки. */
    val autoConnectOnWifi: Flow<Boolean> = context.dataStore.data.map { it[autoConnectOnWifiKey] ?: false }
    suspend fun setAutoConnectOnWifi(enabled: Boolean) {
        context.dataStore.edit { it[autoConnectOnWifiKey] = enabled }
    }

    /** По умолчанию выключено — при включении кратковременные "окна" во время
     *  переключения сервера/автовосстановления после сбоя (см. GodjiVpnService.stopTunnel)
     *  вместо отката на обычную сеть блокируют весь трафик до восстановления туннеля, что для
     *  части пользователей может неожиданно означать "интернет не работает", если они не знают
     *  о причине — поэтому это осознанный выбор, а не поведение по умолчанию. */
    val killSwitchEnabled: Flow<Boolean> = context.dataStore.data.map { it[killSwitchKey] ?: false }
    suspend fun setKillSwitchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[killSwitchKey] = enabled }
    }
    suspend fun killSwitchEnabledNow(): Boolean = killSwitchEnabled.first()

    /** Последний узел, на который реально отправляли ACTION_CONNECT — используется
     *  GodjiVpnService для самостоятельного подъёма туннеля, когда службу запускает сама
     *  система (Always-on VPN), а не наш собственный Intent (см. GodjiVpnService.onStartCommand). */
    suspend fun saveLastConnect(vlessPayload: String, label: String) {
        context.dataStore.edit {
            it[lastConnectPayloadKey] = vlessPayload
            it[lastConnectLabelKey] = label
        }
    }
    suspend fun lastConnectPayloadNow(): String? = context.dataStore.data.first()[lastConnectPayloadKey]
    suspend fun lastConnectLabelNow(): String? = context.dataStore.data.first()[lastConnectLabelKey]

    val perAppProxyMode: Flow<PerAppProxyMode> = context.dataStore.data.map {
        runCatching { PerAppProxyMode.valueOf(it[perAppProxyModeKey] ?: PerAppProxyMode.OFF.name) }.getOrDefault(PerAppProxyMode.OFF)
    }
    suspend fun setPerAppProxyMode(mode: PerAppProxyMode) {
        context.dataStore.edit { it[perAppProxyModeKey] = mode.name }
    }
    suspend fun perAppProxyModeNow(): PerAppProxyMode = perAppProxyMode.first()

    val perAppProxyPackages: Flow<Set<String>> = context.dataStore.data.map { it[perAppProxyPackagesKey] ?: emptySet() }
    suspend fun setPerAppProxyPackages(packages: Set<String>) {
        context.dataStore.edit { it[perAppProxyPackagesKey] = packages }
    }
    suspend fun perAppProxyPackagesNow(): Set<String> = perAppProxyPackages.first()

    companion object {
        const val DEFAULT_PING_URL = "https://cp.cloudflare.com/generate_204"
    }
}
