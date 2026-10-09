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
import xyz.gojihub.vpn.subscription.AccountCache.Key as CacheKey
import xyz.gojihub.vpn.util.formatDateTime
import xyz.gojihub.vpn.util.formatDate
import javax.inject.Inject

data class PeriodUi(val months: Int, val label: String)
data class PlanUi(
    val id: Long,
    val name: String,
    val description: String,
    val priceLabel: String,
    val isCurrent: Boolean,
    // Реальная единица периода у ЭТОЙ конкретной цены (обычно "month", но не гарантия — берём
    // как есть, а не хардкодим), нужна для defaultPeriodUnit в /checkout (см. PlansScreen).
    val periodUnit: String
)
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

data class DeviceUi(
    val hwid: String,
    val name: String,
    val platform: String?,
    val createdAtLabel: String?,
    val connectedVia: String?,
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
    val deviceLimit: Int = 0,
    // customer_discount_percent с /api/dashboard/plans — 0, если у клиента нет персональной
    // скидки (см. PlansResponse.customerDiscountPercent); тогда бейдж просто не показываем.
    val personalDiscountPercent: Int = 0
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
    createdAtLabel = createdAt?.let(::formatDate),
    // Через что реально зарегистрировалось устройство на бэкенде (см. RemnawaveApi.getDevices)
    // — тот же User-Agent, что уходил в запросе подписки (см. SubscriptionRepository.fetchNodes,
    // там он намеренно подменяется на "v2rayNG/1.8.29" — этот же клиент бэкенд и запишет для
    // ЛЮБОГО устройства на Goji, а не только для настоящего v2rayNG; отдаём как есть, без
    // попытки угадать реальное имя клиента там, где бэкенд сам этого не различает).
    connectedVia = userAgent?.takeIf { it.isNotBlank() }
)

private fun displayNameFor(e: ReferralEntry): String {
    e.tgUsername?.takeIf { it.isNotBlank() }?.let { return "@" + maskHalf(it) }
    val fullName = listOfNotNull(e.tgFirstName, e.tgLastName).joinToString(" ").trim()
    if (fullName.isNotEmpty()) return maskHalf(fullName)
    e.email?.takeIf { it.isNotBlank() }?.let { return maskEmail(it) }
    return "ID: ${e.refereeTelegramId ?: e.refereeId ?: 0}"
}

/** Сколько живут сохранённые разделы «Подписки» (тарифы, рефералы, партнёрка, устройства),
 *  прежде чем страница тихо обновит их в фоне. Кнопка «обновить» — всегда сразу. */
private const val PAGE_TTL_MS = 60 * 60_000L

