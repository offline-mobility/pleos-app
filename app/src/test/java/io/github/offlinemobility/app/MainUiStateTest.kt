package io.github.offlinemobility.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateTest {
    @Test
    fun connectedStateIsNotConnecting() {
        val state = MainUiState(isConnected = true)

        assertTrue(state.isConnected)
        assertFalse(state.isConnecting)
    }
}

