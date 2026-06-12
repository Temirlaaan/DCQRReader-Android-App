package kz.tcloud.dcinv.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kz.tcloud.dcinv.BuildConfig
import kz.tcloud.dcinv.data.network.api.DeviceApi
import kz.tcloud.dcinv.data.network.api.MetaApi
import kz.tcloud.dcinv.data.network.api.QrApi
import kz.tcloud.dcinv.data.network.api.SessionApi
import kz.tcloud.dcinv.data.network.api.SystemApi
import kz.tcloud.dcinv.data.network.interceptor.AuthInterceptor
import kz.tcloud.dcinv.data.network.interceptor.RequestIdInterceptor
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        // Backend speaks snake_case; map it to camelCase Kotlin properties.
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    /**
     * TLS certificate pinning (ToR §5.4.1). Pins are intentionally empty for now —
     * an empty pinner is a no-op. Add the current + next corporate cert pins here
     * before production rollout to enable seamless rotation.
     */
    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner = CertificatePinner.Builder()
        // .add("qr.dc.t-cloud.kz", "sha256/CURRENT_CERT_PIN=")
        // .add("qr.dc.t-cloud.kz", "sha256/NEXT_CERT_PIN=")
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        requestIdInterceptor: RequestIdInterceptor,
        certificatePinner: CertificatePinner,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .addInterceptor(requestIdInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                    // Never print the bearer token to logcat, even in debug.
                    redactHeader("Authorization")
                },
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideSystemApi(retrofit: Retrofit): SystemApi = retrofit.create()

    @Provides
    @Singleton
    fun provideQrApi(retrofit: Retrofit): QrApi = retrofit.create()

    @Provides
    @Singleton
    fun provideSessionApi(retrofit: Retrofit): SessionApi = retrofit.create()

    @Provides
    @Singleton
    fun provideDeviceApi(retrofit: Retrofit): DeviceApi = retrofit.create()

    @Provides
    @Singleton
    fun provideMetaApi(retrofit: Retrofit): MetaApi = retrofit.create()
}
