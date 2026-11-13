package com.example.launcher.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.example.launcher.data.AppInfo
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class SolarOrbitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var apps: List<AppInfo> = emptyList()
    var onAppClick: ((AppInfo) -> Unit)? = null
    var onLongClickSolar: (() -> Unit)? = null

    // Center coordinates
    private var cx = 0f
    private var cy = 0f

    // Celestial rotation: smooth, coordinated orbital revolution
    private val celestialSpeed = 4.5f // degrees per second
    private var systemRotation = 0f

    // User manual drag rotation offset
    private var manualRotation = 0f
    private var lastTouchAngle = 0f
    private var isDragging = false

    // Real-time animation loop
    private var orbitAnimator: ValueAnimator? = null
    private var lastFrameTime = 0L

    // Paints
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x33FFD54F.toInt()
        strokeWidth = 2.2f
    }

    private val sunGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val sunCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFFFFD54F.toInt()
    }

    private val sunInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x26FFD54F.toInt()
    }

    private val timeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 58f
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE6C280.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textSize = 24f
        letterSpacing = 0.08f
    }

    private val appLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF0E0C0.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 23f
        setShadowLayer(4f, 0f, 2f, 0xCC000000.toInt())
    }

    private val planetBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0xFFFFD54F.toInt()
    }

    private val planetGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = 0x4DFFD54F.toInt()
    }

    private val planetBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x590B101D.toInt()
    }

    private val starDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xAAFFD54F.toInt()
    }

    // Planetary placement data
    private data class PlacedOrb(
        val app: AppInfo,
        val orbitIndex: Int,
        val angleDeg: Float,
        var currentX: Float = 0f,
        var currentY: Float = 0f,
        val radius: Float = 44f
    )

    private val placedOrbs = mutableListOf<PlacedOrb>()

    // Ellipse parameters
    private val orbitRatios = floatArrayOf(0.24f, 0.35f, 0.47f, 0.58f)
    private val yRatio = 0.74f // tilted perspective

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

    // Long press detector
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPressed = false

    init {
        setupOrbitAnimation()
    }

    private fun setupOrbitAnimation() {
        orbitAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val now = System.currentTimeMillis()
                if (lastFrameTime > 0) {
                    val dt = (now - lastFrameTime) / 1000f
                    systemRotation = (systemRotation + celestialSpeed * dt) % 360f
                    invalidate()
                }
                lastFrameTime = now
            }
        }
    }

    fun startAnimation() {
        lastFrameTime = System.currentTimeMillis()
        if (orbitAnimator?.isRunning != true) {
            orbitAnimator?.start()
        }
    }

    fun stopAnimation() {
        orbitAnimator?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    fun getAppsCount(): Int = apps.size

    fun setApps(newApps: List<AppInfo>) {
        this.apps = newApps
        if (width > 0 && height > 0) {
            cx = width / 2f
            cy = height * 0.42f
            recalculatePlacements()
            invalidate()
        } else {
            post {
                if (width > 0 && height > 0) {
                    cx = width / 2f
                    cy = height * 0.42f
                    recalculatePlacements()
                    invalidate()
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h * 0.42f
        recalculatePlacements()
        invalidate()
    }

    private fun recalculatePlacements() {
        placedOrbs.clear()
        if (apps.isEmpty() || width <= 0) return

        val count = apps.size

        // Symmetrical celestial distribution across concentric orbits
        val distribution = when (count) {
            1 -> intArrayOf(0, 1, 0, 0)
            2 -> intArrayOf(0, 1, 1, 0)
            3 -> intArrayOf(0, 1, 2, 0)
            4 -> intArrayOf(0, 1, 1, 2)
            5 -> intArrayOf(1, 1, 1, 2)
            6 -> intArrayOf(1, 1, 2, 2)
            7 -> intArrayOf(1, 2, 2, 2)
            8 -> intArrayOf(1, 2, 2, 3)
            9 -> intArrayOf(1, 2, 3, 3)
            10 -> intArrayOf(1, 2, 3, 4)
            else -> {
                val o0 = 1
                val o1 = max(1, count / 5)
                val o2 = max(2, (count * 2) / 5)
                val o3 = count - (o0 + o1 + o2)
                intArrayOf(o0, o1, o2, o3)
            }
        }

        // Elegant celestial spiral offsets for each ring
        val phaseOffsets = floatArrayOf(45f, 10f, 30f, 15f)

        var appIdx = 0
        for (orbitIdx in 0..3) {
            val numAppsOnOrbit = distribution[orbitIdx]
            if (numAppsOnOrbit <= 0) continue

            val step = 360f / numAppsOnOrbit
            val basePhase = phaseOffsets[orbitIdx]

            for (j in 0 until numAppsOnOrbit) {
                if (appIdx >= apps.size) break
                val angle = (basePhase + j * step) % 360f
                placedOrbs.add(
                    PlacedOrb(
                        app = apps[appIdx],
                        orbitIndex = orbitIdx,
                        angleDeg = angle
                    )
                )
                appIdx++
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val baseRadius = width.toFloat()

        // 1. Draw Concentric Orbital Ellipses & Celestial Star Beads
        for (i in orbitRatios.indices) {
            val rx = baseRadius * orbitRatios[i]
            val ry = rx * yRatio
            val rect = RectF(cx - rx, cy - ry, cx + rx, cy + ry)
            canvas.drawOval(rect, orbitPaint)

            // Small starlight beads revolving smoothly along each orbit
            val beadAngle = Math.toRadians((i * 65f + systemRotation * 0.8f + manualRotation).toDouble())
            val bx = cx + (rx * cos(beadAngle)).toFloat()
            val by = cy + (ry * sin(beadAngle)).toFloat()
            canvas.drawCircle(bx, by, 3.5f, starDotPaint)
        }

        // 2. Draw Central Sun / Clock Core
        drawCentralSun(canvas)

        // 3. Draw Planetary Apps maintaining their clean geometric shape in synchronized revolution
        for (orb in placedOrbs) {
            val rx = baseRadius * orbitRatios[orb.orbitIndex]
            val ry = rx * yRatio
            val totalAngle = orb.angleDeg + systemRotation + manualRotation
            val rad = Math.toRadians(totalAngle.toDouble())

            orb.currentX = cx + (rx * cos(rad)).toFloat()
            orb.currentY = cy + (ry * sin(rad)).toFloat()

            drawPlanetaryOrb(canvas, orb)
        }
    }

    private fun drawCentralSun(canvas: Canvas) {
        val sunRadius = width * 0.14f

        // Outer Corona Glow
        val glowShader = RadialGradient(
            cx, cy, sunRadius * 1.6f,
            intArrayOf(0x55FFA000.toInt(), 0x22FFA000.toInt(), Color.TRANSPARENT),
            floatArrayOf(0.4f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        sunGlowPaint.shader = glowShader
        canvas.drawCircle(cx, cy, sunRadius * 1.6f, sunGlowPaint)

        // Sun Rings
        canvas.drawCircle(cx, cy, sunRadius * 1.15f, sunCorePaint)
        canvas.drawCircle(cx, cy, sunRadius, sunInnerPaint)

        // Decorative corona orbit
        val decorRect = RectF(
            cx - sunRadius * 1.35f, cy - sunRadius * 0.95f,
            cx + sunRadius * 1.35f, cy + sunRadius * 0.95f
        )
        canvas.drawOval(decorRect, orbitPaint)

        // Live Time & Date Display inside Sun
        val now = Date()
        val timeStr = timeFormat.format(now)
        val dateStr = dateFormat.format(now).uppercase() + " • 78°"

        canvas.drawText(timeStr, cx, cy + 12f, timeTextPaint)
        canvas.drawText(dateStr, cx, cy + 52f, subTextPaint)
    }

    private fun drawPlanetaryOrb(canvas: Canvas, orb: PlacedOrb) {
        val px = orb.currentX
        val py = orb.currentY
        val r = orb.radius

        canvas.save()
        canvas.translate(px, py)

        // 1. Orbital dark glass backdrop
        canvas.drawCircle(0f, 0f, r, planetBgPaint)

        // 2. Outer golden glow & orbit rim
        canvas.drawCircle(0f, 0f, r + 4f, planetGlowPaint)
        canvas.drawCircle(0f, 0f, r, planetBorderPaint)

        // 3. Draw default app icon
        val iconSize = (r * 1.35f).toInt()
        val half = iconSize / 2
        orb.app.icon.setBounds(-half, -half, half, half)
        orb.app.icon.draw(canvas)

        canvas.restore()

        // 4. App Label below orb
        canvas.drawText(orb.app.label, px, py + r + 30f, appLabelPaint)
    }

    // Touch & Interactive Orbit Rotation + Long Press
    private var touchStartX = 0f
    private var touchStartY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                lastTouchAngle = calculateAngle(event.x, event.y)
                isDragging = false
                isLongPressed = false

                // Schedule long-press
                longPressRunnable = Runnable {
                    isLongPressed = true
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    onLongClickSolar?.invoke()
                }
                longPressRunnable?.let { handler.postDelayed(it, 500) }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                if (hypot(dx.toDouble(), dy.toDouble()) > 15f) {
                    abortLongPressTimer()
                    isDragging = true
                    val currentAngle = calculateAngle(event.x, event.y)
                    val delta = currentAngle - lastTouchAngle
                    manualRotation = (manualRotation + delta) % 360f
                    lastTouchAngle = currentAngle
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                abortLongPressTimer()
                if (!isDragging && !isLongPressed && event.actionMasked == MotionEvent.ACTION_UP) {
                    // Check if tapped on any planetary orb
                    val clicked = placedOrbs.find {
                        val d = hypot((event.x - it.currentX).toDouble(), (event.y - it.currentY).toDouble())
                        d <= it.radius * 1.5f
                    }
                    if (clicked != null) {
                        onAppClick?.invoke(clicked.app)
                    } else {
                        // Check if tapped inside the Sun
                        val distSun = hypot((event.x - cx).toDouble(), (event.y - cy).toDouble())
                        if (distSun <= width * 0.16f) {
                            onLongClickSolar?.invoke()
                        }
                    }
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun abortLongPressTimer() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
        super.cancelLongPress()
    }

    private fun calculateAngle(x: Float, y: Float): Float {
        val dx = x - cx
        val dy = y - cy
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }
}
