package com.juzi.lianji.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY isFavorite DESC, nameEn") fun observeAll(): Flow<List<ExerciseEntity>>
    @Query("SELECT * FROM exercises WHERE id = :id") fun observe(id: String): Flow<ExerciseEntity?>
    @Query("SELECT * FROM exercises WHERE id = :id") suspend fun get(id: String): ExerciseEntity?
    @Query("SELECT COUNT(*) FROM exercises WHERE isCustom = 0") suspend fun builtinCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<ExerciseEntity>)
    @Query("INSERT OR REPLACE INTO exercises (id,nameEn,nameZh,datasetNameZh,bodyPart,equipment,target,muscleGroup,secondaryMuscles,instructionsZh,instructionsEn,imagePath,gifPath,attribution,trackingMode,isCustom,isFavorite) VALUES (:id,:nameEn,COALESCE((SELECT nameZh FROM exercises WHERE id=:id),:nameZh),:nameZh,:bodyPart,:equipment,:target,:muscleGroup,:secondaryMuscles,:instructionsZh,:instructionsEn,:imagePath,:gifPath,:attribution,:trackingMode,0,COALESCE((SELECT isFavorite FROM exercises WHERE id=:id),0))")
    suspend fun insertBuiltinPreservingUserState(id:String,nameEn:String,nameZh:String,bodyPart:String,equipment:String,target:String,muscleGroup:String,secondaryMuscles:String,instructionsZh:String,instructionsEn:String,imagePath:String?,gifPath:String?,attribution:String,trackingMode:String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: ExerciseEntity)
    @Query("UPDATE exercises SET isFavorite = NOT isFavorite WHERE id = :id") suspend fun toggleFavorite(id: String)
    @Query("DELETE FROM exercises WHERE id = :id AND isCustom = 1") suspend fun deleteCustom(id: String)
    @Query("SELECT * FROM exercises WHERE isCustom = 1") suspend fun customForBackup(): List<ExerciseEntity>
    @Query("UPDATE exercises SET nameZh = :nameZh WHERE id = :id") suspend fun updateNameZh(id:String,nameZh:String)
    @Query("UPDATE exercises SET nameZh = datasetNameZh WHERE id = :id AND isCustom = 0") suspend fun restoreDatasetNameZh(id:String)
    @Query("SELECT id,nameZh FROM exercises WHERE isCustom = 0 AND nameZh != datasetNameZh") suspend fun builtinNameOverrides():List<ExerciseNameOverride>
}

