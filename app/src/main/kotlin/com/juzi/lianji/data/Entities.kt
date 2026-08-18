package com.juzi.lianji.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val nameEn: String,
    val nameZh: String,
    val bodyPart: String,
    val equipment: String,
    val target: String,
    val muscleGroup: String,
    val secondaryMuscles: String,
    val instructionsZh: String,
    val instructionsEn: String,
    val imagePath: String?,
    val gifPath: String?,
    val attribution: String,
    @ColumnInfo(defaultValue = "''") val datasetNameZh: String = "",
    @ColumnInfo(defaultValue = "'STRENGTH'") val trackingMode: String = TrackingMode.STRENGTH,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
)

@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "plan_exercises",
    foreignKeys = [
        ForeignKey(entity = WorkoutPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index("planId"), Index("exerciseId")],
)
data class PlanExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val exerciseId: String = "",
    val position: Int,
    val defaultSets: Int = 3,
    val defaultReps: Int = 10,
    val defaultWeightKg: Double = 0.0,
    val restSeconds: Int = 90,
)

@Entity(
    tableName = "scheduled_workouts",
    foreignKeys = [ForeignKey(entity = WorkoutPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("planId"), Index("scheduledDate")],
)
data class ScheduledWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long?,
    val planName: String,
    val scheduledDate: String,
    val status: String = "PLANNED",
)

@Entity(tableName = "workout_sessions", indices = [Index("localDate")])
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePlanId: Long?,
    val planNameSnapshot: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val localDate: String,
    val status: String = "ACTIVE",
)

@Entity(
    tableName = "session_exercises",
    foreignKeys = [ForeignKey(entity = WorkoutSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val exerciseNameSnapshot: String,
    val position: Int,
    val restSeconds: Int,
    @ColumnInfo(defaultValue = "'STRENGTH'") val trackingMode: String = TrackingMode.STRENGTH,
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(entity = SessionExerciseEntity::class, parentColumns = ["id"], childColumns = ["sessionExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionExerciseId")],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val position: Int,
    val weightKg: Double,
    val reps: Int,
    val completed: Boolean = false,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val durationSeconds: Int = 0,
    val pausedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val pausedDurationMillis: Long = 0,
    val restStartedAt: Long? = null,
    val restEndedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val restDurationSeconds: Int = 0,
    @ColumnInfo(defaultValue = "0.0") val distanceKm: Double = 0.0,
)

object TrackingMode {
    const val STRENGTH = "STRENGTH"
    const val CARDIO = "CARDIO"
}

val ExerciseEntity.isCardio: Boolean get() = trackingMode == TrackingMode.CARDIO

data class PlanSummary(val id: Long, val name: String, val note: String, val exerciseCount: Int)
data class ExerciseNameOverride(val id:String,val nameZh:String)
data class DaySummary(val localDate: String, val title: String, val status: String)
data class MonthlyExerciseStat(
    val exerciseName: String,
    val bodyPart: String,
    val completedSets: Int,
    val activeSeconds: Long,
    val volume: Double,
    val trackingMode: String = TrackingMode.STRENGTH,
)
data class ExercisePersonalBest(
    val exerciseId: String,
    val maxWeightKg: Double,
    val maxReps: Int,
    val maxDistanceKm: Double,
    val maxDurationSeconds: Int,
)
data class SessionSetRow(
    val setId: Long,
    val sessionExerciseId: Long,
    val exerciseName: String,
    val exercisePosition: Int,
    val restSeconds: Int,
    val setPosition: Int,
    val weightKg: Double,
    val reps: Int,
    val completed: Boolean,
    val exerciseId: String = "",
    val imagePath: String? = null,
    val gifPath: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val durationSeconds: Int = 0,
    val pausedAt: Long? = null,
    val pausedDurationMillis: Long = 0,
    val restStartedAt: Long? = null,
    val restEndedAt: Long? = null,
    val restDurationSeconds: Int = 0,
    val trackingMode: String = TrackingMode.STRENGTH,
    val distanceKm: Double = 0.0,
)
