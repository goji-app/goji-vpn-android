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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.settings.SettingsRepository
import xyz.gojihub.vpn.subscription.SubscriptionRefreshWorker
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.util.AppLogger
import xyz.gojihub.vpn.vpn.GeoAssets
import xyz.gojihub.vpn.vpn.geo.GeoDataDownloader
import xyz.gojihub.vpn.vpn.geo.GeoDataRefreshWorker
import xyz.gojihub.vpn.vpn.geo.MobileWhitelistDownloader
import xyz.gojihub.vpn.vpn.geo.MobileWhitelistRefreshWorker
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
        scheduleGeoDataRefresh()
        scheduleMobileWhitelistRefresh()
        // Разовая попытка сразу после установки/первого запуска — периодический воркер и так
        // рано или поздно скачает свежие geoip.dat/geosite.dat, но при первом же реальном
        // подключении (см. GodjiVpnService.resolveGeoDataRules) лучше уже иметь российский
        // набор runetfreedom, а не только общий, встроенный в assets (см. GeoAssets).
        CoroutineScope(Dispatchers.IO).launch { GeoDataDownloader.refresh(this@GodjiApplication) }
        CoroutineScope(Dispatchers.IO).launch { MobileWhitelistDownloader.refresh(this@GodjiApplication) }
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

    /** runetfreedom/russia-v2ray-rules-dat обновляется каждые 6 часов на их стороне — раз в
     *  сутки достаточно, чтобы не отставать надолго, не гоняя загрузку слишком часто впустую. */
    private fun scheduleGeoDataRefresh() {
        val request = PeriodicWorkRequestBuilder<GeoDataRefreshWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            GeoDataRefreshWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** hxehex/russia-mobile-internet-whitelist — вручную пополняемый краудсорс-список
     *  (см. MobileWhitelistDownloader), обновляется гораздо реже, чем category-ru — раз в
     *  неделю достаточно для актуализации адресов, не гоняя загрузку 470КБ+ CIDR-файла
     *  чаще, чем реально нужно. */
    private fun scheduleMobileWhitelistRefresh() {
        val request = PeriodicWorkRequestBuilder<MobileWhitelistRefreshWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MobileWhitelistRefreshWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
