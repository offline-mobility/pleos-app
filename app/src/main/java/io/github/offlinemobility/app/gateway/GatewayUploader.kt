package io.github.offlinemobility.app.gateway

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.github.offlinemobility.app.core.gateway.UploadJson
import io.github.offlinemobility.app.debugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.offlinemobility.app.net.ServerClient
import java.util.UUID

data class GatewayStatus(
    val enabled: Boolean,
    val online: Boolean = false,
    val pending: Int = 0,
    val lastUploadMs: Long? = null,
    val lastUploadedKeys: Set<String> = emptySet(),
)

/**
 * When any validated internet connection appears (cellular comes back, Wi-Fi, satellite), sends the
 * stored rescue messages to the rescue server.
 *
 * - HTTPS only (see [ServerClient]).
 * - No secret is embedded in the app. The server identifies submissions by a random per-install
 *   reporter id and must validate and rate-limit them (docs/GATEWAY_API.md).
 * - Disabled while no server is configured.
 */
class GatewayUploader(
    context: Context,
    private val outbox: RescueOutbox,
    private val client: ServerClient,
    private val scope: CoroutineScope,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val prefs = context.getSharedPreferences("gateway", Context.MODE_PRIVATE)
    private val reporterId: String = prefs.getString(KEY_REPORTER, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_REPORTER, it).apply() }

    private val enabled = client.enabled
    private val _status = MutableStateFlow(GatewayStatus(enabled = enabled, pending = outbox.pending().size))
    val status: StateFlow<GatewayStatus> = _status.asStateFlow()

    private var uploadJob: Job? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _status.value = _status.value.copy(online = true)
            flush()
        }

        override fun onLost(network: Network) {
            _status.value = _status.value.copy(online = false)
        }
    }

    fun start() {
        if (!enabled || connectivity == null) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        runCatching { connectivity.registerNetworkCallback(request, callback) }
    }

    /** Call after adding to the outbox; uploads right away when online. */
    fun onQueued() {
        _status.value = _status.value.copy(pending = outbox.pending().size)
        if (_status.value.online) flush()
    }

    private fun flush() {
        if (!enabled || uploadJob?.isActive == true) return
        uploadJob = scope.launch {
            var backoff = 5_000L
            while (_status.value.online) {
                val batch = outbox.pending().take(BATCH)
                if (batch.isEmpty()) break
                val ok = withContext(Dispatchers.IO) { client.postJson("/api/reports", UploadJson.body(reporterId, batch)) }
                debugLog { "gateway upload ${batch.size} → $ok" }
                if (ok) {
                    val keys = batch.map { it.key }.toSet()
                    outbox.remove(keys)
                    _status.value = _status.value.copy(
                        pending = outbox.pending().size,
                        lastUploadMs = System.currentTimeMillis(),
                        lastUploadedKeys = _status.value.lastUploadedKeys + keys,
                    )
                    backoff = 5_000L
                } else {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(5 * 60_000L)
                }
            }
        }
    }

    private companion object {
        const val KEY_REPORTER = "reporter_id"
        const val BATCH = 50
    }
}
