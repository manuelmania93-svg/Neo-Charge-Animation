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

class HexagonOverlayView(context: Context, private val chargeLabel: String) : View(context) {

    private val density = resources.displayMetrics.density
    private var basePercent: Int = getLiveStatusbarBattery()
    private var decimalFraction: Float = 0.16f

    private var tickerAnimator: ValueAnimator? = null

    // Live listener: The instant your status bar ticks 97% -> 98%, this fires immediately
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            if (level > 0 && level != basePercent) {
                basePercent = level
                invalidate()
            }
        }
    }

    // Reusable paths & paints for smooth 60fps performance
    private val hexPath = Path()
    private val lightningPath = Path()
    private val radialDimPaint = Paint().apply { isAntiAlias = true }

    private val hexRimPaint = Paint().apply {
        color = Color.parseColor("#1A1116")
        style = Paint.Style.STROKE
        strokeWidth = 9f * density
        isAntiAlias = true
        pathEffect = CornerPathEffect(14f * density)
    }

    private val hexBorderPaint = Paint().apply {
        color = Color.parseColor("#33FF1E38")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        isAntiAlias = true
        pathEffect = CornerPathEffect(12f * density)
    }

    private val cyanGlowPaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        isAntiAlias = true
        setShadowLayer(10f * density, 0f, 0f, Color.parseColor("#00B0FF"))
    }

    private val magentaGlowPaint = Paint().apply {
        color = Color.parseColor("#FF0055")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        isAntiAlias = true
        setShadowLayer(10f * density, 0f, 0f, Color.parseColor("#FF1744"))
    }

    private val lightningCorePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        isAntiAlias = true
    }

    private val laserPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        isAntiAlias = true
    }

    private val laserGlowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        isAntiAlias = true
        color = Color.parseColor("#4400E5FF")
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(16f * density, 0f, 0f, Color.parseColor("#AA000000"))
    }

    private val labelPaint = Paint().apply {
        color = Color.parseColor("#FFCC00")
        textSize = 13f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        letterSpacing = 0.2f
        setShadowLayer(8f * density, 0f, 0f, Color.parseColor("#FF9100"))
    }

    // Direct hardware query (reads directly from /sys/class/power_supply)
    private fun getLiveStatusbarBattery(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val hardwareCapacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (hardwareCapacity in 1..100) {
            return hardwareCapacity
        }

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 97
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return ((level.toFloat() / scale.toFloat()) * 100f).toInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Always refresh immediately when drawn on screen
        basePercent = getLiveStatusbarBattery()

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

        // Active decimal ticker loop (.00 to .99)
        tickerAnimator = ValueAnimator.ofFloat(0.00f, 0.99f).apply {
            duration = 3800
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

        // 1. Subtle AMOLED background vignette
        radialDimPaint.shader = RadialGradient(
            cx, cy, radius * 1.6f,
            intArrayOf(Color.parseColor("#CC08080C"), Color.TRANSPARENT),
            floatArrayOf(0.45f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.6f, radialDimPaint)

        // 2. Compute 6 hexagon corners
        val verticesX = FloatArray(6)
        val verticesY = FloatArray(6)
        hexPath.reset()
        for (i in 0 until 6) {
            val angle = (Math.PI / 3 * i) - (Math.PI / 2)
            val x = (cx + radius * cos(angle)).toFloat()
            val y = (cy + radius * sin(angle)).toFloat()
            verticesX[i] = x
            verticesY[i] = y
            if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
        }
        hexPath.close()

        // 3. Draw Hexagon Rim Frames
        canvas.drawPath(hexPath, hexRimPaint)
        canvas.drawPath(hexPath, hexBorderPaint)

        // 4. Draw Electric Lightning Arcs (Cyan on left, Magenta on right)
        drawLightningEdge(canvas, verticesX[3], verticesY[3], verticesX[4], verticesY[4], cyanGlowPaint)
        drawLightningEdge(canvas, verticesX[4], verticesY[4], verticesX[5], verticesY[5], cyanGlowPaint)
        drawLightningEdge(canvas, verticesX[5], verticesY[5], verticesX[0], verticesY[0], cyanGlowPaint)

        drawLightningEdge(canvas, verticesX[0], verticesY[0], verticesX[1], verticesY[1], magentaGlowPaint)
        drawLightningEdge(canvas, verticesX[1], verticesY[1], verticesX[2], verticesY[2], magentaGlowPaint)
        drawLightningEdge(canvas, verticesX[2], verticesY[2], verticesX[3], verticesY[3], magentaGlowPaint)

        // 5. Vertical Laser Line to Fingerprint Reader
        val bottomTipX = verticesX[3]
        val bottomTipY = verticesY[3]
        val fingerprintTargetY = height * 0.81f

        laserPaint.shader = LinearGradient(
            bottomTipX, bottomTipY, bottomTipX, fingerprintTargetY,
            intArrayOf(Color.parseColor("#FF0055"), Color.parseColor("#00E5FF"), Color.TRANSPARENT),
            floatArrayOf(0.0f, 0.7f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawLine(bottomTipX, bottomTipY + (4f * density), bottomTipX, fingerprintTargetY, laserGlowPaint)
        canvas.drawLine(bottomTipX, bottomTipY + (4f * density), bottomTipX, fingerprintTargetY, laserPaint)

        // 6. Draw Exact Synchronized 4-digit percentage (e.g. 97.16%)
        val totalPercentage = basePercent.toFloat() + decimalFraction
        val formattedPercent = String.format(Locale.US, "%05.2f%%", totalPercentage)
        canvas.drawText(formattedPercent, cx, cy + (6f * density), textPaint)

        // 7. Draw MAX CHARGE
        canvas.drawText(chargeLabel, cx, cy + (34f * density), labelPaint)
    }

    private fun drawLightningEdge(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, glowPaint: Paint) {
        lightningPath.reset()
        lightningPath.moveTo(x1, y1)

        val segments = 4
        val dx = (x2 - x1) / segments
        val dy = (y2 - y1) / segments
        val nx = -dy
        val ny = dx
        val len = sqrt(nx * nx + ny * ny)

        for (s in 1 until segments) {
            val progressX = x1 + dx * s
            val progressY = y1 + dy * s
            val jitter = (Random.nextFloat() - 0.5f) * (18f * density)
            lightningPath.lineTo(progressX + (nx / len) * jitter, progressY + (ny / len) * jitter)
        }
        lightningPath.lineTo(x2, y2)

        canvas.drawPath(lightningPath, glowPaint)
        canvas.drawPath(lightningPath, lightningCorePaint)
    }
}
