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
