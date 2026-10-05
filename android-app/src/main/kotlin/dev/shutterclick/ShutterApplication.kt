package dev.shutterclick

import android.app.Application
import android.content.Context
import android.os.SystemClock
import dev.shutterclick.core.CameraSnapshot
import dev.shutterclick.core.CameraState
import dev.shutterclick.core.ClaimStore
import dev.shutterclick.core.RemoteEngine
import java.util.UUID

class ShutterApplication : Application() {
    lateinit var remote: RemoteController; private set
    override fun onCreate() { super.onCreate(); remote = RemoteController(this) }
}

class RemoteController(context: Context) {
    private val claims = context.getSharedPreferences("claims", Context.MODE_PRIVATE)
    private val observers = mutableSetOf<() -> Unit>()
    var running = false; private set
    val engine = RemoteEngine(
        now = SystemClock::elapsedRealtime,
        newToken = { UUID.randomUUID().toString() },
        claims = ClaimStore { key ->
            !claims.contains(key) && claims.edit().putBoolean(key, true).commit()
        },
        snapshot = { CameraControlService.current?.snapshot() ?: CameraSnapshot(CameraState.PERMISSION) },
        click = { expected -> CameraControlService.current?.clickShutter(expected) == true }
    )
    val garmin = GarminBridge(context, ::notifyObservers, engine::invalidate) { message ->
        engine.handle(message).also { notifyObservers() }
    }

    fun begin() {
        // Restart never restores capabilities; old messages cannot survive a new session.
        if (!claims.edit().clear().commit()) error("Unable to initialize request storage")
        engine.start(); running = true; notifyObservers()
    }
    fun end() { engine.stop(); running = false; garmin.close(); notifyObservers() }
    fun cameraChanged() { engine.invalidate(); notifyObservers() }
    fun addObserver(observer: () -> Unit) { observers.add(observer) }
    fun removeObserver(observer: () -> Unit) { observers.remove(observer) }
    fun notifyObservers() { observers.toList().forEach { it() } }
}
