package ru.voboost.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.voboost.Paths
import ru.voboost.StatusReader

/**
 * UI state manager for daemon status.
 *
 * Provides reactive StateFlow of daemon status with periodic polling.
 * Handles null status (daemon unavailable) gracefully.
 */
class StatusState(
    private val paths: Paths,
) {
    companion object {
        private const val POLL_INTERVAL_MS = 2000L // Poll every 2 seconds
    }

    private val statusReader = StatusReader(paths)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<StatusReader.DaemonStatus?>(null)
    val status: StateFlow<StatusReader.DaemonStatus?> = _status.asStateFlow()

    private var isPolling = false

    /**
     * Start periodic status polling.
     */
    fun startPolling() {
        if (isPolling) return
        isPolling = true

        scope.launch {
            while (isPolling) {
                refreshStatus()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop periodic status polling.
     */
    fun stopPolling() {
        isPolling = false
    }

    /**
     * Refresh status immediately (single read).
     */
    fun refreshStatus() {
        _status.value = statusReader.read()
    }

    /**
     * Shutdown and release resources.
     */
    fun shutdown() {
        stopPolling()
    }
}
