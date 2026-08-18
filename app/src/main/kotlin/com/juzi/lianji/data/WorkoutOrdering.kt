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

/**
 * Keeps exercises that have actually been started in the order the user first
 * touched them. Exercises that have not been started yet retain their plan order.
 */
fun orderedWorkoutGroups(rows: List<SessionSetRow>): List<List<SessionSetRow>> =
    rows.groupBy { it.sessionExerciseId }
        .values
        .map { it.sortedBy(SessionSetRow::setPosition) }
        .sortedWith(
            compareBy<List<SessionSetRow>>(
                { group -> group.mapNotNull { it.startedAt ?: it.completedAt }.minOrNull() == null },
                { group -> group.mapNotNull { it.startedAt ?: it.completedAt }.minOrNull() ?: Long.MAX_VALUE },
                { group -> group.first().exercisePosition },
            ),
        )

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
