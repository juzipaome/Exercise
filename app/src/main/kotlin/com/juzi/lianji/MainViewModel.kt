package com.juzi.lianji

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juzi.lianji.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MainUiState(
    val isReady: Boolean = false,
    val exercises: List<ExerciseEntity> = emptyList(),
    val plans: List<PlanSummary> = emptyList(),
    val schedules: List<ScheduledWorkoutEntity> = emptyList(),
    val sessions: List<WorkoutSessionEntity> = emptyList(),
    val days: List<DaySummary> = emptyList(),
    val active: WorkoutSessionEntity? = null,
    val personalBests: Map<String, ExercisePersonalBest> = emptyMap(),
    val settings: AppSettings = AppSettings(),
)

private data class StartupState(
    val plans: List<PlanSummary>,
    val active: WorkoutSessionEntity?,
    val personalBests: List<ExercisePersonalBest>,
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
        repository.personalBests,
        settingsStore.settings,
    ) { plans, active, personalBests, settings -> StartupState(plans, active, personalBests, settings) }

    val state: StateFlow<MainUiState> = combine(
        startupState,
        repository.exercises.onStart { emit(emptyList()) },
        repository.schedules.onStart { emit(emptyList()) },
        repository.sessions.onStart { emit(emptyList()) },
        repository.days.onStart { emit(emptyList()) },
    ) { startup, exercises, schedules, sessions, days ->
        MainUiState(
            isReady = true,
            exercises = exercises,
            plans = startup.plans,
            schedules = schedules,
            sessions = sessions,
            days = days,
            active = startup.active,
            personalBests = startup.personalBests.associateBy { it.exerciseId },
            settings = startup.settings,
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    fun createPlan(name: String, exerciseIds: List<String>, onDone: (Long)->Unit = {}) = viewModelScope.launch {
        val id = repository.save(WorkoutPlanEntity(name = name), exerciseIds.mapIndexed { i, ex -> PlanExerciseEntity(planId=0,exerciseId=ex,position=i,restSeconds=state.value.settings.defaultRestSeconds) })
        onDone(id)
    }
    fun loadPlan(id:Long,onDone:(WorkoutPlanEntity,List<String>)->Unit)=viewModelScope.launch {
        repository.plan(id)?.let { (plan,items) -> onDone(plan,items.map { it.exerciseId }) }
    }
    fun savePlan(id:Long,name:String,exerciseIds:List<String>,onDone:(Long)->Unit={})=viewModelScope.launch {
        val (existing,items)=repository.plan(id) ?: return@launch
        val byExercise=items.associateBy { it.exerciseId }
        onDone(repository.save(existing.copy(name=name),exerciseIds.mapIndexed{i,ex->
            byExercise[ex]?.copy(position=i) ?: PlanExerciseEntity(planId=id,exerciseId=ex,position=i,restSeconds=state.value.settings.defaultRestSeconds)
        }))
    }
    fun deletePlan(id: Long) = viewModelScope.launch { repository.delete(id) }
    fun duplicatePlan(id: Long) = viewModelScope.launch { repository.duplicate(id) }
    fun start(id: Long, onDone:(Long)->Unit) = viewModelScope.launch { onDone(repository.start(id)) }
    fun beginSet(id:Long)=viewModelScope.launch { repository.beginSet(id) }
    fun pauseSet(id:Long)=viewModelScope.launch { repository.pauseSet(id) }
    fun completeSet(id:Long,weight:Double,reps:Int)=viewModelScope.launch { repository.completeSet(id,weight,reps) }
    fun updateSetValues(id:Long,weight:Double,reps:Int)=viewModelScope.launch { repository.updateSetValues(id,weight,reps) }
    fun completeCardio(id:Long,distanceKm:Double)=viewModelScope.launch { repository.completeCardio(id,distanceKm) }
    fun updateCardioValues(id:Long,durationSeconds:Int,distanceKm:Double)=viewModelScope.launch { repository.updateCardioValues(id,durationSeconds,distanceKm) }
    fun setSessionRest(id:Long,seconds:Int)=viewModelScope.launch { repository.setSessionRest(id,seconds) }
    fun addSet(sessionExerciseId:Long,position:Int,weight:Double,reps:Int)=viewModelScope.launch { repository.addSet(sessionExerciseId,position,weight,reps) }
    fun deleteSet(id:Long)=viewModelScope.launch { repository.deleteSet(id) }
    fun addExercise(sessionId:Long,exerciseId:String)=viewModelScope.launch { repository.addExercise(sessionId,exerciseId,state.value.settings.defaultRestSeconds) }
    fun finish(id:Long,onDone:(Boolean)->Unit={})=viewModelScope.launch { onDone(repository.finish(id)) }
    fun saveSessionPlan(id:Long,overwrite:Boolean,onDone:(Long)->Unit={})=viewModelScope.launch { onDone(repository.saveSessionPlan(id,overwrite)) }
    fun addPastWorkout(planId:Long,exerciseIds:List<String>,date:LocalDate,startMinute:Int,endMinute:Int,cardioDistancesKm:Map<String,Double>)=viewModelScope.launch { repository.addPastWorkout(planId,exerciseIds,date,startMinute,endMinute,cardioDistancesKm) }
    fun discard(id:Long)=viewModelScope.launch { repository.discard(id) }
    fun deleteHistory(id:Long)=viewModelScope.launch { repository.deleteHistory(id) }
    fun deleteSchedule(item:ScheduledWorkoutEntity)=viewModelScope.launch { repository.delete(item) }
    fun toggleFavorite(id:String)=viewModelScope.launch { repository.toggleFavorite(id) }
    fun updateExerciseName(id:String,nameZh:String)=viewModelScope.launch { repository.updateName(id,nameZh) }
    fun restoreExerciseName(id:String)=viewModelScope.launch { repository.restoreDatasetName(id) }
    fun saveCustom(name:String, bodyPart:String, equipment:String, instructions:String, trackingMode:String)=viewModelScope.launch {
        val storedBodyPart = if (trackingMode == TrackingMode.CARDIO) "cardio" else bodyPart
        repository.saveCustom(ExerciseEntity(id="",nameEn=name,nameZh=name,bodyPart=storedBodyPart,equipment=equipment,target="",muscleGroup="",secondaryMuscles="",instructionsZh=instructions,instructionsEn=instructions,imagePath=null,gifPath=null,attribution="用户自定义",trackingMode=trackingMode,isCustom=true))
    }
}
