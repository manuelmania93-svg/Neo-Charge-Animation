package com.redmagic.neocharge.animation

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

class HexagonOverlayView(context: Context, private val chargeLabel: String) : View(context) {

    private var currentLevel: Float = getBatteryLevel(context)
    private var displayPercentage: Float = currentLevel
    private val density = resources.displayMetrics.density

    // Reusable paths for performance
    private val hexPath = Path()
    private val lightningPath = Path()

    // --- Paint Styles ---
    private val radialDimPaint = Paint().apply { isAntiAlias = true }

    // Outer dark hexagon rim
    private val hexRimPaint = Paint().apply {
        color = Color.parseColor("#1A1116")
        style = Paint.Style.STROKE
        strokeWidth = 9f * density
        isAntiAlias = true
        pathEffect = CornerPathEffect(14f * density)
    }

    // Inner bright neon border
    private val hexBorderPaint = Paint().apply {
        color = Color.parseColor("#33FF1E38")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        isAntiAlias = true
        pathEffect = CornerPathEffect(12f * density)
    }

    // Cyan Electric Arc (Left Side)
    private val cyanGlowPaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        isAntiAlias = true
        setShadowLayer(10f * density, 0f, 0f, Color.parseColor("#00B0FF"))
    }

    // Magenta/Red Electric Arc (Right Side)
    private val magentaGlowPaint = Paint().apply {
        color = Color.parseColor("#FF0055")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        isAntiAlias = true
        setShadowLayer(10f * density, 0f, 0f, Color.parseColor("#FF1744"))
    }

    // Lightning White Core
    private val lightningCorePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        isAntiAlias = true
    }

    // Vertical Laser Guide Line (to fingerprint sensor)
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

    // Big 4-digit percentage text
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(16f * density, 0f, 0f, Color.parseColor("#AA000000"))
    }

    // MAX CHARGE text
    private val labelPaint = Paint().apply {
        color = Color.parseColor("#FFCC00") // Electric Gold
        textSize = 13f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        letterSpacing = 0.2f
        setShadowLayer(8f * density, 0f, 0f, Color.parseColor("#FF9100"))
    }

    init {
        // Continuous animation loop for lightning flicker & decimal ticker
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val fraction = it.animatedFraction
                displayPercentage = currentLevel + (fraction * 0.99f)
                invalidate() // Triggers real-time lightning frame update
            }
        }
        animator.start()
    }

    private fun getBatteryLevel(context: Context): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 50
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        return (level.toFloat() / scale.toFloat()) * 100.0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height * 0.50f
        val radius = min(width, height) * 0.25f

        // 1. Center subtle vignette so lock screen wallpaper stays visible
        radialDimPaint.shader = RadialGradient(
            cx, cy, radius * 1.6f,
            intArrayOf(Color.parseColor("#CC08080C"), Color.TRANSPARENT),
            floatArrayOf(0.45f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.6f, radialDimPaint)

        // 2. Calculate the 6 vertices of the hexagon
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

        // 3. Draw Base Hexagon Frames
        canvas.drawPath(hexPath, hexRimPaint)
        canvas.drawPath(hexPath, hexBorderPaint)

        // 4. Draw Electric Lightning Arcs
        // Left Edges (vertices 3->4, 4->5, 5->0) = Cyan Lightning
        drawLightningEdge(canvas, verticesX[3], verticesY[3], verticesX[4], verticesY[4], cyanGlowPaint)
        drawLightningEdge(canvas, verticesX[4], verticesY[4], verticesX[5], verticesY[5], cyanGlowPaint)
        drawLightningEdge(canvas, verticesX[5], verticesY[5], verticesX[0], verticesY[0], cyanGlowPaint)

        // Right Edges (vertices 0->1, 1->2, 2->3) = Magenta/Pink Lightning
        drawLightningEdge(canvas, verticesX[0], verticesY[0], verticesX[1], verticesY[1], magentaGlowPaint)
        drawLightningEdge(canvas, verticesX[1], verticesY[1], verticesX[2], verticesY[2], magentaGlowPaint)
        drawLightningEdge(canvas, verticesX[2], verticesY[2], verticesX[3], verticesY[3], magentaGlowPaint)

        // 5. Draw the Vertical Laser Guide Line (Bottom tip straight down to fingerprint sensor)
        val bottomTipX = verticesX[3]
        val bottomTipY = verticesY[3]
        val fingerprintTargetY = height * 0.81f // Optical fingerprint location

        laserPaint.shader = LinearGradient(
            bottomTipX, bottomTipY, bottomTipX, fingerprintTargetY,
            intArrayOf(Color.parseColor("#FF0055"), Color.parseColor("#00E5FF"), Color.TRANSPARENT),
            floatArrayOf(0.0f, 0.7f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawLine(bottomTipX, bottomTipY + (4f * density), bottomTipX, fingerprintTargetY, laserGlowPaint)
        canvas.drawLine(bottomTipX, bottomTipY + (4f * density), bottomTipX, fingerprintTargetY, laserPaint)

        // 6. Draw 4 Numbers (XX.YY%)
        val formattedPercent = String.format(Locale.US, "%05.2f%%", displayPercentage)
        canvas.drawText(formattedPercent, cx, cy + (6f * density), textPaint)

        // 7. Draw MAX CHARGE
        canvas.drawText(chargeLabel, cx, cy + (34f * density), labelPaint)
    }

    // Procedural chaotic lightning algorithm
    private fun drawLightningEdge(
        canvas: Canvas,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        glowPaint: Paint
    ) {
        lightningPath.reset()
        lightningPath.moveTo(x1, y1)

        val segments = 4
        val dx = (x2 - x1) / segments
        val dy = (y2 - y1) / segments

        // Perpendicular vector for chaotic jitter
        val nx = -dy
        val ny = dx
        val len = sqrt(nx * nx + ny * ny)

        for (s in 1 until segments) {
            val progressX = x1 + dx * s
            val progressY = y1 + dy * s

            // Random jagged displacement (-12dp to +12dp)
            val jitter = (Random.nextFloat() - 0.5f) * (20f * density)
            val offsetX = progressX + (nx / len) * jitter
            val offsetY = progressY + (ny / len) * jitter

            lightningPath.lineTo(offsetX, offsetY)
        }

        lightningPath.lineTo(x2, y2)

        // Pass 1: Colored neon glow arc
        canvas.drawPath(lightningPath, glowPaint)
        // Pass 2: Intense white electrical core
        canvas.drawPath(lightningPath, lightningCorePaint)
    }
}
