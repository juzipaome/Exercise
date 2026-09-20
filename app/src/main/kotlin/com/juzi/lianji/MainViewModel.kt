package com.juzi.lianji

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juzi.lianji.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

data class MainUiState(
    val isReady: Boolean = false,
    val exercises: List<ExerciseEntity> = emptyList(),
    val plans: List<PlanSummary> = emptyList(),
    val active: WorkoutSessionEntity? = null,
    val settings: AppSettings = AppSettings(),
)

private data class StartupState(
    val plans: List<PlanSummary>,
    val active: WorkoutSessionEntity?,
    val settings: AppSettings,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LianJiApplication
    val repository = app.repository
    val settingsStore = app.settingsStore
    val backupManager = app.backupManager
    private val startupState = combine(
        repository.plans,
        repository.activeSession,
        settingsStore.settings,
    ) { plans, active, settings -> StartupState(plans, active, settings) }

    val state: StateFlow<MainUiState> = combine(
        startupState,
        repository.exercises.onStart { emit(emptyList()) },
    ) { startup, exercises ->
        MainUiState(
            isReady = true,
            exercises = exercises,
            plans = startup.plans,
            active = startup.active,
            settings = startup.settings,
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError = _operationError.asStateFlow()
    fun dismissOperationError() { _operationError.value = null }

    private fun launchAction(block: suspend () -> Unit) = viewModelScope.launch {
        runUserAction(onFailure = {
            android.util.Log.e("MainViewModel","User operation failed",it)
            _operationError.value = if (it is IllegalArgumentException) it.message ?: "请检查输入后重试"
                else "操作未完成，请重试。若仍失败，请检查存储空间。"
        }, block = block)
    }

    fun createPlan(name: String, exerciseIds: List<String>, onDone: (Long)->Unit = {}) = launchAction {
        val id = repository.save(WorkoutPlanEntity(name = name), exerciseIds.mapIndexed { i, ex -> PlanExerciseEntity(planId=0,exerciseId=ex,position=i,restSeconds=state.value.settings.defaultRestSeconds) })
        onDone(id)
    }
    fun loadPlan(id:Long,onDone:(WorkoutPlanEntity,List<String>)->Unit)=launchAction {
        repository.plan(id)?.let { (plan,items) -> onDone(plan,items.map { it.exerciseId }) }
    }
    fun savePlan(id:Long,name:String,exerciseIds:List<String>,onDone:(Long)->Unit={})=launchAction {
        val (existing,items)=repository.plan(id) ?: error("计划不存在")
        val byExercise=items.associateBy { it.exerciseId }
        onDone(repository.save(existing.copy(name=name),exerciseIds.mapIndexed{i,ex->
            byExercise[ex]?.copy(position=i) ?: PlanExerciseEntity(planId=id,exerciseId=ex,position=i,restSeconds=state.value.settings.defaultRestSeconds)
        }))
    }
    fun deletePlan(id: Long) = launchAction { repository.delete(id) }
    fun duplicatePlan(id: Long) = launchAction { repository.duplicate(id) }
    fun start(id: Long, onDone:(Long)->Unit) = launchAction { onDone(repository.start(id)) }
    fun beginSet(id:Long)=launchAction { repository.beginSet(id) }
    fun pauseSet(id:Long)=launchAction { repository.pauseSet(id) }
    fun completeSet(id:Long,weight:Double,reps:Int)=launchAction { repository.completeSet(id,weight,reps) }
    fun updateSetValues(id:Long,weight:Double,reps:Int)=launchAction { repository.updateSetValues(id,weight,reps) }
    fun completeCardio(id:Long,distanceKm:Double)=launchAction { repository.completeCardio(id,distanceKm) }
    fun updateCardioDraft(id:Long,distanceKm:Double)=launchAction { repository.updateCardioDraft(id,distanceKm) }
    fun updateCardioValues(id:Long,durationSeconds:Int,distanceKm:Double)=launchAction { repository.updateCardioValues(id,durationSeconds,distanceKm) }
    fun setSessionRest(id:Long,seconds:Int)=launchAction { repository.setSessionRest(id,seconds) }
    fun addSet(sessionExerciseId:Long,position:Int,weight:Double,reps:Int)=launchAction { repository.addSet(sessionExerciseId,position,weight,reps) }
    fun deleteSet(id:Long,confirmed:Boolean=false)=launchAction { repository.deleteSet(id,confirmed) }
    fun deleteExercise(id:Long)=launchAction { repository.deleteExercise(id,confirmed=true) }
    fun reorderExercises(sessionId:Long,orderedIds:List<Long>)=launchAction { repository.reorderExercises(sessionId,orderedIds) }
    fun addExercise(sessionId:Long,exerciseId:String,onDone:()->Unit={})=launchAction { repository.addExercise(sessionId,exerciseId,state.value.settings.defaultRestSeconds); onDone() }
    fun finish(id:Long,onDone:(Boolean)->Unit={})=launchAction { onDone(repository.finish(id)) }
    fun saveSessionPlan(id:Long,overwrite:Boolean,onDone:(Long)->Unit={})=launchAction { onDone(repository.saveSessionPlan(id,overwrite)) }
    fun addPastWorkout(planId:Long,exerciseIds:List<String>,date:LocalDate,startMinute:Int,endMinute:Int,cardioDistancesKm:Map<String,Double>,cardioDurationsSeconds:Map<String,Int>,onDone:()->Unit={})=launchAction { repository.addPastWorkout(planId,exerciseIds,date,startMinute,endMinute,cardioDistancesKm,cardioDurationsSeconds); onDone() }
    fun discard(id:Long)=launchAction { repository.discard(id) }
    fun deleteHistory(id:Long)=launchAction { repository.deleteHistory(id) }
    fun deleteSchedule(item:ScheduledWorkoutEntity)=launchAction { repository.delete(item) }
    fun toggleFavorite(id:String)=launchAction { repository.toggleFavorite(id) }
    fun updateExerciseName(id:String,nameZh:String,onDone:()->Unit={})=launchAction { repository.updateName(id,nameZh); onDone() }
    fun restoreExerciseName(id:String,onDone:()->Unit={})=launchAction { repository.restoreDatasetName(id); onDone() }
    fun saveCustom(name:String, bodyPart:String, equipment:String, instructions:String, trackingMode:String,onDone:()->Unit={})=launchAction {
        val storedBodyPart = if (trackingMode == TrackingMode.CARDIO) "cardio" else bodyPart
        repository.saveCustom(ExerciseEntity(id="",nameEn=name,nameZh=name,bodyPart=storedBodyPart,equipment=equipment,target="",muscleGroup="",secondaryMuscles="",instructionsZh=instructions,instructionsEn=instructions,imagePath=null,gifPath=null,attribution="用户自定义",trackingMode=trackingMode,isCustom=true))
        onDone()
    }
}

/** Cancellation is lifecycle control, not a failed user operation. */
internal suspend fun runUserAction(onFailure: (Exception) -> Unit, block: suspend () -> Unit) {
    try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { onFailure(error) }
}
