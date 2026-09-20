package com.juzi.lianji.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY isFavorite DESC, nameEn") fun observeAll(): Flow<List<ExerciseEntity>>
    @Query("SELECT * FROM exercises WHERE id = :id") fun observe(id: String): Flow<ExerciseEntity?>
    @Query("SELECT * FROM exercises WHERE id = :id") suspend fun get(id: String): ExerciseEntity?
    @Query("SELECT COUNT(*) FROM exercises WHERE isCustom = 0") suspend fun builtinCount(): Int
    @Upsert suspend fun upsert(item: ExerciseEntity)
    @Upsert suspend fun upsertAll(items: List<ExerciseEntity>)
    @Query("SELECT * FROM exercises WHERE isCustom = 0") suspend fun getBuiltin(): List<ExerciseEntity>
    @Query("SELECT id FROM exercises WHERE isFavorite = 1 AND isCustom = 0") suspend fun builtinFavorites(): List<String>
    @Query("UPDATE exercises SET isFavorite = 0 WHERE isCustom = 0") suspend fun clearBuiltinFavorites()
    @Query("UPDATE exercises SET isFavorite = 1 WHERE isCustom = 0 AND id IN (:ids)") suspend fun restoreFavorites(ids: List<String>)
    @Query("UPDATE exercises SET isFavorite = NOT isFavorite WHERE id = :id") suspend fun toggleFavorite(id: String)
    @Query("DELETE FROM exercises WHERE id = :id AND isCustom = 1") suspend fun deleteCustom(id: String)
    @Query("SELECT * FROM exercises WHERE isCustom = 1") suspend fun customForBackup(): List<ExerciseEntity>
    @Query("UPDATE exercises SET nameZh = :nameZh WHERE id = :id") suspend fun updateNameZh(id:String,nameZh:String)
    @Query("UPDATE exercises SET nameZh = datasetNameZh WHERE id = :id AND isCustom = 0") suspend fun restoreDatasetNameZh(id:String)
    @Query("SELECT id,nameZh FROM exercises WHERE isCustom = 0 AND nameZh != datasetNameZh") suspend fun builtinNameOverrides():List<ExerciseNameOverride>
}


