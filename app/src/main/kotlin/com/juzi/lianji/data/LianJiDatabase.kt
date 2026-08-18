package com.juzi.lianji.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExerciseEntity::class, WorkoutPlanEntity::class, PlanExerciseEntity::class, ScheduledWorkoutEntity::class, WorkoutSessionEntity::class, SessionExerciseEntity::class, WorkoutSetEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class LianJiDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun planDao(): PlanDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun sessionDao(): SessionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN startedAt INTEGER")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN completedAt INTEGER")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN durationSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN restStartedAt INTEGER")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN restEndedAt INTEGER")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN restDurationSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN trackingMode TEXT NOT NULL DEFAULT 'STRENGTH'")
                db.execSQL("UPDATE exercises SET trackingMode = 'CARDIO' WHERE bodyPart = 'cardio'")
                // Existing sessions retain their original set-based semantics; new sessions snapshot the new mode.
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN trackingMode TEXT NOT NULL DEFAULT 'STRENGTH'")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN distanceKm REAL NOT NULL DEFAULT 0.0")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN datasetNameZh TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE exercises SET datasetNameZh = nameZh")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN pausedAt INTEGER")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN pausedDurationMillis INTEGER NOT NULL DEFAULT 0")
            }
        }
        fun create(context: Context) = Room.databaseBuilder(context, LianJiDatabase::class.java, "lianji.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
    }
}
