package com.juzi.lianji.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

@Serializable
data class BackupEnvelope(
    val schemaVersion: Int = 5,
    val exportedAt: Long,
    val customExercises: List<ExerciseEntityDto>,
    val plans: List<PlanDto>,
    val schedules: List<ScheduleDto>,
    val sessions: List<SessionDto>,
    val exerciseNameOverrides: List<ExerciseNameOverrideDto> = emptyList(),
    val builtinFavoriteIds: List<String>? = null,
    val settings: AppSettings? = null,
)
@Serializable data class ExerciseNameOverrideDto(val id:String,val nameZh:String)
@Serializable data class ExerciseEntityDto(val id:String,val nameEn:String,val nameZh:String,val bodyPart:String,val equipment:String,val target:String,val muscleGroup:String,val secondaryMuscles:String,val instructionsZh:String,val instructionsEn:String,val imagePath:String?,val gifPath:String?,val attribution:String,val isFavorite:Boolean,val trackingMode:String="")
@Serializable data class PlanDto(val id:Long,val name:String,val note:String,val createdAt:Long,val items:List<PlanItemDto>)
@Serializable data class PlanItemDto(val id:Long,val planId:Long,val exerciseId:String,val position:Int,val defaultSets:Int,val defaultReps:Int,val defaultWeightKg:Double,val restSeconds:Int)
@Serializable data class ScheduleDto(val id:Long,val planId:Long?,val planName:String,val scheduledDate:String,val status:String)
@Serializable data class SessionDto(val id:Long,val sourcePlanId:Long?,val planName:String,val startedAt:Long,val endedAt:Long?,val localDate:String,val status:String,val exercises:List<SessionExerciseDto>)
@Serializable data class SessionExerciseDto(val id:Long,val exerciseId:String,val name:String,val position:Int,val restSeconds:Int,val sets:List<SetDto>,val trackingMode:String=TrackingMode.STRENGTH)
@Serializable data class SetDto(val id:Long,val position:Int,val weightKg:Double,val reps:Int,val completed:Boolean,val startedAt:Long?=null,val completedAt:Long?=null,val durationSeconds:Int=0,val restStartedAt:Long?=null,val restEndedAt:Long?=null,val restDurationSeconds:Int=0,val distanceKm:Double=0.0,val pausedAt:Long?=null,val pausedDurationMillis:Long=0)

