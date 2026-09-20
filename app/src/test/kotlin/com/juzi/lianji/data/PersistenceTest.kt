package com.juzi.lianji.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class PersistenceTest {
    @Test fun version_five_upgrade_preserves_display_order_and_all_sessions() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val name="migration-test.db"
        context.deleteDatabase(name)
        context.getDatabasePath(name).parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use { old ->
            javaClass.getResourceAsStream("/schema-v5.sql")!!.bufferedReader().use { it.readText() }
                .split(';').filter { it.isNotBlank() }.forEach(old::execSQL)
            old.execSQL("INSERT INTO workout_sessions VALUES (1,NULL,'old',1000,NULL,'2026-09-17','ACTIVE'),(2,NULL,'latest',2000,NULL,'2026-09-17','ACTIVE')")
            old.execSQL("INSERT INTO session_exercises VALUES (1,2,'a','a',0,90,'STRENGTH'),(2,2,'b','b',1,90,'STRENGTH'),(3,2,'empty','empty',2,90,'STRENGTH')")
            old.execSQL("INSERT INTO workout_sets (id,sessionExerciseId,position,weightKg,reps,completed,startedAt) VALUES (1,1,0,30,10,0,NULL),(2,2,0,40,8,0,2100)")
            old.version=5
        }
        val db=Room.databaseBuilder(context,LianJiDatabase::class.java,name).addMigrations(LianJiDatabase.MIGRATION_5_6).build()
        try {
            val rows=db.sessionDao().observeRows(2).first()
            assertEquals(listOf("b","a"),rows.map { it.exerciseId })
            assertEquals(2,db.sessionDao().getSessionExercises(2).size)
            assertEquals("INTERRUPTED",db.sessionDao().getSession(1)!!.status)
            assertEquals(2L,db.sessionDao().getActive()!!.id)
            assertEquals(40.0,rows.first().weightKg,0.0)
            assertNull(rows.first().restNotifiedAt)
            db.sessionDao().recordImport(ImportedBackupEntity("test",1))
            assertEquals(1,db.sessionDao().importedCount("test"))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun backup_round_trip_is_additive_idempotent_and_rejects_invalid_data() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val db=Room.inMemoryDatabaseBuilder(context,LianJiDatabase::class.java).build()
        val target=Room.inMemoryDatabaseBuilder(context,LianJiDatabase::class.java).build()
        val file=File(context.cacheDir,"round-trip.json")
        try {
            ExerciseImporter.seedIfNeeded(context,db)
            val exercise=db.exerciseDao().getBuiltin().first()
            db.exerciseDao().toggleFavorite(exercise.id)
            val repository=LianJiRepository(db)
            val plan=repository.save(WorkoutPlanEntity(name="Test"),listOf(PlanExerciseEntity(planId=0,exerciseId=exercise.id,position=0,defaultSets=1)))
            repository.start(plan)
            val settings=SettingsStore(context)
            settings.setNavigationBarStyle("LIQUID")
            BackupManager(context,db,settings).exportTo(Uri.fromFile(file))
            val raw=file.readText()
            val envelope=Json.decodeFromString<BackupEnvelope>(raw)
            validateBackup(envelope)
            settings.setNavigationBarStyle("STANDARD")
            val backup=BackupManager(context,target,settings)
            ExerciseImporter.seedIfNeeded(context,target)
            val targetOnlyFavorite=target.exerciseDao().getBuiltin().first { it.id!=exercise.id }
            target.exerciseDao().toggleFavorite(targetOnlyFavorite.id)
            backup.importFrom(Uri.fromFile(file))
            backup.importFrom(Uri.fromFile(file))
            assertEquals(1,target.planDao().getPlans().size)
            assertEquals(1,target.sessionDao().getAll().size)
            assertEquals("INTERRUPTED",target.sessionDao().getAll().single().status)
            assertNull(target.sessionDao().getActive())
            assertTrue(target.exerciseDao().get(exercise.id)!!.isFavorite)
            assertFalse(target.exerciseDao().get(targetOnlyFavorite.id)!!.isFavorite)
            assertEquals("LIQUID",settings.settings.first().navigationBarStyle)
            file.writeText(Json.encodeToString(envelope.copy(plans=envelope.plans.map { it.copy(items=it.items.map { item->item.copy(defaultSets=-1) }) })))
            try { backup.importFrom(Uri.fromFile(file)); fail("Invalid backup accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(1,target.planDao().getPlans().size)
            assertEquals(1,target.sessionDao().getAll().size)
        } finally { db.close(); target.close(); file.delete() }
    }
}
