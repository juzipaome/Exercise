package com.juzi.lianji

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Icon
import android.os.Bundle
import com.juzi.lianji.data.SessionSetRow
import com.juzi.lianji.data.TrackingMode
import com.juzi.lianji.data.WorkoutRepository
import com.juzi.lianji.data.WorkoutSessionEntity
import com.juzi.lianji.data.activeDurationSeconds
import com.juzi.lianji.data.nextWorkoutSet
import com.juzi.lianji.data.orderedWorkoutGroups
import com.juzi.lianji.ui.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong

const val ACTION_OPEN_WORKOUT = "com.juzi.lianji.action.OPEN_WORKOUT"
const val EXTRA_SESSION_ID = "session_id"

private const val ACTION_START_SET = "com.juzi.lianji.action.START_SET"
private const val ACTION_PAUSE_SET = "com.juzi.lianji.action.PAUSE_SET"
private const val ACTION_COMPLETE_SET = "com.juzi.lianji.action.COMPLETE_SET"
private const val EXTRA_SET_ID = "set_id"
private const val CHANNEL_ID = "workout_timer"
private const val NOTIFICATION_ID = 91
private const val PRIMARY_ACTION_KEY = "miui.focus.action_workout"
private const val COMPLETE_ACTION_KEY = "miui.focus.action_complete"
private const val PICTURE_KEY = "miui.focus.pic_workout"

internal enum class WorkoutTimerType(val islandValue: Int) { Countdown(-1), CountUp(1), Static(0) }

internal data class WorkoutNotificationModel(
    val sessionId: Long,
    val title: String,
    val status: String,
    val timerType: WorkoutTimerType,
    val timerWhen: Long,
    val timerTotal: Long = 0,
    val staticSeconds: Long = 0,
    val actionTitle: String? = null,
    val actionSetId: Long? = null,
    val actionIsPause: Boolean = false,
    val completeActionTitle: String? = null,
    val completeSetId: Long? = null,
    val picturePath: String? = null,
)

private data class WorkoutActionSpec(val key: String, val title: String, val intentAction: String, val setId: Long, val icon: Int)

internal fun workoutNotificationModel(
    session: WorkoutSessionEntity,
    rows: List<SessionSetRow>,
    now: Long = System.currentTimeMillis(),
): WorkoutNotificationModel {
    val rest = rows.lastOrNull { it.restStartedAt != null && it.restEndedAt == null }
    if (rest != null) {
        val next = nextWorkoutSet(rows, rest.setId)
        val restDurationMillis = rest.restSeconds * 1_000L
        return WorkoutNotificationModel(
            session.id,
            rest.exerciseName,
            "组间休息",
            WorkoutTimerType.Countdown,
            rest.restStartedAt!! + restDurationMillis,
            actionTitle = next?.let { "开始下一项" },
            actionSetId = next?.setId,
            picturePath = rest.imagePath ?: rest.gifPath,
        )
    }

    val running = rows.firstOrNull { it.startedAt != null && !it.completed && it.pausedAt == null }
    if (running != null) return WorkoutNotificationModel(
        session.id,
        running.exerciseName,
        if (running.trackingMode == TrackingMode.CARDIO) "有氧训练" else "第${running.setPosition + 1}组",
        WorkoutTimerType.CountUp,
        running.startedAt!! + running.pausedDurationMillis,
        actionTitle = "暂停",
        actionSetId = running.setId,
        actionIsPause = true,
        completeActionTitle = if (running.trackingMode == TrackingMode.CARDIO) "结束有氧" else "完成本组",
        completeSetId = running.setId,
        picturePath = running.imagePath ?: running.gifPath,
    )

    val paused = rows.filter { it.startedAt != null && !it.completed && it.pausedAt != null }.maxByOrNull { it.pausedAt!! }
    if (paused != null) return WorkoutNotificationModel(
        session.id,
        paused.exerciseName,
        "已暂停",
        WorkoutTimerType.Static,
        paused.startedAt!!,
        staticSeconds = activeDurationSeconds(paused.startedAt, now, paused.pausedAt, paused.pausedDurationMillis),
        actionTitle = "继续",
        actionSetId = paused.setId,
        completeActionTitle = if (paused.trackingMode == TrackingMode.CARDIO) "结束有氧" else "完成本组",
        completeSetId = paused.setId,
        picturePath = paused.imagePath ?: paused.gifPath,
    )

    val next = orderedWorkoutGroups(rows).asSequence().flatten().firstOrNull { !it.completed }
    return WorkoutNotificationModel(
        session.id,
        next?.exerciseName ?: session.planNameSnapshot,
        if (next == null) "等待完成" else "等待开始",
        WorkoutTimerType.Static,
        session.startedAt,
        actionTitle = next?.let { "开始" },
        actionSetId = next?.setId,
        picturePath = next?.imagePath ?: next?.gifPath,
    )
}

