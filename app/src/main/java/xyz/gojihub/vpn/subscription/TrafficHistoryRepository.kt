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

/** [hasData] отличает "расход честно посчитан и равен нулю" от "снимков за этот день ещё/уже
 *  не существует" — первый день, за который вообще есть хоть один снимок, не может дать дельту
 *  (не с чем сравнивать), и это НЕ то же самое, что подтверждённый нулевой расход. UI показывает
 *  эти случаи по-разному, чтобы дни до начала локальной истории не выглядели как "баг", а как
 *  честное "данных пока нет". */
data class TrafficDayUsage(val date: LocalDate, val bytes: Long, val hasData: Boolean)

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

    /** Дневной расход (не суммарный) за последние [days] дней, включая сегодня. Раньше требовал
     *  снимок РОВНО за предыдущий день — если приложение не открывали (или часовой фоновый
     *  воркер не отработал, что Samsung с их агрессивной оптимизацией батареи вполне может
     *  сделать) хотя бы один день, разрыв цепочки обнулял не только пропущенный день, но и весь
     *  следующий: у него не находилось "вчера", с которым сравнивать. Снаружи это выглядело так,
     *  будто расход за несколько прошедших дней просто пропал, хотя трафик реально шёл.
     *
     *  Теперь берём дельту между ЛЮБЫМИ двумя соседними по времени снимками (не обязательно
     *  сутки друг за другом) и равномерно размазываем её по всем дням разрыва — так пропуск в
     *  днях даёт честный средний расход за период вместо однодневного ложного всплеска или тишины.
     *  Отрицательная разница (начался новый платёжный период, used_bytes обнулился) по-прежнему
     *  считается за 0, а не "минус трафик". Дни ДО самого первого снимка (истории ещё физически
     *  не существует — см. recordToday) помечены hasData=false, а не молча приравнены к 0. */
    suspend fun dailyUsageLast(days: Int): List<TrafficDayUsage> {
        val snapshots = decode(context.trafficHistoryDataStore.data.first()[historyKey]).sortedBy { it.epochDay }
        val today = LocalDate.now().toEpochDay()
        val windowStart = today - (days - 1)
        val firstSnapshotDay = snapshots.firstOrNull()?.epochDay

        val perDay = HashMap<Long, Long>()
        for (i in 1 until snapshots.size) {
            val prev = snapshots[i - 1]
            val curr = snapshots[i]
            val spanDays = curr.epochDay - prev.epochDay
            if (spanDays <= 0) continue
            val delta = (curr.usedBytes - prev.usedBytes).coerceAtLeast(0L)
            val share = delta / spanDays
            val remainder = delta % spanDays
            for (offset in 1..spanDays) {
                val day = prev.epochDay + offset
                if (day < windowStart) continue
                // Последний день разрыва забирает остаток от целочисленного деления — сумма
                // распределённых долей в точности равна исходной дельте, ни один байт не теряется.
                perDay[day] = (perDay[day] ?: 0L) + share + if (offset == spanDays) remainder else 0L
            }
        }

        return (days - 1).downTo(0).map { offset ->
            val day = today - offset
            // День имеет посчитанную дельту, только если он строго позже самого первого снимка —
            // у самого первого снимка (baseline) и у всех более ранних дней в принципе нет "вчера",
            // с которым сравнивать.
            val hasData = firstSnapshotDay != null && day > firstSnapshotDay
            TrafficDayUsage(LocalDate.ofEpochDay(day), perDay[day] ?: 0L, hasData)
        }
    }
}
