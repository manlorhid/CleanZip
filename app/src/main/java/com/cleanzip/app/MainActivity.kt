package com.cleanzip.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.cleanzip.app.data.ThemeMode
import com.cleanzip.app.ui.screens.MainScreen
import com.cleanzip.app.ui.screens.OnboardingScreen
import com.cleanzip.app.ui.theme.CleanZipTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val prefs = viewModel.prefs
            val dark = when (prefs.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Iconos de la barra de estado acordes al tema elegido.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            var showTutorial by rememberSaveable { mutableStateOf(false) }

            CleanZipTheme(darkTheme = dark) {
                Crossfade(targetState = !prefs.onboardingDone || showTutorial, label = "onboarding") { onboarding ->
                    if (onboarding) {
                        OnboardingScreen(onFinish = {
                            prefs.markOnboardingDone(true)
                            showTutorial = false
                        })
                    } else {
                        MainScreen(
                            viewModel = viewModel,
                            onShowTutorial = { showTutorial = true }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Archivo abierto desde otra app (por ejemplo, el gestor de archivos). */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let(viewModel::onArchiveSelected)
        }
    }
}
