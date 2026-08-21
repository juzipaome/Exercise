package com.juzi.lianji

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.juzi.lianji.ui.LianJiApp

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val requestedWorkoutId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedWorkoutId.value = intent.workoutId()
        enableEdgeToEdge()
        setContent { LianJiApp(mainViewModel, requestedWorkoutId.value) { requestedWorkoutId.value = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedWorkoutId.value = intent.workoutId()
    }

    private fun Intent.workoutId() = takeIf { action == ACTION_OPEN_WORKOUT }
        ?.getLongExtra(EXTRA_SESSION_ID, 0)
        ?.takeIf { it > 0 }
}
