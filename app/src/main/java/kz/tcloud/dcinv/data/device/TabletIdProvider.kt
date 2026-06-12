package kz.tcloud.dcinv.data.device

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the `tablet_id` sent with shift sessions. ToR §4.1.3 lets this be
 * "Android ID, MDM-assigned name, anything" — for now we use Android ID.
 * A provisioning-time override (set by ops per phone) plugs in here later.
 */
@Singleton
class TabletIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("HardwareIds")
    fun tabletId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-tablet"
}
