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

    fun show(onDismissed: () -> Unit) {
        if (isAttached || overlayView != null) return

        triggerHapticPulse()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val view = HexagonOverlayView(context) {
            dismiss()
            onDismissed.invoke()
        }

        overlayView = view

        try {
            windowManager.addView(view, params)
            isAttached = true
        } catch (e: Exception) {
            e.printStackTrace()
            isAttached = false
            overlayView = null
        }
    }

    fun updateTelemetry(state: TelemetryState) {
        if (isAttached) {
            overlayView?.submitTelemetry(state)
        }
    }

    fun dismiss() {
        if (!isAttached || overlayView == null) return

        try {
            overlayView?.let {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            isAttached = false
        }
    }

    private fun triggerHapticPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 40, 60, 40)
                val amplitudes = intArrayOf(0, 200, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
