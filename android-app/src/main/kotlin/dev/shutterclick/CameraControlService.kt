package dev.shutterclick

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.shutterclick.core.CameraSnapshot
import dev.shutterclick.core.CameraState
import dev.shutterclick.core.PhotoShutter
import dev.shutterclick.core.ShutterCandidate

class CameraControlService : AccessibilityService() {
    companion object {
        const val CAMERA_PACKAGE = "com.google.android.GoogleCamera"
        var current: CameraControlService? = null; private set
    }
    private val remote get() = (application as ShutterApplication).remote
    override fun onServiceConnected() { current = this; remote.cameraChanged() }
    override fun onInterrupt() { remote.cameraChanged() }
    override fun onDestroy() { current = null; remote.cameraChanged(); super.onDestroy() }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig); remote.cameraChanged()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) remote.cameraChanged()
    }

    private data class Inspection(val snapshot: CameraSnapshot, val shutter: AccessibilityNodeInfo? = null)
    fun snapshot(): CameraSnapshot = inspect().snapshot

    fun clickShutter(expectedContext: String): Boolean {
        if (!remote.running || !remote.engine.active) return false
        val fresh = inspect()
        if (fresh.snapshot.state != CameraState.READY || fresh.snapshot.context != expectedContext) return false
        // Gesture fallback is deferred until it can be validated on the actual phone.
        return try { fresh.shutter?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
        catch (_: RuntimeException) { false }
    }

    private fun inspect(): Inspection = try { inspectActiveWindow() }
    catch (_: RuntimeException) { Inspection(CameraSnapshot(CameraState.UNSUPPORTED)) }

    private fun inspectActiveWindow(): Inspection {
        if (!getSharedPreferences("setup", MODE_PRIVATE).getBoolean("disclosure", false)) {
            return Inspection(CameraSnapshot(CameraState.PERMISSION))
        }
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        if (locked || !interactive) return Inspection(CameraSnapshot(CameraState.LOCKED))
        val root = rootInActiveWindow ?: return Inspection(CameraSnapshot(CameraState.CLOSED))
        if (root.packageName?.toString() != CAMERA_PACKAGE) return Inspection(CameraSnapshot(CameraState.CLOSED))
        val active = windows.firstOrNull { it.isActive }
        if (active == null || active.id != root.windowId) return Inspection(CameraSnapshot(CameraState.CLOSED))
        val nodes = mutableListOf<Pair<ShutterCandidate, AccessibilityNodeInfo>>()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 600) {
            val node = pending.removeFirst()
            if (node.packageName?.toString() == CAMERA_PACKAGE) {
                val bounds = Rect(); node.getBoundsInScreen(bounds)
                nodes += ShutterCandidate(
                    node.viewIdResourceName.orEmpty(), node.contentDescription?.toString().orEmpty(),
                    node.isVisibleToUser, node.isEnabled, node.isClickable,
                    bounds.left, bounds.top, bounds.right, bounds.bottom
                ) to node
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
        }
        // A truncated tree could hide another matching node.
        if (pending.isNotEmpty()) return Inspection(CameraSnapshot(CameraState.UNSUPPORTED))
        val selected = PhotoShutter.select(nodes.map { it.first })
        if (selected == null) {
            val video = nodes.any { it.first.visible && it.first.description.lowercase() in
                setOf("start video", "stop video", "start recording", "stop recording") }
            return Inspection(CameraSnapshot(if (video) CameraState.WRONG_MODE else CameraState.UNSUPPORTED))
        }
        val identity = "${root.windowId}/${active.displayId}/${selected.id}/" +
            "${selected.left},${selected.top},${selected.right},${selected.bottom}"
        return Inspection(CameraSnapshot(CameraState.READY, identity), nodes.single { it.first == selected }.second)
    }
}
