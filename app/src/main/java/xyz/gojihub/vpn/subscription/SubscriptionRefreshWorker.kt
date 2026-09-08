package xyz.gojihub.vpn.subscription

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Раз в час (см. GodjiApplication.schedulePeriodicRefresh) обновляет подписку и перемеряет
 * пинг серверов — работает и когда приложение свёрнуто в фон, в отличие от таймеров внутри
 * ViewModel, которые живут только пока живы соответствующие экраны/процесс на переднем плане.
 */
@HiltWorker
class SubscriptionRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val subscriptionRepository: SubscriptionRepository,
    private val pingRepository: PingRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        subscriptionRepository.refresh()
        pingRepository.pingAllInternal()
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        const val UNIQUE_WORK_NAME = "subscription_hourly_refresh"
    }
}
