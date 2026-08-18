package com.juzi.lianji.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

interface ExerciseRepository {
    val exercises: Flow<List<ExerciseEntity>>
    fun exercise(id: String): Flow<ExerciseEntity?>
    suspend fun toggleFavorite(id: String)
    suspend fun updateName(id:String,nameZh:String)
    suspend fun restoreDatasetName(id:String)
    suspend fun saveCustom(item: ExerciseEntity)
    suspend fun deleteCustom(id: String)
}

interface PlanRepository {
    val plans: Flow<List<PlanSummary>>
    suspend fun plan(id: Long): Pair<WorkoutPlanEntity, List<PlanExerciseEntity>>?
    suspend fun save(plan: WorkoutPlanEntity, items: List<PlanExerciseEntity>): Long
    suspend fun duplicate(id: Long): Long
    suspend fun delete(id: Long)
}

interface ScheduleRepository {
    val schedules: Flow<List<ScheduledWorkoutEntity>>
    suspend fun delete(item: ScheduledWorkoutEntity)
}

interface WorkoutRepository {
    val activeSession: Flow<WorkoutSessionEntity?>
    val sessions: Flow<List<WorkoutSessionEntity>>
    val days: Flow<List<DaySummary>>
    val personalBests: Flow<List<ExercisePersonalBest>>
    fun session(sessionId: Long): Flow<WorkoutSessionEntity?>
    fun rows(sessionId: Long): Flow<List<SessionSetRow>>
    fun sessionExercises(sessionId: Long): Flow<List<SessionExerciseEntity>>
    fun monthlyExerciseStats(monthPrefix: String): Flow<List<MonthlyExerciseStat>>
    suspend fun start(planId: Long): Long
    suspend fun beginSet(id: Long)
    suspend fun pauseSet(id: Long)
    suspend fun completeSet(id: Long, weight: Double, reps: Int)
    suspend fun updateSetValues(id: Long, weight: Double, reps: Int)
    suspend fun completeCardio(id: Long, distanceKm: Double)
    suspend fun updateCardioValues(id: Long, durationSeconds: Int, distanceKm: Double)
    suspend fun setSessionRest(sessionId: Long, seconds: Int)
    suspend fun addSet(sessionExerciseId: Long, position: Int, weight: Double, reps: Int)
    suspend fun deleteSet(id: Long)
    suspend fun addExercise(sessionId: Long, exerciseId: String, restSeconds: Int)
    suspend fun finish(id: Long): Boolean
    suspend fun saveSessionPlan(id: Long, overwrite: Boolean): Long
    suspend fun addPastWorkout(planId: Long, exerciseIds: List<String>, date: LocalDate, startMinute: Int, endMinute: Int): Long
    suspend fun discard(id: Long)
    suspend fun deleteHistory(id: Long)
}

fun trainingVolume(rows: List<SessionSetRow>): Double = rows.filter { it.completed }.sumOf { it.weightKg * it.reps }
fun initialRecordCount(exercise: ExerciseEntity, strengthSets: Int): Int = if (exercise.isCardio) 1 else strengthSets
fun initialReps(exercise: ExerciseEntity, strengthReps: Int): Int = if (exercise.isCardio) 0 else strengthReps
fun hasPlanStructureChanges(
    planItems: List<PlanExerciseEntity>,
    sessionExercises: List<SessionExerciseEntity>,
    recordCounts: Map<Long, Int>,
): Boolean {
    val planByExercise = planItems.associateBy { it.exerciseId }
    if (sessionExercises.any { it.exerciseId !in planByExercise }) return true
    return sessionExercises.any { exercise ->
        val planItem = planByExercise.getValue(exercise.exerciseId)
        val expected = if (exercise.trackingMode == TrackingMode.CARDIO) 1 else planItem.defaultSets
        (recordCounts[exercise.id] ?: 0) != expected
    }
}

