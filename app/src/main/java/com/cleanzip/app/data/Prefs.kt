package com.cleanzip.app.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Preferencias simples guardadas en SharedPreferences y expuestas como estado de Compose. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("cleanzip", Context.MODE_PRIVATE)

    var onboardingDone by mutableStateOf(sp.getBoolean(KEY_ONBOARDING, false))
        private set

    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(sp.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
    )
        private set

    var lastDestination: Uri?
        get() = sp.getString(KEY_DEST, null)?.let(Uri::parse)
        set(value) = sp.edit().putString(KEY_DEST, value?.toString()).apply()

    fun setOnboardingDone(done: Boolean) {
        onboardingDone = done
        sp.edit().putBoolean(KEY_ONBOARDING, done).apply()
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        sp.edit().putString(KEY_THEME, mode.name).apply()
    }

    private companion object {
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_THEME = "theme_mode"
        const val KEY_DEST = "last_destination"
    }
}
