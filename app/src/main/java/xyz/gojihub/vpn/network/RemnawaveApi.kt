package xyz.gojihub.vpn.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import xyz.gojihub.vpn.network.models.*

// Пути и форматы ниже сверены напрямую с реальным бэкендом gojihub.xyz
// (перехвачены сетевые запросы веб-версии приложения через email-вход).
interface RemnawaveApi {

    @POST("api/auth/email/send-otp")
    suspend fun sendOtp(@Body body: SendOtpRequest): SendOtpResponse

    // Response<...>, а не голый VerifyOtpResponse — с 7.1.0 сам JWT-токен сессии приходит
    // только в заголовке Set-Cookie (rw_session_token), тела ответа для этого недостаточно.
    // См. AuthRepository.verifyOtp() и комментарий у VerifyOtpResponse.
    @POST("api/auth/email/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpRequest): Response<VerifyOtpResponse>

    @GET("api/auth/me")
    suspend fun getMe(): MeResponse

    // provider: "google" | "yandex" | "telegram-oidc" (vk/apple существуют в UI, но сейчас
    // не сконфигурированы на бэкенде — возвращают 403).
    // Параметр называется именно app_redirect (сверено с логами бэкенда: с redirect_uri
    // сервер оставлял origin="" и не редиректил обратно в приложение после логина).
    @GET("api/auth/{provider}/start")
    suspend fun startOAuth(
        @Path("provider") provider: String,
        @Query("app_redirect") appRedirect: String,
        @Query("code_challenge") codeChallenge: String,
        @Query("code_challenge_method") codeChallengeMethod: String = "S256"
    ): StartAuthResponse

    @POST("api/auth/native/exchange")
    suspend fun exchangeNativeOAuth(@Body body: NativeExchangeRequest): NativeExchangeResponse

    @POST("api/auth/consent")
    suspend fun consent(@Body body: ConsentRequest): Response<Void>

    @GET("api/subscriptions")
    suspend fun getSubscriptions(): SubscriptionsResponse

    // С бэкенда 7.1.0 GET api/subscriptions (список выше) больше не отдаёт traffic вообще —
    // подтверждено живым запросом (см. SubscriptionInfo.traffic) — только этот, детальный
    // эндпоинт по конкретному id. SubscriptionRepository.refresh() дозапрашивает его для
    // выбранной подписки.
    @GET("api/subscriptions/{id}")
    suspend fun getSubscription(@Path("id") id: Long): SubscriptionInfo

    @GET("api/dashboard/plans")
    suspend fun getPlans(): PlansResponse

    // Страница "Мои рассылки"/"Новости" веб-версии (#/my-broadcasts) — список уже
    // отправленных пользователю новостей/объявлений.
    @GET("api/broadcasts/completed")
    suspend fun getBroadcasts(): List<BroadcastDto>

    // Страница "Рефералы" веб-версии (#/my-referrals, за флагом referral_enabled).
    @GET("api/dashboard/referrals")
    suspend fun getReferrals(): ReferralsResponse

    // Страница "Партнёрская программа" веб-версии (#/partner-dashboard, за флагом
    // partner_program_enabled) — только сводка/статус, см. PartnerStatusResponse.
    @GET("api/partner/status")
    suspend fun getPartnerStatus(): PartnerStatusResponse

    @GET("api/subscriptions/{id}/devices")
    suspend fun getDevices(@Path("id") id: Long): List<DeviceDto>

    @PATCH("api/subscriptions/{id}/devices/{hwid}")
    suspend fun renameDevice(
        @Path("id") id: Long,
        @Path("hwid") hwid: String,
        @Body body: RenameDeviceRequest
    ): Response<Void>

    @DELETE("api/subscriptions/{id}/devices/{hwid}")
    suspend fun deleteDevice(@Path("id") id: Long, @Path("hwid") hwid: String): Response<Void>
}
