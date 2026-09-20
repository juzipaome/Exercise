package com.juzi.lianji.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExerciseEntity::class, WorkoutPlanEntity::class, PlanExerciseEntity::class, ScheduledWorkoutEntity::class, WorkoutSessionEntity::class, SessionExerciseEntity::class, WorkoutSetEntity::class, ImportedBackupEntity::class],
    version = 6,
    exportSchema = true,
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
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN restNotifiedAt INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS imported_backups (fingerprint TEXT NOT NULL PRIMARY KEY, importedAt INTEGER NOT NULL)")
                // Preserve the former timestamp-derived display order once, then persist all moves.
                val positions = mutableListOf<Pair<Long, Int>>()
                db.query("SELECT se.id, se.sessionId, MIN(COALESCE(ws.startedAt,ws.completedAt)) firstStarted FROM session_exercises se LEFT JOIN workout_sets ws ON ws.sessionExerciseId=se.id GROUP BY se.id ORDER BY se.sessionId, firstStarted IS NULL, firstStarted, se.position").use { cursor ->
                    var sessionId = -1L
                    var position = 0
                    while (cursor.moveToNext()) {
                        if (sessionId != cursor.getLong(1)) { sessionId = cursor.getLong(1); position = 0 }
                        positions += cursor.getLong(0) to position++
                    }
                }
                positions.forEach { (id, position) -> db.execSQL("UPDATE session_exercises SET position=? WHERE id=?", arrayOf<Any>(position,id)) }
                db.execSQL("DELETE FROM session_exercises WHERE NOT EXISTS (SELECT 1 FROM workout_sets WHERE sessionExerciseId=session_exercises.id)")
                // Keep old unfinished sessions recoverable in history, without competing timers.
                db.execSQL("UPDATE workout_sets SET pausedAt=COALESCE(pausedAt,startedAt) WHERE completed=0 AND sessionExerciseId IN (SELECT se.id FROM session_exercises se JOIN workout_sessions s ON se.sessionId=s.id WHERE s.status='ACTIVE' AND s.id!=(SELECT id FROM workout_sessions WHERE status='ACTIVE' ORDER BY startedAt DESC, id DESC LIMIT 1))")
                db.execSQL("UPDATE workout_sessions SET status='INTERRUPTED', endedAt=COALESCE(endedAt,?) WHERE status='ACTIVE' AND id!=(SELECT id FROM workout_sessions WHERE status='ACTIVE' ORDER BY startedAt DESC, id DESC LIMIT 1)", arrayOf<Any>(System.currentTimeMillis()))
            }
        }
        fun create(context: Context) = Room.databaseBuilder(context, LianJiDatabase::class.java, "lianji.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
    }
}
