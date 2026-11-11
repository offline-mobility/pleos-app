package io.github.offlinemobility.app

import android.util.Log

/**
 * Debug-only logging. Frames carry location and message content, so nothing is written to logcat
 * in release builds; the message lambda is not even evaluated there.
 */
internal inline fun debugLog(message: () -> String) {
    if (BuildConfig.DEBUG) Log.d("OfflineMobility", message())
}