@Dao
interface PlanDao {
    @Query("SELECT p.id,p.name,p.note,COUNT(pe.id) exerciseCount FROM workout_plans p LEFT JOIN plan_exercises pe ON p.id=pe.planId GROUP BY p.id ORDER BY p.createdAt DESC")
    fun observeSummaries(): Flow<List<PlanSummary>>
    @Query("SELECT * FROM workout_plans WHERE id=:id") suspend fun getPlan(id: Long): WorkoutPlanEntity?
    @Query("SELECT * FROM workout_plans") suspend fun getPlans(): List<WorkoutPlanEntity>
    @Query("SELECT * FROM plan_exercises WHERE planId=:planId ORDER BY position") suspend fun getItems(planId: Long): List<PlanExerciseEntity>
    @Query("SELECT * FROM plan_exercises") suspend fun getAllItems(): List<PlanExerciseEntity>
    @Insert suspend fun insertPlan(plan: WorkoutPlanEntity): Long
    @Update suspend fun updatePlan(plan: WorkoutPlanEntity)
    @Insert suspend fun insertItems(items: List<PlanExerciseEntity>)
    @Query("DELETE FROM plan_exercises WHERE planId=:planId") suspend fun clearItems(planId: Long)
    @Query("DELETE FROM workout_plans WHERE id=:id") suspend fun delete(id: Long)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM scheduled_workouts WHERE scheduledDate >= :fromDate AND scheduledDate < :untilDate ORDER BY scheduledDate") fun observeRange(fromDate: String, untilDate: String): Flow<List<ScheduledWorkoutEntity>>
    @Query("SELECT * FROM scheduled_workouts") suspend fun getAll(): List<ScheduledWorkoutEntity>
    @Insert suspend fun insert(item: ScheduledWorkoutEntity): Long
    @Update suspend fun update(item: ScheduledWorkoutEntity)
    @Delete suspend fun delete(item: ScheduledWorkoutEntity)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM workout_sessions WHERE status='ACTIVE' ORDER BY startedAt DESC, id DESC LIMIT 1") suspend fun getActive(): WorkoutSessionEntity?
    @Query("SELECT COUNT(*) FROM imported_backups WHERE fingerprint=:fingerprint") suspend fun importedCount(fingerprint: String): Int
    @Insert suspend fun recordImport(item: ImportedBackupEntity)
    @Query("SELECT * FROM workout_sessions WHERE status='ACTIVE' ORDER BY startedAt DESC, id DESC LIMIT 1") fun observeActive(): Flow<WorkoutSessionEntity?>
    @Query("SELECT * FROM workout_sessions WHERE localDate >= :fromDate AND localDate < :untilDate AND status != 'DISCARDED' ORDER BY startedAt DESC") fun observeRange(fromDate: String, untilDate: String): Flow<List<WorkoutSessionEntity>>
    @Query("SELECT * FROM workout_sessions") suspend fun getAll(): List<WorkoutSessionEntity>
    @Query("SELECT localDate, planNameSnapshot title, status FROM workout_sessions WHERE status != 'DISCARDED' AND localDate >= :fromDate AND localDate < :untilDate UNION ALL SELECT scheduledDate localDate, planName title, status FROM scheduled_workouts WHERE scheduledDate >= :fromDate AND scheduledDate < :untilDate ORDER BY localDate") fun observeDaySummaries(fromDate: String, untilDate: String): Flow<List<DaySummary>>
    @Query("SELECT * FROM workout_sessions WHERE id=:id") suspend fun getSession(id: Long): WorkoutSessionEntity?
    @Query("SELECT * FROM workout_sessions WHERE id=:id") fun observeSession(id: Long): Flow<WorkoutSessionEntity?>
    @Query("""
        SELECT se.exerciseId,
               COALESCE(MAX(CASE WHEN se.trackingMode = 'STRENGTH' THEN ws.weightKg END), 0.0) maxWeightKg,
               COALESCE(MAX(CASE WHEN se.trackingMode = 'STRENGTH' THEN ws.reps END), 0) maxReps,
               COALESCE(MAX(CASE WHEN se.trackingMode = 'CARDIO' THEN ws.distanceKm END), 0.0) maxDistanceKm,
               COALESCE(MAX(CASE WHEN se.trackingMode = 'CARDIO' THEN ws.durationSeconds END), 0) maxDurationSeconds
        FROM session_exercises se
        JOIN workout_sets ws ON ws.sessionExerciseId = se.id AND ws.completed = 1
        WHERE se.exerciseId = :exerciseId
        GROUP BY se.exerciseId
    """) fun observePersonalBest(exerciseId: String): Flow<ExercisePersonalBest?>
    @Query("""
        SELECT se.exerciseNameSnapshot exerciseName,
               COALESCE(e.bodyPart, 'other') bodyPart,
               COUNT(ws.id) completedSets,
               COALESCE(SUM(ws.durationSeconds), 0) activeSeconds,
               COALESCE(SUM(ws.weightKg * ws.reps), 0.0) volume
               ,se.trackingMode trackingMode
        FROM workout_sessions s
        JOIN session_exercises se ON se.sessionId = s.id
        LEFT JOIN workout_sets ws ON ws.sessionExerciseId = se.id AND ws.completed = 1
        LEFT JOIN exercises e ON e.id = se.exerciseId
        WHERE s.status = 'COMPLETED' AND s.localDate LIKE :monthPrefix
        GROUP BY se.exerciseId, se.exerciseNameSnapshot, COALESCE(e.bodyPart, 'other'), se.trackingMode
        ORDER BY completedSets DESC, exerciseName
    """) fun observeMonthlyExerciseStats(monthPrefix: String): Flow<List<MonthlyExerciseStat>>
    @Query("SELECT * FROM session_exercises WHERE sessionId=:sessionId ORDER BY position") fun observeSessionExercises(sessionId: Long): Flow<List<SessionExerciseEntity>>
    @Insert suspend fun insertSession(item: WorkoutSessionEntity): Long
    @Update suspend fun updateSession(item: WorkoutSessionEntity)
    @Insert suspend fun insertSessionExercise(item: SessionExerciseEntity): Long
    @Update suspend fun updateSessionExercise(item: SessionExerciseEntity)
    @Query("SELECT * FROM session_exercises") suspend fun getAllExercises(): List<SessionExerciseEntity>
    @Query("SELECT * FROM session_exercises WHERE sessionId=:sessionId ORDER BY position") suspend fun getSessionExercises(sessionId: Long): List<SessionExerciseEntity>
    @Query("SELECT DISTINCT se.id FROM session_exercises se JOIN workout_sets ws ON ws.sessionExerciseId=se.id WHERE se.sessionId=:sessionId AND (ws.startedAt IS NOT NULL OR ws.completed=1)") suspend fun startedExerciseIds(sessionId: Long): List<Long>
    @Query("DELETE FROM session_exercises WHERE id=:id") suspend fun deleteSessionExercise(id: Long)
    @Query("SELECT COALESCE(MAX(position), -1) FROM session_exercises WHERE sessionId=:sessionId") suspend fun maxExercisePosition(sessionId: Long): Int
    @Insert suspend fun insertSets(items: List<WorkoutSetEntity>)
    @Insert suspend fun insertSet(item: WorkoutSetEntity): Long
    @Query("SELECT * FROM workout_sets") suspend fun getAllSets(): List<WorkoutSetEntity>
    @Query("SELECT * FROM workout_sets WHERE sessionExerciseId=:sessionExerciseId ORDER BY position") suspend fun getSetsForExercise(sessionExerciseId: Long): List<WorkoutSetEntity>
    @Query("SELECT ws.id setId,se.id sessionExerciseId,se.exerciseId,se.exerciseNameSnapshot exerciseName,e.imagePath,e.gifPath,se.position exercisePosition,se.restSeconds,ws.position setPosition,ws.weightKg,ws.reps,ws.completed,ws.startedAt,ws.completedAt,ws.durationSeconds,ws.pausedAt,ws.pausedDurationMillis,ws.restStartedAt,ws.restEndedAt,ws.restDurationSeconds,se.trackingMode,ws.distanceKm,ws.restNotifiedAt FROM session_exercises se JOIN workout_sets ws ON se.id=ws.sessionExerciseId LEFT JOIN exercises e ON e.id=se.exerciseId WHERE se.sessionId=:sessionId ORDER BY se.position,ws.position") fun observeRows(sessionId: Long): Flow<List<SessionSetRow>>
    @Query("SELECT trackingMode FROM session_exercises WHERE id=:id") suspend fun trackingMode(id: Long): String?
    @Query("UPDATE workout_sets SET distanceKm=:distance WHERE id=:id AND completed=0") suspend fun updateCardioDraft(id: Long, distance: Double)
    @Query("SELECT * FROM workout_sets WHERE id=:setId") suspend fun getSet(setId: Long): WorkoutSetEntity?
    @Update suspend fun updateSet(set: WorkoutSetEntity)
    @Query("UPDATE workout_sets SET weightKg=:weight, reps=:reps WHERE id=:setId") suspend fun updateSetValues(setId:Long,weight:Double,reps:Int)
    @Query("UPDATE workout_sets SET durationSeconds=:durationSeconds, distanceKm=:distanceKm WHERE id=:setId") suspend fun updateCardioValues(setId:Long,durationSeconds:Int,distanceKm:Double)
    @Query("UPDATE session_exercises SET restSeconds=:seconds WHERE sessionId=:sessionId") suspend fun updateSessionRest(sessionId:Long,seconds:Int)
    @Query("SELECT ws.* FROM workout_sets ws JOIN session_exercises se ON se.id=ws.sessionExerciseId WHERE se.sessionId=:sessionId AND ws.restStartedAt IS NOT NULL AND ws.restEndedAt IS NULL ORDER BY ws.restStartedAt DESC LIMIT 1") suspend fun getOpenRest(sessionId: Long): WorkoutSetEntity?
    @Query("UPDATE workout_sets SET restEndedAt=:now, restDurationSeconds=MAX(0, (:now-restStartedAt)/1000) WHERE sessionExerciseId IN (SELECT id FROM session_exercises WHERE sessionId=:sessionId) AND restStartedAt IS NOT NULL AND restEndedAt IS NULL") suspend fun closeOpenRests(sessionId: Long, now: Long)
    @Query("SELECT ws.* FROM workout_sets ws JOIN session_exercises se ON se.id=ws.sessionExerciseId WHERE se.sessionId=:sessionId AND ws.id!=:exceptSetId AND ws.startedAt IS NOT NULL AND ws.completed=0 AND ws.pausedAt IS NULL") suspend fun getRunningSetsExcept(sessionId:Long,exceptSetId:Long):List<WorkoutSetEntity>
    @Query("SELECT se.sessionId FROM session_exercises se JOIN workout_sets ws ON ws.sessionExerciseId=se.id WHERE ws.id=:setId") suspend fun sessionIdForSet(setId: Long): Long?
    @Query("SELECT COUNT(*) FROM workout_sets ws JOIN session_exercises se ON se.id=ws.sessionExerciseId WHERE se.sessionId=:sessionId AND ws.completed=0") suspend fun unfinishedCount(sessionId: Long): Int
    @Query("DELETE FROM workout_sets WHERE id=:setId") suspend fun deleteSet(setId: Long)
    @Query("DELETE FROM workout_sessions WHERE id=:id") suspend fun deleteSession(id: Long)
}
