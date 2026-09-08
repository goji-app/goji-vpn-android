package xyz.gojihub.vpn.subscription

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import xyz.gojihub.vpn.BuildConfig
import xyz.gojihub.vpn.geo.CountryGeoLookup
import xyz.gojihub.vpn.geo.CountryGeo
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.network.RemnawaveApi
import xyz.gojihub.vpn.network.models.SubscriptionInfo
import xyz.gojihub.vpn.util.AppLogger
import xyz.gojihub.vpn.util.LogCategory
import xyz.gojihub.vpn.vpn.GodjiVpnService
import xyz.gojihub.vpn.widget.GojiWidgetProvider
import java.net.URI
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/** Один сервер внутри ссылки подписки пользователя. [geo] — определена по тексту
 *  remark (см. CountryGeoLookup), может быть null.
 *  [connectPayload] — то, что нужно передать в GodjiVpnService для подключения: либо
 *  целиком готовый Xray-конфиг (dns/routing/outbounds, JSON-объект — реальный формат
 *  этого бэкенда при переданном X-HWID), либо, для обратной совместимости, сырая
 *  vless://-ссылка, которую сервис прогонит через libXray.convertShareLinksToXrayJson. */
data class VlessNode(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val connectPayload: String,
    val geo: CountryGeo? = null,
    /** UUID клиента в Remnawave (settings.vnext[].id у VLESS-аутбаунда) — тот же для всех
     *  профилей одного пользователя, реальный Remnawave-идентификатор для поддержки, в отличие
     *  от customer_id шоп-бэкенда (не совпадает с тем, что видно в панели Remnawave). */
    val uuid: String? = null
)

fun VlessNode.toGlobeNode(): xyz.gojihub.vpn.globe.GlobeNode? =
    geo?.let { xyz.gojihub.vpn.globe.GlobeNode(id, it.lat, it.lon, it.country) }

/**
 * Ссылка подписки (subscription_link из /api/subscriptions) без клиентского заголовка
 * X-HWID отдаёт лишь заглушку ("Приложение не поддерживается") — сервер считает
 * устройство непривязанным. С X-HWID он отдаёт настоящий список серверов: JSON-массив
 * готовых профилей Xray (dns/routing/outbounds на VLESS+Reality+XHTTP), а не привычный
 * base64 со списком vless://-ссылок — этот старый формат оставлен как запасной вариант.
 */
