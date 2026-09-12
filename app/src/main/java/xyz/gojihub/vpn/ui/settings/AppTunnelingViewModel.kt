package xyz.gojihub.vpn.ui.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.gojihub.vpn.settings.PerAppProxyMode
import xyz.gojihub.vpn.settings.SettingsRepository
import javax.inject.Inject

data class InstalledAppUi(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isSystem: Boolean
)

data class AppTunnelingUiState(
    val mode: PerAppProxyMode = PerAppProxyMode.OFF,
    val selected: Set<String> = emptySet(),
    val apps: List<InstalledAppUi> = emptyList(),
    val loading: Boolean = true,
    val query: String = ""
)

/** Отдельная ViewModel, а не часть SettingsViewModel — единственный экран, которому нужен
 *  список ВСЕХ установленных приложений (PackageManager.getInstalledApplications), это
 *  заметно тяжелее обычных настроек и не должно грузиться вместе с остальным экраном Settings. */
@HiltViewModel
class AppTunnelingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(AppTunnelingUiState())
    val state: StateFlow<AppTunnelingUiState> = _state

    // Полный список — фильтрация поиском работает по нему, не требуя повторного похода в
    // PackageManager на каждое нажатие клавиши.
    private var allApps: List<InstalledAppUi> = emptyList()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                mode = settingsRepository.perAppProxyModeNow(),
                selected = settingsRepository.perAppProxyPackagesNow()
            )
            loadApps()
        }
    }

    private suspend fun loadApps() {
        val pm = appContext.packageManager
        val apps = withContext(Dispatchers.Default) {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
                .map { info ->
                    InstalledAppUi(
                        packageName = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        // 48x48 — маленькая иконка в строке списка, полноразмерная (часто
                        // 192x192 и выше) была бы совершенно излишней для сотен приложений сразу.
                        icon = runCatching { pm.getApplicationIcon(info).toBitmap(48, 48).asImageBitmap() }.getOrNull(),
                        isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
        allApps = apps
        _state.value = _state.value.copy(apps = filtered(apps, _state.value.query), loading = false)
    }

    private fun filtered(apps: List<InstalledAppUi>, query: String): List<InstalledAppUi> =
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }

    fun setMode(mode: PerAppProxyMode) {
        _state.value = _state.value.copy(mode = mode)
        viewModelScope.launch { settingsRepository.setPerAppProxyMode(mode) }
    }

    fun toggleApp(packageName: String) {
        val updated = _state.value.selected.let { if (packageName in it) it - packageName else it + packageName }
        _state.value = _state.value.copy(selected = updated)
        viewModelScope.launch { settingsRepository.setPerAppProxyPackages(updated) }
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query, apps = filtered(allApps, query))
    }
}
