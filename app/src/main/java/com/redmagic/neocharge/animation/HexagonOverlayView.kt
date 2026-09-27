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

    // Live battery listener to keep it 100% synchronized with the status bar
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            if (level > 0 && level != basePercent) {
                basePercent = level
                invalidate()
            }
        }
    }

    private val hexPath = Path()
    private val primaryWavePath = Path()
    private val secondaryWavePath = Path()
    private val radialDimPaint = Paint().apply { isAntiAlias = true }

    // Outer Dark Red Rim Frame
    private val hexRimPaint = Paint().apply {
        color = Color.parseColor("#240407") // Deep void crimson
        style = Paint.Style.STROKE
        strokeWidth = 9f * density
        isAntiAlias = true
        pathEffect = CornerPathEffect(14f * density)
    }

    // Inner Hot Red Hexagon Outline
    private val hexBorderPaint = Paint().apply {
        color = Color.parseColor("#55FF0033")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        isAntiAlias = true
        pathEffect = CornerPathEffect(12f * density)
    }

    // Intense Red Electrical Plasma Outer Glow
    private val redOuterGlowPaint = Paint().apply {
        color = Color.parseColor("#FF0033") // Neon Electric Red
        style = Paint.Style.STROKE
        strokeWidth = 4.5f * density
        isAntiAlias = true
        setShadowLayer(18f * density, 0f, 0f, Color.parseColor("#FF1744"))
    }

    // Secondary Chaotic Red Spark Wave
    private val redArcPaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        isAntiAlias = true
        setShadowLayer(10f * density, 0f, 0f, Color.parseColor("#FF5252"))
    }

    // White-Hot Electrical Plasma Core
    private val lightningCorePaint = Paint().apply {
        color = Color.parseColor("#FFF0F2") // Ultra hot white-red core
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
        isAntiAlias = true
    }

    // Red Laser Tracer Guide to Fingerprint Sensor
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

    // Crisp 4-Digit Battery Text with Deep Red Ambient Glow
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(22f * density, 0f, 0f, Color.parseColor("#FF0033"))
    }

    // MAX CHARGE Badge (Electric Crimson Gold)
    private val labelPaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        textSize = 13f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        letterSpacing = 0.22f
        setShadowLayer(10f * density, 0f, 0f, Color.parseColor("#FF0033"))
    }

    private fun getLiveStatusbarBattery(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val hardwareCapacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (hardwareCapacity in 1..100) return hardwareCapacity

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 97
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return ((level.toFloat() / scale.toFloat()) * 100f).toInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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

        // 60FPS High-speed electrical plasma wave cycle
        tickerAnimator = ValueAnimator.ofFloat(0.00f, 0.99f).apply {
            duration = 3600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                decimalFraction = it.animatedValue as Float
                invalidate() // Triggers real-time crazy lightning update
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

        // 2. Calculate the 6 vertices of the hexagon
        val vx = FloatArray(6)
        val vy = FloatArray(6)
        hexPath.reset()
        for (i in 0 until 6) {
            val angle = (Math.PI / 3 * i) - (Math.PI / 2)
            val x = (cx + radius * cos(angle)).toFloat()
            val y = (cy + radius * sin(angle)).toFloat()
            vx[i] = x
            vy[i] = y
            if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
        }
        hexPath.close()

        // 3. Draw Base Hexagon Frames
        canvas.drawPath(hexPath, hexRimPaint)
        canvas.drawPath(hexPath, hexBorderPaint)

        // 4. Draw Crazy Red Electrical Wave across all 6 sides
        for (i in 0 until 6) {
            val next = (i + 1) % 6
            drawCrazyElectricalWave(canvas, vx[i], vy[i], vx[next], vy[next])
        }

        // 5. Vertical Red Laser Guide Line (Shooting straight to fingerprint sensor)
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

        // 6. 100% Synchronized Status Bar Percentage
        val totalPercentage = basePercent.toFloat() + decimalFraction
        val formattedPercent = String.format(Locale.US, "%05.2f%%", totalPercentage)
        canvas.drawText(formattedPercent, cx, cy + (6f * density), textPaint)

        // 7. MAX CHARGE Indicator
        canvas.drawText("⚡ $chargeLabel", cx, cy + (34f * density), labelPaint)
    }

    // High-energy chaotic dual-path red lightning wave generator
    private fun drawCrazyElectricalWave(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        val nx = -dy
        val ny = dx
        val len = sqrt(nx * nx + ny * ny)

        // --- Wave Path 1: High-amplitude erratic main strike ---
        primaryWavePath.reset()
        primaryWavePath.moveTo(x1, y1)
        val segments1 = 6
        for (s in 1 until segments1) {
            val px = x1 + (dx / segments1) * s
            val py = y1 + (dy / segments1) * s
            // Wild perpendicular jitter
            val jitter = (Random.nextFloat() - 0.5f) * (26f * density)
            primaryWavePath.lineTo(px + (nx / len) * jitter, py + (ny / len) * jitter)
        }
        primaryWavePath.lineTo(x2, y2)

        // Render Wave 1 (Deep Red Neon + White Hot Core)
        canvas.drawPath(primaryWavePath, redOuterGlowPaint)
        canvas.drawPath(primaryWavePath, redArcPaint)
        canvas.drawPath(primaryWavePath, lightningCorePaint)

        // --- Wave Path 2: Secondary erratic spark wave surging around the rim ---
        secondaryWavePath.reset()
        secondaryWavePath.moveTo(x1, y1)
        val segments2 = 4
        for (s in 1 until segments2) {
            val px = x1 + (dx / segments2) * s
            val py = y1 + (dy / segments2) * s
            val jitter = (Random.nextFloat() - 0.5f) * (16f * density)
            secondaryWavePath.lineTo(px + (nx / len) * jitter, py + (ny / len) * jitter)
        }
        secondaryWavePath.lineTo(x2, y2)

        // Render Wave 2 (Crimson Arcs)
        canvas.drawPath(secondaryWavePath, redArcPaint)
    }
}
