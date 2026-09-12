package xyz.gojihub.vpn.subscription

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.trafficHistoryDataStore by preferencesDataStore(name = "godji_traffic_history")

data class TrafficDaySnapshot(val epochDay: Long, val usedBytes: Long)

/** Бэкенд отдаёт только ТЕКУЩИЙ суммарный расход трафика (traffic.used_bytes) за весь платёжный
 *  период — истории по дням он не хранит вообще (см. Models.kt/TrafficInfo). Строим её сами:
 *  при каждом успешном SubscriptionRepository.refresh() запоминаем today's used_bytes
 *  (перезаписывая запись за сегодня, если уже есть), а дневной расход считаем задним числом как
 *  разницу между соседними днями. История копится только с момента установки этого обновления —
 *  глубже заглянуть невозможно, бэкенд этих данных просто не хранит. */
@Singleton
class TrafficHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val historyKey = stringPreferencesKey("snapshots")

    // Простой ручной формат "day:bytes;day:bytes" вместо JSON/Moshi — записей всего пара
    // десятков и формат предельно простой, заводить сюда ещё одну зависимость незачем.
    private fun encode(list: List<TrafficDaySnapshot>): String =
        list.joinToString(";") { "${it.epochDay}:${it.usedBytes}" }

    private fun decode(raw: String?): List<TrafficDaySnapshot> =
        raw?.takeIf { it.isNotBlank() }?.split(";")?.mapNotNull { entry ->
            val parts = entry.split(":")
            val day = parts.getOrNull(0)?.toLongOrNull()
            val bytes = parts.getOrNull(1)?.toLongOrNull()
            if (day != null && bytes != null) TrafficDaySnapshot(day, bytes) else null
        }.orEmpty()

    /** [usedBytes] — суммарный расход за весь текущий период (то же поле, что уже показывается
     *  на экране "Подписка"), не дневной дельта. */
    suspend fun recordToday(usedBytes: Long) {
        val today = LocalDate.now().toEpochDay()
        context.trafficHistoryDataStore.edit { prefs ->
            val updated = decode(prefs[historyKey]).filterNot { it.epochDay == today } + TrafficDaySnapshot(today, usedBytes)
            // Не даём списку расти бесконечно — 60 дней с запасом хватает для любого разумного графика.
            prefs[historyKey] = encode(updated.sortedBy { it.epochDay }.takeLast(60))
        }
    }

    /** Дневной расход (не суммарный) за последние [days] дней, включая сегодня — разница между
     *  соседними снимками. Отрицательная разница (начался новый платёжный период, used_bytes
     *  обнулился) считается за 0, а не "минус трафик". Дни без снимка (приложение не открывали)
     *  — тоже 0, не "неизвестно": так график рисуется ровно, без дыр/особых случаев в UI. */
    suspend fun dailyUsageLast(days: Int): List<Pair<LocalDate, Long>> {
        val snapshots = decode(context.trafficHistoryDataStore.data.first()[historyKey]).associateBy { it.epochDay }
        val today = LocalDate.now().toEpochDay()
        return (days - 1).downTo(0).map { offset ->
            val day = today - offset
            val current = snapshots[day]?.usedBytes
            val previous = snapshots[day - 1]?.usedBytes
            val delta = if (current != null && previous != null && current >= previous) current - previous else 0L
            LocalDate.ofEpochDay(day) to delta
        }
    }
}
