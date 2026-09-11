package xyz.gojihub.vpn.network.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── Auth (email + OTP, см. gojihub.xyz/api/auth/email/*) ────

@JsonClass(generateAdapter = true)
data class SendOtpRequest(val email: String)

@JsonClass(generateAdapter = true)
data class SendOtpResponse(
    val sent: Boolean,
    @Json(name = "expires_in") val expiresIn: Int
)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(val email: String, val code: String)

@JsonClass(generateAdapter = true)
data class VerifyOtpResponse(
    val token: String,
    @Json(name = "expires_in") val expiresIn: Long,
    val user: AuthUser,
    @Json(name = "account_existed") val accountExisted: Boolean
)

@JsonClass(generateAdapter = true)
data class AuthUser(
    @Json(name = "customer_id") val customerId: String,
    val email: String?
)

@JsonClass(generateAdapter = true)
data class MeResponse(
    @Json(name = "customer_id") val customerId: String,
    val email: String?,
    val role: String,
    @Json(name = "consent_required") val consentRequired: Boolean
)

@JsonClass(generateAdapter = true)
data class StartAuthResponse(
    @Json(name = "auth_url") val authUrl: String
)

@JsonClass(generateAdapter = true)
data class NativeExchangeRequest(
    val code: String,
    @Json(name = "code_verifier") val codeVerifier: String,
    val provider: String
)

// Сверено напрямую с реальным ответом бэкенда (перехвачен в логах OkHttp) — в отличие от
// /api/auth/email/verify-otp, здесь нет "token"/"user"/"account_existed", ключи называются
// access_token/refresh_token. Раньше этот ответ ошибочно парсился как VerifyOtpResponse —
// Moshi падал на отсутствующем обязательном поле "token" ещё до вызова onAuthenticated(),
// поэтому токен никогда не сохранялся, а пользователя тихо возвращало на экран логина.
@JsonClass(generateAdapter = true)
data class NativeExchangeResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String?,
    @Json(name = "expires_in") val expiresIn: Long
)

// Поля сверены напрямую с бэкендом (перебором через curl с реальным токеном) —
// "terms" и "personal_data", ответ содержит terms_consent_at/pd_consent_at.
@JsonClass(generateAdapter = true)
data class ConsentRequest(
    val terms: Boolean = true,
    @Json(name = "personal_data") val personalData: Boolean = true
)

// ── Подписка (gojihub.xyz/api/subscriptions) ─────────────────

@JsonClass(generateAdapter = true)
data class SubscriptionsResponse(
    val subscriptions: List<SubscriptionInfo>
)

@JsonClass(generateAdapter = true)
data class SubscriptionInfo(
    val id: Long,
    val name: String,
    @Json(name = "is_primary") val isPrimary: Boolean,
    @Json(name = "is_active") val isActive: Boolean,
    @Json(name = "plan_name") val planName: String,
    @Json(name = "expire_at") val expireAt: String, // ISO-8601
    @Json(name = "days_left") val daysLeft: Int,
    @Json(name = "subscription_link") val subscriptionLink: String,
    @Json(name = "device_limit") val deviceLimit: Int,
    val traffic: TrafficInfo,
    // "trial" для пробных тарифов (device_limit:1) — см. project-subscription-hwid-gate.
    // Используется для разного порога уведомления об окончании (12ч для триала, 3 дня для
    // платных). Не приходит в старых ответах — по умолчанию null, тогда считаем "не триал".
    val kind: String? = null
)

@JsonClass(generateAdapter = true)
data class TrafficInfo(
    @Json(name = "used_bytes") val usedBytes: Long,
    @Json(name = "limit_bytes") val limitBytes: Long,
    @Json(name = "is_unlimited") val isUnlimited: Boolean
)

// ── Тарифы (gojihub.xyz/api/dashboard/plans) ─────────────────

@JsonClass(generateAdapter = true)
data class PlansResponse(
    val plans: List<PlanInfo>
)

@JsonClass(generateAdapter = true)
data class PlanInfo(
    val id: Long,
    val name: String,
    val description: String,
    val prices: List<PriceInfo>
)

@JsonClass(generateAdapter = true)
data class PriceInfo(
    @Json(name = "price_type") val priceType: String,
    val price: Int,
    val currency: String,
    @Json(name = "period_value") val periodValue: Int,
    @Json(name = "period_unit") val periodUnit: String
)

// ── Новости/рассылки (gojihub.xyz/api/broadcasts/completed) ──────────────
// Формат сверен с реальным ответом (перехвачен в сетевых запросах веб-версии, страница
// "Мои рассылки"/"Новости", #/my-broadcasts) — сама веб-версия защищается от
// непоследовательного регистра ключей (`e.Buttons||e.buttons||[]`), поэтому оба варианта
// объявлены здесь тоже, а BroadcastDto.buttons() ниже выбирает непустой.

@JsonClass(generateAdapter = true)
data class BroadcastDto(
    @Json(name = "ID") val id: String,
    @Json(name = "Content") val content: String,
    @Json(name = "CreatedAt") val createdAt: String,
    @Json(name = "Buttons") val buttonsUpper: List<BroadcastButtonDto>? = null,
    @Json(name = "buttons") val buttonsLower: List<BroadcastButtonDto>? = null
) {
    fun buttons(): List<BroadcastButtonDto> = buttonsUpper?.takeIf { it.isNotEmpty() } ?: buttonsLower.orEmpty()
}

@JsonClass(generateAdapter = true)
data class BroadcastButtonDto(val url: String, val text: String)

// ── Рефералы (gojihub.xyz/api/dashboard/referrals) ────────────────────────
// Формат сверен так же, как и broadcasts — анализом JS-бандла веб-версии
// (ReferralsPage), без входа в чей-либо аккаунт.

@JsonClass(generateAdapter = true)
data class ReferralsResponse(
    val link: String,
    @Json(name = "web_link") val webLink: String?,
    val description: String?,
    val summary: ReferralSummary,
    val referrals: List<ReferralEntry>
)

@JsonClass(generateAdapter = true)
data class ReferralSummary(
    @Json(name = "total_referrals") val totalReferrals: Int,
    @Json(name = "active_referrals") val activeReferrals: Int,
    @Json(name = "total_bonus_days") val totalBonusDays: Int
)

@JsonClass(generateAdapter = true)
data class ReferralEntry(
    val id: Long,
    @Json(name = "tg_username") val tgUsername: String? = null,
    @Json(name = "tg_first_name") val tgFirstName: String? = null,
    @Json(name = "tg_last_name") val tgLastName: String? = null,
    val email: String? = null,
    @Json(name = "referee_telegram_id") val refereeTelegramId: Long? = null,
    @Json(name = "referee_id") val refereeId: Long? = null,
    @Json(name = "is_active") val isActive: Boolean,
    @Json(name = "used_at") val usedAt: String?,
    @Json(name = "bonus_days") val bonusDays: Int
)

// ── Партнёрская программа (gojihub.xyz/api/partner/status) ────────────────
// Только то подмножество полей, которое реально показываем (сводка/статус) — форму заявки
// и вывода средств на клиенте не переопределяем, открываем веб-версию (см. PlansScreen),
// как и "Продлить" для тарифов — те же соображения: не переизобретать денежные формы нативно.

@JsonClass(generateAdapter = true)
data class PartnerStatusResponse(
    @Json(name = "is_partner") val isPartner: Boolean,
    val description: String?,
    val application: PartnerApplication?,
    val partner: PartnerInfo?,
    val stats: PartnerStats?,
    @Json(name = "approval_message") val approvalMessage: String?
)

@JsonClass(generateAdapter = true)
data class PartnerApplication(val status: String)

@JsonClass(generateAdapter = true)
data class PartnerInfo(
    @Json(name = "is_active") val isActive: Boolean,
    @Json(name = "commission_rate") val commissionRate: Double,
    @Json(name = "available_balance") val availableBalance: Double,
    @Json(name = "pending_balance") val pendingBalance: Double? = null,
    @Json(name = "total_earned") val totalEarned: Double
)

@JsonClass(generateAdapter = true)
data class PartnerStats(@Json(name = "client_count") val clientCount: Int = 0)
