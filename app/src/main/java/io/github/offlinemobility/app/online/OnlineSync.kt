package io.github.offlinemobility.app.online

import android.content.Context
import io.github.offlinemobility.app.core.online.OfficialAlert
import io.github.offlinemobility.app.core.online.OnlineJson
import io.github.offlinemobility.app.core.online.RemoteIncident
import io.github.offlinemobility.app.core.online.Shelter
import io.github.offlinemobility.app.core.protocol.Geo
import io.github.offlinemobility.app.debugLog
import io.github.offlinemobility.app.location.Fix
import io.github.offlinemobility.app.net.ServerClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class OnlineState(
    val alerts: List<OfficialAlert> = emptyList(),
    val shelters: List<Shelter> = emptyList(),
    /** When the shelter list was downloaded; it keeps working offline from the on-device copy. */
    val sheltersFetchedAtMs: Long? = null,
    /** True when the data came from a development server's sample set. */
    val sample: Boolean = false,
    val incidents: List<RemoteIncident> = emptyList(),
)

/**
 * While a network is available: official disaster messages for the area, shelters around the car
 * (saved on the device for use after the network goes down) and rescue requests other cars uploaded.
 *
 * Only a coarse position (two decimals, about 1 km) is sent to the server.
 */
class OnlineSync(
    context: Context,
    private val client: ServerClient,
    private val scope: CoroutineScope,
    private val isOnline: () -> Boolean,
    private val position: () -> Fix?,
) {
    private val cacheFile = File(context.filesDir, "shelters.json")
    private val _state = MutableStateFlow(OnlineState())
    val state: StateFlow<OnlineState> = _state.asStateFlow()

    private var shelterCenter: Pair<Double, Double>? = null
    private var lastAlertsMs = 0L

    fun start() {
        scope.launch {
            withContext(Dispatchers.IO) { loadShelterCache() }
            if (!client.enabled) return@launch
            while (true) {
                val fix = position()
                val ready = isOnline() && fix != null
                if (ready && fix != null) {
                    val now = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        if (now - lastAlertsMs >= ALERTS_EVERY_MS) refreshAlerts(fix, now)
                        if (sheltersStale(fix, now)) refreshShelters(fix, now)
                        refreshIncidents(fix)
                    }
                }
                // Until the first position and connection are known, check again soon.
                delay(if (ready) TICK_MS else WAIT_MS)
            }
        }
    }

    private fun coarse(value: Double) = String.format(Locale.ROOT, "%.2f", value)

    private fun at(fix: Fix) = mapOf("lat" to coarse(fix.latitude), "lon" to coarse(fix.longitude))

    private fun refreshAlerts(fix: Fix, now: Long) {
        val result = client.get("/api/alerts", at(fix))?.let(OnlineJson::alerts) ?: return
        lastAlertsMs = now
        debugLog { "alerts ${result.source} ${result.items.size}" }
        _state.update { it.copy(alerts = result.items, sample = it.sample || result.source == "sample") }
    }

    private fun sheltersStale(fix: Fix, now: Long): Boolean {
        val center = shelterCenter ?: return true
        val fetched = _state.value.sheltersFetchedAtMs ?: return true
        val moved = Geo.offset(center.first, center.second, fix.latitude, fix.longitude).meters
        return moved > REFETCH_MOVE_M || now - fetched > SHELTER_MAX_AGE_MS
    }

    private fun refreshShelters(fix: Fix, now: Long) {
        val body = client.get("/api/shelters", at(fix) + ("km" to SHELTER_RADIUS_KM)) ?: return
        val result = OnlineJson.shelters(body) ?: return
        debugLog { "shelters ${result.source} ${result.items.size}" }
        if (result.source == "unconfigured") return
        shelterCenter = fix.latitude to fix.longitude
        _state.update { it.copy(shelters = result.items, sheltersFetchedAtMs = now, sample = it.sample || result.source == "sample") }
        runCatching {
            val wrapper = JSONObject()
                .put("fetchedAt", now)
                .put("lat", fix.latitude)
                .put("lon", fix.longitude)
                .put("response", JSONObject(body))
            val tmp = File(cacheFile.parentFile, "shelters.tmp")
            tmp.writeText(wrapper.toString())
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.delete()
                tmp.renameTo(cacheFile)
            }
        }
    }

    private fun refreshIncidents(fix: Fix) {
        val result = client.get("/api/reports/nearby", at(fix) + ("km" to INCIDENT_RADIUS_KM))?.let(OnlineJson::incidents) ?: return
        _state.update { it.copy(incidents = result) }
    }

    private fun loadShelterCache() {
        if (!cacheFile.exists()) return
        runCatching {
            val wrapper = JSONObject(cacheFile.readText())
            val result = OnlineJson.shelters(wrapper.getJSONObject("response").toString()) ?: return
            shelterCenter = wrapper.getDouble("lat") to wrapper.getDouble("lon")
            _state.update {
                it.copy(shelters = result.items, sheltersFetchedAtMs = wrapper.getLong("fetchedAt"), sample = result.source == "sample")
            }
            debugLog { "shelter cache ${result.items.size}" }
        }
    }

    private companion object {
        const val TICK_MS = 30_000L
        const val WAIT_MS = 3_000L
        const val ALERTS_EVERY_MS = 120_000L
        const val REFETCH_MOVE_M = 2_000.0
        const val SHELTER_MAX_AGE_MS = 24 * 3_600_000L
        const val SHELTER_RADIUS_KM = "10"
        const val INCIDENT_RADIUS_KM = "10"
    }
}
