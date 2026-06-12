package kz.tcloud.dcinv.di

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kz.tcloud.dcinv.BuildConfig
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    /** Keycloak OIDC endpoints, derived from the realm config in BuildConfig. */
    @Provides
    @Singleton
    fun provideServiceConfig(): AuthorizationServiceConfiguration {
        val base = BuildConfig.KEYCLOAK_BASE_URL.trimEnd('/')
        val realm = BuildConfig.KEYCLOAK_REALM
        val prefix = "$base/realms/$realm/protocol/openid-connect"
        return AuthorizationServiceConfiguration(
            Uri.parse("$prefix/auth"),
            Uri.parse("$prefix/token"),
            null,
            Uri.parse("$prefix/logout"),
        )
    }

    @Provides
    @Singleton
    fun provideAuthorizationService(@ApplicationContext context: Context): AuthorizationService =
        AuthorizationService(context)

    /** Android Keystore-backed encrypted prefs for tokens and (later) the PIN hash. */
    @Provides
    @Singleton
    fun provideSecurePrefs(@ApplicationContext context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "dcinv_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