class WorkoutNotificationCoordinator(
    private val context: Context,
    private val repository: WorkoutRepository,
) {
    @Volatile private var latest: WorkoutNotificationModel? = null
    private lateinit var scope: CoroutineScope
    private val sequence = AtomicLong(System.currentTimeMillis())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            repository.activeSession.flatMapLatest { session ->
                if (session == null) flowOf(null)
                else repository.rows(session.id).map { workoutNotificationModel(session, it) }
            }.collect { model ->
                latest = model
                if (model == null) context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                else publish(model)
            }
        }
    }

    fun refresh() { scope.launch { latest?.let(::publish) } }

    private fun publish(model: WorkoutNotificationModel) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val specs = buildList {
            model.actionSetId?.let { setId ->
                add(WorkoutActionSpec(
                    PRIMARY_ACTION_KEY,
                    model.actionTitle ?: return@let,
                    if (model.actionIsPause) ACTION_PAUSE_SET else ACTION_START_SET,
                    setId,
                    when {
                        model.actionIsPause -> R.drawable.ic_notification_pause
                        else -> R.drawable.ic_notification_play
                    },
                ))
            }
            model.completeSetId?.let { setId ->
                add(WorkoutActionSpec(COMPLETE_ACTION_KEY, model.completeActionTitle ?: "完成", ACTION_COMPLETE_SET, setId, R.drawable.ic_notification_complete))
            }
        }
        val actions = specs.associate { spec ->
            val intent = Intent(context, WorkoutNotificationReceiver::class.java).apply {
                action = spec.intentAction
                putExtra(EXTRA_SET_ID, spec.setId)
                putExtra(EXTRA_SESSION_ID, model.sessionId)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (spec.setId * 4 + specs.indexOf(spec)).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            spec.key to Notification.Action.Builder(Icon.createWithResource(context, spec.icon), spec.title, pendingIntent).build()
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            model.sessionId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                this.action = ACTION_OPEN_WORKOUT
                putExtra(EXTRA_SESSION_ID, model.sessionId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(model.title)
            .setContentText(model.status + if (model.timerType == WorkoutTimerType.Static) " · ${formatDuration(model.staticSeconds)}" else "")
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
        when (model.timerType) {
            WorkoutTimerType.Countdown -> builder.setWhen(model.timerWhen + model.timerTotal).setUsesChronometer(true).setChronometerCountDown(true)
            WorkoutTimerType.CountUp -> builder.setWhen(model.timerWhen).setUsesChronometer(true)
            WorkoutTimerType.Static -> builder.setUsesChronometer(false)
        }
        actions.values.forEach(builder::addAction)
        val notification = builder.build()
        if (actions.isNotEmpty()) notification.extras.putBundle("miui.focus.actions", Bundle().apply { actions.forEach(::putParcelable) })
        val picture = model.picturePath?.let { path ->
            runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }?.let(::roundedPicture)?.let(Icon::createWithBitmap) ?: Icon.createWithResource(context, R.mipmap.ic_launcher)
        notification.extras.putBundle("miui.focus.pics", Bundle().apply { putParcelable(PICTURE_KEY, picture) })
        notification.extras.putString("miui.focus.param", islandParams(model, actions.keys.toList(), sequence.incrementAndGet()).toString())
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }
}

private fun roundedPicture(source: Bitmap): Bitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { result ->
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    Canvas(result).drawRoundRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), minOf(source.width, source.height) / 4f, minOf(source.width, source.height) / 4f, paint)
}

