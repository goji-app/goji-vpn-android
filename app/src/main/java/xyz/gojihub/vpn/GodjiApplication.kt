package xyz.gojihub.vpn

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.settings.SettingsRepository
import xyz.gojihub.vpn.subscription.SubscriptionRefreshWorker
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.util.AppLogger
import xyz.gojihub.vpn.vpn.GeoAssets
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class GodjiApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsRepository: SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Заведомо раньше первого обращения к libXray где-либо в приложении — см. GeoAssets.
        GeoAssets.applyEnv(this)
        // Синхронно (runBlocking) и до первой отрисовки — иначе первый кадр рисуется светлой
        // темой по умолчанию, и при включённой тёмной теме экран на миг "мигает" светлым.
        // DataStore-чтение тут — попадание в уже прогретый на диске файл на несколько КБ,
        // блокировка на старте пренебрежимо мала.
        runBlocking {
            if (settingsRepository.darkThemeEnabled.first()) GodjiColors.applyDark()
            Loc.lang = settingsRepository.appLanguageNow()
            AppLogger.level = settingsRepository.logLevelNow()
        }
        schedulePeriodicRefresh()
    }

    /** "Автообновление подписки каждый час, когда приложение активно или в фоне" —
     *  enqueueUniquePeriodicWork с KEEP не пересоздаёт задачу при каждом запуске процесса,
     *  так что реальный интервал остаётся ровно часовым, а не "час с момента последнего старта". */
    private fun schedulePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<SubscriptionRefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SubscriptionRefreshWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
