package com.redmagic.neocharge.animation.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.redmagic.neocharge.animation.core.BatteryHardwareProvider
import com.redmagic.neocharge.animation.core.TelemetryState
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

class HexagonOverlayView(
    context: Context,
    private val onDismissRequest: () -> Unit = {}
) : View(context) {

    private val density = resources.displayMetrics.density
    private var telemetryState: TelemetryState

    private val hexPath = Path()
    private val wavePath1 = Path()
    private val wavePath2 = Path()
    private val radialDimPaint = Paint().apply { isAntiAlias = true }

    private var pulseAngle = 0.0

    private val hexRimPaint = Paint().apply {
        color = Color.parseColor("#330508")
        style = Paint.Style.STROKE
        strokeWidth = 7f * density
        isAntiAlias = true
        pathEffect = CornerPathEffect(14f * density)
    }

    private val hexBorderPaint = Paint().apply {
        color = Color.parseColor("#FF0033")
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        isAntiAlias = true
        pathEffect = CornerPathEffect(12f * density)
        setShadowLayer(14f * density, 0f, 0f, Color.parseColor("#FF0033"))
    }

    private val redGlowPaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.STROKE
        strokeWidth = 3.2f * density
        isAntiAlias = true
        setShadowLayer(12f * density, 0f, 0f, Color.parseColor("#FF0033"))
    }

    private val redArcPaint = Paint().apply {
        color = Color.parseColor("#FF5252")
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        isAntiAlias = true
    }

    private val whiteCorePaint = Paint().apply {
        color = Color.parseColor("#FFF5F5")
        style = Paint.Style.STROKE
        strokeWidth = 1.1f * density
        isAntiAlias = true
    }

    private val laserPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
        isAntiAlias = true
    }

    private val laserGlowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.0f * density
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(20f * density, 0f, 0f, Color.parseColor("#FF0033"))
    }

    private val labelPaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        textSize = 12f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        letterSpacing = 0.18f
        setShadowLayer(8f * density, 0f, 0f, Color.parseColor("#FF0033"))
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
        // Initialize immediately with the phone's actual battery level
        val initialPercent = BatteryHardwareProvider(context).getLiveStatusbarPercent()
        telemetryState = TelemetryState(
            basePercent = initialPercent,
            displayPercentage = initialPercent.toFloat()
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val cx = width / 2f
            val cy = height * 0.50f
            val dx = event.x - cx
            val dy = event.y - cy
            val dist = sqrt(dx * dx + dy * dy)
            val radius = min(width, height) * 0.35f

            if (dist <= radius) {
                onDismissRequest.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun submitTelemetry(state: TelemetryState) {
        this.telemetryState = state
        pulseAngle += 0.08
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height * 0.50f
        val radius = min(width, height) * 0.25f

        radialDimPaint.shader = RadialGradient(
            cx, cy, radius * 1.65f,
            intArrayOf(Color.parseColor("#E6120205"), Color.parseColor("#4D0A0103"), Color.TRANSPARENT),
            floatArrayOf(0.40f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.65f, radialDimPaint)

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

        val isFull = telemetryState.basePercent >= 100
        val jitterMagnitude = if (isFull) 2.5f * density else 8.5f * density

        for (i in 0 until 6) {
            val next = (i + 1) % 6
            drawTightElectricalWave(canvas, vx[i], vy[i], vx[next], vy[next], jitterMagnitude)
        }

        val bottomTipX = vx[3]
        val bottomTipY = vy[3]
        val fingerprintTargetY = height * 0.81f

        val pulseAlpha = (0.7f + 0.3f * sin(pulseAngle).toFloat()).coerceIn(0.4f, 1.0f)
        val laserColor = Color.argb((255 * pulseAlpha).toInt(), 255, 0, 51)
        val glowColor = Color.argb((90 * pulseAlpha).toInt(), 255, 0, 51)

        laserPaint.shader = LinearGradient(
            bottomTipX, bottomTipY, bottomTipX, fingerprintTargetY,
            intArrayOf(laserColor, Color.parseColor("#50FF1744"), Color.TRANSPARENT),
            floatArrayOf(0.0f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )
        laserGlowPaint.color = glowColor

        canvas.drawLine(bottomTipX, bottomTipY + (3f * density), bottomTipX, fingerprintTargetY, laserGlowPaint)
        canvas.drawLine(bottomTipX, bottomTipY + (3f * density), bottomTipX, fingerprintTargetY, laserPaint)

        val formattedPercent = String.format(Locale.US, "%05.2f%%", telemetryState.displayPercentage)
        canvas.drawText(formattedPercent, cx, cy + (4f * density), textPaint)

        canvas.drawText("\u26A1 ${telemetryState.chargeGrade}", cx, cy + (26f * density), labelPaint)

        val wattText = if (telemetryState.watts > 0f) String.format(Locale.US, "%.1fW", telemetryState.watts) else "--W"
        val tempText = if (telemetryState.tempCelsius > 0f) String.format(Locale.US, "%.1f\u00B0C", telemetryState.tempCelsius) else "--\u00B0C"
        canvas.drawText("$wattText  \u2022  $tempText", cx, cy + (44f * density), telemetryPaint)
    }

    private fun drawTightElectricalWave(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, maxJitter: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        val nx = -dy
        val ny = dx
        val len = sqrt(nx * nx + ny * ny)

        wavePath1.reset()
        wavePath1.moveTo(x1, y1)
        val segs1 = 5
        for (s in 1 until segs1) {
            val px = x1 + (dx / segs1) * s
            val py = y1 + (dy / segs1) * s
            val jitter = (Random.nextFloat() - 0.5f) * maxJitter
            wavePath1.lineTo(px + (nx / len) * jitter, py + (ny / len) * jitter)
        }
        wavePath1.lineTo(x2, y2)

        canvas.drawPath(wavePath1, redGlowPaint)
        canvas.drawPath(wavePath1, redArcPaint)
        canvas.drawPath(wavePath1, whiteCorePaint)

        wavePath2.reset()
        wavePath2.moveTo(x1, y1)
        val segs2 = 3
        for (s in 1 until segs2) {
            val px = x1 + (dx / segs2) * s
            val py = y1 + (dy / segs2) * s
            val jitter = (Random.nextFloat() - 0.5f) * (maxJitter * 0.6f)
            wavePath2.lineTo(px + (nx / len) * jitter, py + (ny / len) * jitter)
        }
        wavePath2.lineTo(x2, y2)
        canvas.drawPath(wavePath2, redArcPaint)
    }
}
