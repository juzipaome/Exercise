package com.juzi.lianji.data

fun activeDurationSeconds(
    startedAt: Long?,
    now: Long,
    pausedAt: Long?,
    pausedDurationMillis: Long,
): Long {
    val started = startedAt ?: return 0
    val countingUntil = pausedAt ?: now
    return ((countingUntil - started - pausedDurationMillis).coerceAtLeast(0) / 1000)
}

fun <T> movedItem(items:List<T>,from:Int,to:Int):List<T> =
    if(from==to||from !in items.indices||to !in items.indices)items
    else items.toMutableList().apply{add(to,removeAt(from))}

/**
 * Persisted order is shared by UI, notifications and plan export. Automatic
 * promotion happens once in beginSet's transaction, not on every render.
 */
fun orderedWorkoutGroups(rows: List<SessionSetRow>): List<List<SessionSetRow>> =
    rows.groupBy { it.sessionExerciseId }
        .values
        .map { it.sortedBy(SessionSetRow::setPosition) }
        .sortedBy { it.first().exercisePosition }

fun promoteStartedExercise(order: List<Long>, startedIds: Set<Long>, selectedId: Long): List<Long> {
    if (selectedId in startedIds || selectedId !in order) return order
    val remaining = order.filterNot { it == selectedId }.toMutableList()
    remaining.add(remaining.indexOfLast { it in startedIds } + 1, selectedId)
    return remaining
}

fun validWeight(value: String): Double? = value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
fun validReps(value: String): Int? = value.toIntOrNull()?.takeIf { it >= 0 }

fun startedWorkoutGroupIndex(rows: List<SessionSetRow>, setId: Long): Int? {
    val exerciseId = rows.firstOrNull { it.setId == setId && it.startedAt != null }?.sessionExerciseId ?: return null
    return orderedWorkoutGroups(rows).indexOfFirst { it.first().sessionExerciseId == exerciseId }.takeIf { it >= 0 }
}

/**
 * After rest, continue the exercise the user just completed before moving to
 * another exercise. This deliberately does not use the plan's first unfinished
 * row, because workouts may be performed out of order.
 */
fun nextWorkoutSet(rows: List<SessionSetRow>, completedSetId: Long): SessionSetRow? {
    val groups = orderedWorkoutGroups(rows)
    val completed = rows.firstOrNull { it.setId == completedSetId }

    completed?.let { previous ->
        groups.firstOrNull { it.first().sessionExerciseId == previous.sessionExerciseId }
            ?.firstOrNull { !it.completed }
            ?.let { return it }
    }

    return groups.asSequence()
        .filter { group -> group.first().sessionExerciseId != completed?.sessionExerciseId }
        .flatMap { it.asSequence() }
        .firstOrNull { !it.completed }
}
