package com.coder.gateway.util

import com.coder.gateway.CoderRemoteConnectionHandle.Companion.processSetupCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

internal class SetupCommandTest {

    @Test
    fun executionErrors() {
        assertEquals(
            "Execution error",
            assertThrows<Exception> {
                processSetupCommand({ throw Exception("Execution error") }, false)
            }.message
        )
        processSetupCommand({ throw Exception("Execution error") }, true)
    }

    @Test
    fun setupScriptError() {
        assertEquals(
            "Your IDE is expired, please update",
            assertThrows<Exception> {
                processSetupCommand({
                """
                execution line 1    
                execution line 2
                CODER_SETUP_ERRORYour IDE is expired, please update
                execution line 3    
                """
                }, false)
            }.message
        )

        processSetupCommand({
            """
                execution line 1    
                execution line 2
                CODER_SETUP_ERRORYour IDE is expired, please update
                execution line 3    
                """
        }, true)

    }
}