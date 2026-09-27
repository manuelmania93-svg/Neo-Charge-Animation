package com.redmagic.neocharge.animation

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

class HexagonOverlayView @JvmOverloads constructor(
    context: Context,
    private val onUnplugged: () -> Unit = {}
) : View(context) {

    private val density = resources.displayMetrics.density
    private var basePercent: Int = 88
    private var decimalFraction: Float = 0.00f

    // Live Metrics
    private var liveWatts: Float = 0f
    private var liveTempC: Float = 0f
    private var chargeTitle: String = "CHARGING"

    private var tickerAnimator: ValueAnimator? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent == null) return

            // 1. Instantly dismiss when cable is unplugged
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            if (status != BatteryManager.BATTERY_STATUS_CHARGING && plugged == 0) {
                onUnplugged.invoke()
                return
            }

            // 2. Sync Real Battery Level
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            if (level > 0 && level != basePercent) {
                basePercent = level
                decimalFraction = 0.00f
                restartTicker()
            }

            // 3. Live Temperature
            val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            liveTempC = tempRaw / 10.0f

            // 4. Live Wattage & Fast Charge Title
            updatePowerMetrics(intent, plugged)
            invalidate()
        }
    }

    private val hexPath = Path()
    private val wavePath1 = Path()
    private val wavePath2 = Path()
    private val radialDimPaint = Paint().apply { isAntiAlias = true }

    private val hexRimPaint = Paint().apply {
        color = Color.parseColor("#260407")
        style = Paint.Style.STROKE
        strokeWidth = 9f * density
        isAntiAlias = true
        pathEffect = CornerPathEffect(14f * density)
    }

    private val hexBorderPaint = Paint().apply {
        color = Color.parseColor("#44FF0033")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        isAntiAlias = true
        pathEffect = CornerPathEffect(12f * density)
    }

    private val redGlowPaint = Paint().apply {
        color = Color.parseColor("#FF0033")
        style = Paint.Style.STROKE
        strokeWidth = 4.2f * density
        isAntiAlias = true
        setShadowLayer(18f * density, 0f, 0f, Color.parseColor("#FF1744"))
    }

    private val redArcPaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        isAntiAlias = true
        setShadowLayer(10f * density, 0f, 0f, Color.parseColor("#FF5252"))
    }

    private val whiteCorePaint = Paint().apply {
        color = Color.parseColor("#FFF0F2")
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        isAntiAlias = true
    }

    private val laserPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
        isAntiAlias = true
    }

    private val laserGlowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f * density
        isAntiAlias = true
        color = Color.parseColor("#66FF0033")
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(22f * density, 0f, 0f, Color.parseColor("#FF0033"))
    }

    private val labelPaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        textSize = 12f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        letterSpacing = 0.18f
        setShadowLayer(10f * density, 0f, 0f, Color.parseColor("#FF0033"))
    }

    private val telemetryPaint = Paint().apply {
        color = Color.parseColor("#FFAAA0")
        textSize = 11f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        letterSpacing = 0.15f
    }

    init {
        basePercent = queryLiveBattery()
    }

    private fun queryLiveBattery(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val hardwareCapacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (hardwareCapacity in 1..100) return hardwareCapacity

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 88) ?: 88
    }

    private fun updatePowerMetrics(intent: Intent, plugged: Int) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentUa = abs(bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0)
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000)

        if (currentUa > 0 && voltageMv > 0) {
            val amps = currentUa / 1000000f
            val volts = voltageMv / 1000f
            liveWatts = volts * amps
        }

        chargeTitle = when {
            liveWatts >= 30f || plugged == BatteryManager.BATTERY_PLUGGED_AC -> "MAX CHARGE"
            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS TURBO"
            else -> "FAST CHARGE"
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        basePercent = queryLiveBattery()

        try {
            ContextCompat.registerReceiver(
                context,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        restartTicker()
    }

    private fun restartTicker() {
        tickerAnimator?.cancel()
        tickerAnimator = ValueAnimator.ofFloat(0.00f, 0.99f).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                decimalFraction = it.animatedValue as Float
                invalidate()
            }
        }
        tickerAnimator?.start()
    }

    override fun onDetachedFromWindow() {
        tickerAnimator?.cancel()
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height * 0.50f
        val radius = min(width, height) * 0.25f

        // 1. Dark red plasma ambient aura behind the hexagon
        radialDimPaint.shader = RadialGradient(
            cx, cy, radius * 1.65f,
            intArrayOf(Color.parseColor("#E6120205"), Color.parseColor("#4D0A0103"), Color.TRANSPARENT),
            floatArrayOf(0.40f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.65f, radialDimPaint)

        // 2. Compute 6 hexagon vertices
        val vx = FloatArray(6)
        val vy = FloatArray(6)
        hexPath.reset()
        for (i in 0 until 6) {
            val angle = (Math.PI / 3.0 * i) - (Math.PI / 2.0)
            val x = (cx + radius * cos(angle)).toFloat()
            val y = (cy + radius * sin(angle)).toFloat()
            vx[i] = x
            vy[i] = y
            if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
        }
        hexPath.close()

        canvas.drawPath(hexPath, hexRimPaint)
        canvas.drawPath(hexPath, hexBorderPaint)

        // 3. Draw All-Red Crazy Electrical Wave
        for (i in 0 until 6) {
            val next = (i + 1) % 6
            drawCrazyElectricalWave(canvas, vx[i], vy[i], vx[next], vy[next])
        }

        // 4. Vertical Red Laser Guide Line
        val bottomTipX = vx[3]
        val bottomTipY = vy[3]
        val fingerprintTargetY = height * 0.81f

        laserPaint.shader = LinearGradient(
            bottomTipX, bottomTipY, bottomTipX, fingerprintTargetY,
            intArrayOf(Color.parseColor("#FF0033"), Color.parseColor("#FF1744"), Color.TRANSPARENT),
            floatArrayOf(0.0f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawLine(bottomTipX, bottomTipY + (4f * density), bottomTipX, fingerprintTargetY, laserGlowPaint)
        canvas.drawLine(bottomTipX, bottomTipY + (4f * density), bottomTipX, fingerprintTargetY, laserPaint)

        // 5. Synchronized Percentage (Base + Ticker)
        val currentDisplay = basePercent.toFloat() + decimalFraction
        val formattedPercent = String.format(Locale.US, "%05.2f%%", currentDisplay)
        canvas.drawText(formattedPercent, cx, cy + (4f * density), textPaint)

        // 6. Charging Mode Title
        canvas.drawText("\u26A1 $chargeTitle", cx, cy + (26f * density), labelPaint)

        // 7. Live Telemetry: Watts & Temperature (Using safe unicode escapes)
        val wattText = if (liveWatts > 0f) String.format(Locale.US, "%.1fW", liveWatts) else "--W"
        val tempText = if (liveTempC > 0f) String.format(Locale.US, "%.1f\u00B0C", liveTempC) else "--\u00B0C"
        canvas.drawText("$wattText  \u2022  $tempText", cx, cy + (44f * density), telemetryPaint)
    }

    private fun drawCrazyElectricalWave(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        val nx = -dy
        val ny = dx
        val len = sqrt(nx * nx + ny * ny)

        wavePath1.reset()
        wavePath1.moveTo(x1, y1)
        val segs1 = 6
        for (s in 1 until segs1) {
            val px = x1 + (dx / segs1) * s
            val py = y1 + (dy / segs1) * s
            val jitter = (Random.nextFloat() - 0.5f) * (26f * density)
            wavePath1.lineTo(px + (nx / len) * jitter, py + (ny / len) * jitter)
        }
        wavePath1.lineTo(x2, y2)

        canvas.drawPath(wavePath1, redGlowPaint)
        canvas.drawPath(wavePath1, redArcPaint)
        canvas.drawPath(wavePath1, whiteCorePaint)

        wavePath2.reset()
        wavePath2.moveTo(x1, y1)
        val segs2 = 4
        for (s in 1 until segs2) {
            val px = x1 + (dx / segs2) * s
            val py = y1 + (dy / segs2) * s
            val jitter = (Random.nextFloat() - 0.5f) * (16f * density)
            wavePath2.lineTo(px + (nx / len) * jitter, py + (ny / len) * jitter)
        }
        wavePath2.lineTo(x2, y2)
        canvas.drawPath(wavePath2, redArcPaint)
    }
}
