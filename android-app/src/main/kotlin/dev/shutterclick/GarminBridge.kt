package dev.shutterclick

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GarminBridge(
    private val context: Context,
    private val changed: () -> Unit,
    private val disconnected: () -> Unit,
    private val receive: (Map<*, *>) -> Map<String, Any>?
) {
    companion object { const val APP_ID = "8d675184594c4f4a8d185d1e6a6e0aa7" }
    private val main = Handler(Looper.getMainLooper())
    private val preferences = context.getSharedPreferences("setup", Context.MODE_PRIVATE)
    private var sdk: ConnectIQ? = null
    private var starting = false
    private var ready = false
    @Volatile private var generation = 0
    @Volatile private var selectionEpoch = 0
    private var simulatorSender: ExecutorService? = null
    private var selectedApp: IQApp? = null
    private var replySequence = 0L
    private var latestReplyOutcome = 0L
    val devices = mutableListOf<IQDevice>()
    var selected: IQDevice? = null; private set
    var appInstalled = false; private set
    var detail = "Connect to Garmin"; private set
    val connected: Boolean get() = ready && selected?.status == IQDevice.IQDeviceStatus.CONNECTED

    fun connect() {
        if (starting || ready) return
        starting = true
        detail = if (BuildConfig.CIQ_SIMULATOR) "Connecting to Garmin simulator over ADB…"
            else "Connecting to Garmin Connect…"
        changed()
        val current = ++generation
        try {
            if (BuildConfig.CIQ_SIMULATOR && simulatorSender == null) {
                simulatorSender = Executors.newSingleThreadExecutor { task ->
                    Thread(task, "GarminSimulatorReplies")
                }
            }
            val transport = if (BuildConfig.CIQ_SIMULATOR) ConnectIQ.IQConnectType.TETHERED
                else ConnectIQ.IQConnectType.WIRELESS
            sdk = ConnectIQ.getInstance(context, transport)
            sdk!!.initialize(context, false, object : ConnectIQ.ConnectIQListener {
                override fun onSdkReady() = post(current) {
                    if (BuildConfig.DEBUG) Log.d("ShutterClickBridge", "SDK ready")
                    starting = false; ready = true; refresh()
                }
                override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) = post(current) {
                    if (BuildConfig.DEBUG) Log.d("ShutterClickBridge", "SDK initialization: ${status.name}")
                    starting = false; ready = false; appInstalled = false
                    detail = "Open Garmin Connect and pair your watch. SDK: ${status.name}"
                    disconnected(); changed()
                }
                override fun onSdkShutDown() = post(current) {
                    starting = false; ready = false; appInstalled = false
                    detail = "Garmin Connect unavailable"; disconnected(); changed()
                }
            })
        } catch (error: Exception) { failed(error) }
    }

    fun refresh() {
        if (!ready) { connect(); return }
        try {
            devices.clear()
            devices.addAll(sdk!!.knownDevices.orEmpty())
            devices.forEach { it.status = sdk!!.getDeviceStatus(it) }
            if (BuildConfig.DEBUG) Log.d("ShutterClickBridge", "Known watches: ${devices.size}; states=${devices.map { it.status.name }}")
            val saved = preferences.getLong("device", -1)
            val choice = devices.firstOrNull { it.deviceIdentifier == saved }
                ?: devices.singleOrNull()
                ?: devices.filter { it.friendlyName.orEmpty().contains("955") }.singleOrNull()
            if (choice != null) select(choice)
            else {
                selectionEpoch++; sdk!!.unregisterAllForEvents()
                selected = null; selectedApp = null; appInstalled = false; disconnected()
                detail = "Choose your Forerunner from Garmin Connect"; changed()
            }
        } catch (error: Exception) { failed(error) }
    }

    fun select(device: IQDevice) {
        if (!ready) return
        val selection = ++selectionEpoch
        disconnected()
        try {
            sdk!!.unregisterAllForEvents()
            selected = device
            if (BuildConfig.DEBUG) Log.d("ShutterClickBridge", "Selected watch: ${device.status.name}")
            selectedApp = null
            appInstalled = false
            preferences.edit().putLong("device", device.deviceIdentifier).apply()
            val current = generation
            sdk!!.registerForDeviceEvents(device) { _, status -> postSelection(current, selection) {
                device.status = status
                if (status != IQDevice.IQDeviceStatus.CONNECTED) {
                    selectedApp = null; appInstalled = false; disconnected()
                } else checkApplication(device, current, selection)
                detail = if (connected) "Watch connected" else "Watch disconnected"
                changed()
            } }
            val listener = ConnectIQ.IQApplicationEventListener { from, _, messages, status ->
                postSelection(current, selection) {
                    if (BuildConfig.DEBUG) Log.d("ShutterClickBridge", "Watch event: ${status.name}, selected=${from.deviceIdentifier == selected?.deviceIdentifier}, connected=$connected")
                    if (from.deviceIdentifier != selected?.deviceIdentifier || !connected) return@postSelection
                    if (status != ConnectIQ.IQMessageStatus.SUCCESS) return@postSelection
                    messages.orEmpty().forEach { payload ->
                        (payload as? Map<*, *>)?.let {
                            if (BuildConfig.DEBUG) Log.d("ShutterClickBridge", "Received watch ${it["type"]}")
                            receive(it)?.let(::send)
                        }
                    }
                }
            }
            sdk!!.registerForAppEvents(device, IQApp(APP_ID), listener)
            // Companion SDK 2.4.0 routes tethered messages under an empty app ID.
            // This additional listener is restricted to the local simulator build.
            if (BuildConfig.CIQ_SIMULATOR) sdk!!.registerForAppEvents(device, IQApp(""), listener)
            if (connected) checkApplication(device, current, selection)
            else detail = "Watch disconnected"
            changed()
        } catch (error: Exception) { failed(error) }
    }

    private fun checkApplication(device: IQDevice, current: Int, selection: Int) {
        if (BuildConfig.CIQ_SIMULATOR) {
            // Simulator metadata is not proof of a physical app installation.
            // A valid hello/readiness exchange is still required before any capture.
            appInstalled = connected
            selectedApp = IQApp(APP_ID)
            detail = if (connected) "Simulator connected" else "Simulator disconnected"
            changed()
            return
        }
        try {
            sdk!!.getApplicationInfo(APP_ID, device, object : ConnectIQ.IQApplicationInfoListener {
                override fun onApplicationInfoReceived(app: IQApp) = postSelection(current, selection) {
                    if (!connected) return@postSelection
                    appInstalled = app.status == IQApp.IQAppStatus.INSTALLED
                    selectedApp = if (appInstalled) app else null
                    if (BuildConfig.DEBUG) Log.d("ShutterClickBridge", "Installed app: ${app.status}")
                    detail = if (appInstalled) {
                        if (connected) "Watch connected" else "Watch disconnected"
                    } else "Install Shutter Click on the watch"
                    changed()
                }
                override fun onApplicationNotInstalled(applicationId: String) = postSelection(current, selection) {
                    selectedApp = null; appInstalled = false; detail = "Install Shutter Click on the watch"; changed()
                }
            })
        } catch (error: Exception) { failed(error) }
    }

    private fun send(message: Map<String, Any>) {
        val device = selected ?: return
        val current = generation
        val selection = selectionEpoch
        val connection = sdk ?: return
        val app = selectedApp ?: return
        val reply = ++replySequence
        val deliver = {
            try {
                if (BuildConfig.DEBUG) Log.d("ShutterClickBridge", "Reply ${message["type"]}: ${message["state"]}, installed=${app.status}")
                connection.sendMessage(device, app, message) { _, _, status ->
                    if (BuildConfig.DEBUG) Log.d("ShutterClickBridge", "Phone reply: ${status.name}")
                    postSelection(current, selection) {
                        if (reply < latestReplyOutcome) return@postSelection
                        latestReplyOutcome = reply
                        if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
                            detail = "Watch reply failed: ${status.name}"
                            disconnected(); changed()
                        } else if (detail.startsWith("Watch reply failed:")) {
                            detail = if (BuildConfig.CIQ_SIMULATOR) "Simulator connected" else "Watch connected"
                            changed()
                        }
                    }
                }
            } catch (error: Exception) {
                postSelection(current, selection) { failed(error) }
            }
        }
        if (BuildConfig.CIQ_SIMULATOR) {
            // TETHERED writes its socket synchronously; keep it off Android's UI thread.
            simulatorSender?.execute {
                if (generation == current && selectionEpoch == selection) deliver()
            }
        } else deliver()
    }

    fun close() {
        generation++
        starting = false; ready = false; appInstalled = false
        selected = null; selectedApp = null; devices.clear()
        simulatorSender?.shutdownNow(); simulatorSender = null
        try { sdk?.unregisterAllForEvents(); sdk?.shutdown(context) } catch (_: Exception) {}
        sdk = null; detail = "Remote stopped"; changed()
    }

    private fun post(expected: Int, action: () -> Unit) {
        main.post { if (generation == expected) action() }
    }
    private fun postSelection(expected: Int, selection: Int, action: () -> Unit) =
        post(expected) { if (selectionEpoch == selection) action() }
    private fun failed(error: Exception) {
        if (BuildConfig.DEBUG) Log.d("ShutterClickBridge", "Bridge exception: ${error.javaClass.simpleName}")
        detail = "Garmin connection unavailable: ${error.javaClass.simpleName}"
        appInstalled = false; starting = false
        disconnected(); changed()
    }
}