class BackupManager(private val context: Context, private val db: LianJiDatabase, private val settingsStore: SettingsStore) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false; encodeDefaults = true }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val settings = settingsStore.settings.first()
        val envelope = db.withTransaction {
        val planItems = db.planDao().getAllItems().groupBy { it.planId }
        val sessionExercises = db.sessionDao().getAllExercises().groupBy { it.sessionId }
        val sets = db.sessionDao().getAllSets().groupBy { it.sessionExerciseId }
        BackupEnvelope(
            schemaVersion = 6,
            exportedAt = System.currentTimeMillis(),
            customExercises = db.exerciseDao().customForBackup().map { it.dto() },
            plans = db.planDao().getPlans().map { p -> PlanDto(p.id,p.name,p.note,p.createdAt,planItems[p.id].orEmpty().map { it.dto() }) },
            schedules = db.scheduleDao().getAll().map { ScheduleDto(it.id,it.planId,it.planName,it.scheduledDate,it.status) },
            sessions = db.sessionDao().getAll().map { s -> SessionDto(s.id,s.sourcePlanId,s.planNameSnapshot,s.startedAt,s.endedAt,s.localDate,s.status,sessionExercises[s.id].orEmpty().map { e -> SessionExerciseDto(e.id,e.exerciseId,e.exerciseNameSnapshot,e.position,e.restSeconds,sets[e.id].orEmpty().map { SetDto(it.id,it.position,it.weightKg,it.reps,it.completed,it.startedAt,it.completedAt,it.durationSeconds,it.restStartedAt,it.restEndedAt,it.restDurationSeconds,it.distanceKm,it.pausedAt,it.pausedDurationMillis) },e.trackingMode) }) },
            exerciseNameOverrides = db.exerciseDao().builtinNameOverrides().map { ExerciseNameOverrideDto(it.id,it.nameZh) },
            builtinFavoriteIds = db.exerciseDao().builtinFavorites(),
            settings = settings,
        )
        }
        val encoded = json.encodeToString(envelope)
        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入备份文件" }.bufferedWriter().use { it.write(encoded) }
    }

    suspend fun importFrom(uri: Uri): String = withContext(Dispatchers.IO) {
        val raw = checkNotNull(context.contentResolver.openInputStream(uri)) { "无法读取备份文件" }.use {
            val bytes=it.readNBytes(32*1024*1024+1)
            require(bytes.size<=32*1024*1024) { "备份文件超过 32 MB，无法恢复" }
            bytes.toString(Charsets.UTF_8)
        }
        val backup = json.decodeFromString<BackupEnvelope>(raw)
        validateBackup(backup)
        ExerciseImporter.seedIfNeeded(context,db)
        val fingerprint = backupFingerprint(backup)
        val imported = db.withTransaction {
            if (db.sessionDao().importedCount(fingerprint)>0) return@withTransaction false
            val builtinIds=db.exerciseDao().getBuiltin().mapTo(mutableSetOf()){it.id}
            require(backup.customExercises.none{it.id in builtinIds}) { "自定义动作不能覆盖内置动作" }
            require(backup.plans.flatMap{it.items}.all{it.exerciseId in builtinIds || backup.customExercises.any{e->e.id==it.exerciseId}}) { "备份计划引用了不存在的动作" }
            db.exerciseDao().upsertAll(backup.customExercises.map { it.entity() })
            backup.exerciseNameOverrides.forEach { db.exerciseDao().updateNameZh(it.id,it.nameZh) }
            backup.builtinFavoriteIds?.let { ids -> db.exerciseDao().clearBuiltinFavorites(); db.exerciseDao().restoreFavorites(ids) }
            val planIdMap = mutableMapOf<Long,Long>()
            backup.plans.forEach { p ->
                val newId = db.planDao().insertPlan(WorkoutPlanEntity(name=p.name,note=p.note,createdAt=p.createdAt)); planIdMap[p.id]=newId
                db.planDao().insertItems(p.items.map { it.entity(newId) })
            }
            backup.schedules.forEach { s -> db.scheduleDao().insert(ScheduledWorkoutEntity(planId=s.planId?.let(planIdMap::get),planName=s.planName,scheduledDate=s.scheduledDate,status=s.status)) }
            backup.sessions.forEach { s ->
                val interrupted=s.status=="ACTIVE" || s.status=="INTERRUPTED"
                val stoppedAt=s.endedAt ?: backup.exportedAt.coerceAtLeast(s.startedAt)
                val sessionId=db.sessionDao().insertSession(WorkoutSessionEntity(sourcePlanId=s.sourcePlanId?.let(planIdMap::get),planNameSnapshot=s.planName,startedAt=s.startedAt,endedAt=if(interrupted)stoppedAt else s.endedAt,localDate=s.localDate,status=if(interrupted)"INTERRUPTED" else s.status))
                val ordered=s.exercises.sortedWith(if(backup.schemaVersion<6)compareBy({e:SessionExerciseDto->e.sets.mapNotNull{it.startedAt?:it.completedAt}.minOrNull()==null},{e->e.sets.mapNotNull{it.startedAt?:it.completedAt}.minOrNull()?:Long.MAX_VALUE},{it.position})else compareBy{it.position})
                ordered.filter{it.sets.isNotEmpty()}.forEachIndexed { position,e -> val eid=db.sessionDao().insertSessionExercise(SessionExerciseEntity(sessionId=sessionId,exerciseId=e.exerciseId,exerciseNameSnapshot=e.name,position=position,restSeconds=e.restSeconds,trackingMode=e.trackingMode)); db.sessionDao().insertSets(e.sets.map { WorkoutSetEntity(sessionExerciseId=eid,position=it.position,weightKg=it.weightKg,reps=it.reps,completed=it.completed,startedAt=it.startedAt,completedAt=it.completedAt,durationSeconds=it.durationSeconds,pausedAt=it.pausedAt?:stoppedAt.takeIf{_->interrupted&&!it.completed&&it.startedAt!=null},pausedDurationMillis=it.pausedDurationMillis,restStartedAt=it.restStartedAt,restEndedAt=it.restEndedAt?:stoppedAt.takeIf{_->interrupted&&it.restStartedAt!=null},restDurationSeconds=if(interrupted&&it.restStartedAt!=null&&it.restEndedAt==null)((stoppedAt-it.restStartedAt)/1000).coerceIn(0,Int.MAX_VALUE.toLong()).toInt()else it.restDurationSeconds,distanceKm=it.distanceKm) }) }
            }
            db.sessionDao().recordImport(ImportedBackupEntity(fingerprint,System.currentTimeMillis()))
            true
        }
        try { backup.settings?.let { settingsStore.restore(it) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withContext "训练数据已恢复，设置恢复失败；可再次导入同一文件重试，不会重复增加记录" }
        if(imported) "恢复完成；未完成训练作为中断历史保留" else "此备份已恢复过，未重复增加记录"
    }
}

private fun ExerciseEntity.dto()=ExerciseEntityDto(id,nameEn,nameZh,bodyPart,equipment,target,muscleGroup,secondaryMuscles,instructionsZh,instructionsEn,imagePath,gifPath,attribution,isFavorite,trackingMode)
private fun ExerciseEntityDto.entity()=ExerciseEntity(id=id,nameEn=nameEn,nameZh=nameZh,bodyPart=bodyPart,equipment=equipment,target=target,muscleGroup=muscleGroup,secondaryMuscles=secondaryMuscles,instructionsZh=instructionsZh,instructionsEn=instructionsEn,imagePath=imagePath,gifPath=gifPath,attribution=attribution,datasetNameZh=nameZh,trackingMode=trackingMode.ifBlank{if(bodyPart=="cardio")TrackingMode.CARDIO else TrackingMode.STRENGTH},isCustom=true,isFavorite=isFavorite)
private fun PlanExerciseEntity.dto()=PlanItemDto(id,planId,exerciseId,position,defaultSets,defaultReps,defaultWeightKg,restSeconds)
private fun PlanItemDto.entity(newPlanId:Long)=PlanExerciseEntity(planId=newPlanId,exerciseId=exerciseId,position=position,defaultSets=defaultSets,defaultReps=defaultReps,defaultWeightKg=defaultWeightKg,restSeconds=restSeconds)
