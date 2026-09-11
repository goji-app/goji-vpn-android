package xyz.gojihub.vpn.ui.connect

import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.gojihub.vpn.geo.CountryGeo
import xyz.gojihub.vpn.geo.CountryGeoLookup
import xyz.gojihub.vpn.geo.Greetings
import xyz.gojihub.vpn.globe.GlobeNode
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.network.NetState
import xyz.gojihub.vpn.network.NetworkMonitor
import xyz.gojihub.vpn.settings.SettingsRepository
import xyz.gojihub.vpn.subscription.PingRepository
import xyz.gojihub.vpn.subscription.SubscriptionRepository
import xyz.gojihub.vpn.subscription.VlessNode
import xyz.gojihub.vpn.subscription.toGlobeNode
import xyz.gojihub.vpn.util.formatDate
import xyz.gojihub.vpn.util.stripLeadingFlag
import xyz.gojihub.vpn.vpn.GodjiVpnService
import javax.inject.Inject

data class ConnectUiState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val downSpeedMbps: Double = 0.0,
    val upSpeedMbps: Double = 0.0,
    val usedGb: Double = 0.0,
    val quotaGb: Double = 0.0,
    val isUnlimited: Boolean = false,
    val expiryLabel: String = "—",
    val daysLeft: Int = 0,
    val error: String? = null,

    val globeStatus: String = "off", // "off" | "connecting" | "on"
    val globeNode: GlobeNode? = null,

    val currentNodeName: String = "",
    val currentGeo: CountryGeo? = null,
    val currentCode: String = "АВТО",
    val currentFlag: String = "🌐",

    val greetingHi: String = "Hello",
    val greetingSub: String = "английский",
    val greetingUsesSerif: Boolean = true,

    val netState: NetState = NetState.CELLULAR,
    val banner: String? = null,
    val bannerKind: BannerKind = BannerKind.INFO,

    val connectedTimeLabel: String = "00:00:00"
)

