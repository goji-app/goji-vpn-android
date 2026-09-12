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
import xyz.gojihub.vpn.network.models.ReferralEntry
import xyz.gojihub.vpn.network.models.DeviceDto
import xyz.gojihub.vpn.network.models.RenameDeviceRequest
import xyz.gojihub.vpn.subscription.SubscriptionRepository
import xyz.gojihub.vpn.subscription.TrafficHistoryRepository
import xyz.gojihub.vpn.util.formatDateTime
import xyz.gojihub.vpn.util.formatDate
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class PeriodUi(val months: Int, val label: String)
data class PlanUi(val id: Long, val name: String, val description: String, val priceLabel: String, val isCurrent: Boolean)
data class NewsButtonUi(val url: String, val text: String)
data class NewsUi(val id: String, val rawContent: String, val dateLabel: String, val buttons: List<NewsButtonUi>)

data class ReferralEntryUi(val displayName: String, val isActive: Boolean, val bonusDays: Int)
data class ReferralUi(
    val link: String,
    val webLink: String?,
    val totalReferrals: Int,
    val activeReferrals: Int,
    val totalBonusDays: Int,
    val entries: List<ReferralEntryUi>
)

/** applicationStatus: null (нет заявки — можно подать), "pending", "rejected". Остальные
 *  денежные действия (подать заявку, запросить вывод) — только через веб, см. PlansScreen. */
data class PartnerUi(
    val isPartner: Boolean,
    val isActive: Boolean,
    val applicationStatus: String?,
    val commissionRate: Double,
    val clientCount: Int,
    val totalEarned: Double,
    val availableBalance: Double,
    val pendingBalance: Double
)

data class TrafficDayUi(val dayLabel: String, val bytes: Long, val isToday: Boolean)

data class DeviceUi(
    val hwid: String,
    val name: String,
    val platform: String?,
    val createdAtLabel: String?,
    val busy: Boolean = false
)

/** Сколько новостей показывать не разворачивая список, и сколько на страницу после
 *  разворачивания (см. PlansScreen — свёрнутый вид против постраничного). */
const val NEWS_PREVIEW_COUNT = 2
const val NEWS_PAGE_SIZE = 3

data class PlansUiState(
    val planName: String = "—",
    val expiryLabel: String = "—",
    val daysLeft: Int = 0,
    val periods: List<PeriodUi> = emptyList(),
    val selectedMonths: Int = 1,
    val plans: List<PlanUi> = emptyList(),
    val refreshing: Boolean = false,
    val customerId: String? = null,
    val news: List<NewsUi> = emptyList(),
    val newsExpanded: Boolean = false,
    val newsPage: Int = 0,
    val referral: ReferralUi? = null,
    val partner: PartnerUi? = null,
    val subscriptionId: Long? = null,
    // На пробном/бесплатном тарифе — как на сайте, самостоятельное удаление устройства скрыто
    // за "обратитесь в поддержку" (см. комментарий у DeviceDto в Models.kt).
    val devicesDeleteSupportOnly: Boolean = false,
    val devices: List<DeviceUi> = emptyList(),
    val trafficHistory: List<TrafficDayUi> = emptyList()
)

/** Веб-версия маскирует половину имени/юзернейма/локальной части email точками —
 *  повторяем то же самое, а не показываем приглашённых пользователей полностью открытым
 *  текстом (это не наши данные, а личные данные третьих лиц). */
private fun maskHalf(s: String): String {
    if (s.isEmpty()) return s
    val visible = (s.length + 1) / 2
    return s.take(visible) + "•".repeat(s.length - visible)
}

private fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at < 0) return maskHalf(email)
    return maskHalf(email.substring(0, at)) + email.substring(at)
}

private fun DeviceDto.toUi(): DeviceUi = DeviceUi(
    hwid = hwid,
    name = readableName?.takeIf { it.isNotBlank() } ?: platform?.takeIf { it.isNotBlank() } ?: hwid.take(8),
    platform = platform,
    createdAtLabel = createdAt?.let(::formatDate)
)

private fun displayNameFor(e: ReferralEntry): String {
    e.tgUsername?.takeIf { it.isNotBlank() }?.let { return "@" + maskHalf(it) }
    val fullName = listOfNotNull(e.tgFirstName, e.tgLastName).joinToString(" ").trim()
    if (fullName.isNotEmpty()) return maskHalf(fullName)
    e.email?.takeIf { it.isNotBlank() }?.let { return maskEmail(it) }
    return "ID: ${e.refereeTelegramId ?: e.refereeId ?: 0}"
}

