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
                processSetupCommand(false) { throw Exception("Execution error") }
            }.message
        )
        processSetupCommand(true) { throw Exception("Execution error") }
    }

    @Test
    fun setupScriptError() {
        assertEquals(
            "Your IDE is expired, please update",
            assertThrows<Exception> {
                processSetupCommand(false) {
                    """
                execution line 1    
                execution line 2
                CODER_SETUP_ERRORYour IDE is expired, please update
                execution line 3    
                """
                }
            }.message
        )

        processSetupCommand(true) {
            """
                execution line 1    
                execution line 2
                CODER_SETUP_ERRORYour IDE is expired, please update
                execution line 3    
                """
        }

    }
}