suspend fun ExerciseDao.insertBuiltinPreservingUserState(items:List<ExerciseEntity>) = items.forEach { insertBuiltinPreservingUserState(it.id,it.nameEn,it.nameZh,it.bodyPart,it.equipment,it.target,it.muscleGroup,it.secondaryMuscles,it.instructionsZh,it.instructionsEn,it.imagePath,it.gifPath,it.attribution,it.trackingMode) }

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
    @Query("SELECT * FROM scheduled_workouts ORDER BY scheduledDate") fun observeAll(): Flow<List<ScheduledWorkoutEntity>>
    @Query("SELECT * FROM scheduled_workouts") suspend fun getAll(): List<ScheduledWorkoutEntity>
    @Insert suspend fun insert(item: ScheduledWorkoutEntity): Long
    @Update suspend fun update(item: ScheduledWorkoutEntity)
    @Delete suspend fun delete(item: ScheduledWorkoutEntity)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM workout_sessions WHERE status='ACTIVE' ORDER BY startedAt DESC LIMIT 1") fun observeActive(): Flow<WorkoutSessionEntity?>
    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC") fun observeAll(): Flow<List<WorkoutSessionEntity>>
    @Query("SELECT * FROM workout_sessions") suspend fun getAll(): List<WorkoutSessionEntity>
    @Query("SELECT localDate, planNameSnapshot title, status FROM workout_sessions WHERE status != 'DISCARDED' UNION ALL SELECT scheduledDate localDate, planName title, status FROM scheduled_workouts ORDER BY localDate") fun observeDaySummaries(): Flow<List<DaySummary>>
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
        GROUP BY se.exerciseId
    """) fun observePersonalBests(): Flow<List<ExercisePersonalBest>>
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
    @Query("SELECT * FROM session_exercises") suspend fun getAllExercises(): List<SessionExerciseEntity>
    @Query("SELECT * FROM session_exercises WHERE sessionId=:sessionId ORDER BY position") suspend fun getSessionExercises(sessionId: Long): List<SessionExerciseEntity>
    @Query("SELECT COALESCE(MAX(position), -1) FROM session_exercises WHERE sessionId=:sessionId") suspend fun maxExercisePosition(sessionId: Long): Int
    @Insert suspend fun insertSets(items: List<WorkoutSetEntity>)
    @Insert suspend fun insertSet(item: WorkoutSetEntity): Long
    @Query("SELECT * FROM workout_sets") suspend fun getAllSets(): List<WorkoutSetEntity>
    @Query("SELECT * FROM workout_sets WHERE sessionExerciseId=:sessionExerciseId ORDER BY position") suspend fun getSetsForExercise(sessionExerciseId: Long): List<WorkoutSetEntity>
    @Query("SELECT ws.id setId,se.id sessionExerciseId,se.exerciseId,se.exerciseNameSnapshot exerciseName,e.imagePath,e.gifPath,se.position exercisePosition,se.restSeconds,ws.position setPosition,ws.weightKg,ws.reps,ws.completed,ws.startedAt,ws.completedAt,ws.durationSeconds,ws.pausedAt,ws.pausedDurationMillis,ws.restStartedAt,ws.restEndedAt,ws.restDurationSeconds,se.trackingMode,ws.distanceKm FROM session_exercises se JOIN workout_sets ws ON se.id=ws.sessionExerciseId LEFT JOIN exercises e ON e.id=se.exerciseId WHERE se.sessionId=:sessionId ORDER BY se.position,ws.position") fun observeRows(sessionId: Long): Flow<List<SessionSetRow>>
    @Query("SELECT * FROM workout_sets WHERE id=:setId") suspend fun getSet(setId: Long): WorkoutSetEntity?
    @Update suspend fun updateSet(set: WorkoutSetEntity)
    @Query("UPDATE workout_sets SET weightKg=:weight, reps=:reps WHERE id=:setId") suspend fun updateSetValues(setId:Long,weight:Double,reps:Int)
    @Query("UPDATE workout_sets SET durationSeconds=:durationSeconds, distanceKm=:distanceKm WHERE id=:setId") suspend fun updateCardioValues(setId:Long,durationSeconds:Int,distanceKm:Double)
    @Query("UPDATE session_exercises SET restSeconds=:seconds WHERE sessionId=:sessionId") suspend fun updateSessionRest(sessionId:Long,seconds:Int)
    @Query("SELECT ws.* FROM workout_sets ws JOIN session_exercises se ON se.id=ws.sessionExerciseId WHERE se.sessionId=:sessionId AND ws.restStartedAt IS NOT NULL AND ws.restEndedAt IS NULL ORDER BY ws.restStartedAt DESC LIMIT 1") suspend fun getOpenRest(sessionId: Long): WorkoutSetEntity?
    @Query("SELECT ws.* FROM workout_sets ws JOIN session_exercises se ON se.id=ws.sessionExerciseId WHERE se.sessionId=:sessionId AND ws.id!=:exceptSetId AND ws.startedAt IS NOT NULL AND ws.completed=0 AND ws.pausedAt IS NULL") suspend fun getRunningSetsExcept(sessionId:Long,exceptSetId:Long):List<WorkoutSetEntity>
    @Query("SELECT se.sessionId FROM session_exercises se JOIN workout_sets ws ON ws.sessionExerciseId=se.id WHERE ws.id=:setId") suspend fun sessionIdForSet(setId: Long): Long?
    @Query("SELECT COUNT(*) FROM workout_sets ws JOIN session_exercises se ON se.id=ws.sessionExerciseId WHERE se.sessionId=:sessionId AND ws.completed=0") suspend fun unfinishedCount(sessionId: Long): Int
    @Query("DELETE FROM workout_sets WHERE id=:setId") suspend fun deleteSet(setId: Long)
    @Query("DELETE FROM workout_sessions WHERE id=:id") suspend fun deleteSession(id: Long)
}
