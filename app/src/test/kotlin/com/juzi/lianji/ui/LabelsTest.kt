package com.juzi.lianji.ui

import com.juzi.lianji.data.ExercisePersonalBest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelsTest {
    @Test fun personal_best_labels_keep_strength_and_cardio_records_distinct() {
        assertEquals("PB · 最高 100 kg · 最多 12 次",personalBestLabel(ExercisePersonalBest("squat",100.0,12,0.0,0)))
        assertEquals("PB · 最远 5.25 km · 最久 30:00",personalBestLabel(ExercisePersonalBest("run",0.0,0,5.25,1800)))
        assertEquals("PB · 最高 100 kg · 最多 12 次 · 最远 5.25 km · 最久 30:00",personalBestLabel(ExercisePersonalBest("mixed",100.0,12,5.25,1800)))
    }

    @Test fun decimal_labels_round_to_two_places()=assertEquals("0.02",displayDecimal(1/60.0))

    @Test fun past_cardio_duration_rejects_present_but_invalid_values() {
        assertNull(parsePastCardioDurationSeconds("0",3600))
        assertNull(parsePastCardioDurationSeconds("-1",3600))
        assertNull(parsePastCardioDurationSeconds("NaN",3600))
        assertNull(parsePastCardioDurationSeconds("Infinity",3600))
        assertNull(parsePastCardioDurationSeconds("61",3600))
        assertEquals(1800,parsePastCardioDurationSeconds("30",3600))
    }

    @Test fun cardio_edit_values_round_trip_without_precision_loss() {
        assertEquals("5.1234",exactDecimal(5.1234))
        assertEquals(62,minutesToSeconds(minutesForEdit(62)))
    }
}
