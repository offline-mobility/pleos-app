package io.github.offlinemobility.app.vehicle

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import io.github.offlinemobility.app.debugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RouteRequest { NONE, ASKED, STARTED, FAILED }

/**
 * Hands a destination to the car's own navigation through the Pleos NaviHelper SDK, so the way to
 * a shelter is shown on the map the driver already uses.
 *
 * Required manifest permission: pleos.car.permission.NAVI_ROUTE_SEARCH.
 */
class CarNavigator(context: Context) {
    private val navi = NaviHelper(context.applicationContext)
    private var ready = false

    private val _request = MutableStateFlow(RouteRequest.NONE)
    val request: StateFlow<RouteRequest> = _request.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) {
            debugLog { "navi route started" }
            _request.value = RouteRequest.STARTED
        }

        override fun onError(code: NaviErrorCode) {
            debugLog { "navi error $code" }
            _request.value = RouteRequest.FAILED
        }
    }

    fun routeTo(name: String, latitude: Double, longitude: Double, address: String) {
        _request.value = RouteRequest.ASKED
        val destination = RouteInfo(latitude = latitude, longitude = longitude, poiName = name, poiId = "", address = address, poiSubId = "")
        runCatching {
            if (ready) {
                navi.requestRoute(destination)
            } else {
                navi.initialize(
                    {
                        ready = true
                        navi.addListener(listener)
                        runCatching { navi.requestRoute(destination) }.onFailure(::failed)
                    },
                    ::failed,
                )
            }
        }.onFailure(::failed)
    }

    fun clear() {
        _request.value = RouteRequest.NONE
    }

    private fun failed(error: Throwable) {
        debugLog { "navi request failed: ${error.message}" }
        _request.value = RouteRequest.FAILED
    }
}
