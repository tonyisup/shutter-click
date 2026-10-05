package dev.shutterclick.core

import org.junit.Test
import kotlin.test.*

class RemoteEngineTest {
    private class Fixture {
        var time = 100L
        var serial = 0
        var camera = CameraSnapshot(CameraState.READY, "window-a/display-0/button-1")
        var clicks = 0
        var actionSucceeds = true
        var storageAvailable = true
        val claimed = mutableSetOf<String>()
        val engine = RemoteEngine({ time }, { "token-${++serial}" },
            ClaimStore { storageAvailable && claimed.add(it) },
            { camera }, { clicks++; actionSucceeds }).apply { start() }
        fun hello(client: String = "watch-a", id: String = "query-1") =
            engine.handle(mapOf("v" to 1, "type" to "hello", "client" to client, "id" to id))!!
        fun capture(token: String, id: String = "shot-1", client: String = "watch-a") =
            engine.handle(mapOf("v" to 1, "type" to "capture", "client" to client, "id" to id, "token" to token))!!
        fun token() = hello()["token"] as String
    }
    @Test fun readyRequestActivatesOnce() {
        val f = Fixture()
        assertEquals("accepted", f.capture(f.token())["state"])
        assertEquals(1, f.clicks)
    }
    @Test fun backgroundReadinessRenewalKeepsCurrentLeaseUsableForOneCapture() {
        val f = Fixture(); val current = f.token()
        f.time += 3_000
        val renewed = f.hello(id = "background-query")["token"] as String
        assertEquals("accepted", f.capture(current)["state"])
        assertEquals("stale", f.capture(renewed, id = "second-shot")["state"])
        assertEquals(1, f.clicks)
    }
    @Test fun duplicatePacketNeverActivatesTwice() {
        val f = Fixture(); val token = f.token()
        f.capture(token)
        assertEquals("stale", f.capture(token)["state"])
        assertEquals(1, f.clicks)
    }
    @Test fun duplicateRequestWithFreshTokenIsRejected() {
        val f = Fixture()
        f.capture(f.token())
        assertEquals("duplicate_or_storage_error", f.capture(f.token())["state"])
        assertEquals(1, f.clicks)
    }
    @Test fun expiryBoundaryRejectsDelayedCommand() {
        val f = Fixture(); val token = f.token()
        f.time += RemoteEngine.LEASE_MS
        assertEquals("stale", f.capture(token)["state"])
        assertEquals(0, f.clicks)
    }
    @Test fun cameraOrFoldChangeInvalidatesRequest() {
        val f = Fixture(); val token = f.token()
        f.camera = CameraSnapshot(CameraState.READY, "window-b/display-1/button-2")
        assertEquals("stale", f.capture(token)["state"])
        assertEquals(0, f.clicks)
    }
    @Test fun disconnectAndReconnectCannotReplay() {
        val f = Fixture(); val token = f.token()
        f.engine.invalidate()
        f.hello()
        assertEquals("stale", f.capture(token)["state"])
        assertEquals(0, f.clicks)
    }
    @Test fun everyUnavailableCameraStatePreventsReadinessAndClick() {
        for (state in CameraState.entries.filter { it != CameraState.READY }) {
            val f = Fixture(); val token = f.token()
            f.camera = CameraSnapshot(state)
            assertEquals(state.wire, f.capture(token)["state"])
            assertNull(f.hello()["token"])
            assertEquals(0, f.clicks)
        }
    }
    @Test fun emptyContextCannotBecomeReady() {
        val f = Fixture(); f.camera = CameraSnapshot(CameraState.READY)
        assertEquals("unsupported_camera", f.hello()["state"])
        assertNull(f.hello()["token"])
    }
    @Test fun endingSessionPreventsQueuedClick() {
        val f = Fixture(); val token = f.token()
        f.engine.stop()
        assertEquals("session_inactive", f.capture(token)["state"])
        assertEquals(0, f.clicks)
    }
    @Test fun newSessionRejectsOldCapabilities() {
        val f = Fixture(); val token = f.token()
        f.engine.start()
        assertEquals("stale", f.capture(token)["state"])
        assertEquals(0, f.clicks)
    }
    @Test fun differentWatchInstanceCannotReuseToken() {
        val f = Fixture(); val token = f.token()
        assertEquals("stale", f.capture(token, client = "watch-b")["state"])
        assertEquals(0, f.clicks)
    }
    @Test fun failedDurableClaimPreventsTheAction() {
        val f = Fixture(); f.storageAvailable = false
        assertEquals("duplicate_or_storage_error", f.capture(f.token())["state"])
        assertEquals(0, f.clicks)
    }
    @Test fun failedClickCannotBeAutomaticallyRepeated() {
        val f = Fixture(); f.actionSucceeds = false
        assertEquals("click_failed", f.capture(f.token())["state"])
        assertEquals("duplicate_or_storage_error", f.capture(f.token())["state"])
        assertEquals(1, f.clicks)
    }
    @Test fun watchExitInvalidatesOutstandingCapabilities() {
        val f = Fixture(); val token = f.token()
        f.engine.handle(mapOf("v" to 1, "type" to "bye", "client" to "watch-a", "id" to "exit-1"))
        assertEquals("stale", f.capture(token)["state"])
        assertEquals(0, f.clicks)
    }
    @Test fun pollingDoesNotExtendIdleSessionForever() {
        val f = Fixture()
        f.time += RemoteEngine.IDLE_MS
        assertEquals("session_inactive", f.hello()["state"])
        assertFalse(f.engine.active)
    }
    @Test fun malformedPacketsAreIgnored() {
        val f = Fixture()
        assertNull(f.engine.handle(emptyMap<String, Any>()))
        assertNull(f.engine.handle(mapOf("v" to 2, "type" to "hello", "client" to "watch", "id" to "query")))
        assertNull(f.engine.handle(mapOf("v" to 1.5, "type" to "hello", "client" to "watch", "id" to "query")))
        assertNull(f.engine.handle(mapOf("v" to 1, "type" to "capture", "client" to "watch", "id" to "")))
        assertEquals(0, f.clicks)
    }
}
