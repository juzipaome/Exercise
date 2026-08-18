package com.juzi.lianji.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupEnvelope(
    val schemaVersion: Int = 5,
    val exportedAt: Long,
    val customExercises: List<ExerciseEntityDto>,
    val plans: List<PlanDto>,
    val schedules: List<ScheduleDto>,
    val sessions: List<SessionDto>,
    val exerciseNameOverrides: List<ExerciseNameOverrideDto> = emptyList(),
)
@Serializable data class ExerciseNameOverrideDto(val id:String,val nameZh:String)
@Serializable data class ExerciseEntityDto(val id:String,val nameEn:String,val nameZh:String,val bodyPart:String,val equipment:String,val target:String,val muscleGroup:String,val secondaryMuscles:String,val instructionsZh:String,val instructionsEn:String,val imagePath:String?,val gifPath:String?,val attribution:String,val isFavorite:Boolean,val trackingMode:String="")
@Serializable data class PlanDto(val id:Long,val name:String,val note:String,val createdAt:Long,val items:List<PlanItemDto>)
@Serializable data class PlanItemDto(val id:Long,val planId:Long,val exerciseId:String,val position:Int,val defaultSets:Int,val defaultReps:Int,val defaultWeightKg:Double,val restSeconds:Int)
@Serializable data class ScheduleDto(val id:Long,val planId:Long?,val planName:String,val scheduledDate:String,val status:String)
@Serializable data class SessionDto(val id:Long,val sourcePlanId:Long?,val planName:String,val startedAt:Long,val endedAt:Long?,val localDate:String,val status:String,val exercises:List<SessionExerciseDto>)
@Serializable data class SessionExerciseDto(val id:Long,val exerciseId:String,val name:String,val position:Int,val restSeconds:Int,val sets:List<SetDto>,val trackingMode:String=TrackingMode.STRENGTH)
@Serializable data class SetDto(val id:Long,val position:Int,val weightKg:Double,val reps:Int,val completed:Boolean,val startedAt:Long?=null,val completedAt:Long?=null,val durationSeconds:Int=0,val restStartedAt:Long?=null,val restEndedAt:Long?=null,val restDurationSeconds:Int=0,val distanceKm:Double=0.0,val pausedAt:Long?=null,val pausedDurationMillis:Long=0)

class BackupManager(private val context: Context, private val db: LianJiDatabase) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false }

    suspend fun exportTo(uri: Uri) {
        val planItems = db.planDao().getAllItems().groupBy { it.planId }
        val sessionExercises = db.sessionDao().getAllExercises()
        val sets = db.sessionDao().getAllSets().groupBy { it.sessionExerciseId }
        val envelope = BackupEnvelope(
            exportedAt = System.currentTimeMillis(),
            customExercises = db.exerciseDao().customForBackup().map { it.dto() },
            plans = db.planDao().getPlans().map { p -> PlanDto(p.id,p.name,p.note,p.createdAt,planItems[p.id].orEmpty().map { it.dto() }) },
            schedules = db.scheduleDao().getAll().map { ScheduleDto(it.id,it.planId,it.planName,it.scheduledDate,it.status) },
            sessions = db.sessionDao().getAll().map { s -> SessionDto(s.id,s.sourcePlanId,s.planNameSnapshot,s.startedAt,s.endedAt,s.localDate,s.status,sessionExercises.filter { it.sessionId==s.id }.map { e -> SessionExerciseDto(e.id,e.exerciseId,e.exerciseNameSnapshot,e.position,e.restSeconds,sets[e.id].orEmpty().map { SetDto(it.id,it.position,it.weightKg,it.reps,it.completed,it.startedAt,it.completedAt,it.durationSeconds,it.restStartedAt,it.restEndedAt,it.restDurationSeconds,it.distanceKm,it.pausedAt,it.pausedDurationMillis) },e.trackingMode) }) },
            exerciseNameOverrides = db.exerciseDao().builtinNameOverrides().map { ExerciseNameOverrideDto(it.id,it.nameZh) },
        )
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(json.encodeToString(envelope)) }
    }

    suspend fun importFrom(uri: Uri) {
        val raw = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val backup = json.decodeFromString<BackupEnvelope>(raw)
        require(backup.schemaVersion in 1..5) { "不支持的备份版本 ${backup.schemaVersion}" }
        db.withTransaction {
            backup.customExercises.forEach { db.exerciseDao().upsert(it.entity()) }
            backup.exerciseNameOverrides.forEach { db.exerciseDao().updateNameZh(it.id,it.nameZh) }
            val planIdMap = mutableMapOf<Long,Long>()
            backup.plans.forEach { p ->
                val newId = db.planDao().insertPlan(WorkoutPlanEntity(name=p.name,note=p.note,createdAt=p.createdAt)); planIdMap[p.id]=newId
                db.planDao().insertItems(p.items.map { it.entity(newId) })
            }
            backup.schedules.forEach { s -> db.scheduleDao().insert(ScheduledWorkoutEntity(planId=s.planId?.let(planIdMap::get),planName=s.planName,scheduledDate=s.scheduledDate,status=s.status)) }
            backup.sessions.forEach { s ->
                val sessionId=db.sessionDao().insertSession(WorkoutSessionEntity(sourcePlanId=s.sourcePlanId?.let(planIdMap::get),planNameSnapshot=s.planName,startedAt=s.startedAt,endedAt=s.endedAt,localDate=s.localDate,status=s.status))
                s.exercises.forEach { e -> val eid=db.sessionDao().insertSessionExercise(SessionExerciseEntity(sessionId=sessionId,exerciseId=e.exerciseId,exerciseNameSnapshot=e.name,position=e.position,restSeconds=e.restSeconds,trackingMode=e.trackingMode)); db.sessionDao().insertSets(e.sets.map { WorkoutSetEntity(sessionExerciseId=eid,position=it.position,weightKg=it.weightKg,reps=it.reps,completed=it.completed,startedAt=it.startedAt,completedAt=it.completedAt,durationSeconds=it.durationSeconds,pausedAt=it.pausedAt,pausedDurationMillis=it.pausedDurationMillis,restStartedAt=it.restStartedAt,restEndedAt=it.restEndedAt,restDurationSeconds=it.restDurationSeconds,distanceKm=it.distanceKm) }) }
            }
        }
    }
}

private fun ExerciseEntity.dto()=ExerciseEntityDto(id,nameEn,nameZh,bodyPart,equipment,target,muscleGroup,secondaryMuscles,instructionsZh,instructionsEn,imagePath,gifPath,attribution,isFavorite,trackingMode)
private fun ExerciseEntityDto.entity()=ExerciseEntity(id=id,nameEn=nameEn,nameZh=nameZh,bodyPart=bodyPart,equipment=equipment,target=target,muscleGroup=muscleGroup,secondaryMuscles=secondaryMuscles,instructionsZh=instructionsZh,instructionsEn=instructionsEn,imagePath=imagePath,gifPath=gifPath,attribution=attribution,datasetNameZh=nameZh,trackingMode=trackingMode.ifBlank{if(bodyPart=="cardio")TrackingMode.CARDIO else TrackingMode.STRENGTH},isCustom=true,isFavorite=isFavorite)
private fun PlanExerciseEntity.dto()=PlanItemDto(id,planId,exerciseId,position,defaultSets,defaultReps,defaultWeightKg,restSeconds)
private fun PlanItemDto.entity(newPlanId:Long)=PlanExerciseEntity(planId=newPlanId,exerciseId=exerciseId,position=position,defaultSets=defaultSets,defaultReps=defaultReps,defaultWeightKg=defaultWeightKg,restSeconds=restSeconds)
