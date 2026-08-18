package com.juzi.lianji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.juzi.lianji.ui.LianJiApp

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ always supplies a system splash for cold and warm starts.
        // Do not keep it over database work or Compose measurement; the app can
        // render its initial state immediately and update as local data arrives.
        installSplashScreen().setOnExitAnimationListener { it.remove() }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LianJiApp(mainViewModel) }
    }
}
