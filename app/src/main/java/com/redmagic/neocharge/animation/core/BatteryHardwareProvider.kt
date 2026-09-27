package com.redmagic.neocharge.animation.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

class BatteryHardwareProvider(private val context: Context) {

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    fun getLiveStatusbarPercent(): Int {
        val hardwareCapacity = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (hardwareCapacity in 1..100) return hardwareCapacity

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 88
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return ((level.toFloat() / scale.toFloat()) * 100f).toInt()
    }

    fun isChargerPhysicallyConnected(): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING
    }

    fun readHardwareTelemetry(batteryIntent: Intent?): RawTelemetry {
        val intent = batteryIntent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000) ?: 4000

        val rawCurrentUa = abs(batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0)

        var watts = 0f
        if (rawCurrentUa > 0 && voltageMv > 0) {
            val amps = rawCurrentUa / 1000000f
            val volts = voltageMv / 1000f
            watts = volts * amps
        }

        val grade = when {
            watts >= 28f || plugged == BatteryManager.BATTERY_PLUGGED_AC -> "MAX CHARGE"
            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS TURBO"
            else -> "FAST CHARGE"
        }

        return RawTelemetry(
            percent = getLiveStatusbarPercent(),
            watts = watts,
            tempCelsius = tempRaw / 10.0f,
            isPlugged = plugged != 0,
            grade = grade
        )
    }

    data class RawTelemetry(
        val percent: Int,
        val watts: Float,
        val tempCelsius: Float,
        val isPlugged: Boolean,
        val grade: String
    )
}
