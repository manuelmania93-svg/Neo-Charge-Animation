package com.redmagic.neocharge.animation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class ChargingService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    val chargeType = getChargeType(context)
                    showOverlay(chargeType)
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

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        // Required syntax for Android 13/14+ to prevent security crashes
        ContextCompat.registerReceiver(
            this,
            powerReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        startForeground(1, createNotification())
    }

    private fun getChargeType(context: Context?): String {
        val batteryIntent = context?.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1

        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "FAST CHARGE"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS CHARGE"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB CHARGE"
            else -> "CHARGING"
        }
    }

    private fun showOverlay(chargeLabel: String) {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val customView = HexagonOverlayView(this, chargeLabel)
        overlayView = customView

        customView.setOnClickListener { removeOverlay() }
        windowManager.addView(customView, params)

        // Dismiss after 7.5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            removeOverlay()
        }, 7500)
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
        val channelId = "neocharge_listener"
        val channel = NotificationChannel(channelId, "NeoCharge Service", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NeoCharge Active")
            .setContentText("Monitoring charging events")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .build()
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        unregisterReceiver(powerReceiver)
        super.onDestroy()
    }
}
