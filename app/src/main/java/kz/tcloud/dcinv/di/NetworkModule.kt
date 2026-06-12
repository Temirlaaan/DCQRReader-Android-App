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
     * TLS certificate pinning (ToR §5.4.1). Pins are the Sectigo CA keys, not the
     * leaf: the leaf (`*.t-cloud.kz`, DV) is reissued yearly and its key may
     * rotate, while the intermediate (DV R36, valid to 2036) and root (R46, to
     * 2038) survive renewals as long as certs keep coming from Sectigo. OkHttp
     * accepts the chain when ANY pinned key appears in it. If the company ever
     * switches CA vendors, these pins must be updated in the same release.
     *
     * Re-derive a pin: openssl s_client -connect host:443 -showcerts, then per
     * cert: x509 -pubkey | pkey -pubin -outform der | dgst -sha256 -binary | base64
     */
    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner = CertificatePinner.Builder()
        // Sectigo Public Server Authentication CA DV R36 (intermediate)
        .add("qr-dc.t-cloud.kz", "sha256/a9khLOZJxlnJyrxstg/P+seiDCm+Yf3OsrXyFocBaI0=")
        // Sectigo Public Server Authentication Root R46 (backup)
        .add("qr-dc.t-cloud.kz", "sha256/Douxi77vs4G+Ib/BogbTFymEYq0QSFXwSgVCaZcI09Q=")
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
