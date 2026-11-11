package io.github.offlinemobility.app

import android.app.Application

class OfflineMobilityApp : Application() {
    /** One engine per process, shared by the screen and the background service. */
    val engine: SignalEngine by lazy { SignalEngine(this) }
}
