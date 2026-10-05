package dev.shutterclick.core

enum class CameraState(val wire: String) {
    READY("ready"), CLOSED("camera_closed"), LOCKED("phone_locked"),
    UNSUPPORTED("unsupported_camera"), PERMISSION("permission_missing"),
    WRONG_MODE("photo_mode_required"), INACTIVE("session_inactive")
}

data class CameraSnapshot(val state: CameraState, val context: String = "")

/** Must durably claim the key before returning true. Failure must return false. */
fun interface ClaimStore { fun claim(key: String): Boolean }

/**
 * Runs on one thread. Readiness is a short-lived capability, never a queued command.
 * Both clocks and the camera are injected so expiry and duplicate behavior are testable.
 */
class RemoteEngine(
    private val now: () -> Long,
    private val newToken: () -> String,
    private val claims: ClaimStore,
    private val snapshot: () -> CameraSnapshot,
    private val click: (expectedContext: String) -> Boolean
) {
    companion object {
        const val VERSION = 1
        const val LEASE_MS = 8_000L
        const val IDLE_MS = 10 * 60_000L
    }
    private data class Lease(val client: String, val context: String, val expiry: Long)
    private val leases = linkedMapOf<String, Lease>()
    private var session: String? = null
    private var client: String? = null
    private var lastCapture = 0L
    private var lastContext: String? = null
    val active: Boolean get() = session != null && now() - lastCapture < IDLE_MS

    fun start() {
        session = newToken()
        lastCapture = now()
        client = null
        invalidate()
    }

    fun stop() { session = null; client = null; invalidate() }
    fun invalidate() { leases.clear(); lastContext = null }

    fun handle(message: Map<*, *>): Map<String, Any>? {
        if ((message["v"] as? Number)?.toDouble() != VERSION.toDouble()) return null
        val type = message["type"] as? String ?: return null
        val from = field(message, "client") ?: return null
        val id = field(message, "id") ?: return null
        if (type == "bye") {
            if (from == client) { client = null; invalidate() }
            return null
        }
        if (type != "hello" && type != "capture") return null
        val response = linkedMapOf<String, Any>(
            "v" to VERSION, "type" to if (type == "hello") "status" else "result",
            "client" to from, "id" to id
        )
        if (!active) {
            stop()
            return response.apply { put("state", CameraState.INACTIVE.wire) }
        }
        if (type == "hello") {
            if (client != from) { client = from; invalidate() }
            val camera = snapshot()
            if (camera.state != CameraState.READY || camera.context.isBlank()) {
                invalidate()
                val state = if (camera.state == CameraState.READY) CameraState.UNSUPPORTED else camera.state
                return response.apply { put("state", state.wire) }
            }
            if (lastContext != camera.context) invalidate()
            lastContext = camera.context
            leases.entries.removeAll { it.value.expiry <= now() }
            while (leases.size >= 8) leases.remove(leases.keys.first())
            val token = newToken()
            leases[token] = Lease(from, camera.context, now() + LEASE_MS)
            return response.apply {
                put("state", "ready"); put("token", token); put("ttl", LEASE_MS.toInt())
            }
        }
        fun result(state: String) = response.apply { put("state", state) }
        val token = field(message, "token") ?: return result("stale")
        val lease = leases.remove(token) ?: return result("stale")
        if (from != client || lease.client != from || lease.expiry <= now()) return result("stale")
        val camera = snapshot()
        if (camera.state != CameraState.READY) {
            invalidate()
            return result(camera.state.wire)
        }
        if (camera.context != lease.context) { invalidate(); return result("stale") }
        val key = "$session/$from/$id"
        if (!claims.claim(key)) return result("duplicate_or_storage_error")
        // Re-read the camera inside click as well; window/fold changes must never be guessed.
        invalidate()
        lastCapture = now()
        return result(if (click(lease.context)) "accepted" else "click_failed")
    }

    private fun field(message: Map<*, *>, key: String): String? =
        (message[key] as? String)?.takeIf { it.isNotBlank() && it.length <= 96 }
}
