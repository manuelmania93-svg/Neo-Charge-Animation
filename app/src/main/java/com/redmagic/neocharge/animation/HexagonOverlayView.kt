package com.redmagic.neocharge.animation

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.BatteryManager
import android.os.Build
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * NeoCharge's signature overlay: a neon hexagon HUD with a live 4-digit
 * decimal percentage ticker, a bank of short-lived "crackle" veins that
 * spawn white-hot and cool down to deep red as they age, and a soft
 * pulsing glow halo around the hexagon outline.
 *
 * Visual identity deliberately differs from stock "charging animation"
 * apps: cracked/branching veins instead of a single lightning ring,
 * a honeycomb-free clean readout zone, and NeoCharge's own wordmark.
 */
class HexagonOverlayView(context: Context, private val chargeLabel: String) : View(context) {

    private val density = resources.displayMetrics.density

    // ---- Battery percentage ticker -----------------------------------

    private var currentLevel: Float = getBatteryLevel(context)
    private var displayPercentage: Float = currentLevel

    private val percentPaint = Paint().apply {
        color = Color.WHITE
        textSize = 27f * density
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.parseColor("#FF6B6B")
        textSize = 13f * density
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            letterSpacing = 0.2f
        }
    }

    private val wordmarkPaint = Paint().apply {
        color = Color.parseColor("#FF3355")
        textSize = 12f * density
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            letterSpacing = 0.25f
        }
    }

    private val wavePaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
        isAntiAlias = true
        alpha = 200
    }

    // ---- Hexagon outline + glow ---------------------------------------

    private val hexOutlinePaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        isAntiAlias = true
    }

    private data class GlowLayer(val scale: Float, val baseAlpha: Int)
    private val glowLayers = listOf(
        GlowLayer(1.35f, 20),
        GlowLayer(1.22f, 36),
        GlowLayer(1.10f, 64)
    )
    private val glowPaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.STROKE
        strokeWidth = 6f * density
        isAntiAlias = true
    }

    // ---- Crackle vein system -------------------------------------------

    private data class Vein(val points: List<PointF>, val bornAt: Long, val lifeMs: Long)

    private val veins = mutableListOf<Vein>()
    private val targetVeinCount = 90 // tuned for real-device GPU cost; raise for denser look
    private val veinPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val haloPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        maskFilter = BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL)
    }

    init {
        // Software layer required for BlurMaskFilter to render on most devices.
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        // Drives both the percentage ticker and the continuous invalidate loop
        // that ages/respawns veins and pulses the glow.
        val animator = ValueAnimator.ofFloat(currentLevel, currentLevel + 0.99f).apply {
            duration = 3800
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

    // ---- Geometry helpers ------------------------------------------------

    private fun hexVertices(cx: Float, cy: Float, r: Float): List<PointF> {
        val pts = ArrayList<PointF>(6)
        for (i in 0 until 6) {
            val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
            pts.add(PointF(cx + r * cos(angle), cy + r * sin(angle)))
        }
        return pts
    }

    private fun edgePoint(a: PointF, b: PointF, t: Float): PointF =
        PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    private fun buildVeinPoints(start: PointF, cx: Float, cy: Float, clearR: Float): List<PointF> {
        val pts = ArrayList<PointF>()
        var x = start.x
        var y = start.y
        pts.add(PointF(x, y))
        val steps = 5 + Random.nextInt(3)
        for (s in 0 until steps) {
            var dx = cx - x
            var dy = cy - y
            val mag = kotlin.math.hypot(dx, dy).let { if (it == 0f) 1f else it }
            dx /= mag
            dy /= mag
            val len = (8 + Random.nextFloat() * 6) * density
            var nx = x + dx * len + (Random.nextFloat() - 0.5f) * 16f * density
            var ny = y + dy * len + (Random.nextFloat() - 0.5f) * 16f * density
            val distToCenter = kotlin.math.hypot(nx - cx, ny - cy)
            if (distToCenter < clearR) {
                val ang = kotlin.math.atan2(ny - cy, nx - cx)
                nx = cx + clearR * cos(ang)
                ny = cy + clearR * sin(ang)
                pts.add(PointF(nx, ny))
                break
            }
            pts.add(PointF(nx, ny))
            x = nx
            y = ny
        }
        return pts
    }

    private fun spawnVein(vertices: List<PointF>, cx: Float, cy: Float, clearR: Float) {
        val e = Random.nextInt(6)
        val start = edgePoint(vertices[e], vertices[(e + 1) % 6], Random.nextFloat())
        val pts = buildVeinPoints(start, cx, cy, clearR)
        veins.add(Vein(pts, System.currentTimeMillis(), 400L + Random.nextInt(400)))
    }

    /** White-hot -> bright red -> deep cooled red, keyed by 0f..1f lifecycle progress. */
    private fun veinColor(t: Float): Int {
        val hot = intArrayOf(255, 255, 255)
        val mid = intArrayOf(255, 60, 60)
        val cool = intArrayOf(110, 10, 10)
        val (r, g, b) = if (t < 0.18f) {
            val k = t / 0.18f
            Triple(
                (hot[0] + (mid[0] - hot[0]) * k).toInt(),
                (hot[1] + (mid[1] - hot[1]) * k).toInt(),
                (hot[2] + (mid[2] - hot[2]) * k).toInt()
            )
        } else {
            val k = (t - 0.18f) / 0.82f
            Triple(
                (mid[0] + (cool[0] - mid[0]) * k).toInt(),
                (mid[1] + (cool[1] - mid[1]) * k).toInt(),
                (mid[2] + (cool[2] - mid[2]) * k).toInt()
            )
        }
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun pathFromPoints(points: List<PointF>): Path {
        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        return path
    }

    // ---- Draw --------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.26f
        val clearR = radius * 0.82f
        val vertices = hexVertices(cx, cy, radius)
        val now = System.currentTimeMillis()

        // 1. Pulsing glow rings behind the hexagon
        val pulse = 0.85f + 0.15f * sin(now / 500.0).toFloat()
        glowLayers.forEach { layer ->
            val scaledVerts = vertices.map { PointF(cx + (it.x - cx) * layer.scale, cy + (it.y - cy) * layer.scale) }
            val path = pathFromPoints(scaledVerts + scaledVerts[0])
            glowPaint.alpha = (layer.baseAlpha * pulse).toInt().coerceIn(0, 255)
            canvas.drawPath(path, glowPaint)
        }

        // 2. Age existing veins, cull expired ones, spawn replacements
        val iterator = veins.iterator()
        while (iterator.hasNext()) {
            val v = iterator.next()
            if (now - v.bornAt > v.lifeMs) iterator.remove()
        }
        while (veins.size < targetVeinCount) spawnVein(vertices, cx, cy, clearR)

        // 3. Draw veins: soft blurred halo pass, then sharp core pass
        veins.forEach { v ->
            val t = ((now - v.bornAt).toFloat() / v.lifeMs).coerceIn(0f, 1f)
            val color = veinColor(t)
            val path = pathFromPoints(v.points)

            haloPaint.color = color
            haloPaint.alpha = ((1f - t) * 0.14f * 255).toInt().coerceIn(0, 255)
            haloPaint.strokeWidth = 5f * density
            canvas.drawPath(path, haloPaint)

            veinPaint.color = color
            veinPaint.alpha = ((1f - t) * 0.75f * 255 + 12).toInt().coerceIn(0, 255)
            veinPaint.strokeWidth = (0.8f + Random.nextFloat() * 0.6f) * density
            canvas.drawPath(path, veinPaint)
        }

        // 4. Static hex outline on top of the crackle
        val outlinePath = pathFromPoints(vertices + vertices[0])
        canvas.drawPath(outlinePath, hexOutlinePaint)

        // 5. Clean readout: 4-digit percentage + charge label (never touched by veins)
        val formattedPercent = String.format(Locale.US, "%05.2f%%", displayPercentage)
        canvas.drawText(formattedPercent, cx, cy - (2f * density), percentPaint)
        canvas.drawText(chargeLabel, cx, cy + (20f * density), labelPaint)

        // 6. Wordmark + underline wave, drawn below the hexagon
        val wmY = cy + radius * 1.55f
        canvas.drawText("NEOCHARGE", cx, wmY, wordmarkPaint)
        drawWave(canvas, cx, wmY + 10f * density, radius * 0.9f, now)
    }

    private fun drawWave(canvas: Canvas, cx: Float, y: Float, halfWidth: Float, now: Long) {
        val path = Path()
        val steps = 24
        for (i in 0..steps) {
            val fx = cx - halfWidth + (halfWidth * 2f) * (i / steps.toFloat())
            val phase = now / 200.0
            val fy = y + (3f * density) * sin((i / steps.toFloat()) * Math.PI * 6 + phase).toFloat()
            if (i == 0) path.moveTo(fx, fy) else path.lineTo(fx, fy)
        }
        canvas.drawPath(path, wavePaint)
    }
}