class LianJiRepository(private val db: LianJiDatabase) : ExerciseRepository, PlanRepository, ScheduleRepository, WorkoutRepository {
    override val exercises = db.exerciseDao().observeAll()
    override fun exercise(id: String) = db.exerciseDao().observe(id)
    override suspend fun toggleFavorite(id: String) = db.exerciseDao().toggleFavorite(id)
    override suspend fun updateName(id:String,nameZh:String) { db.exerciseDao().updateNameZh(id,nameZh.trim()) }
    override suspend fun restoreDatasetName(id:String) = db.exerciseDao().restoreDatasetNameZh(id)
    override suspend fun saveCustom(item: ExerciseEntity) = db.exerciseDao().upsert(item.copy(id = item.id.ifBlank { "custom-${UUID.randomUUID()}" }, isCustom = true))
    override suspend fun deleteCustom(id: String) = db.exerciseDao().deleteCustom(id)

    override val plans = db.planDao().observeSummaries()
    override suspend fun plan(id: Long) = db.planDao().getPlan(id)?.let { it to db.planDao().getItems(id) }
    override suspend fun save(plan: WorkoutPlanEntity, items: List<PlanExerciseEntity>): Long = db.withTransaction {
        val id = if (plan.id == 0L) db.planDao().insertPlan(plan) else { db.planDao().updatePlan(plan); plan.id }
        db.planDao().clearItems(id)
        db.planDao().insertItems(items.mapIndexed { index, item -> item.copy(id = 0, planId = id, position = index) })
        id
    }
    override suspend fun duplicate(id: Long): Long {
        val (plan, items) = plan(id) ?: error("计划不存在")
        return save(plan.copy(id = 0, name = "${plan.name} 副本", createdAt = System.currentTimeMillis()), items)
    }
    override suspend fun delete(id: Long) = db.planDao().delete(id)

    override val schedules = db.scheduleDao().observeAll()
    override suspend fun delete(item: ScheduledWorkoutEntity) = db.scheduleDao().delete(item)

