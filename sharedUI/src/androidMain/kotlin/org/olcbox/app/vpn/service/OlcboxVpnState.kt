package org.olcbox.app.vpn.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mobile.Mobile
import org.olcbox.app.vpn.VpnStatus
import java.io.File

object OlcboxVpnState {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _status = MutableStateFlow<VpnStatus>(VpnStatus.Disconnected)
    val status = _status.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private var olcrtcPollJob: Job? = null
    private var lastOlcrtcLogPos: Int = 0

    fun setStatus(status: VpnStatus) {
        _status.value = status
        _isConnected.value = status is VpnStatus.Connected
    }

    fun addLog(msg: String) {
        Log.d(TAG, msg)
        _logs.update { (it + msg).takeLast(MAX_LOG_ENTRIES) }
    }

    fun startOlcrtcLogPolling(scope: CoroutineScope) {
        stopOlcrtcLogPolling()
        lastOlcrtcLogPos = 0
        olcrtcPollJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(1000)
                readOlcrtcLog增量()
            }
        }
    }

    fun stopOlcrtcLogPolling() {
        olcrtcPollJob?.cancel()
        olcrtcPollJob = null
    }

    private fun readOlcrtcLog增量() {
        try {
            val lines = Mobile.readLogLines(200)
            if (lines.isBlank()) return
            val allLines = lines.split("\n")
            if (allLines.size <= lastOlcrtcLogPos) return
            val newLines = allLines.drop(lastOlcrtcLogPos)
            lastOlcrtcLogPos = allLines.size
            for (line in newLines) {
                if (line.isNotBlank()) {
                    _logs.update { (it + "[rtc] $line").takeLast(MAX_LOG_ENTRIES) }
                }
            }
        } catch (_: Throwable) {
            // Mobile not initialized yet
        }
    }

    private const val MAX_LOG_ENTRIES = 1_000
    private const val TAG = "OlcboxVpnService"
}
