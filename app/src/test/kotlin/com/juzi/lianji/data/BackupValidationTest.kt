package com.juzi.lianji.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BackupValidationTest {
    private val empty=BackupEnvelope(exportedAt=1000,customExercises=emptyList(),plans=emptyList(),schedules=emptyList(),sessions=emptyList())
    @Test fun old_and_current_backups_round_trip() {
        (1..6).forEach { version ->
            val backup=empty.copy(schemaVersion=version,settings=AppSettings(navigationBarStyle="LIQUID"),builtinFavoriteIds=listOf("1"))
            validateBackup(backup)
            val decoded=Json.decodeFromString<BackupEnvelope>(Json.encodeToString(backup))
            assertEquals(backup,decoded)
            assertEquals(backupFingerprint(backup),backupFingerprint(decoded))
        }
    }
    @Test fun invalid_backup_is_rejected_before_writes() {
        val plan=PlanDto(1,"Plan","",1,listOf(PlanItemDto(1,1,"a",0,-1,10,0.0,90)))
        assertThrows(IllegalArgumentException::class.java) { validateBackup(empty.copy(plans=listOf(plan))) }
        assertThrows(IllegalArgumentException::class.java) { validateBackup(empty.copy(plans=listOf(plan,plan))) }
        assertThrows(IllegalArgumentException::class.java) { validateBackup(empty.copy(settings=AppSettings(navigationBarStyle="UNKNOWN"))) }
        assertThrows(IllegalArgumentException::class.java) { validateBackup(empty.copy(schemaVersion=99)) }
    }
}
