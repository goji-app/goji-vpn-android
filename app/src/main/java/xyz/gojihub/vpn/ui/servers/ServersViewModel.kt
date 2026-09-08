package xyz.gojihub.vpn.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.gojihub.vpn.geo.CountryGeoLookup
import xyz.gojihub.vpn.subscription.PingRepository
import xyz.gojihub.vpn.subscription.SubscriptionRepository
import xyz.gojihub.vpn.util.stripLeadingFlag
import javax.inject.Inject

data class RefreshMessage(val isError: Boolean)

data class NodeUi(
    val id: String,
    val name: String,
    val code: String,
    val flag: String,
    val host: String,
    val port: Int,
    val pingMs: Int
)

data class ServersUiState(
    val servers: List<NodeUi> = emptyList(),
    val selectedId: String? = null,
    val checkingId: String? = null,
    val checkingAll: Boolean = false,
    val loading: Boolean = true
)

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val pingRepository: PingRepository
) : ViewModel() {

    val state: StateFlow<ServersUiState> = combine(
        subscriptionRepository.nodes,
        subscriptionRepository.selectedId,
        pingRepository.pings,
        pingRepository.checkingId,
        pingRepository.checkingAll
    ) { nodes, selectedId, pings, checkingId, checkingAll ->
        ServersUiState(
            servers = nodes.map {
                NodeUi(
                    id = it.id, name = stripLeadingFlag(it.name),
                    code = it.geo?.code ?: "??",
                    flag = it.geo?.code?.let(CountryGeoLookup::flagEmoji) ?: "🌐",
                    host = it.host, port = it.port, pingMs = pings[it.id] ?: -2
                )
            },
            selectedId = selectedId,
            checkingId = checkingId,
            checkingAll = checkingAll,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServersUiState())

    private val _refreshingSubscription = MutableStateFlow(false)
    val refreshingSubscription: StateFlow<Boolean> = _refreshingSubscription.asStateFlow()

    /** Результат ручного обновления подписки — показываем собственным баннером (в стиле
     *  приложения, не системным Toast) и прячем его сами через задержку ниже. */
    private val _refreshMessage = MutableStateFlow<RefreshMessage?>(null)
    val refreshMessage: StateFlow<RefreshMessage?> = _refreshMessage.asStateFlow()
    private var refreshMessageJob: Job? = null

    init {
        viewModelScope.launch {
            subscriptionRepository.refresh()
            pingRepository.pingAllInternal()
        }
    }

    fun select(id: String) = subscriptionRepository.select(id)

    fun pingOne(id: String) = pingRepository.pingOne(id)

    fun pingAll() = pingRepository.pingAll()

    /** Ручное обновление подписки (список серверов мог поменяться на бэкенде) — отдельная
     *  кнопка от "обновить пинг", с уведомлением об успехе/ошибке. */
    fun refreshSubscription() {
        if (_refreshingSubscription.value) return
        viewModelScope.launch {
            _refreshingSubscription.value = true
            val ok = subscriptionRepository.refresh()
            if (ok) pingRepository.pingAllInternal()
            _refreshingSubscription.value = false
            showRefreshMessage(RefreshMessage(isError = !ok))
        }
    }

    private fun showRefreshMessage(message: RefreshMessage) {
        refreshMessageJob?.cancel()
        _refreshMessage.value = message
        refreshMessageJob = viewModelScope.launch {
            delay(4500)
            _refreshMessage.value = null
        }
    }

    fun dismissRefreshMessage() {
        refreshMessageJob?.cancel()
        _refreshMessage.value = null
    }
}
