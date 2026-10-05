package dev.shutterclick

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val remote get() = (application as ShutterApplication).remote
    private lateinit var status: TextView
    private lateinit var watch: Button
    private lateinit var permissions: Button
    private lateinit var accessibility: Button
    private lateinit var start: Button
    private lateinit var camera: Button
    private lateinit var end: Button
    private val observer: () -> Unit = { runOnUiThread { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
            setBackgroundColor(Color.rgb(245, 245, 240))
        }
        scroll.addView(layout)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        fun text(value: String, size: Float): TextView = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.rgb(23, 33, 27))
            setPadding(0, dp(8), 0, dp(14)); layout.addView(this)
        }
        fun button(value: String, action: () -> Unit): Button = Button(this).apply {
            text = value; isAllCaps = false; minHeight = dp(52)
            layout.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            setOnClickListener { action() }
        }
        text("Shutter Click", 30f)
        text("Your Pixel Camera. One press from your Forerunner.", 17f)
        if (BuildConfig.CIQ_SIMULATOR) text("Simulator build · ADB connection", 14f)
        status = text("", 16f)
        watch = button("Choose watch") {
            val choices = remote.garmin.devices.toList()
            if (choices.isEmpty()) { remote.garmin.refresh(); return@button }
            AlertDialog.Builder(this).setTitle("Choose your Forerunner")
                .setItems(choices.map { it.friendlyName.orEmpty() }.toTypedArray()) { _, index ->
                    remote.garmin.select(choices[index])
                }.show()
        }
        button("Refresh Garmin connection") { remote.garmin.refresh() }
        permissions = button("Allow nearby devices and notifications") {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        accessibility = button("Enable camera control") { discloseAndOpenAccessibility() }
        start = button("Start remote") {
            try { startForegroundService(Intent(this, RemoteService::class.java)) }
            catch (_: Exception) { status.setText(R.string.remote_start_failed) }
        }
        camera = button("Open Pixel Camera") {
            try {
                startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .setPackage(CameraControlService.CAMERA_PACKAGE))
            } catch (_: Exception) { status.setText(R.string.camera_open_failed) }
        }
        end = button("End remote") { stopService(Intent(this, RemoteService::class.java)) }
        text("Keep the phone unlocked with Pixel Camera visible in Photo mode. On the watch, press START or tap SHOOT. BACK exits the watch app.", 15f)
        text("The first camera profile recognizes English photo-shutter labels. Unknown controls stay disabled until verified. If Android Advanced Protection blocks the Accessibility service, this approach cannot operate under that setting.", 14f)
        setContentView(scroll)
    }
    override fun onResume() {
        super.onResume()
        remote.addObserver(observer)
        remote.garmin.connect()
        render()
    }
    override fun onPause() { remote.removeObserver(observer); super.onPause() }

    private fun render() {
        val allowed = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
            .all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val enabled = CameraControlService.current != null
        status.text = buildString {
            append(remote.garmin.detail)
            append("\nCamera control: ${if (enabled) "enabled" else "needs setup"}")
            append("\nRemote: ${if (remote.running) "active" else "off"}")
        }
        watch.text = remote.garmin.selected?.friendlyName?.let { "Watch: $it" } ?: "Choose watch"
        watch.isEnabled = !remote.running
        permissions.visibility = if (allowed) View.GONE else View.VISIBLE
        accessibility.text = if (enabled) "Camera control settings" else "Enable camera control"
        start.isEnabled = allowed && enabled && remote.garmin.connected && remote.garmin.appInstalled && !remote.running
        camera.isEnabled = remote.running
        end.visibility = if (remote.running) View.VISIBLE else View.GONE
    }
    private fun discloseAndOpenAccessibility() {
        AlertDialog.Builder(this).setTitle("Enable camera control")
            .setMessage("Android Accessibility lets Shutter Click read Pixel Camera controls and press its visible photo shutter when you request a photo from your watch. It operates only during a remote session. Shutter Click does not store screen contents or read your photos.\n\nYou can disable the service at any time in Android Accessibility settings.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean("disclosure", true).apply()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }.show()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); render()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
