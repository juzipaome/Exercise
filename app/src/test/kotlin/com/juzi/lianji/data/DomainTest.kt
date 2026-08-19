package com.juzi.lianji.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainTest {
    @Test fun volume_counts_only_completed_sets() {
        val rows = listOf(
            SessionSetRow(1,1,"深蹲",0,90,0,100.0,5,true),
            SessionSetRow(2,1,"深蹲",0,90,1,100.0,5,false),
            SessionSetRow(3,1,"深蹲",0,90,2,80.0,8,true),
        )
        assertEquals(1140.0, trainingVolume(rows), 0.001)
    }

    @Test fun plan_snapshot_name_is_independent_from_template() {
        val plan = WorkoutPlanEntity(id=1,name="推日")
        val session = WorkoutSessionEntity(sourcePlanId=plan.id,planNameSnapshot=plan.name,startedAt=1,localDate="2026-08-12")
        val edited = plan.copy(name="胸肩日")
        assertEquals("推日", session.planNameSnapshot)
        assertEquals("胸肩日", edited.name)
    }

    @Test fun cardio_uses_one_timed_record_without_reps() {
        val cardio = ExerciseEntity(
            id="run",nameEn="Run",nameZh="跑步",bodyPart="cardio",equipment="body weight",
            target="",muscleGroup="",secondaryMuscles="",instructionsZh="",instructionsEn="",
            imagePath=null,gifPath=null,attribution="test",trackingMode=TrackingMode.CARDIO,
        )
        assertEquals(1, initialRecordCount(cardio, 3))
        assertEquals(0, initialReps(cardio, 10))
    }

    @Test fun past_cardio_record_keeps_session_duration_and_distance() {
        val cardio = ExerciseEntity(
            id="run",nameEn="Run",nameZh="跑步",bodyPart="cardio",equipment="body weight",
            target="",muscleGroup="",secondaryMuscles="",instructionsZh="",instructionsEn="",
            imagePath=null,gifPath=null,attribution="test",trackingMode=TrackingMode.CARDIO,
        )
        val record = pastWorkoutSet(cardio,7,0,20.0,10,1_000,1_801_000,5.25)

        assertEquals(1800, record.durationSeconds)
        assertEquals(5.25, record.distanceKm, 0.001)
        assertEquals(0.0, record.weightKg, 0.001)
        assertEquals(0, record.reps)
    }

    @Test fun session_exercise_name_remains_a_snapshot_after_library_rename() {
        val snapshot = SessionExerciseEntity(sessionId=1,exerciseId="1",exerciseNameSnapshot="原显示名",position=0,restSeconds=90)
        val renamedLibraryExercise = "更好的中文名"
        assertEquals("原显示名", snapshot.exerciseNameSnapshot)
        assertEquals("更好的中文名", renamedLibraryExercise)
    }

    @Test fun added_strength_set_is_detected_as_plan_change() {
        val planItems=listOf(PlanExerciseEntity(planId=1,exerciseId="squat",position=0,defaultSets=3))
        val sessionExercises=listOf(SessionExerciseEntity(id=10,sessionId=2,exerciseId="squat",exerciseNameSnapshot="深蹲",position=0,restSeconds=90))
        assertEquals(true,hasPlanStructureChanges(planItems,sessionExercises,mapOf(10L to 4)))
        assertEquals(false,hasPlanStructureChanges(planItems,sessionExercises,mapOf(10L to 3)))
    }

    @Test fun cardio_single_record_does_not_look_like_removed_sets() {
        val planItems=listOf(PlanExerciseEntity(planId=1,exerciseId="run",position=0,defaultSets=3))
        val sessionExercises=listOf(SessionExerciseEntity(id=11,sessionId=2,exerciseId="run",exerciseNameSnapshot="跑步",position=0,restSeconds=90,trackingMode=TrackingMode.CARDIO))
        assertEquals(false,hasPlanStructureChanges(planItems,sessionExercises,mapOf(11L to 1)))
    }

    @Test fun workout_order_follows_the_exercises_the_user_actually_started() {
        val rows = listOf(
            workoutRow(1, 10, "计划第一项", exercisePosition=0),
            workoutRow(2, 20, "先挑着做", exercisePosition=1, startedAt=100),
            workoutRow(3, 30, "之后挑着做", exercisePosition=2, startedAt=200),
        )

        assertEquals(
            listOf("先挑着做", "之后挑着做", "计划第一项"),
            orderedWorkoutGroups(rows).map { it.first().exerciseName },
        )
    }

    @Test fun rest_continues_the_exercise_just_completed_instead_of_plan_first() {
        val rows = listOf(
            workoutRow(1, 10, "计划第一项", exercisePosition=0),
            workoutRow(2, 20, "挑着做", exercisePosition=1, setPosition=0, completed=true, startedAt=100),
            workoutRow(3, 20, "挑着做", exercisePosition=1, setPosition=1),
            workoutRow(4, 20, "挑着做", exercisePosition=1, setPosition=2),
        )

        assertEquals(3L, nextWorkoutSet(rows, completedSetId=2)?.setId)
    }

    @Test fun rest_moves_to_the_next_remaining_exercise_after_current_is_done() {
        val rows = listOf(
            workoutRow(1, 10, "计划第一项", exercisePosition=0),
            workoutRow(2, 20, "挑着做", exercisePosition=1, completed=true, startedAt=100),
            workoutRow(3, 30, "计划第三项", exercisePosition=2),
        )

        assertEquals(1L, nextWorkoutSet(rows, completedSetId=2)?.setId)
    }

    @Test fun paused_activity_time_stays_frozen() {
        assertEquals(
            12L,
            activeDurationSeconds(
                startedAt=1_000,
                now=30_000,
                pausedAt=15_000,
                pausedDurationMillis=2_000,
            ),
        )
    }

    @Test fun resumed_activity_excludes_all_previous_pauses() {
        assertEquals(
            20L,
            activeDurationSeconds(
                startedAt=1_000,
                now=26_000,
                pausedAt=null,
                pausedDurationMillis=5_000,
            ),
        )
    }

    private fun workoutRow(
        setId: Long,
        exerciseId: Long,
        name: String,
        exercisePosition: Int,
        setPosition: Int = 0,
        completed: Boolean = false,
        startedAt: Long? = null,
    ) = SessionSetRow(
        setId=setId,
        sessionExerciseId=exerciseId,
        exerciseName=name,
        exercisePosition=exercisePosition,
        restSeconds=90,
        setPosition=setPosition,
        weightKg=0.0,
        reps=10,
        completed=completed,
        startedAt=startedAt,
    )
}
