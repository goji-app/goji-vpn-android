package xyz.gojihub.vpn.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.gojihub.vpn.i18n.Loc
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Локальный файловый журнал приложения — независимо от Log.d/Log.e, которые в релизной сборке
 * никуда не попадают (см. BuildConfig.DEBUG-гварды в GodjiVpnService/PingRepository и т.п.).
 * Нужен, чтобы при жалобе пользователя на "не подключается"/"не продлевается" было что
 * посмотреть, а не гадать вслепую.
 *
 * Разбит на категории (см. LogCategory) — каждая пишется в свой файл, чтобы в экране "Логи"
 * можно было смотреть их по отдельности (ядро/подписки/служба/пуши/остальное), а не искать
 * нужное событие в одной большой простыне. [level] — порог детализации (см. LogLevel),
 * настраивается в отдельном экране "Уровень логирования" и по умолчанию Debug (пишем всё,
 * как и раньше, пока пользователь сам не понизит уровень).
 *
 * Важно для документации/политики конфиденциальности: этот журнал пишется и хранится
 * ИСКЛЮЧИТЕЛЬНО локально на устройстве и НИКУДА автоматически не отправляется — открывается
 * только по запросу пользователя в самом приложении (см. readAll/LogViewerDialog), без сети.
 */
object AppLogger {
    private const val MAX_SIZE_BYTES = 1_000_000L // ~1 МБ на категорию, дальше ротация в .old

    @Volatile var level: LogLevel = LogLevel.DEBUG

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun d(context: Context, category: LogCategory, tag: String, message: String) =
        write(context, category, LogLevel.DEBUG, "D", tag, message, null)

    fun i(context: Context, category: LogCategory, tag: String, message: String) =
        write(context, category, LogLevel.INFO, "I", tag, message, null)

    fun w(context: Context, category: LogCategory, tag: String, message: String, t: Throwable? = null) =
        write(context, category, LogLevel.WARNING, "W", tag, message, t)

    fun e(context: Context, category: LogCategory, tag: String, message: String, t: Throwable? = null) =
        write(context, category, LogLevel.ERROR, "E", tag, message, t)

    private fun write(
        context: Context,
        category: LogCategory,
        messageLevel: LogLevel,
        levelLabel: String,
        tag: String,
        message: String,
        t: Throwable?
    ) {
        if (level.rank < messageLevel.rank) return
        val appContext = context.applicationContext
        val line = buildString {
            append(LocalDateTime.now().format(timeFormatter))
            append(" ").append(levelLabel).append("/").append(tag).append(": ").append(message)
            if (t != null) append(" — ").append(t.javaClass.simpleName).append(": ").append(t.message)
        }
        scope.launch {
            mutex.withLock {
                runCatching {
                    val dir = appContext.filesDir
                    val file = File(dir, "${category.fileBaseName}.log")
                    val oldFile = File(dir, "${category.fileBaseName}.log.old")
                    if (file.exists() && file.length() > MAX_SIZE_BYTES) {
                        file.copyTo(oldFile, overwrite = true)
                        file.delete()
                    }
                    file.appendText(line + "\n")
                }
            }
        }
    }

    /** Содержимое журнала конкретной категории для показа прямо в приложении (см.
     *  LogViewerDialog) — без отправки куда-либо, только чтение локального файла. Старый
     *  .old-файл (после ротации) читаем первым, чтобы порядок строк оставался хронологическим. */
    fun readAll(context: Context, category: LogCategory): String {
        val dir = context.filesDir
        val old = File(dir, "${category.fileBaseName}.log.old").takeIf { it.exists() }?.readText().orEmpty()
        val current = File(dir, "${category.fileBaseName}.log").takeIf { it.exists() }?.readText().orEmpty()
        val combined = old + current
        return combined.ifBlank { Loc.s.logEmpty }
    }
}
