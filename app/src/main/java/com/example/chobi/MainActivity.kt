package com.example.chobi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
      dataStore.data.collect { preferences ->
        dynamicColorState = preferences[DYNAMIC_COLOR_KEY] ?: false
        themeModeState = preferences[THEME_MODE_KEY] ?: "system"
        isReady = true
      }
    }

    // Keep splash screen visible until settings are loaded
    splashScreen.setKeepOnScreenCondition {
      !isReady
    }

    enableEdgeToEdge()
    setContent {
      if (isReady) {
        val darkTheme = when (themeModeState) {
          "light" -> false
          "dark" -> true
          else -> isSystemInDarkTheme()
        }

        ChobiTheme(
          darkTheme = darkTheme,
          dynamicColor = dynamicColorState
        ) {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
          ) {
            MainNavigation()
          }
        }
      }
    }
  }
}
