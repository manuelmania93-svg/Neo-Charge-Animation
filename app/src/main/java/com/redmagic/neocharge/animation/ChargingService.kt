package com.redmagic.neocharge.animation

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class ChargingService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val prefs by lazy { getSharedPreferences("neocharge_prefs", Context.MODE_PRIVATE) }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    wakeUpScreen()
                    val chargeType = getChargeType(context)
                    showLockscreenAnimation(chargeType)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    removeOverlay()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "neocharge:lockscreen_wake"
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
            showLockscreenAnimation("⚡ MAX CHARGE")
        }
        return START_STICKY
    }

    private fun wakeUpScreen() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(5000) // Wakes screen for 5s
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getChargeType(context: Context?): String {
        val batteryIntent = context?.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1

        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "⚡ MAX CHARGE"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "⚡ WIRELESS TURBO"
            BatteryManager.BATTERY_PLUGGED_USB -> "⚡ USB CHARGING"
            else -> "⚡ CHARGING"
        }
    }

    private fun showLockscreenAnimation(chargeLabel: String) {
        if (overlayView != null) return

        // Flags to mount directly over the Keyguard / Lock Screen
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // Allows fingerprint touches to pass through
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val customView = HexagonOverlayView(this, chargeLabel)
        overlayView = customView

        // Tapping the animation dismisses it so you can unlock anytime
        customView.setOnClickListener { removeOverlay() }
        windowManager.addView(customView, params)

        val isPermanent = prefs.getBoolean("perm_mode", true)
        if (!isPermanent) {
            Handler(Looper.getMainLooper()).postDelayed({
                removeOverlay()
            }, 8000)
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
            overlayView = null
        }
    }

    private fun createNotification(): Notification {
        val channelId = "neocharge_service"
        val channel = NotificationChannel(channelId, "NeoCharge Engine", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NeoCharge Lockscreen Active")
            .setContentText("Ready for charging events")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .build()
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        unregisterReceiver(powerReceiver)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
