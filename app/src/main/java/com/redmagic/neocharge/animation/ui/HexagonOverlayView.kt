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
    private val innerHexPath = Path()
    private val wavePath1 = Path()
    private val wavePath2 = Path()
    private val radialDimPaint = Paint().apply { isAntiAlias = true }

    // Solid Cyber-Hexagon Structural Rim (Sharp Definition)
    private val hexRimPaint = Paint().apply {
        color = Color.parseColor("#330508") // Deep cyber-titanium rim
        style = Paint.Style.STROKE
        strokeWidth = 7f * density
        isAntiAlias = true
        pathEffect = CornerPathEffect(14f * density)
    }

    // High-Contrast Neon Red Cyber Frame
    private val hexBorderPaint = Paint().apply {
        color = Color.parseColor("#FF0033")
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        isAntiAlias = true
        pathEffect = CornerPathEffect(12f * density)
        setShadowLayer(14f * density, 0f, 0f, Color.parseColor("#FF0033"))
    }

    // Outer Electric Red Plasma (Controlled Jitter)
    private val redGlowPaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.STROKE
        strokeWidth = 3.2f * density
        isAntiAlias = true
        setShadowLayer(12f * density, 0f, 0f, Color.parseColor("#FF0033"))
    }

    // Secondary Chaotic Crackle
    private val redArcPaint = Paint().apply {
        color = Color.parseColor("#FF5252")
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        isAntiAlias = true
    }

    // Hot-White Plasma Core
    private val whiteCorePaint = Paint().apply {
        color = Color.parseColor("#FFF5F5")
        style = Paint.Style.STROKE
        strokeWidth = 1.1f * density
        isAntiAlias = true
    }

    // Fingerprint Laser Guide (Tapered Soft Beam)
    private val laserPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
        isAntiAlias = true
    }

    private val laserGlowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.0f * density
        isAntiAlias = true
        color = Color.parseColor("#44FF0033")
    }

    // 4-Digit Live Percentage Text
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(20f * density, 0f, 0f, Color.parseColor("#FF0033"))
    }

    // MAX CHARGE Badge
    private val labelPaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        textSize = 12f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        letterSpacing = 0.18f
        setShadowLayer(8f * density, 0f, 0f, Color.parseColor("#FF0033"))
    }

    // Telemetry: Watts & Temp
    private val telemetryPaint = Paint().apply {
        color = Color.parseColor("#FFAAA0")
        textSize = 11f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        letterSpacing = 0.15f
    }

    init {
        setOnClickListener { onDismissRequest.invoke() }
    }

    fun submitTelemetry(state: TelemetryState) {
        this.telemetryState = state
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height * 0.50f
        val radius = min(width, height) * 0.25f

        // 1. Dark red ambient aura
        radialDimPaint.shader = RadialGradient(
            cx, cy, radius * 1.65f,
            intArrayOf(Color.parseColor("#E6120205"), Color.parseColor("#4D0A0103"), Color.TRANSPARENT),
            floatArrayOf(0.40f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.65f, radialDimPaint)

        // 2. Compute 6 hexagon corners
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

        // 3. Draw Clean Structural Cyber-Hexagon
        canvas.drawPath(hexPath, hexRimPaint)
        canvas.drawPath(hexPath, hexBorderPaint)

        // 4. Draw Electric Lightning Arcs (Tightly wrapping the perimeter)
        val isFull = telemetryState.basePercent >= 100
        val jitterMagnitude = if (isFull) 3f * density else 8.5f * density

        for (i in 0 until 6) {
            val next = (i + 1) % 6
            drawTightElectricalWave(canvas, vx[i], vy[i], vx[next], vy[next], jitterMagnitude)
        }

        // 5. Vertical Red Laser Guide (Soft downward gradient to fingerprint sensor)
        val bottomTipX = vx[3]
        val bottomTipY = vy[3]
        val fingerprintTargetY = height * 0.81f

        laserPaint.shader = LinearGradient(
            bottomTipX, bottomTipY, bottomTipX, fingerprintTargetY,
            intArrayOf(Color.parseColor("#FF0033"), Color.parseColor("#80FF1744"), Color.TRANSPARENT),
            floatArrayOf(0.0f, 0.60f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawLine(bottomTipX, bottomTipY + (3f * density), bottomTipX, fingerprintTargetY, laserGlowPaint)
        canvas.drawLine(bottomTipX, bottomTipY + (3f * density), bottomTipX, fingerprintTargetY, laserPaint)

        // 6. Synchronized 4-Digit Display Percentage
        val formattedPercent = String.format(Locale.US, "%05.2f%%", telemetryState.displayPercentage)
        canvas.drawText(formattedPercent, cx, cy + (4f * density), textPaint)

        // 7. Title Badge
        canvas.drawText("\u26A1 ${telemetryState.chargeGrade}", cx, cy + (26f * density), labelPaint)

        // 8. Live Watts & Temperature Telemetry
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

        // Wave 1: Primary electrical arc hugging the edge
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

        // Wave 2: Micro crackle spark
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