@HiltViewModel
class PlansViewModel @Inject constructor(
    private val api: RemnawaveApi,
    private val subscriptionRepository: SubscriptionRepository,
    private val authRepository: xyz.gojihub.vpn.auth.AuthRepository,
    private val accountCache: xyz.gojihub.vpn.subscription.AccountCache
) : ViewModel() {

    /** Код переноса входа на другое устройство (см. AuthRepository.transferUri). */
    fun transferUri(): String? = authRepository.transferUri()

    private val _state = MutableStateFlow(PlansUiState())
    val state: StateFlow<PlansUiState> = _state

    private var rawPlans: List<PlanInfo> = emptyList()
    private var rawDevices: List<DeviceDto> = emptyList()
    private val savedAt = mutableMapOf<xyz.gojihub.vpn.subscription.AccountCache.Key, Long>()

    init {
        // 1) Сразу — всё, что уже сохранено на телефоне: страница не бывает пустой, даже если
        //    сеть пропала или VPN как раз переподключается.
        showCached()
        // 2) Подписка и новости — живые потоки репозитория (их обновляют воркер, «Главная» и
        //    кнопка «обновить»), страница просто следует за ними.
        viewModelScope.launch { subscriptionRepository.subscription.collect { applySubscription(it) } }
        viewModelScope.launch { subscriptionRepository.broadcasts.collect { applyNews(it) } }
        // 3) Тихое обновление в фоне — только устаревших разделов (старше PAGE_TTL_MS).
        viewModelScope.launch { fetch(force = false) }
    }

    private fun showCached() {
        accountCache.load<xyz.gojihub.vpn.network.models.PlansResponse>(CacheKey.PLANS)?.let { savedAt[CacheKey.PLANS] = it.savedAt; applyPlans(it.value) }
        accountCache.load<xyz.gojihub.vpn.network.models.ReferralsResponse>(CacheKey.REFERRALS)?.let { savedAt[CacheKey.REFERRALS] = it.savedAt; applyReferrals(it.value) }
        accountCache.load<xyz.gojihub.vpn.network.models.PartnerStatusResponse>(CacheKey.PARTNER)?.let { savedAt[CacheKey.PARTNER] = it.savedAt; applyPartner(it.value) }
        accountCache.load<List<DeviceDto>>(CacheKey.DEVICES)?.let { savedAt[CacheKey.DEVICES] = it.savedAt; applyDevices(it.value) }
    }

    private fun stale(key: xyz.gojihub.vpn.subscription.AccountCache.Key) =
        System.currentTimeMillis() - (savedAt[key] ?: 0L) > PAGE_TTL_MS

    /** Сбой любого запроса ничего не стирает: на экране остаётся последнее сохранённое. */
    private suspend fun fetch(force: Boolean) {
        if (force) subscriptionRepository.refresh() else subscriptionRepository.refreshIfStale()
        val sub = subscriptionRepository.subscription.value
        if (force || stale(CacheKey.PLANS)) {
            runCatching { api.getPlans(subscriptionId = sub?.id) }.onSuccess { saveAndApply(CacheKey.PLANS, it) { r -> applyPlans(r) } }
        }
        if (force || stale(CacheKey.REFERRALS)) {
            runCatching { api.getReferrals() }.onSuccess { saveAndApply(CacheKey.REFERRALS, it) { r -> applyReferrals(r) } }
        }
        if (sub?.id != null && (force || stale(CacheKey.DEVICES))) {
            runCatching { api.getDevices(sub.id) }.onSuccess { saveAndApply(CacheKey.DEVICES, it) { r -> applyDevices(r) } }
        }
        if (force || stale(CacheKey.PARTNER)) {
            runCatching { api.getPartnerStatus() }.onSuccess { saveAndApply(CacheKey.PARTNER, it) { r -> applyPartner(r) } }
        }
    }

    private suspend fun <T : Any> saveAndApply(key: xyz.gojihub.vpn.subscription.AccountCache.Key, value: T, apply: (T) -> Unit) {
        apply(value)
        savedAt[key] = System.currentTimeMillis()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { accountCache.save(key, value) }
    }

    private fun applySubscription(sub: xyz.gojihub.vpn.network.models.SubscriptionInfo?) {
        if (sub == null) return
        _state.value = _state.value.copy(
            planName = sub.planName,
            expiryLabel = formatDate(sub.expireAt),
            daysLeft = sub.daysLeft,
            // UUID клиента в Remnawave (settings.vnext[].users[].id у VLESS-профиля) — тот же,
            // что зашит в конфиг узлов, реальный Remnawave-идентификатор, в отличие от
            // customer_id (UUID шоп-бэкенда) и subscription.id (внутренний ID шоп-бэкенда) —
            // ни один из них не находится в панели Remnawave по словам пользователя.
            customerId = subscriptionRepository.clientUuid(),
            subscriptionId = sub.id,
            devicesDeleteSupportOnly = sub.kind == "trial" || sub.kind == "free",
            deviceLimit = sub.deviceLimit
        )
        rebuildPlanCards()
    }

    private fun applyNews(list: List<xyz.gojihub.vpn.network.models.BroadcastDto>) {
        _state.value = _state.value.copy(
            news = list
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
    }

    private fun applyPlans(response: xyz.gojihub.vpn.network.models.PlansResponse) {
        rawPlans = response.plans
        val periods = rawPlans.flatMap { it.prices }
            .filter { it.priceType == "base" }
            .map { it.periodValue }
            .distinct().sorted()
            .map { m -> PeriodUi(m, Loc.s.plansMonthsLabel(m)) }
        val keep = _state.value.selectedMonths.takeIf { m -> periods.any { it.months == m } }
        _state.value = _state.value.copy(
            personalDiscountPercent = response.customerDiscountPercent?.toInt() ?: 0,
            periods = periods,
            selectedMonths = keep ?: periods.firstOrNull()?.months ?: 1
        )
        rebuildPlanCards()
    }

    // referral_enabled/partner_program_enabled — оба флага сейчас включены на бэкенде, но не
    // проверяются отдельным запросом настроек: если фичу выключат, эндпоинт просто перестанет
    // отвечать успешно, и секция тихо не покажется.
    private fun applyReferrals(r: xyz.gojihub.vpn.network.models.ReferralsResponse) {
        _state.value = _state.value.copy(
            referral = ReferralUi(
                link = r.link,
                webLink = r.webLink,
                totalReferrals = r.summary.totalReferrals,
                activeReferrals = r.summary.activeReferrals,
                totalBonusDays = r.summary.totalBonusDays,
                entries = r.referrals.map { e -> ReferralEntryUi(displayNameFor(e), e.isActive, e.bonusDays) }
            )
        )
    }

    private fun applyDevices(list: List<DeviceDto>) {
        rawDevices = list
        _state.value = _state.value.copy(devices = list.map { it.toUi() })
    }

    private fun applyPartner(p: xyz.gojihub.vpn.network.models.PartnerStatusResponse) {
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
            PlanUi(plan.id, plan.name, plan.description, priceLabel, isCurrent = plan.name == current, periodUnit = price?.periodUnit ?: "month")
        }
        _state.value = _state.value.copy(plans = cards)
    }

    fun renameDevice(hwid: String, newName: String) {
        val subId = _state.value.subscriptionId ?: return
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        setDeviceBusy(hwid, true)
        viewModelScope.launch {
            runCatching {
                val response = api.renameDevice(subId, hwid, RenameDeviceRequest(trimmed))
                if (!response.isSuccessful) {
                    error("HTTP ${response.code()}: ${response.errorBody()?.string().orEmpty()}")
                }
            }
                .onSuccess {
                    _state.value = _state.value.copy(devices = _state.value.devices.map {
                        if (it.hwid == hwid) it.copy(name = trimmed, busy = false) else it
                    })
                    rawDevices = rawDevices.map { if (it.hwid == hwid) it.copy(readableName = trimmed) else it }
                    accountCache.save(xyz.gojihub.vpn.subscription.AccountCache.Key.DEVICES, rawDevices)
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
            runCatching {
                val response = api.deleteDevice(subId, hwid)
                if (!response.isSuccessful) {
                    error("HTTP ${response.code()}: ${response.errorBody()?.string().orEmpty()}")
                }
            }
                .onSuccess {
                    _state.value = _state.value.copy(devices = _state.value.devices.filterNot { it.hwid == hwid })
                    rawDevices = rawDevices.filterNot { it.hwid == hwid }
                    accountCache.save(xyz.gojihub.vpn.subscription.AccountCache.Key.DEVICES, rawDevices)
                }
                .onFailure { setDeviceBusy(hwid, false) }
        }
    }

    private fun setDeviceBusy(hwid: String, busy: Boolean) {
        _state.value = _state.value.copy(devices = _state.value.devices.map {
            if (it.hwid == hwid) it.copy(busy = busy) else it
        })
    }

    /** Кнопка «обновить»: всё сразу с сервера. Раньше индикатор гас мгновенно — load()
     *  запускал отдельную корутину и не ждал её; теперь ждём реального окончания запросов. */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.value = _state.value.copy(refreshing = true)
        viewModelScope.launch {
            fetch(force = true)
            _state.value = _state.value.copy(refreshing = false)
        }
    }
}
