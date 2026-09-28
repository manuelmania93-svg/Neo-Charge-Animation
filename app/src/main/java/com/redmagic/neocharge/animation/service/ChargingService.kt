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
    private var disconnectedCounter = 0

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    startChargingSession(isPreview = false)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    stopChargingSession()
                }
            }
        }
    }

    private val telemetryLoop = object : Runnable {
        override fun run() {
            if (!overlayController.isShowing()) return

            val now = System.currentTimeMillis()
            val deltaSec = (now - lastFrameTime) / 1000f
            lastFrameTime = now

            if (!isPreviewSession) {
                if (!hardwareProvider.isChargerPhysicallyConnected()) {
                    disconnectedCounter++
                    if (disconnectedCounter >= 6) {
                        stopChargingSession()
                        return
                    }
                } else {
                    disconnectedCounter = 0
                }
            }

            val rawData = hardwareProvider.readHardwareTelemetry(null)
            val computedState = physicsIntegrator.update(rawData, deltaSec.coerceIn(0.016f, 0.1f))

            overlayController.updateTelemetry(computedState)
            tickerHandler.postDelayed(this, 16L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        hardwareProvider = BatteryHardwareProvider(this)
        physicsIntegrator = BatteryPhysicsIntegrator(this) // Context injected for memory storage
        overlayController = OverlayController(this)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "neocharge:charging_stay_awake"
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
            startChargingSession(isPreview = true)
        }
        return START_STICKY
    }

    private fun startChargingSession(isPreview: Boolean) {
        if (overlayController.isShowing()) return

        this.isPreviewSession = isPreview
        this.disconnectedCounter = 0
        physicsIntegrator.reset()
        lastFrameTime = System.currentTimeMillis()

        acquireWakeLock()

        overlayController.show(onDismissed = {
            stopChargingSession()
        })

        tickerHandler.post(telemetryLoop)

        if (isPreview) {
            tickerHandler.postDelayed({ stopChargingSession() }, 6000L)
        }
    }

    private fun stopChargingSession() {
        tickerHandler.removeCallbacks(telemetryLoop)
        overlayController.dismiss()
        physicsIntegrator.reset()
        releaseWakeLock()
        isPreviewSession = false
        disconnectedCounter = 0
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        releaseWakeLock()
        super.onDestroy()
    }
}
