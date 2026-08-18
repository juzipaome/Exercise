package com.juzi.lianji.ui

import com.juzi.lianji.data.ExercisePersonalBest
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelsTest {
    @Test fun personal_best_labels_keep_strength_and_cardio_records_distinct() {
        assertEquals("PB · 最高 100 kg · 最多 12 次",personalBestLabel(ExercisePersonalBest("squat",100.0,12,0.0,0)))
        assertEquals("PB · 最远 5.25 km · 最久 30:00",personalBestLabel(ExercisePersonalBest("run",0.0,0,5.25,1800)))
        assertEquals("PB · 最高 100 kg · 最多 12 次 · 最远 5.25 km · 最久 30:00",personalBestLabel(ExercisePersonalBest("mixed",100.0,12,5.25,1800)))
    }

    @Test fun decimal_labels_round_to_two_places()=assertEquals("0.02",trimDecimal(1/60.0))
}