internal fun islandParams(model: WorkoutNotificationModel, actionKeys: List<String>, sequence: Long = 1): JsonObject {
    val timer = timerInfo(model)
    return buildJsonObject { put("param_v2", buildJsonObject {
        put("protocol", 1)
        put("business", "workout")
        put("islandFirstFloat", true)
        put("enableFloat", false)
        put("updatable", true)
        put("sequence", sequence)
        put("filterWhenNoPermission", false)
        put("timeout", 720)
        put("ticker", "${model.status} · ${model.title}")
        put("aodTitle", "${model.status} · ${model.title}")
        put("chatInfo", buildJsonObject {
            put("picProfile", PICTURE_KEY)
            put("title", if (model.timerType == WorkoutTimerType.Countdown) "休息 · ${model.title}" else model.title)
            if (timer != null) put("timerInfo", timer)
            else put("content", "${model.status} · ${formatDuration(model.staticSeconds)}")
        })
        if (actionKeys.isNotEmpty()) put("actions", buildJsonArray { actionKeys.forEach { key -> add(buildJsonObject {
            val color = if (key == COMPLETE_ACTION_KEY) "#30D158" else "#3482FF"
            put("type", 0)
            put("action", key)
            put("clickWithCollapse", false)
            put("actionTitleColor", "#FFFFFF")
            put("actionTitleColorDark", "#FFFFFF")
            put("actionBgColor", color)
            put("actionBgColorDark", color)
        }) } })
        put("param_island", buildJsonObject {
            put("islandProperty", 2)
            put("islandTimeout", 43_200)
            put("bigIslandArea", buildJsonObject {
                put("imageTextInfoLeft", buildJsonObject {
                    put("type", 1)
                    put("textInfo", buildJsonObject {
                        put("title", model.status)
                        put("showHighlightColor", true)
                    })
                })
                put("sameWidthDigitInfo", buildJsonObject {
                    if (timer != null) put("timerInfo", timer)
                    else put("digit", formatDuration(model.staticSeconds))
                    put("showHighlightColor", true)
                })
            })
            put("smallIslandArea", buildJsonObject {})
        })
    }) }
}

private fun timerInfo(model: WorkoutNotificationModel): JsonObject? = model.timerType.takeUnless { it == WorkoutTimerType.Static }?.let { timerType ->
    buildJsonObject {
        val current = System.currentTimeMillis()
        put("timerType", timerType.islandValue)
        put("timerWhen", model.timerWhen)
        put("timerTotal", model.timerTotal)
        // Official field tables use timerSystemCurrent, while their JSON examples use timerCurrent.
        put("timerSystemCurrent", current)
        put("timerCurrent", current)
    }
}

class WorkoutNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val setId = intent.getLongExtra(EXTRA_SET_ID, 0).takeIf { it > 0 } ?: return
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, 0).takeIf { it > 0 } ?: return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as LianJiApplication).repository
                when (intent.action) {
                    ACTION_START_SET -> repository.beginSet(setId)
                    ACTION_PAUSE_SET -> repository.pauseSet(setId)
                    ACTION_COMPLETE_SET -> repository.rows(sessionId).first().firstOrNull { it.setId == setId }?.let { row ->
                        if (row.trackingMode == TrackingMode.CARDIO) repository.completeCardio(setId, row.distanceKm)
                        else repository.completeSet(setId, row.weightKg, row.reps)
                    }
                }
            } finally {
                result.finish()
            }
        }
    }
}