@HiltViewModel
class PlansViewModel @Inject constructor(
    private val api: RemnawaveApi,
    private val subscriptionRepository: SubscriptionRepository,
    private val trafficHistoryRepository: TrafficHistoryRepository
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
            val today = LocalDate.now()
            val locale = when (Loc.lang) {
                xyz.gojihub.vpn.i18n.AppLanguage.RU -> Locale("ru")
                xyz.gojihub.vpn.i18n.AppLanguage.ZH -> Locale.CHINESE
                xyz.gojihub.vpn.i18n.AppLanguage.EN -> Locale.ENGLISH
            }
            val trafficHistory = trafficHistoryRepository.dailyUsageLast(7).map { (date, bytes) ->
                TrafficDayUi(
                    dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    bytes = bytes,
                    isToday = date == today
                )
            }
            _state.value = _state.value.copy(
                planName = sub?.planName ?: "—",
                expiryLabel = sub?.expireAt?.let(::formatDate) ?: "—",
                daysLeft = sub?.daysLeft ?: 0,
                // UUID клиента в Remnawave (settings.vnext[].users[].id у VLESS-профиля) — тот же,
                // что зашит в конфиг узлов, реальный Remnawave-идентификатор, в отличие от
                // customer_id (UUID шоп-бэкенда) и subscription.id (внутренний ID шоп-бэкенда) —
                // ни один из них не находится в панели Remnawave по словам пользователя.
                customerId = subscriptionRepository.clientUuid(),
                subscriptionId = sub?.id,
                devicesDeleteSupportOnly = sub?.kind == "trial" || sub?.kind == "free",
                trafficHistory = trafficHistory,
                news = subscriptionRepository.broadcasts.value
                    .sortedByDescending { it.createdAt }
                    .map { b ->
                        NewsUi(
                            id = b.id,
                            rawContent = b.content,
                            dateLabel = formatDateTime(b.createdAt),
                            buttons = b.buttons().map { NewsButtonUi(it.url, it.text) }
                        )
                    }
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

            // referral_enabled/partner_program_enabled — оба флага сейчас включены на
            // бэкенде, но не проверяются отдельным запросом настроек: если фича когда-нибудь
            // выключат, эндпоинт просто перестанет отвечать успешно, и секция тихо не
            // покажется (тот же принцип, что уже применяется к getBroadcasts/getPlans).
            runCatching { api.getReferrals() }.onSuccess { r ->
                _state.value = _state.value.copy(
                    referral = ReferralUi(
                        link = r.link,
                        webLink = r.webLink,
                        totalReferrals = r.summary.totalReferrals,
                        activeReferrals = r.summary.activeReferrals,
                        totalBonusDays = r.summary.totalBonusDays,
                        entries = r.referrals.map { e ->
                            ReferralEntryUi(displayNameFor(e), e.isActive, e.bonusDays)
                        }
                    )
                )
            }

            sub?.id?.let { subId ->
                runCatching { api.getDevices(subId) }.onSuccess { list ->
                    _state.value = _state.value.copy(devices = list.map { it.toUi() })
                }
            }

            runCatching { api.getPartnerStatus() }.onSuccess { p ->
                _state.value = _state.value.copy(
                    partner = PartnerUi(
                        isPartner = p.isPartner,
                        isActive = p.partner?.isActive ?: false,
                        applicationStatus = p.application?.status,
                        commissionRate = p.partner?.commissionRate ?: 0.0,
                        clientCount = p.stats?.clientCount ?: 0,
                        totalEarned = p.partner?.totalEarned ?: 0.0,
                        availableBalance = p.partner?.availableBalance ?: 0.0,
                        pendingBalance = p.partner?.pendingBalance ?: 0.0
                    )
                )
            }
        }
    }

    /** Свёрнутый вид (NEWS_PREVIEW_COUNT новостей) <-> постраничный (NEWS_PAGE_SIZE на
     *  страницу, начиная с первой). */
    fun toggleNewsExpanded() {
        _state.value = _state.value.copy(newsExpanded = !_state.value.newsExpanded, newsPage = 0)
    }

    fun setNewsPage(page: Int) {
        _state.value = _state.value.copy(newsPage = page)
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

    fun renameDevice(hwid: String, newName: String) {
        val subId = _state.value.subscriptionId ?: return
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        setDeviceBusy(hwid, true)
        viewModelScope.launch {
            runCatching { api.renameDevice(subId, hwid, RenameDeviceRequest(trimmed)) }
                .onSuccess {
                    _state.value = _state.value.copy(devices = _state.value.devices.map {
                        if (it.hwid == hwid) it.copy(name = trimmed, busy = false) else it
                    })
                }
                .onFailure { setDeviceBusy(hwid, false) }
        }
    }

    /** [devicesDeleteSupportOnly] проверяется на экране до вызова — здесь дополнительно не
     *  дублируем проверку, чтобы не завязывать ViewModel на текст диалога поддержки. */
    fun deleteDevice(hwid: String) {
        val subId = _state.value.subscriptionId ?: return
        setDeviceBusy(hwid, true)
        viewModelScope.launch {
            runCatching { api.deleteDevice(subId, hwid) }
                .onSuccess {
                    _state.value = _state.value.copy(devices = _state.value.devices.filterNot { it.hwid == hwid })
                }
                .onFailure { setDeviceBusy(hwid, false) }
        }
    }

    private fun setDeviceBusy(hwid: String, busy: Boolean) {
        _state.value = _state.value.copy(devices = _state.value.devices.map {
            if (it.hwid == hwid) it.copy(busy = busy) else it
        })
    }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true)
        viewModelScope.launch {
            load()
            _state.value = _state.value.copy(refreshing = false)
        }
    }
}
