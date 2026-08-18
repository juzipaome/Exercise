package com.juzi.lianji

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.juzi.lianji.data.*
import kotlinx.coroutines.*

class LianJiApplication : Application() {
    lateinit var database: LianJiDatabase
    lateinit var repository: LianJiRepository
    lateinit var settingsStore: SettingsStore
    lateinit var backupManager: BackupManager

    override fun onCreate() {
        super.onCreate()
        database = LianJiDatabase.create(this)
        repository = LianJiRepository(database)
        settingsStore = SettingsStore(this)
        backupManager = BackupManager(this, database)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("rest_timer", "休息计时", NotificationManager.IMPORTANCE_HIGH))
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { ExerciseImporter.seedIfNeeded(this@LianJiApplication, database.exerciseDao()) }
    }
}
