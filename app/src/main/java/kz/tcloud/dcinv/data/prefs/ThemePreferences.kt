package kz.tcloud.dcinv.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** UI-only preference, so plain (unencrypted) SharedPreferences is fine. */
@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM),
    )
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit().putString(KEY, mode.name).apply()
    }

    private companion object {
        const val KEY = "theme_mode"
    }
}