@Singleton
class SubscriptionRepository @Inject constructor(
    private val api: RemnawaveApi,
    @ApplicationContext private val appContext: Context
) {
    // Тот же приём, что и в NetworkModule: пока туннель поднят, гоняем запрос списка серверов
    // через локальный SOCKS самого Xray — иначе он идёт по сырой сети телефона и падает в зоне
    // глушения мобильной сети даже при уже подключённом VPN на российский узел.
    private val plainClient = OkHttpClient.Builder()
        .proxySelector(GodjiVpnService.tunnelAwareProxySelector())
        .build()

    // Только для фоновой записи в NodeListCache из select() — сама функция не suspend
    // (вызывается напрямую из UI-обработчиков клика).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val hwid: String by lazy {
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "godji-${System.identityHashCode(appContext)}"
    }

    /** То же значение X-HWID, что уходит на бэкенд — для экрана "О программе". */
    fun hwidNow(): String = hwid

    private val _subscription = MutableStateFlow<SubscriptionInfo?>(null)
    val subscription: StateFlow<SubscriptionInfo?> = _subscription.asStateFlow()

    // Заполняем из диска ДО первого сетевого запроса — как в Happ/Incy: если gojihub.xyz или
    // subs.gojihub.xyz недоступны прямо на старте (сайт/панель Remnawave легли, глушение сети
    // и т.п.), пользователь видит последний известный список серверов и может подключиться,
    // а не пустой экран "Серверы" с нулём узлов. refresh() ниже перезапишет этот список свежим,
    // как только сеть появится.
    private val cachedOnDisk = NodeListCache.load(appContext)

    private val _nodes = MutableStateFlow(cachedOnDisk?.nodes.orEmpty())
    val nodes: StateFlow<List<VlessNode>> = _nodes.asStateFlow()

    private val _selectedId = MutableStateFlow(cachedOnDisk?.selectedId)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    /** @return true, если подписку удалось реально получить с бэкенда (для UI ручного
     *  обновления — показать "обновлено" или ошибку сети/сервера). */
    suspend fun refresh(): Boolean {
        val active = runCatching { api.getSubscriptions() }
            .onFailure {
                if (BuildConfig.DEBUG) android.util.Log.e("GodjiSub", "getSubscriptions failed", it)
                AppLogger.e(appContext, LogCategory.SUBSCRIPTION, "GodjiSub", "getSubscriptions failed", it)
            }
            .getOrNull()
            ?.subscriptions
            ?.let { list -> list.firstOrNull { it.isPrimary } ?: list.firstOrNull() }
        _subscription.value = active
        // Уведомления о скором окончании подписки/успешной оплате — считаются здесь, а не в
        // отдельных вызывающих местах (воркер + 3 ViewModel), чтобы сработать при любом
        // источнике обновления, а не только раз в час в фоне.
        active?.let { SubscriptionNotifier.check(appContext, it) }

        val link = active?.subscriptionLink ?: return false
        val nodes = fetchNodes(link)
        // Пустой список из fetchNodes означает "не удалось получить" (см. runCatching там же),
        // а не "в подписке теперь ноль узлов" — если затирать им текущий список при временном
        // сбое сети (например subs.gojihub.xyz недоступен через текущий узел, а сама подписка
        // на gojihub.xyz получена), пользователь на экране "Серверы" внезапно теряет и активный,
        // и вообще все узлы, хотя реальное VPN-подключение всё это время работает как ни в чём
        // не бывало. Оставляем прежний список нетронутым, пока не придёт непустой новый.
        if (nodes.isNotEmpty()) {
            _nodes.value = nodes
            if (_selectedId.value == null || nodes.none { it.id == _selectedId.value }) {
                _selectedId.value = nodes.firstOrNull()?.id
            }
            // Подтягиваем и сохраняем полные клиентские JSON-профили узлов на диск — не только
            // держим в памяти на момент подключения (см. ClientProfileStorage), плюс сам список
            // (имя/geo/uuid) — для офлайн-показа на случай следующего запуска без сети.
            withContext(Dispatchers.IO) {
                ClientProfileStorage.saveAll(appContext, nodes)
                NodeListCache.save(appContext, nodes, _selectedId.value)
            }
        }
        return true
    }

    fun select(id: String) {
        _selectedId.value = id
        scope.launch { NodeListCache.save(appContext, _nodes.value, id) }
        GojiWidgetProvider.refresh(appContext)
    }

    fun selectedNode(): VlessNode? = _nodes.value.firstOrNull { it.id == _selectedId.value }

    /** UUID клиента в Remnawave — одинаков для всех профилей подписки, берём из первого. */
    fun clientUuid(): String? = _nodes.value.firstNotNullOfOrNull { it.uuid }

    private suspend fun fetchNodes(url: String): List<VlessNode> = withContext(Dispatchers.IO) {
        runCatching {
            // User-Agent/X-HWID намеренно не трогаем — бэкенд по User-Agent "v2rayNG/1.8.29"
            // определяет, что клиенту нужно отдавать настоящий JSON-формат профилей (см.
            // комментарий класса); замена на что-то своё возвращала бы старый base64-формат.
            // Название приложения/ОС/модель устройства/версию приложения передаём отдельными
            // заголовками — не мешают распознаванию клиента, но дают бэкенду показать в списке
            // устройств не голый HWID, а что это реально Godji app такой-то версии на таком-то
            // устройстве и версии Android.
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "v2rayNG/1.8.29")
                .header("X-HWID", hwid)
                .header("X-Device-OS", "Android")
                .header("X-Device-OS-Version", Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString())
                .header("X-Device-Model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                .header("X-App-Name", "Goji app")
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .build()
            plainClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                parseJsonProfiles(raw) ?: parseVlessText(raw)
            }
        }.onFailure {
            if (BuildConfig.DEBUG) android.util.Log.e("GodjiSub", "fetchNodes failed", it)
            AppLogger.e(appContext, LogCategory.SUBSCRIPTION, "GodjiSub", "fetchNodes failed", it)
        }.getOrElse { emptyList() }
    }

    /** Реальный формат: JSON-массив готовых профилей `{remarks, dns, routing, outbounds, ...}`. */
    private fun parseJsonProfiles(raw: String): List<VlessNode>? = runCatching {
        val array = JSONArray(raw.trim())
        (0 until array.length()).mapNotNull { index ->
            val profile = array.getJSONObject(index)
            val outbounds = profile.optJSONArray("outbounds") ?: return@mapNotNull null
            val proxyOutbound = (0 until outbounds.length())
                .map { outbounds.getJSONObject(it) }
                .firstOrNull { it.optJSONObject("settings")?.optJSONArray("vnext") != null }
                ?: return@mapNotNull null
            val vnext = proxyOutbound.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
            val host = vnext.optString("address").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val port = vnext.optInt("port").takeIf { it > 0 } ?: 443
            val uuid = vnext.optJSONArray("users")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
            val remark = profile.optString("remarks").takeIf { it.isNotBlank() } ?: Loc.s.fallbackServerName(index + 1)
            VlessNode(
                id = index.toString(),
                name = remark,
                host = host,
                port = port,
                connectPayload = profile.toString(),
                geo = CountryGeoLookup.find(remark),
                uuid = uuid
            )
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** Запасной формат (легаси): base64 со списком vless://-ссылок построчно. */
    private fun parseVlessText(raw: String): List<VlessNode> {
        val decoded = runCatching {
            String(Base64.decode(raw.trim(), Base64.DEFAULT), Charsets.UTF_8)
        }.getOrDefault(raw)
        return decoded.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("vless://") }
            .mapIndexedNotNull { index, line -> parseVless(index, line) }
            .toList()
    }

    private fun parseVless(index: Int, link: String): VlessNode? = runCatching {
        val uri = URI(link)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val remark = uri.rawFragment
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?.takeIf { it.isNotBlank() }
            ?: Loc.s.fallbackServerName(index + 1)
        val uuid = uri.userInfo?.takeIf { it.isNotBlank() }
        VlessNode(id = index.toString(), name = remark, host = host, port = port, connectPayload = link, geo = CountryGeoLookup.find(remark), uuid = uuid)
    }.getOrNull()
}
