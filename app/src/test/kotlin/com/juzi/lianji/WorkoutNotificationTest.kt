package com.juzi.lianji

import com.juzi.lianji.data.SessionSetRow
import com.juzi.lianji.data.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WorkoutNotificationTest {
    private val session = WorkoutSessionEntity(7, 1, "训练计划", 1_000, localDate = "2026-08-21")
    private val row = SessionSetRow(11, 3, "深蹲", 0, 90, 0, 20.0, 10, false)

    @Test fun mapsStartRunningPauseAndRest() {
        val waiting = workoutNotificationModel(session, listOf(row), 10_000)
        assertEquals("开始", waiting.actionTitle)
        assertEquals(WorkoutTimerType.Static, waiting.timerType)

        val running = row.copy(startedAt = 2_000, imagePath = "exercise_dataset/images/squat.jpg")
        val runningModel = workoutNotificationModel(session, listOf(running), 10_000)
        assertEquals(WorkoutTimerType.CountUp, runningModel.timerType)
        assertEquals(2_000, runningModel.timerWhen)
        assertEquals("暂停", runningModel.actionTitle)
        assertEquals("完成本组", runningModel.completeActionTitle)
        assertEquals(11L, runningModel.completeSetId)
        assertEquals("exercise_dataset/images/squat.jpg", runningModel.picturePath)

        assertEquals(5_000, workoutNotificationModel(session, listOf(running.copy(pausedDurationMillis = 3_000)), 10_000).timerWhen)

        val pausedModel = workoutNotificationModel(session, listOf(running.copy(pausedAt = 7_000)), 10_000)
        assertEquals(WorkoutTimerType.Static, pausedModel.timerType)
        assertEquals(5, pausedModel.staticSeconds)
        assertEquals("继续", pausedModel.actionTitle)

        val next = row.copy(setId = 12, setPosition = 1)
        val rest = running.copy(completed = true, completedAt = 8_000, restStartedAt = 8_000)
        val restModel = workoutNotificationModel(session, listOf(rest, next), 10_000)
        assertEquals(WorkoutTimerType.Countdown, restModel.timerType)
        assertEquals(98_000, restModel.timerWhen)
        assertEquals(0, restModel.timerTotal)
        assertEquals("开始下一项", restModel.actionTitle)
        assertEquals(12L, restModel.actionSetId)
    }

    @Test fun buildsOfficialTimerAndActionFields() {
        val model = workoutNotificationModel(session, listOf(row.copy(startedAt = 2_000)), 10_000)
        val param = islandParams(model, listOf("miui.focus.action_workout", "miui.focus.action_complete"), sequence = 9)["param_v2"]!!.jsonObject
        val bigIsland = param["param_island"]!!.jsonObject["bigIslandArea"]!!.jsonObject

        assertEquals(1, param["protocol"]!!.jsonPrimitive.content.toInt())
        assertEquals(9, param["sequence"]!!.jsonPrimitive.content.toLong())
        val chat = param["chatInfo"]!!.jsonObject
        assertEquals("深蹲", chat["title"]!!.jsonPrimitive.content)
        val focusTimer = chat["timerInfo"]!!.jsonObject
        assertNotNull(focusTimer["timerSystemCurrent"])
        assertNotNull(focusTimer["timerCurrent"])
        assertNull(chat["content"])
        val islandTimer = bigIsland["sameWidthDigitInfo"]!!.jsonObject["timerInfo"]!!.jsonObject
        assertNotNull(islandTimer["timerSystemCurrent"])
        assertNotNull(islandTimer["timerCurrent"])
        assertNull(bigIsland["sameWidthDigitInfo"]!!.jsonObject["digit"])
        val actions = param["actions"]!!.jsonArray
        assertEquals(2, actions.size)
        assertEquals("#3482FF", actions[0].jsonObject["actionBgColor"]!!.jsonPrimitive.content)
        assertEquals("#30D158", actions[1].jsonObject["actionBgColor"]!!.jsonPrimitive.content)

        val rest = row.copy(startedAt = 2_000, completed = true, restStartedAt = 8_000)
        val restChat = islandParams(workoutNotificationModel(session, listOf(rest), 10_000), emptyList())["param_v2"]!!
            .jsonObject["chatInfo"]!!.jsonObject
        assertEquals("休息 · 深蹲", restChat["title"]!!.jsonPrimitive.content)
        val restTimer = restChat["timerInfo"]!!.jsonObject
        assertEquals(-1, restTimer["timerType"]!!.jsonPrimitive.content.toInt())
        assertEquals(98_000, restTimer["timerWhen"]!!.jsonPrimitive.content.toLong())
        assertEquals(0, restTimer["timerTotal"]!!.jsonPrimitive.content.toLong())
    }
}
