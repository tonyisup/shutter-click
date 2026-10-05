package dev.shutterclick

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
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
    private lateinit var notice: TextView
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
        }
        scroll.addView(layout)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        fun text(value: Int?, size: Float): TextView = TextView(this).apply {
            value?.let(::setText); textSize = size; setTextColor(getColor(R.color.text_primary))
            setPadding(0, dp(8), 0, dp(14)); layout.addView(this)
        }
        fun button(value: Int, action: () -> Unit): Button = Button(this).apply {
            setText(value); isAllCaps = false; minHeight = dp(52)
            layout.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            setOnClickListener { action() }
        }
        text(R.string.app_name, 30f).isAccessibilityHeading = true
        text(R.string.tagline, 17f)
        if (BuildConfig.CIQ_SIMULATOR) text(R.string.simulator_build, 14f)
        // TalkBack announces setup progress without moving focus.
        status = text(null, 16f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        notice = text(null, 16f)
        watch = button(R.string.choose_watch) {
            val choices = remote.garmin.devices.toList()
            if (choices.isEmpty()) { remote.garmin.refresh(); return@button }
            AlertDialog.Builder(this).setTitle(R.string.choose_watch_title)
                .setItems(choices.map { it.friendlyName.orEmpty() }.toTypedArray()) { _, index ->
                    remote.garmin.select(choices[index])
                }.show()
        }
        button(R.string.refresh_garmin) { remote.garmin.refresh() }
        permissions = button(R.string.allow_permissions) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        accessibility = button(R.string.enable_camera_control) { discloseAndOpenAccessibility() }
        start = button(R.string.start_remote) {
            try { startForegroundService(Intent(this, RemoteService::class.java)) }
            catch (_: Exception) { status.setText(R.string.remote_start_failed) }
        }
        camera = button(R.string.open_camera) {
            try {
                startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .setPackage(CameraControlService.CAMERA_PACKAGE))
            } catch (_: Exception) { status.setText(R.string.camera_open_failed) }
        }
        end = button(R.string.end_remote) { stopService(Intent(this, RemoteService::class.java)) }
        text(R.string.help_use, 15f)
        text(R.string.help_limits, 14f)
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
        val paired = remote.garmin.connected && remote.garmin.appInstalled
        val summary = listOf(
            remote.garmin.detail,
            getString(if (enabled) R.string.status_camera_control_enabled else R.string.status_camera_control_needed),
            getString(if (remote.running) R.string.status_remote_active else R.string.status_remote_off),
            getString(when {
                remote.running -> R.string.next_running
                !allowed -> R.string.next_permissions
                !enabled -> R.string.next_camera_control
                !paired -> R.string.next_watch
                else -> R.string.next_start
            })
        ).joinToString("\n")
        // Unchanged text must not re-trigger the live region on every watch message.
        if (status.text.toString() != summary) status.text = summary
        // Pixel Camera labels follow the phone language, not this app's own resources.
        val english = Resources.getSystem().configuration.locales[0].language == "en"
        val warnings = listOfNotNull(
            if (cameraInstalled()) null else getString(R.string.notice_no_pixel_camera),
            if (english) null else getString(R.string.notice_english_only)
        )
        notice.visibility = if (warnings.isEmpty()) View.GONE else View.VISIBLE
        notice.text = warnings.joinToString("\n\n")
        watch.text = remote.garmin.selected?.friendlyName?.let { getString(R.string.selected_watch, it) }
            ?: getString(R.string.choose_watch)
        watch.isEnabled = !remote.running
        permissions.visibility = if (allowed) View.GONE else View.VISIBLE
        accessibility.setText(if (enabled) R.string.camera_control_settings else R.string.enable_camera_control)
        start.isEnabled = allowed && enabled && paired && !remote.running
        camera.isEnabled = remote.running
        end.visibility = if (remote.running) View.VISIBLE else View.GONE
    }
    private fun discloseAndOpenAccessibility() {
        AlertDialog.Builder(this).setTitle(R.string.disclosure_title)
            .setMessage(R.string.disclosure_message)
            .setNegativeButton(R.string.disclosure_cancel, null)
            .setPositiveButton(R.string.disclosure_continue) { _, _ ->
                getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean("disclosure", true).apply()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }.show()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); render()
    }
    private fun cameraInstalled() = try {
        packageManager.getPackageInfo(CameraControlService.CAMERA_PACKAGE, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