    override val activeSession = db.sessionDao().observeActive()
    override val sessions = db.sessionDao().observeAll()
    override val days = db.sessionDao().observeDaySummaries()
    override val personalBests = db.sessionDao().observePersonalBests()
    override fun session(sessionId: Long) = db.sessionDao().observeSession(sessionId)
    override fun rows(sessionId: Long) = db.sessionDao().observeRows(sessionId)
    override fun sessionExercises(sessionId: Long) = db.sessionDao().observeSessionExercises(sessionId)
    override fun monthlyExerciseStats(monthPrefix: String) = db.sessionDao().observeMonthlyExerciseStats(monthPrefix)
    override suspend fun start(planId: Long): Long = db.withTransaction {
        val plan = db.planDao().getPlan(planId) ?: error("计划不存在")
        val items = db.planDao().getItems(planId)
        require(items.isNotEmpty()) { "计划里还没有动作" }
        val sessionId = db.sessionDao().insertSession(WorkoutSessionEntity(sourcePlanId = planId, planNameSnapshot = plan.name, startedAt = System.currentTimeMillis(), localDate = LocalDate.now().toString()))
        items.forEach { item ->
            val exercise = db.exerciseDao().get(item.exerciseId) ?: return@forEach
            val sessionExerciseId = db.sessionDao().insertSessionExercise(SessionExerciseEntity(sessionId = sessionId, exerciseId = exercise.id, exerciseNameSnapshot = exercise.nameZh, position = item.position, restSeconds = item.restSeconds, trackingMode = exercise.trackingMode))
            val recordCount = initialRecordCount(exercise,item.defaultSets)
            db.sessionDao().insertSets(List(recordCount) { index -> WorkoutSetEntity(sessionExerciseId = sessionExerciseId, position = index, weightKg = if (exercise.isCardio) 0.0 else item.defaultWeightKg, reps = initialReps(exercise,item.defaultReps)) })
        }
        sessionId
    }
    override suspend fun beginSet(id: Long) { db.withTransaction {
        val now = System.currentTimeMillis()
        val sessionId = db.sessionDao().sessionIdForSet(id) ?: return@withTransaction
        db.sessionDao().getOpenRest(sessionId)?.let { previous ->
            db.sessionDao().updateSet(previous.copy(restEndedAt = now, restDurationSeconds = ((now - (previous.restStartedAt ?: now)) / 1000).toInt()))
        }
        db.sessionDao().getRunningSetsExcept(sessionId,id).forEach { running ->
            db.sessionDao().updateSet(running.copy(pausedAt=now))
        }
        db.sessionDao().getSet(id)?.takeIf { !it.completed }?.let { set ->
            val resumedPause = set.pausedAt?.let { (now - it).coerceAtLeast(0) } ?: 0
            db.sessionDao().updateSet(set.copy(startedAt=set.startedAt ?: now,pausedAt=null,pausedDurationMillis=set.pausedDurationMillis+resumedPause))
        }
    } }
    override suspend fun pauseSet(id: Long) {
        val now = System.currentTimeMillis()
        db.sessionDao().getSet(id)?.takeIf { it.startedAt != null && !it.completed && it.pausedAt == null }
            ?.let { db.sessionDao().updateSet(it.copy(pausedAt=now)) }
    }
    override suspend fun completeSet(id: Long, weight: Double, reps: Int) { db.withTransaction {
        val now = System.currentTimeMillis()
        val sessionId = db.sessionDao().sessionIdForSet(id)
        db.sessionDao().getSet(id)?.let { set ->
            val started = set.startedAt ?: now
            val pausedMillis=set.pausedDurationMillis+(set.pausedAt?.let{(now-it).coerceAtLeast(0)}?:0)
            db.sessionDao().updateSet(set.copy(weightKg=weight,reps=reps,completed=true,startedAt=started,completedAt=now,durationSeconds=activeDurationSeconds(started,now,null,pausedMillis).toInt(),pausedAt=null,pausedDurationMillis=pausedMillis))
            if (sessionId != null && db.sessionDao().unfinishedCount(sessionId) > 0) {
                db.sessionDao().updateSet(db.sessionDao().getSet(id)!!.copy(restStartedAt=now))
            }
        }
    } }
    override suspend fun updateSetValues(id: Long, weight: Double, reps: Int) { db.sessionDao().updateSetValues(id,weight,reps) }
    override suspend fun completeCardio(id: Long, distanceKm: Double) { db.withTransaction {
        val now = System.currentTimeMillis()
        db.sessionDao().getSet(id)?.let { record ->
            val started = record.startedAt ?: now
            val pausedMillis=record.pausedDurationMillis+(record.pausedAt?.let{(now-it).coerceAtLeast(0)}?:0)
            db.sessionDao().updateSet(record.copy(weightKg=0.0,reps=0,completed=true,startedAt=started,completedAt=now,durationSeconds=activeDurationSeconds(started,now,null,pausedMillis).toInt(),pausedAt=null,pausedDurationMillis=pausedMillis,distanceKm=distanceKm.coerceAtLeast(0.0)))
        }
    } }
    override suspend fun updateCardioValues(id: Long, durationSeconds: Int, distanceKm: Double) { db.sessionDao().updateCardioValues(id,durationSeconds.coerceAtLeast(0),distanceKm.coerceAtLeast(0.0)) }
    override suspend fun setSessionRest(sessionId: Long, seconds: Int) { db.sessionDao().updateSessionRest(sessionId,seconds) }
    override suspend fun addSet(sessionExerciseId: Long, position: Int, weight: Double, reps: Int) { db.sessionDao().insertSet(WorkoutSetEntity(sessionExerciseId=sessionExerciseId,position=position,weightKg=weight,reps=reps)) }
    override suspend fun addExercise(sessionId: Long, exerciseId: String, restSeconds: Int) { db.withTransaction {
        if (db.sessionDao().getSessionExercises(sessionId).any { it.exerciseId == exerciseId }) return@withTransaction
        val exercise = db.exerciseDao().get(exerciseId) ?: return@withTransaction
        val position = db.sessionDao().maxExercisePosition(sessionId) + 1
        val id = db.sessionDao().insertSessionExercise(SessionExerciseEntity(sessionId=sessionId,exerciseId=exercise.id,exerciseNameSnapshot=exercise.nameZh,position=position,restSeconds=restSeconds,trackingMode=exercise.trackingMode))
        val recordCount = initialRecordCount(exercise,3)
        db.sessionDao().insertSets(List(recordCount) { index -> WorkoutSetEntity(sessionExerciseId=id,position=index,weightKg=0.0,reps=initialReps(exercise,10)) })
    } }
    override suspend fun deleteSet(id: Long) = db.sessionDao().deleteSet(id)
    override suspend fun finish(id: Long): Boolean = db.withTransaction {
        val now = System.currentTimeMillis()
        db.sessionDao().getOpenRest(id)?.let { rest -> db.sessionDao().updateSet(rest.copy(restEndedAt=now,restDurationSeconds=((now-(rest.restStartedAt?:now))/1000).toInt())) }
        val session = db.sessionDao().getSession(id) ?: return@withTransaction false
        db.sessionDao().updateSession(session.copy(endedAt = now, status = "COMPLETED"))
        val sourcePlanId = session.sourcePlanId ?: return@withTransaction false
        val planItems = db.planDao().getItems(sourcePlanId)
        val exercises = db.sessionDao().getSessionExercises(id)
        val recordCounts = exercises.associate { it.id to db.sessionDao().getSetsForExercise(it.id).size }
        hasPlanStructureChanges(planItems,exercises,recordCounts)
    }
    override suspend fun saveSessionPlan(id: Long, overwrite: Boolean): Long = db.withTransaction {
        val session = db.sessionDao().getSession(id) ?: error("训练不存在")
        val source = session.sourcePlanId?.let { db.planDao().getPlan(it) }
        val plan = if (overwrite && source != null) source else WorkoutPlanEntity(name="${session.planNameSnapshot}（训练调整）")
        val items = db.sessionDao().getSessionExercises(id).map { exercise ->
            val sets = db.sessionDao().getSetsForExercise(exercise.id)
            val sample = sets.firstOrNull()
            PlanExerciseEntity(planId=plan.id,exerciseId=exercise.exerciseId,position=exercise.position,defaultSets=sets.size.coerceAtLeast(1),defaultReps=sample?.reps ?: 10,defaultWeightKg=sample?.weightKg ?: 0.0,restSeconds=exercise.restSeconds)
        }
        save(plan, items)
    }
    override suspend fun addPastWorkout(planId: Long, exerciseIds: List<String>, date: LocalDate, startMinute: Int, endMinute: Int): Long = db.withTransaction {
        val plan = db.planDao().getPlan(planId) ?: error("计划不存在")
        require(exerciseIds.isNotEmpty()) { "至少选择一个动作" }
        require(startMinute in 0..1439 && endMinute in 1..1440 && endMinute > startMinute) { "结束时间必须晚于开始时间" }
        val startedAt = date.atStartOfDay().plusMinutes(startMinute.toLong()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endedAt = date.atStartOfDay().plusMinutes(endMinute.toLong()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sessionId = db.sessionDao().insertSession(WorkoutSessionEntity(sourcePlanId=planId,planNameSnapshot=plan.name,startedAt=startedAt,endedAt=endedAt,localDate=date.toString(),status="COMPLETED"))
        val selected = exerciseIds.toSet()
        db.planDao().getItems(planId).filter { it.exerciseId in selected }.forEachIndexed { position, item ->
            val exercise = db.exerciseDao().get(item.exerciseId) ?: return@forEachIndexed
            val exerciseId = db.sessionDao().insertSessionExercise(SessionExerciseEntity(sessionId=sessionId,exerciseId=exercise.id,exerciseNameSnapshot=exercise.nameZh,position=position,restSeconds=item.restSeconds,trackingMode=exercise.trackingMode))
            val recordCount = initialRecordCount(exercise,item.defaultSets)
            db.sessionDao().insertSets(List(recordCount) { index ->
                WorkoutSetEntity(sessionExerciseId=exerciseId,position=index,weightKg=if(exercise.isCardio)0.0 else item.defaultWeightKg,reps=initialReps(exercise,item.defaultReps),completed=true)
            })
        }
        sessionId
    }
    override suspend fun discard(id: Long) = db.sessionDao().deleteSession(id)
    override suspend fun deleteHistory(id: Long) = db.sessionDao().deleteSession(id)
}