enum class BannerKind { WARNING, SUCCESS, INFO }

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val pingRepository: PingRepository,
    private val networkMonitor: NetworkMonitor,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state

    private var speedJob: Job? = null
    private var preferredNodeId: String? = null
    private var lastNetState: NetState? = null

    init {
        viewModelScope.launch {
            // "Автообновление подписки и пинга при открытии приложения" — экран "Защита"
            // это стартовый экран, поэтому именно здесь считаем момент открытия приложения.
            subscriptionRepository.refresh()
            pingRepository.pingAllInternal()
        }

        viewModelScope.launch {
            settingsRepository.preferredNodeId.collect { preferredNodeId = it }
        }

        viewModelScope.launch {
            subscriptionRepository.subscription.collect { sub ->
                val traffic = sub?.traffic
                _state.value = _state.value.copy(
                    usedGb = (traffic?.usedBytes ?: 0L) / 1_000_000_000.0,
                    quotaGb = (traffic?.limitBytes ?: 0L) / 1_000_000_000.0,
                    isUnlimited = traffic?.isUnlimited ?: false,
                    expiryLabel = sub?.expireAt?.let(::formatDate) ?: "—",
                    daysLeft = sub?.daysLeft ?: 0
                )
            }
        }

        viewModelScope.launch {
            subscriptionRepository.nodes.collect { updateNodeDependentState() }
        }
        viewModelScope.launch {
            subscriptionRepository.selectedId.collect { updateNodeDependentState() }
        }

        viewModelScope.launch {
            GodjiVpnService.isRunning.collect { running ->
                _state.value = _state.value.copy(
                    connected = running,
                    connecting = false,
                    globeStatus = if (running) "on" else "off",
                    // Реальное время начала отсчёта живёт в самом сервисе (connectedSinceMillis) —
                    // формируется в startSpeedPolling() ниже; здесь только сбрасываем на "выключено"
                    // для случая, когда туннель уже упал, а UI ещё не обновился спустя тик таймера.
                    connectedTimeLabel = if (running) _state.value.connectedTimeLabel else "00:00:00"
                )
                if (running) startSpeedPolling() else stopSpeedPolling()
            }
        }
        viewModelScope.launch {
            GodjiVpnService.lastError.collect { error ->
                if (error != null) _state.value = _state.value.copy(error = error, connecting = false)
            }
        }
        viewModelScope.launch {
            GodjiVpnService.actualCountry.collect { country ->
                if (country != null) applyActualCountry(country) else updateNodeDependentState()
            }
        }

        viewModelScope.launch {
            networkMonitor.state.collect { net -> onNetworkChanged(net) }
        }
    }

    private fun updateNodeDependentState() {
        val nodes = subscriptionRepository.nodes.value
        val selected = subscriptionRepository.selectedNode()
        val globeNodes = nodes.mapNotNull { it.toGlobeNode() }
        val g = Greetings.forLang(selected?.geo?.lang)
        _state.value = _state.value.copy(
            globeNode = selected?.toGlobeNode() ?: globeNodes.firstOrNull(),
            currentNodeName = selected?.name?.let(::stripLeadingFlag) ?: "",
            currentGeo = selected?.geo,
            currentCode = selected?.geo?.code ?: "АВТО",
            currentFlag = selected?.geo?.code?.let(CountryGeoLookup::flagEmoji) ?: "🌐",
            greetingHi = g.hi,
            greetingSub = (if (g.transliteration.isNotBlank()) "«${g.transliteration}» · " else "") + g.language,
            greetingUsesSerif = g.useSerifFont
        )
    }

    /** Remark выбранного узла — не гарантия реального местоположения выхода (видели живьём
     *  узел "Germany", реально выходивший через Latvia). Подменяем отображаемую страну на
     *  настоящую, определённую GodjiVpnService через сам туннель, когда она известна. */
    private fun applyActualCountry(country: String) {
        val previousGeo = _state.value.currentGeo
        val previousDisplay = previousGeo?.displayCityCountry(Loc.lang)
        val geo = CountryGeoLookup.find(country)
        val g = Greetings.forLang(geo?.lang)
        val selectedId = subscriptionRepository.selectedId.value
        val newDisplay = geo?.displayCityCountry(Loc.lang) ?: country
        _state.value = _state.value.copy(
            currentGeo = geo,
            currentCode = geo?.code ?: country.take(2).uppercase(),
            currentFlag = geo?.code?.let(CountryGeoLookup::flagEmoji) ?: "🌐",
            globeNode = geo?.let { GlobeNode(selectedId ?: "actual", it.lat, it.lon, it.country) } ?: _state.value.globeNode,
            greetingHi = g.hi,
            greetingSub = (if (g.transliteration.isNotBlank()) "«${g.transliteration}» · " else "") + g.language,
            greetingUsesSerif = g.useSerifFont
        )
        // Сравниваем в том же формате "Город, Страна", в котором хранится currentGeo —
        // иначе (сравнение с "голой" страной) баннер срабатывал бы каждый раз, даже когда
        // страна на самом деле не изменилась.
        if (previousDisplay != null && previousDisplay != newDisplay) {
            // У узла "Автовыбор LTE" сама геометка — Россия (это его назначение при глушении),
            // но реально он балансирует по нескольким резервным аутбаундам (см. PingRepository) и
            // вне глушения вполне может выйти через европейский IP — это ожидаемо, а не "подмена
            // страны", поэтому для него отдельное, не тревожное объяснение вместо общего баннера.
            val isLte = subscriptionRepository.selectedNode()?.name?.contains("LTE", ignoreCase = true) == true
            val bannerText = if (isLte) {
                Loc.s.bannerLteOnlyDuringJam
            } else {
                Loc.s.bannerActualCountry(geo?.displayName(Loc.lang) ?: country, previousDisplay)
            }
            _state.value = _state.value.copy(banner = bannerText, bannerKind = BannerKind.INFO)
        }
    }

    /** "Российский" узел для ухода при глушении — у бэкенда это именно узел автовыбора LTE
     *  (тот же критерий, что и в [maybeSwitchToLte]), а не поиск по geo.country == "Russia":
     *  сам узел автовыбора геометки не имел до правки CountryGeoLookup (алиас "lte" → Russia). */
    private fun russianNode(): VlessNode? =
        subscriptionRepository.nodes.value.firstOrNull { it.name.contains("LTE", ignoreCase = true) }

    private fun onNetworkChanged(net: NetState) {
        val prev = lastNetState
        lastNetState = net
        val s = _state.value
        _state.value = s.copy(netState = net)
        if (prev == null || prev == net) return

        if (net == NetState.JAMMED) {
            val ru = russianNode()
            if (ru != null) {
                val current = subscriptionRepository.selectedNode()
                if (current?.id != ru.id) {
                    viewModelScope.launch { settingsRepository.setPreferredNodeId(current?.id) }
                }
                subscriptionRepository.select(ru.id)
                _state.value = _state.value.copy(
                    banner = Loc.s.bannerJammedSwitched(stripLeadingFlag(ru.name)),
                    bannerKind = BannerKind.WARNING
                )
            } else {
                _state.value = _state.value.copy(
                    banner = Loc.s.bannerJammedNoRuNode,
                    bannerKind = BannerKind.WARNING
                )
            }
        } else if (net == NetState.WIFI) {
            val current = subscriptionRepository.selectedNode()
            val ru = russianNode()
            if (ru != null && current?.id == ru.id && preferredNodeId != null && preferredNodeId != ru.id) {
                subscriptionRepository.select(preferredNodeId!!)
                _state.value = _state.value.copy(banner = Loc.s.bannerWifiRestored, bannerKind = BannerKind.SUCCESS)
            }
        } else if (net == NetState.CELLULAR) {
            viewModelScope.launch { maybeSwitchToLte() }
        }
    }

    /** При появлении мобильной сети выбираем между двумя узлами-балансировщиками провайдера:
     *  «Автовыбор серверов EU», если хоть один европейский узел живой (сам балансировщик
     *  внутри переберёт резервные аутбаунды, см. PingRepository), и «Автовыбор LTE» (он
     *  настроен именно под мобильные сети), если по мобильной сети Европа вообще не пингуется.
     *  «Европейские» здесь = любой узел с реальной геометкой страны, кроме России (её уже
     *  обслуживает ветка JAMMED выше) — сам LTE- и EU-автовыбор геометки не имеют и в эту
     *  выборку не попадают. */
    private suspend fun maybeSwitchToLte() {
        val nodes = subscriptionRepository.nodes.value
        val lteNode = nodes.firstOrNull { it.name.contains("LTE", ignoreCase = true) } ?: return
        val euNode = nodes.firstOrNull { it.id != lteNode.id && it.geo == null && it.name.contains("EU") }
        val europeanNodes = nodes.filter { it.id != lteNode.id && it.geo != null && it.geo?.country != "Russia" }
        if (europeanNodes.isEmpty() || euNode == null) return

        pingRepository.pingAllInternal()
        val europeReachable = europeanNodes.any { (pingRepository.pings.value[it.id] ?: -1) >= 0 }
        val target = if (europeReachable) euNode else lteNode
        if (subscriptionRepository.selectedNode()?.id == target.id) return

        subscriptionRepository.select(target.id)
        _state.value = _state.value.copy(
            banner = if (europeReachable)
                Loc.s.bannerCellularSwitchedEu(stripLeadingFlag(euNode.name))
            else
                Loc.s.bannerCellularSwitchedLte(stripLeadingFlag(lteNode.name)),
            bannerKind = BannerKind.WARNING
        )
    }

    fun dismissBanner() {
        _state.value = _state.value.copy(banner = null)
    }

    fun startTunnel(context: Context) {
        val node = subscriptionRepository.selectedNode()
        if (node == null) {
            _state.value = _state.value.copy(error = Loc.s.noServerError)
            return
        }
        _state.value = _state.value.copy(error = null, connecting = true, globeStatus = "connecting")
        val intent = Intent(context, GodjiVpnService::class.java).apply {
            action = GodjiVpnService.ACTION_CONNECT
            putExtra(GodjiVpnService.EXTRA_VLESS_LINK, node.connectPayload)
            putExtra(GodjiVpnService.EXTRA_NODE_LABEL, node.geo?.country ?: stripLeadingFlag(node.name))
        }
        context.startForegroundService(intent)
    }

    fun stopTunnel(context: Context) {
        val intent = Intent(context, GodjiVpnService::class.java).apply {
            action = GodjiVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
        _state.value = _state.value.copy(connecting = false)
    }

    private fun startSpeedPolling() {
        if (speedJob?.isActive == true) return
        speedJob = viewModelScope.launch {
            val uid = Process.myUid()
            var lastRx = TrafficStats.getUidRxBytes(uid)
            var lastTx = TrafficStats.getUidTxBytes(uid)
            var lastTime = System.currentTimeMillis()
            // Считаем сразу же, не дожидаясь первого тика delay(1000) — иначе при пересоздании
            // ViewModel (свернули приложение, процесс убила система) секунду-другую висел бы
            // старый ярлык таймера, пока не сработает первый цикл ниже.
            connectedSinceOrNow().let { start ->
                _state.value = _state.value.copy(connectedTimeLabel = formatElapsed(System.currentTimeMillis() - start))
            }
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                val dtSeconds = (now - lastTime).coerceAtLeast(1) / 1000.0
                _state.value = _state.value.copy(
                    downSpeedMbps = (rx - lastRx).coerceAtLeast(0) / dtSeconds / 1_000_000.0,
                    upSpeedMbps = (tx - lastTx).coerceAtLeast(0) / dtSeconds / 1_000_000.0,
                    connectedTimeLabel = formatElapsed(now - connectedSinceOrNow())
                )
                lastRx = rx
                lastTx = tx
                lastTime = now
            }
        }
    }

    /** Настоящий момент подключения хранится в самом VPN-сервисе (connectedSinceMillis) —
     *  переживает пересоздание этого ViewModel, в отличие от локальной переменной. null
     *  теоретически возможен в узком окне между isRunning=true и записью таймстампа сервисом;
     *  подстраховка на "сейчас" не даёт таймеру не запуститься в этом окне. */
    private fun connectedSinceOrNow(): Long = GodjiVpnService.connectedSinceMillis.value ?: System.currentTimeMillis()

    private fun stopSpeedPolling() {
        speedJob?.cancel()
        speedJob = null
        _state.value = _state.value.copy(downSpeedMbps = 0.0, upSpeedMbps = 0.0)
    }

    private fun formatElapsed(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }
}
