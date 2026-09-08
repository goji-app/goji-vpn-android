package xyz.gojihub.vpn.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.network.RemnawaveApi
import xyz.gojihub.vpn.network.models.PlanInfo
import xyz.gojihub.vpn.subscription.SubscriptionRepository
import xyz.gojihub.vpn.util.formatDate
import javax.inject.Inject

data class PeriodUi(val months: Int, val label: String)
data class PlanUi(val id: Long, val name: String, val description: String, val priceLabel: String, val isCurrent: Boolean)

data class PlansUiState(
    val planName: String = "—",
    val expiryLabel: String = "—",
    val daysLeft: Int = 0,
    val periods: List<PeriodUi> = emptyList(),
    val selectedMonths: Int = 1,
    val plans: List<PlanUi> = emptyList(),
    val refreshing: Boolean = false,
    val customerId: String? = null
)

@HiltViewModel
class PlansViewModel @Inject constructor(
    private val api: RemnawaveApi,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlansUiState())
    val state: StateFlow<PlansUiState> = _state

    private var rawPlans: List<PlanInfo> = emptyList()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            subscriptionRepository.refresh()
            val sub = subscriptionRepository.subscription.value
            _state.value = _state.value.copy(
                planName = sub?.planName ?: "—",
                expiryLabel = sub?.expireAt?.let(::formatDate) ?: "—",
                daysLeft = sub?.daysLeft ?: 0,
                // UUID клиента в Remnawave (settings.vnext[].users[].id у VLESS-профиля) — тот же,
                // что зашит в конфиг узлов, реальный Remnawave-идентификатор, в отличие от
                // customer_id (UUID шоп-бэкенда) и subscription.id (внутренний ID шоп-бэкенда) —
                // ни один из них не находится в панели Remnawave по словам пользователя.
                customerId = subscriptionRepository.clientUuid()
            )

            runCatching { api.getPlans() }.onSuccess { response ->
                rawPlans = response.plans
                val months = rawPlans.flatMap { it.prices }
                    .filter { it.priceType == "base" }
                    .map { it.periodValue }
                    .distinct().sorted()
                val periods = months.map { m ->
                    PeriodUi(m, Loc.s.plansMonthsLabel(m))
                }
                _state.value = _state.value.copy(
                    periods = periods,
                    selectedMonths = periods.firstOrNull()?.months ?: 1
                )
                rebuildPlanCards()
            }
        }
    }

    fun selectPeriod(months: Int) {
        _state.value = _state.value.copy(selectedMonths = months)
        rebuildPlanCards()
    }

    private fun rebuildPlanCards() {
        val months = _state.value.selectedMonths
        val current = _state.value.planName
        val cards = rawPlans.map { plan ->
            val price = plan.prices.firstOrNull { it.priceType == "base" && it.periodValue == months }
                ?: plan.prices.firstOrNull { it.priceType == "base" }
            val priceLabel = price?.let { "${it.price} ${it.currency}" } ?: "—"
            PlanUi(plan.id, plan.name, plan.description, priceLabel, isCurrent = plan.name == current)
        }
        _state.value = _state.value.copy(plans = cards)
    }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true)
        viewModelScope.launch {
            load()
            _state.value = _state.value.copy(refreshing = false)
        }
    }
}
