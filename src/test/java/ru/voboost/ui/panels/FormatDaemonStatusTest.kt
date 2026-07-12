package ru.voboost.ui.panels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.voboost.StatusReader.DaemonState
import ru.voboost.StatusReader.DaemonStatus
import ru.voboost.StatusReader.InjectionState
import ru.voboost.StatusReader.InjectionStatus

/**
 * Tests for [formatDaemonStatus] — the diagnostic formatter used by the
 * Settings panel's daemon-status section.
 *
 * The formatter is a pure function (DaemonStatus? -> String) producing an
 * English-only diagnostic string. These tests pin its output format so future
 * changes are intentional.
 */
class FormatDaemonStatusTest {
    @Test
    fun testNullStatusReturnsUnavailable() {
        assertEquals("Daemon: unavailable", formatDaemonStatus(null))
    }

    @Test
    fun testReadyStateNoFlagsNoInjections() {
        val status =
            DaemonStatus(
                daemon = "voboost-inject 1.0.0",
                manifest = 1,
                state = DaemonState.READY,
                killed = false,
                panic = false,
                injections = emptyList(),
            )
        val result = formatDaemonStatus(status)
        assertTrue(result.contains("Daemon: voboost-inject 1.0.0"))
        assertTrue(result.contains("State: READY"))
        assertTrue(result.contains("Manifest: 1"))
        assertTrue(result.contains("Injections: none"))
        // No flags line when neither killed nor panic.
        assertTrue(!result.contains("Flags:"))
    }

    @Test
    fun testKilledAndPanicFlagsShown() {
        val status =
            DaemonStatus(
                daemon = "voboost-inject 1.0.0",
                manifest = 2,
                state = DaemonState.DEGRADED,
                killed = true,
                panic = true,
                injections = emptyList(),
            )
        val result = formatDaemonStatus(status)
        assertTrue(result.contains("Flags: KILLED, PANIC"))
    }

    @Test
    fun testInjectionListRendered() {
        val status =
            DaemonStatus(
                daemon = "voboost-inject 1.0.0",
                manifest = 1,
                state = DaemonState.READY,
                killed = false,
                panic = false,
                injections =
                    listOf(
                        InjectionStatus(
                            id = "weather",
                            process = "com.weather",
                            state = InjectionState.ACTIVE,
                        ),
                        InjectionStatus(
                            id = "ev",
                            process = "com.ev",
                            state = InjectionState.FAILED,
                        ),
                    ),
            )
        val result = formatDaemonStatus(status)
        assertTrue(result.contains("Injections:"))
        assertTrue(result.contains("- weather: ACTIVE (com.weather)"))
        assertTrue(result.contains("- ev: FAILED (com.ev)"))
        assertTrue(!result.contains("Injections: none"))
    }
}
