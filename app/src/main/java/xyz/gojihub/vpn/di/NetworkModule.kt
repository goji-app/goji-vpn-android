package xyz.gojihub.vpn.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import xyz.gojihub.vpn.BuildConfig
import xyz.gojihub.vpn.auth.TokenManager
import xyz.gojihub.vpn.network.RemnawaveApi
import xyz.gojihub.vpn.vpn.GodjiVpnService
import javax.inject.Singleton

// Реальный клиентский бэкенд (Telegram Mini App) — сверено напрямую с сетевыми запросами
// веб-версии приложения. host.gojihub.xyz — это панель Remnawave (админка), не сюда.
private const val BASE_URL = "https://gojihub.xyz/"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): Interceptor = Interceptor { chain ->
        val token = tokenManager.accessToken()
        val request = chain.request().newBuilder().apply {
            if (token != null) addHeader("Authorization", "Bearer $token")
            // Бэкенд требует этот заголовок на POST /api/auth/* (лёгкая CSRF-защита —
            // проверено: без него /send-otp и /native/exchange отдают голый 403 "Forbidden"
            // ещё до хендлера, с ним — нормальный JSON-ответ). Веб-версия шлёт его через
            // XMLHttpRequest/fetch автоматически, plain OkHttp — нет, добавляем сами.
            addHeader("X-Requested-With", "XMLHttpRequest")
        }.build()
        val response = chain.proceed(request)
        // 401 с уже приложенным токеном — единственный надёжный признак того, что сессия
        // реально мертва на бэкенде (см. TokenManager.isLoggedIn()/markSessionExpired() —
        // раньше это решалось локальным сравнением с expires_at, из-за чего приложение могло
        // разлогинить пользователя ещё до того, как бэкенд на самом деле отказался бы принимать
        // токен). Не трогаем ответ без токена (аноним и так получит 401 по делу, не о протухшей
        // сессии) и не трогаем сами auth-эндпоинты (неверный OTP-код тоже может прийти как 401
        // и не должен разлогинивать несуществующую ещё сессию).
        if (response.code == 401 && token != null && !request.url.encodedPath.startsWith("/api/auth/")) {
            tokenManager.markSessionExpired()
        }
        response
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BODY — удобно для отладки, но логирует токены в plaintext. Не забудьте
            // переключить на NONE/BASIC в релизной сборке.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            // Пускаем запросы к gojihub.xyz через локальный SOCKS самого Xray, пока туннель
            // поднят — без этого собственные запросы приложения (и так исключённые из VPN,
            // см. GodjiVpnService.establishTunnel) шли по сырой сети телефона и падали в зоне
            // глушения мобильной сети, даже когда VPN на российский узел уже подключён.
            .proxySelector(GodjiVpnService.tunnelAwareProxySelector())
            .build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideRemnawaveApi(retrofit: Retrofit): RemnawaveApi =
        retrofit.create(RemnawaveApi::class.java)
}
