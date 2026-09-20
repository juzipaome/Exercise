package com.juzi.lianji.data

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class WorkoutRepositoryTest {
    private lateinit var db:LianJiDatabase
    private lateinit var repository:LianJiRepository
    @Before fun open() {
        db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),LianJiDatabase::class.java).allowMainThreadQueries().build()
        repository=LianJiRepository(db)
    }
    @After fun close() { db.close() }
    private suspend fun plan():Long {
        db.exerciseDao().upsertAll(listOf("a","b").map{ExerciseEntity(id=it,nameEn=it,nameZh=it,bodyPart="",equipment="",target="",muscleGroup="",secondaryMuscles="",instructionsZh="",instructionsEn="",imagePath=null,gifPath=null,attribution="test")})
        return repository.save(WorkoutPlanEntity(name="test"),listOf("a","b").mapIndexed{i,id->PlanExerciseEntity(planId=0,exerciseId=id,position=i,defaultSets=1)})
    }
    @Test fun concurrent_starts_create_one_session()= runBlocking {
        val plan=plan()
        val ids=coroutineScope{List(8){async(Dispatchers.Default){repository.start(plan)}}.awaitAll()}
        assertEquals(1,ids.distinct().size)
        assertEquals(1,db.sessionDao().getAll().size)
    }
    @Test fun promotion_manual_move_and_explicit_exercise_deletion_are_persisted()= runBlocking {
        val id=repository.start(plan())
        val rows=repository.rows(id).first()
        val a=rows.first(); val b=rows.last()
        repository.beginSet(b.setId)
        assertEquals("b",repository.rows(id).first().first().exerciseId)
        repository.reorderExercises(id,listOf(a.sessionExerciseId,b.sessionExerciseId))
        repository.pauseSet(b.setId); repository.beginSet(b.setId)
        assertEquals("a",repository.rows(id).first().first().exerciseId)
        val before=db.sessionDao().getSet(b.setId)
        assertTrue(runCatching{repository.deleteSet(b.setId,confirmed=true)}.exceptionOrNull() is IllegalArgumentException)
        assertEquals(before,db.sessionDao().getSet(b.setId))
        assertTrue(runCatching{repository.deleteExercise(b.sessionExerciseId)}.exceptionOrNull() is IllegalArgumentException)
        repository.deleteExercise(b.sessionExerciseId,confirmed=true)
        assertNull(db.sessionDao().getSet(b.setId))
        assertEquals(1,db.sessionDao().getSessionExercises(id).size)
        repository.addExercise(id,"b",90)
        assertEquals(2,db.sessionDao().getSessionExercises(id).size)
    }
    @Test fun notification_completion_reads_saved_draft()= runBlocking {
        val id=repository.start(plan()); val row=repository.rows(id).first().first()
        repository.beginSet(row.setId)
        repository.updateSetValues(row.setId,32.5,8)
        repository.completeSavedSet(row.setId)
        val saved=db.sessionDao().getSet(row.setId)!!
        assertTrue(saved.completed); assertEquals(32.5,saved.weightKg,0.0); assertEquals(8,saved.reps)
    }

    @Test fun trained_sets_require_confirmation_and_concurrent_deletes_keep_one_set()= runBlocking {
        val plan=plan(); val id=repository.start(plan)
        val a=repository.rows(id).first().first()
        repository.addSet(a.sessionExerciseId,1,20.0,10)
        repository.beginSet(a.setId)
        suspend fun assertProtected() {
            val before=db.sessionDao().getSet(a.setId)
            assertTrue(runCatching{repository.deleteSet(a.setId)}.exceptionOrNull() is IllegalArgumentException)
            assertEquals(before,db.sessionDao().getSet(a.setId))
        }
        assertProtected()
        repository.pauseSet(a.setId); assertProtected()
        repository.completeSet(a.setId,25.0,8); assertProtected()
        repository.deleteSet(a.setId,confirmed=true)
        assertNull(db.sessionDao().getSet(a.setId))
        assertEquals(0,db.sessionDao().getSetsForExercise(a.sessionExerciseId).single().position)
        repository.addSet(a.sessionExerciseId,1,20.0,10)
        val sets=db.sessionDao().getSetsForExercise(a.sessionExerciseId)
        val results=coroutineScope{sets.map{async(Dispatchers.Default){runCatching{repository.deleteSet(it.id)}}}.awaitAll()}
        assertEquals(1,results.count{it.isSuccess})
        assertEquals(1,db.sessionDao().getSetsForExercise(a.sessionExerciseId).size)
        repository.deleteExercise(a.sessionExerciseId,confirmed=true)
        assertTrue(db.sessionDao().getSetsForExercise(a.sessionExerciseId).isEmpty())
        assertEquals(2,repository.plan(plan)!!.second.size)
    }

    @Test fun rest_alarm_revalidates_deadline_and_delivers_only_once_without_a_screen()= runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val settings=SettingsStore(context)
        settings.setVibration(false)
        val reminder=com.juzi.lianji.RestReminder(context,db,settings)
        val id=repository.start(plan()); val row=repository.rows(id).first().first()
        repository.setSessionRest(id,0)
        repository.beginSet(row.setId); repository.completeSet(row.setId,20.0,10)
        val deadline=db.sessionDao().getSet(row.setId)!!.restStartedAt!!
        reminder.deliver(row.setId,deadline-1)
        assertNull(db.sessionDao().getSet(row.setId)!!.restNotifiedAt)
        reminder.reschedule()
        reminder.deliver(row.setId,deadline)
        val notified=db.sessionDao().getSet(row.setId)!!.restNotifiedAt
        assertNotNull(notified)
        reminder.deliver(row.setId,deadline)
        assertEquals(notified,db.sessionDao().getSet(row.setId)!!.restNotifiedAt)
    }

    @Test fun completing_paused_set_keeps_other_set_running_and_notification_on_training()= runBlocking {
        val id=repository.start(plan()); val rows=repository.rows(id).first()
        val a=rows.first(); val b=rows.last()
        repository.beginSet(a.setId); repository.beginSet(b.setId)
        assertNotNull(db.sessionDao().getSet(a.setId)!!.pausedAt)
        repository.completeSavedSet(a.setId)
        assertNull(db.sessionDao().getOpenRest(id))
        assertNull(db.sessionDao().getSet(b.setId)!!.pausedAt)
        val model=com.juzi.lianji.workoutNotificationModel(db.sessionDao().getSession(id)!!,repository.rows(id).first())
        assertEquals(b.setId,model.completeSetId)
        assertEquals(com.juzi.lianji.WorkoutTimerType.CountUp,model.timerType)
    }

    @Test fun completing_paused_set_preserves_existing_rest_and_last_completion_closes_it()= runBlocking {
        val id=repository.start(plan()); val rows=repository.rows(id).first()
        val a=rows.first(); val b=rows.last()
        repository.addSet(a.sessionExerciseId,1,0.0,10)
        val extra=db.sessionDao().getSetsForExercise(a.sessionExerciseId).last()
        repository.beginSet(a.setId); repository.beginSet(b.setId)
        repository.completeSet(b.setId,20.0,10)
        val rest=db.sessionDao().getOpenRest(id)!!
        repository.completeSet(a.setId,25.0,8)
        assertNull(db.sessionDao().getSet(a.setId)!!.restStartedAt)
        assertEquals(rest,db.sessionDao().getOpenRest(id))
        repository.completeSet(extra.id,15.0,10)
        assertNull(db.sessionDao().getOpenRest(id))
        assertNotNull(db.sessionDao().getSet(b.setId)!!.restEndedAt)
    }

    @Test fun early_start_rejects_late_alarm_even_after_deadline()= runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val settings=SettingsStore(context); settings.setVibration(false)
        val reminder=com.juzi.lianji.RestReminder(context,db,settings)
        val id=repository.start(plan()); val rows=repository.rows(id).first()
        val a=rows.first(); val b=rows.last()
        repository.setSessionRest(id,90)
        repository.beginSet(a.setId); repository.completeSet(a.setId,20.0,10)
        val rest=db.sessionDao().getSet(a.setId)!!
        val deadline=rest.restStartedAt!!+90_000
        repository.beginSet(b.setId)
        val closed=db.sessionDao().getSet(a.setId)!!
        assertNotNull(closed.restEndedAt)
        assertTrue(closed.restEndedAt!!<deadline)
        // Java wall time is not Robolectric's uptime. Age the closed-rest fixture,
        // keeping its duration intact, to exercise a late callback without sleeping.
        val age=91_000L
        db.sessionDao().updateSet(closed.copy(restStartedAt=closed.restStartedAt!!-age,restEndedAt=closed.restEndedAt-age))
        assertTrue(System.currentTimeMillis()>=deadline-age)
        reminder.deliver(a.setId,deadline-age)
        assertNull(db.sessionDao().getSet(a.setId)!!.restNotifiedAt)
        assertNull(com.juzi.lianji.pendingRestAlarm(db.sessionDao().getSession(id),repository.rows(id).first()))
    }

    @Test fun starting_next_set_closes_all_legacy_open_rests()= runBlocking {
        val id=repository.start(plan()); val rows=repository.rows(id).first()
        val now=System.currentTimeMillis()
        rows.forEach { row -> db.sessionDao().updateSet(db.sessionDao().getSet(row.setId)!!.copy(restStartedAt=now-5_000)) }
        repository.beginSet(rows.last().setId)
        assertTrue(repository.rows(id).first().all{it.restEndedAt!=null&&it.restDurationSeconds>=5})
    }

    @Test fun scoped_history_and_personal_best_keep_date_boundaries_and_exercise_isolation()= runBlocking {
        val plan=plan()
        for (date in listOf("2026-08-31","2026-09-01","2026-09-30","2026-10-01")) {
            db.sessionDao().insertSession(WorkoutSessionEntity(sourcePlanId=plan,planNameSnapshot=date,startedAt=1,endedAt=2,localDate=date,status="COMPLETED"))
            db.scheduleDao().insert(ScheduledWorkoutEntity(planId=plan,planName=date,scheduledDate=date))
        }
        assertEquals(setOf("2026-09-01","2026-09-30"),repository.sessions("2026-09-01","2026-10-01").first().map{it.localDate}.toSet())
        assertEquals(4,repository.days("2026-09-01","2026-10-01").first().size)
        assertEquals("2026-09-30",repository.schedules("2026-09-30","2026-10-01").first().single().scheduledDate)
        assertEquals(1,repository.sessions("2026-09-30","2026-10-01").first().size)
        assertTrue(repository.days("2026-11-01","2026-12-01").first().isEmpty())
        val id=repository.start(plan); val rows=repository.rows(id).first()
        repository.completeSet(rows.first().setId,30.0,8)
        repository.completeSet(rows.last().setId,100.0,12)
        assertEquals(30.0,repository.personalBest("a").first()!!.maxWeightKg,0.0)
        assertEquals(100.0,repository.personalBest("b").first()!!.maxWeightKg,0.0)
        assertNull(repository.personalBest("unknown").first())
    }
}
