package kz.tcloud.dcinv.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the last site/rack chosen in the device picker so bind/rebind open
 * pre-filtered on the rack the engineer is already working — they usually stay
 * at one rack for a while. Stored as plain ids (UI convenience, not sensitive).
 */
@Singleton
class PickerPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    var lastSiteId: String
        get() = prefs.getString(KEY_SITE, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_SITE, value).apply() }

    var lastRackId: String
        get() = prefs.getString(KEY_RACK, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_RACK, value).apply() }

    private companion object {
        const val KEY_SITE = "picker_last_site"
        const val KEY_RACK = "picker_last_rack"
    }
}
