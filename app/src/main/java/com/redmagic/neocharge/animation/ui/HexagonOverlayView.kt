package com.redmagic.neocharge.animation.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.redmagic.neocharge.animation.core.TelemetryState
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

class HexagonOverlayView(
    context: Context,
    private val onDismissRequest: () -> Unit = {}
) : View(context) {

    private val density = resources.displayMetrics.density
    private var telemetryState = TelemetryState()

    private val hexPath = Path()
    private val wavePath1 = Path()
    private val wavePath2 = Path()
    private val radialDimPaint = Paint().apply { isAntiAlias = true }

    // Outer Dark Red Rim Frame

cat << 'EOF' > app/src/main/java/com/redmagic/neocharge/animation/service/OverlayController.kt
package com.redmagic.neocharge.animation.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import com.redmagic.neocharge.animation.core.TelemetryState
import com.redmagic.neocharge.animation.ui.HexagonOverlayView

class OverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private var overlayView: HexagonOverlayView? = null
    private var isAttached = false

    fun isShowing(): Boolean = isAttached



cat << 'EOF' > app/src/main/java/com/redmagic/neocharge/animation/service/ChargingService.kt
package com.redmagic.neocharge.animation.service

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.redmagic.neocharge.animation.core.BatteryHardwareProvider
import com.redmagic.neocharge.animation.core.BatteryPhysicsIntegrator

class ChargingService : Service() {

    private lateinit var hardwareProvider: BatteryHardwareProvider
    private lateinit var physicsIntegrator: BatteryPhysicsIntegrator
    private lateinit var overlayController: OverlayController
    private var wakeLock: PowerManager.WakeLock? = null

    private val tickerHandler = Handler(Looper.getMainLooper())
    private var lastFrameTime = System.currentTimeMillis()
    private var isPreviewSession = false

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    wakeUpScreen()
                    startChargingSession(isPreview = false)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    stopChargingSession()
                }
            }
        }
    }

    // 60FPS physics loop & heartbeat disconnect guard
    private val telemetryLoop = object : Runnable {
        override fun run() {
            if (!overlayController.isShowing()) return

            val now = System.currentTimeMillis()
            val deltaSec = (now - lastFrameTime) / 1000f
            lastFrameTime = now

            // Unplug Guard: if not in preview and phone physically disconnected, kill immediately
            if (!isPreviewSession && !hardwareProvider.isChargerPhysicallyConnected()) {
                stopChargingSession()
                return
            }

            val rawData = hardwareProvider.readHardwareTelemetry(null)
            val computedState = physicsIntegrator.update(rawData, deltaSec.coerceIn(0.016f, 0.1f))

            overlayController.updateTelemetry(computedState)
            tickerHandler.postDelayed(this, 16L) // ~60 FPS
        }
    }

    override fun onCreate() {
        super.onCreate()
        hardwareProvider = BatteryHardwareProvider(this)
        physicsIntegrator = BatteryPhysicsIntegrator()
        overlayController = OverlayController(this)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "neocharge:wake_guard"
        )

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        ContextCompat.registerReceiver(
            this,
            powerReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "PREVIEW_ANIMATION") {
            wakeUpScreen()
            startChargingSession(isPreview = true)
        }
        return START_STICKY
    }

    private fun wakeUpScreen() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(4000)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startChargingSession(isPreview: Boolean) {
        if (overlayController.isShowing()) return

        this.isPreviewSession = isPreview
        physicsIntegrator.reset()
        lastFrameTime = System.currentTimeMillis()

        overlayController.show(onDismissed = {
            stopChargingSession()
        })

        tickerHandler.post(telemetryLoop)

        // Previews auto-close after 6 seconds
        if (isPreview) {
            tickerHandler.postDelayed({ stopChargingSession() }, 6000L)
        }
    }

    private fun stopChargingSession() {
        tickerHandler.removeCallbacks(telemetryLoop)
        overlayController.dismiss()
        physicsIntegrator.reset()
        isPreviewSession = false
    }

    private fun createNotification(): Notification {
        val channelId = "neocharge_engine"
        val channel = NotificationChannel(channelId, "NeoCharge Engine", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NeoCharge Active")
            .setContentText("Hardware Telemetry Engine Armed")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .build()
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        stopChargingSession()
        unregisterReceiver(powerReceiver)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
