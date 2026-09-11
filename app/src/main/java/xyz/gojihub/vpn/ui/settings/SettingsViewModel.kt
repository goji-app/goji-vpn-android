package xyz.gojihub.vpn.ui.settings

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.gojihub.vpn.BuildConfig
import xyz.gojihub.vpn.auth.AuthRepository
import xyz.gojihub.vpn.i18n.AppLanguage
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.settings.PingMethod
import xyz.gojihub.vpn.settings.SettingsRepository
import xyz.gojihub.vpn.subscription.SubscriptionRepository
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.update.AppUpdateChecker
import xyz.gojihub.vpn.update.AppUpdateDownloader
import xyz.gojihub.vpn.update.UpdateInfo
import xyz.gojihub.vpn.util.AppLogger
import xyz.gojihub.vpn.util.LogLevel
import xyz.gojihub.vpn.vpn.GodjiVpnService
import javax.inject.Inject

data class SettingsUiState(
    val pinNotification: Boolean = true,
    val darkTheme: Boolean = false,
    val language: AppLanguage = AppLanguage.RU,
    val pingMethod: PingMethod = PingMethod.PROXY_GET,
    val pingTestUrl: String = SettingsRepository.DEFAULT_PING_URL,
    val logLevel: LogLevel = LogLevel.DEBUG,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val xrayVersion: String = SettingsViewModel.BUNDLED_XRAY_VERSION,
    val hwid: String = "",
    val deviceInfo: String = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}",
    val updateChecking: Boolean = false,
    val updateChecked: Boolean = false,
    val updateAvailable: UpdateInfo? = null,
    val updateDownloading: Boolean = false,
    val updateDownloadProgress: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val subscriptionRepository: SubscriptionRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                pinNotification = settingsRepository.pinNotificationNow(),
                darkTheme = settingsRepository.darkThemeEnabled.first(),
                pingMethod = settingsRepository.pingMethodNow(),
                pingTestUrl = settingsRepository.pingTestUrlNow(),
                language = settingsRepository.appLanguageNow(),
                logLevel = settingsRepository.logLevelNow(),
                hwid = subscriptionRepository.hwidNow()
            )
        }
    }

    fun setLanguage(language: AppLanguage) {
        _state.value = _state.value.copy(language = language)
        Loc.lang = language
        viewModelScope.launch { settingsRepository.setAppLanguage(language) }
    }

    fun setLogLevel(level: LogLevel) {
        _state.value = _state.value.copy(logLevel = level)
        AppLogger.level = level
        viewModelScope.launch { settingsRepository.setLogLevel(level) }
    }

    companion object {
        // libXray.invoke() в этой сборке AAR поддерживает только 8 методов (runXray, testXray,
        // pingBatch, getFreePorts, countGeoData, generateAgeKeyPair, convertShareLinksToXrayJson,
        // convertXrayJsonToShareLinks) — метода "версия ядра" среди них нет, поэтому его нельзя
        // спросить в рантайме. Версия взята из строки внутри самого нативного .so ("REALITY: the
        // default minimal client version is Xray-core vX.Y.Z", которую xray-core формирует из
        // своей же core.Version()) — при обновлении libs/libXray.aar нужно обновить и эту константу.
        const val BUNDLED_XRAY_VERSION = "v26.3.27"
    }

    fun setPinNotification(pinned: Boolean) {
        _state.value = _state.value.copy(pinNotification = pinned)
        viewModelScope.launch { settingsRepository.setPinNotification(pinned) }
    }

    fun setDarkTheme(enabled: Boolean) {
        _state.value = _state.value.copy(darkTheme = enabled)
        if (enabled) GodjiColors.applyDark() else GodjiColors.applyLight()
        viewModelScope.launch { settingsRepository.setDarkThemeEnabled(enabled) }
    }

    fun setPingMethod(method: PingMethod) {
        _state.value = _state.value.copy(pingMethod = method)
        viewModelScope.launch { settingsRepository.setPingMethod(method) }
    }

    fun setPingTestUrl(url: String) {
        _state.value = _state.value.copy(pingTestUrl = url)
        viewModelScope.launch { settingsRepository.setPingTestUrl(url) }
    }

    fun checkForUpdate() {
        _state.value = _state.value.copy(updateChecking = true)
        viewModelScope.launch {
            val update = AppUpdateChecker.checkForUpdate()
            _state.value = _state.value.copy(updateChecking = false, updateChecked = true, updateAvailable = update)
        }
    }

    /** Запускает загрузку через системный DownloadManager (см. AppUpdateDownloader) и следит
     *  за прогрессом, пока экран открыт — саму установку по завершении запускает отдельный
     *  BroadcastReceiver (переживает закрытие экрана/приложения), эта корутина только
     *  обновляет индикатор прогресса. */
    fun downloadUpdate() {
        val update = _state.value.updateAvailable ?: return
        val id = AppUpdateDownloader.startDownload(appContext, update.apkUrl, update.version)
        _state.value = _state.value.copy(updateDownloading = true, updateDownloadProgress = 0)
        viewModelScope.launch {
            while (true) {
                delay(700)
                val status = AppUpdateDownloader.queryStatus(appContext, id) ?: break
                _state.value = _state.value.copy(updateDownloadProgress = status.percent)
                if (status.status == DownloadManager.STATUS_SUCCESSFUL || status.status == DownloadManager.STATUS_FAILED) break
            }
            _state.value = _state.value.copy(updateDownloading = false)
        }
    }

    fun logout() {
        if (GodjiVpnService.isRunning.value) {
            appContext.startService(Intent(appContext, GodjiVpnService::class.java).apply {
                action = GodjiVpnService.ACTION_DISCONNECT
            })
        }
        authRepository.logout()
    }
}
