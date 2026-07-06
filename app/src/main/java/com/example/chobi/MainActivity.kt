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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chobi.theme.ChobiTheme
import com.example.chobi.ui.main.DYNAMIC_COLOR_KEY
import com.example.chobi.ui.main.THEME_MODE_KEY
import com.example.chobi.ui.main.dataStore
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      val dynamicColorState by remember {
        dataStore.data.map { it[DYNAMIC_COLOR_KEY] ?: false }
      }.collectAsStateWithLifecycle(initialValue = false)

      val themeModeState by remember {
        dataStore.data.map { it[THEME_MODE_KEY] ?: "system" }
      }.collectAsStateWithLifecycle(initialValue = "system")

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
