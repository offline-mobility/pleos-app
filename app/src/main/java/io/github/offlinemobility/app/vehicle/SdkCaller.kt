package io.github.offlinemobility.app.vehicle

import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Turns one callback-style Pleos SDK call into a suspending call. Every SDK call is IPC, so each is
 * given a timeout to keep the app responsive when the vehicle service does not answer; a null
 * value from the SDK counts as a failure.
 */
internal class SdkCaller(private val ready: () -> Boolean) {
    suspend fun <T> call(
        timeoutMs: Long = CALL_TIMEOUT_MS,
        block: (done: (T?) -> Unit, fail: (Exception) -> Unit) -> Unit,
    ): Result<T> {
        if (!ready()) return Result.failure(IllegalStateException("Vehicle SDK가 초기화되지 않았습니다"))
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Result<T>> { cont ->
                runCatching {
                    block(
                        { value ->
                            cont.resumeOnce(
                                if (value != null) Result.success(value)
                                else Result.failure(IllegalStateException("차량 서비스가 빈 값을 반환했습니다")),
                            )
                        },
                        { error -> cont.resumeOnce(Result.failure(error)) },
                    )
                }.onFailure { cont.resumeOnce(Result.failure(it)) }
            }
        }
        return result ?: Result.failure(IllegalStateException("차량 서비스 응답 시간 초과"))
    }

    /** Result of a capability check, or null when the vehicle does not let the app write it. */
    suspend fun <V : Any> writable(
        check: (ok: (CapabilityConfig, V) -> Unit, fail: (Exception) -> Unit) -> Unit,
    ): V? = call<Pair<CapabilityConfig, V>> { done, fail -> check({ config, value -> done(config to value) }, fail) }
        .getOrNull()
        ?.takeIf { it.first.accessType == AccessType.WRITE || it.first.accessType == AccessType.READ_WRITE }
        ?.second

    private fun <T> CancellableContinuation<T>.resumeOnce(value: T) {
        if (isActive) resume(value)
    }

    private companion object {
        const val CALL_TIMEOUT_MS = 2_000L
    }
}
