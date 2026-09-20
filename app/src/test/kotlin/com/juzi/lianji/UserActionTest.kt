package com.juzi.lianji

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UserActionTest {
    @Test fun failure_does_not_run_success_callback_and_retry_can_succeed()= runBlocking {
        val events=mutableListOf<String>()
        suspend fun save(fail:Boolean) { if(fail) throw java.io.IOException("disk full"); events.add("saved") }
        runUserAction({events.add("error")}) { save(true); events.add("navigate") }
        assertEquals(listOf("error"),events)
        runUserAction({events.add("error")}) { save(false); events.add("navigate") }
        assertEquals(listOf("error","saved","navigate"),events)
    }

    @Test fun cancellation_is_not_reported_as_save_failure()= runBlocking {
        val cancellation=CancellationException("screen closed")
        try {
            runUserAction({fail("Cancellation must propagate")}) { throw cancellation }
            fail("Expected cancellation")
        } catch(actual:CancellationException) { assertSame(cancellation,actual) }
    }
}
