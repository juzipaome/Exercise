package com.juzi.lianji.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BackupValidationTest {
    private val empty=BackupEnvelope(exportedAt=1000,customExercises=emptyList(),plans=emptyList(),schedules=emptyList(),sessions=emptyList())
    @Test fun old_and_current_backups_round_trip() {
        val configs = listOf(
            AppSettings(),
            AppSettings(
                paletteStyle="Vibrant",
                colorSpec="Spec2025",
                keyColorIndex=2,
                glassMaterialPreset="POPUP_VIEW",
                glassEdgeStroke="BIG",
                navigationBarAction=false,
                enableSquircle=false,
                progressiveBlur=true,
            ),
        )
        (1..6).forEach { version ->
            configs.forEach { settings ->
                val backup=empty.copy(schemaVersion=version,settings=settings,builtinFavoriteIds=listOf("1"))
                validateBackup(backup)
                val decoded=backupJson.decodeFromString<BackupEnvelope>(backupJson.encodeToString(backup))
                assertEquals(backup,decoded)
                assertEquals(backupFingerprint(backup),backupFingerprint(decoded))
            }
        }
        val legacyJson = """{"schemaVersion":6,"exportedAt":1000,"customExercises":[],"plans":[],"schedules":[],"sessions":[],"settings":{"themeMode":"SYSTEM","navigationBarStyle":"STANDARD","glassStyle":false}}"""
        val decodedLegacy = backupJson.decodeFromString<BackupEnvelope>(legacyJson)
        validateBackup(decodedLegacy)
        assertEquals("DEFAULT",decodedLegacy.settings!!.glassMaterialPreset)
        assertTrue(decodedLegacy.settings!!.progressiveBlur)
        assertTrue(decodedLegacy.settings!!.enableSquircle)
    }
    @Test fun invalid_backup_is_rejected_before_writes() {
        val plan=PlanDto(1,"Plan","",1,listOf(PlanItemDto(1,1,"a",0,-1,10,0.0,90)))
        assertThrows(IllegalArgumentException::class.java) { validateBackup(empty.copy(plans=listOf(plan))) }
        assertThrows(IllegalArgumentException::class.java) { validateBackup(empty.copy(plans=listOf(plan,plan))) }
        assertThrows(IllegalArgumentException::class.java) { validateBackup(empty.copy(settings=AppSettings(glassMaterialPreset="UNKNOWN"))) }
        assertThrows(IllegalArgumentException::class.java) { validateBackup(empty.copy(settings=AppSettings(glassEdgeStroke="UNKNOWN"))) }
        assertThrows(IllegalArgumentException::class.java) { validateBackup(empty.copy(settings=AppSettings(keyColorIndex=99))) }
        assertThrows(IllegalArgumentException::class.java) { validateBackup(empty.copy(schemaVersion=99)) }
    }
}
