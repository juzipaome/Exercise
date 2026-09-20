package com.juzi.lianji

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.juzi.lianji.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class MainViewModelTest {
    private lateinit var db:LianJiDatabase
    private lateinit var vm:MainViewModel
    @Before fun open() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context=RuntimeEnvironment.getApplication()
        db=Room.inMemoryDatabaseBuilder(context,LianJiDatabase::class.java).allowMainThreadQueries().build()
        // Do not start the production application's importer or notification workers in this test.
        val app=LianJiApplication().apply {
            repository=LianJiRepository(db)
            settingsStore=SettingsStore(context)
            backupManager=BackupManager(context,db,settingsStore)
        }
        vm=MainViewModel(app)
    }
    @After fun close() {
        vm.viewModelScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }
    @Test fun failed_custom_save_reports_error_without_navigation_then_retry_saves_once()= runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_save BEFORE INSERT ON exercises BEGIN SELECT RAISE(ABORT, 'simulated write failure'); END")
        var navigations=0
        vm.saveCustom("test","","","",TrackingMode.STRENGTH){navigations++}.join()
        assertNotNull(vm.operationError.value)
        assertEquals(0,navigations)
        assertTrue(db.exerciseDao().customForBackup().isEmpty())
        vm.dismissOperationError()
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_save")
        vm.saveCustom("test","","","",TrackingMode.STRENGTH){navigations++}.join()
        assertNull(vm.operationError.value)
        assertEquals(1,navigations)
        assertEquals("test",db.exerciseDao().customForBackup().single().nameZh)
    }
}
