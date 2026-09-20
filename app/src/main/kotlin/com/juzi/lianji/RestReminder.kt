package com.juzi.lianji

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.room.withTransaction
import com.juzi.lianji.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class RestAlarm(val sessionId: Long, val setId: Long, val deadline: Long)

internal fun pendingRestAlarm(session: WorkoutSessionEntity?, rows: List<SessionSetRow>): RestAlarm? {
    if (session?.status != "ACTIVE") return null
    val rest = rows.lastOrNull { it.restStartedAt != null && it.restEndedAt == null && it.restNotifiedAt == null } ?: return null
    return RestAlarm(session.id,rest.setId,rest.restStartedAt!! + rest.restSeconds * 1_000L)
}

/** The alarm survives page disposal/process death; the in-process delay also works without exact permission. */
class RestReminder(private val context: Context, private val db: LianJiDatabase, private val settings: SettingsStore) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val refreshes = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            val rest = db.sessionDao().observeActive().distinctUntilChanged().flatMapLatest { session ->
                if (session == null) flowOf(null)
                else db.sessionDao().observeRows(session.id).map { pendingRestAlarm(session,it) }
            }.distinctUntilChanged()
            combine(rest,refreshes) { alarm,_ -> alarm }.collectLatest { alarm ->
                schedule(alarm)
                if (alarm != null) {
                    if (alarm.deadline > System.currentTimeMillis()) {
                        delay((alarm.deadline-System.currentTimeMillis()).coerceAtLeast(0))
                    }
                    deliver(alarm.setId,alarm.deadline)
                }
            }
        }
    }

    fun refresh() { refreshes.value++ }

    private fun schedule(alarm: RestAlarm?) {
        alarms.cancel(pendingIntent(null))
        if (alarm == null) return
        val intent = pendingIntent(alarm)
        try {
            if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,alarm.deadline,intent)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,alarm.deadline,intent)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,alarm.deadline,intent)
        }
    }

    internal suspend fun reschedule() {
        // The receiver keeps the process alive until the system alarm is registered again.
        val alarm = db.withTransaction {
            val session = db.sessionDao().getActive()
            pendingRestAlarm(session,session?.let { db.sessionDao().observeRows(it.id).first() }.orEmpty())
        }
        schedule(alarm)
        refresh()
    }

    private fun pendingIntent(alarm: RestAlarm?): PendingIntent = PendingIntent.getBroadcast(
        context,92,Intent(context,RestReminderReceiver::class.java).apply {
            action = ACTION_REST_ALARM
            if (alarm != null) { putExtra("set",alarm.setId); putExtra("deadline",alarm.deadline) }
        },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal suspend fun deliver(setId: Long, expectedDeadline: Long) {
        val preferences = settings.settings.first()
        db.withTransaction {
            val dao = db.sessionDao()
            val sessionId = dao.sessionIdForSet(setId) ?: return@withTransaction
            if (dao.getSession(sessionId)?.status != "ACTIVE") return@withTransaction
            val set = dao.getSet(setId) ?: return@withTransaction
            val exercise = dao.getSessionExercises(sessionId).firstOrNull { it.id == set.sessionExerciseId } ?: return@withTransaction
            val now = System.currentTimeMillis()
            if (set.restStartedAt == null || set.restEndedAt != null || set.restNotifiedAt != null) return@withTransaction
            if (set.restStartedAt + exercise.restSeconds*1_000L != expectedDeadline || now < expectedDeadline) return@withTransaction
            // Claim and post before invalidation can cancel the collector; no suspending work after claiming.
            dao.updateSet(set.copy(restNotifiedAt=now))
            if (preferences.vibration) context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(350,VibrationEffect.DEFAULT_AMPLITUDE))
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                val open = PendingIntent.getActivity(context,92,Intent(context,MainActivity::class.java).apply {
                    action=ACTION_OPEN_WORKOUT; putExtra(EXTRA_SESSION_ID,sessionId)
                    flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                context.getSystemService(NotificationManager::class.java).notify(90,NotificationCompat.Builder(context,"rest_timer")
                    .setSmallIcon(R.drawable.ic_notification_complete).setContentTitle("休息结束").setContentText("准备开始下一组")
                    .setContentIntent(open).setAutoCancel(true).setSilent(!preferences.sound).setTimeoutAfter(5_000).build())
            }
        }
    }
}

private const val ACTION_REST_ALARM = "com.juzi.lianji.action.REST_ALARM"

class RestReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LianJiApplication
        val pending = goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                if (intent.action == ACTION_REST_ALARM) app.restReminder.deliver(intent.getLongExtra("set",0),intent.getLongExtra("deadline",0))
                else app.restReminder.reschedule()
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.e("RestReminder","Unable to deliver or reschedule rest reminder",error) }
            finally { pending.finish() }
        }
    }
}
