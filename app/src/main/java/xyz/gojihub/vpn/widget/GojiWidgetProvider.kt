package xyz.gojihub.vpn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import xyz.gojihub.vpn.MainActivity
import xyz.gojihub.vpn.R
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.subscription.NodeListCache
import xyz.gojihub.vpn.util.stripLeadingFlag
import xyz.gojihub.vpn.vpn.GodjiVpnService

/**
 * Виджет на главный экран — как в Happ/Incy: статус, выбранный сервер, кнопка
 * подключения/отключения, без необходимости открывать само приложение.
 *
 * Обновляется не только по системному updatePeriodMillis (тот не чаще раза в 30 минут —
 * слишком редко для "живого" статуса), а сразу же после реального события — см. вызовы
 * [refresh] из GodjiVpnService (подключение/отключение) и SubscriptionRepository.select()
 * (смена выбранного сервера).
 *
 * Список серверов/выбор берём из NodeListCache — того же файлового кэша, что и офлайн-показ
 * на экране "Серверы" (см. эту задачу выше): виджету не нужен собственный доступ к
 * SubscriptionRepository/сети, обычный синхронный локальный файл на диске.
 */
class GojiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context)) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GojiWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildViews(context))
        }

        private fun buildViews(context: Context): RemoteViews {
            val running = GodjiVpnService.isRunning.value
            val cached = NodeListCache.load(context)
            val selected = cached?.nodes?.firstOrNull { it.id == cached.selectedId }
            val nodeLabel = selected?.let { n ->
                n.geo?.displayCityCountry(Loc.lang) ?: stripLeadingFlag(n.name)
            } ?: Loc.s.widgetNotSelected

            val views = RemoteViews(context.packageName, R.layout.widget_goji)
            views.setTextViewText(R.id.widget_status, if (running) Loc.s.widgetConnected else Loc.s.widgetDisconnected)
            views.setTextViewText(R.id.widget_node, nodeLabel)
            views.setInt(R.id.widget_dot, "setColorFilter", if (running) COLOR_TEAL else COLOR_MUTED)
            views.setTextViewText(R.id.widget_button, if (running) Loc.s.widgetDisconnect else Loc.s.widgetConnect)
            views.setInt(
                R.id.widget_button, "setBackgroundResource",
                if (running) R.drawable.widget_button_red else R.drawable.widget_button_teal
            )

            // Отключение можно дёргать прямо из виджета сервисом напрямую (тот же приём, что
            // уже работает у кнопки "Отключить" в самом уведомлении о статусе VPN). Подключение
            // из виджета не запускаем напрямую — если запрос системного разрешения на VPN ещё
            // не подтверждён (VpnService.prepare() отдаёт Intent, не null), тихий запуск сервиса
            // просто провалится без единого объяснения пользователю; вместо этого открываем
            // приложение — там "Включить" уже умеет правильно запросить это разрешение.
            val buttonIntent = if (running) {
                PendingIntent.getService(
                    context, 0,
                    Intent(context, GodjiVpnService::class.java).setAction(GodjiVpnService.ACTION_DISCONNECT),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                openAppIntent(context, requestCode = 0)
            }
            views.setOnClickPendingIntent(R.id.widget_button, buttonIntent)
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, requestCode = 1))
            return views
        }

        private fun openAppIntent(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private const val COLOR_TEAL = 0xFF00A79B.toInt()
        private const val COLOR_MUTED = 0xFF9AA39B.toInt()
    }
}
