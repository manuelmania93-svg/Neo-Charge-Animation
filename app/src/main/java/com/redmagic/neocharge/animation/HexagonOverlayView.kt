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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class HexagonOverlayView(context: Context, private val chargeLabel: String) : View(context) {

    private var currentLevel: Float = getBatteryLevel(context)
    private var displayPercentage: Float = currentLevel
    private val density = resources.displayMetrics.density

    // Subtle dark gradient vignette around the hexagon only
    private val radialDimPaint = Paint().apply { isAntiAlias = true }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(18f * density, 0f, 0f, Color.parseColor("#FF0033")) // Red cyber glow
    }

    private val labelPaint = Paint().apply {
        color = Color.parseColor("#FFCC00") // Electric Gold MAX CHARGE
        textSize = 13f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        letterSpacing = 0.18f
    }

    private val hexBorderPaint = Paint().apply {
        color = Color.parseColor("#FF1E38") // Cyber Red
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        isAntiAlias = true
        pathEffect = CornerPathEffect(12f * density)
        setShadowLayer(15f * density, 0f, 0f, Color.parseColor("#FF0033"))
    }

    private val hexInnerGlowPaint = Paint().apply {
        color = Color.parseColor("#33FF0033")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    init {
        // Live 4-digit decimal ticker loop (XX.YY%)
        val animator = ValueAnimator.ofFloat(currentLevel, currentLevel + 0.99f).apply {
            duration = 3500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                displayPercentage = it.animatedValue as Float
                invalidate()
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
        // Centered exactly in the middle of screen (above fingerprint reader, below clock)
        val cy = height * 0.52f
        val radius = min(width, height) * 0.25f

        // 1. Draw subtle radial shadow behind the hexagon only (keeps wallpaper visible)
        radialDimPaint.shader = RadialGradient(
            cx, cy, radius * 1.5f,
            intArrayOf(Color.parseColor("#D9000000"), Color.TRANSPARENT),
            floatArrayOf(0.4f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.5f, radialDimPaint)

        // 2. Build Hexagon Path
        val path = Path()
        for (i in 0 until 6) {
            val angle = (Math.PI / 3 * i) - (Math.PI / 2)
            val x = (cx + radius * cos(angle)).toFloat()
            val y = (cy + radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        // 3. Draw Hexagon Body & Neon Edge
        canvas.drawPath(path, hexInnerGlowPaint)
        canvas.drawPath(path, hexBorderPaint)

        // 4. Render 4 numbers (XX.YY%)
        val formattedPercent = String.format(Locale.US, "%05.2f%%", displayPercentage)
        canvas.drawText(formattedPercent, cx, cy + (8f * density), textPaint)

        // 5. Render MAX CHARGE
        canvas.drawText(chargeLabel, cx, cy + (34f * density), labelPaint)
    }
}
