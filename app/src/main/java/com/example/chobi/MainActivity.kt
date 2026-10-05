package com.example.chobi

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.chobi.theme.ChobiTheme
import com.example.chobi.ui.main.DYNAMIC_COLOR_KEY
import com.example.chobi.ui.main.THEME_MODE_KEY
import com.example.chobi.ui.main.dataStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    super.onCreate(savedInstanceState)

    var isReady by mutableStateOf(false)
    var dynamicColorState by mutableStateOf(false)
    var themeModeState by mutableStateOf("system")

    // Retrieve settings asynchronously before showing the main UI
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        dataStore.data.collect { preferences ->
          dynamicColorState = preferences[DYNAMIC_COLOR_KEY] ?: false
          themeModeState = preferences[THEME_MODE_KEY] ?: "system"
          isReady = true
        }
      }
    }

    // Keep splash screen visible until settings are loaded
    splashScreen.setKeepOnScreenCondition {
      !isReady
    }

    enableEdgeToEdge()
    window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
    setContent {
      if (isReady) {
        val darkTheme = when (themeModeState) {
          "light" -> false
          "dark" -> true
          else -> isSystemInDarkTheme()
        }

        // The app can force Light/Dark independently of the system theme, so the system bar
        // icons and the 3-button navigation scrim must follow the resolved app theme, not
        // the system one that the plain enableEdgeToEdge() call in onCreate() sees.
        DisposableEffect(darkTheme) {
          enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
              android.graphics.Color.TRANSPARENT,
              android.graphics.Color.TRANSPARENT
            ) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(
              NavigationBarLightScrim,
              NavigationBarDarkScrim
            ) { darkTheme }
          )
          onDispose {}
        }

        ChobiTheme(
          darkTheme = darkTheme,
          dynamicColor = dynamicColorState
        ) {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
          ) {
            Box(modifier = Modifier.fillMaxSize()) {
              MainNavigation()
              // Drawn last so the status bar area keeps the system bar icons legible
              // while content scrolls behind the (collapsing) app bar. Kept subtle:
              // a stronger scrim would hide the content again and read as a band.
              StatusBarProtection()
            }
          }
        }
      }
    }
  }
}

// Same scrim colors enableEdgeToEdge() uses by default for the 3-button navigation bar.
private val NavigationBarLightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NavigationBarDarkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * Faint scrim matching the app background that fades out across the status bar area, so
 * the system bar icons stay legible while content draws behind them edge-to-edge.
 *
 * Deliberately light: it only needs to darken bright content right under the icons.
 * Anything heavier (an opaque or strongly tinted gradient) hides the content that is
 * scrolling under the status bar and looks like a separate band on top of the screen.
 */
@Composable
private fun StatusBarProtection(
  color: Color = MaterialTheme.colorScheme.background,
) {
  Spacer(
    modifier = Modifier
      .fillMaxWidth()
      .height(
        with(LocalDensity.current) {
          WindowInsets.statusBars.getTop(this).toDp()
        }
      )
      .background(
        brush = Brush.verticalGradient(
          colorStops = arrayOf(
            0f to color.copy(alpha = 0.3f),
            0.5f to color.copy(alpha = 0.1f),
            1f to Color.Transparent
          )
        )
      )
  )
}
