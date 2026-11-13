package com.example.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * NeuralNetworkLockView
 *
 * High-performance, lightweight canvas view rendering an organically alive,
 * constantly morphing, non-fixed neural network on the lock screen:
 * - Dynamic Floating Core: Gently wanders across changing screen coordinates
 * - Orbital Synaptic Drift: Inner and outer nodes continuously counter-rotate and undulate
 * - Wandering Peripheral Nodes: Square IC chips and spider ganglion hubs continuously orbit & shift
 * - Morphing Axon Filaments: Real-time twisting Bezier curves adapting to moving nodes
 * - Synaptic Action Potentials: Light pulses flowing along dynamically morphing pathways
 * - Central Luminous Clock & Date: Moves synchronously with the neural core
 * - Interactive Touch Impulse: Sends electric excitation ripples through nearby synaptic nodes
 *
 * Performance:
 * - ZERO allocations in onDraw()
 * - Reused pre-allocated FloatArrays, Paths, and Paints
 * - Pure register math for buttery smooth 60-120 FPS
 */
class NeuralNetworkLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paints
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x14FFD54F.toInt() // 8% gold grid
        strokeWidth = 1.2f
        style = Paint.Style.STROKE
    }

    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFD54F.toInt()
        strokeWidth = 2.0f
        style = Paint.Style.STROKE
    }

    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFD54F.toInt()
        textSize = 28f
        letterSpacing = 0.25f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private val timeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 120f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
    }

    private val dateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFD54F.toInt()
        textSize = 32f
        letterSpacing = 0.15f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private val axonBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFCA28.toInt()
        strokeWidth = 1.2f
        style = Paint.Style.STROKE
    }

    private val axonHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFE082.toInt()
        strokeWidth = 1.8f
        style = Paint.Style.STROKE
    }

    private val nodeCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }

    private val nodeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFD54F.toInt()
        style = Paint.Style.FILL
    }

    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF9C4.toInt()
        style = Paint.Style.FILL
    }

    private val pulseGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFD54F.toInt()
        style = Paint.Style.FILL
    }

    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55FFD54F.toInt()
        strokeWidth = 2.0f
        style = Paint.Style.STROKE
    }

    private val chipFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22FFD54F.toInt()
        style = Paint.Style.FILL
    }

    // Geometry & Wandering Core State
    private var baseCenterX = 0f
    private var baseCenterY = 0f
    private var currentCenterX = 0f
    private var currentCenterY = 0f
    private var innerRadius = 0f
    private var outerRadius = 0f

    // Random phases to ensure fresh evolving location on each screen turn-on
    private var centerPhaseX = Random.nextFloat() * 10f
    private var centerPhaseY = Random.nextFloat() * 10f

    // Structural Nodes
    private val innerNodeCount = 18
    private val outerNodeCount = 36
    private val innerNodeX = FloatArray(innerNodeCount)
    private val innerNodeY = FloatArray(innerNodeCount)
    private val outerNodeX = FloatArray(outerNodeCount)
    private val outerNodeY = FloatArray(outerNodeCount)

    // Peripheral Dynamic Wandering Nodes
    private class DynamicPeripheralNode(
        var x: Float = 0f,
        var y: Float = 0f,
        var baseAngle: Double = 0.0,
        var currentAngle: Double = 0.0,
        var baseDistance: Float = 90f,
        var orbitSpeed: Float = 0.03f,
        var phase: Float = 0f,
        var size: Float = 28f,
        var isChip: Boolean = true,
        var parentOuterIdx: Int = 0
    )
    private val peripheralNodes = ArrayList<DynamicPeripheralNode>()

    // Axon Connection Topology
    private class DynamicAxon(
        var innerIdx: Int = 0,
        var outerIdx: Int = 0,
        var peripheralIdx: Int = -1,
        var startX: Float = 0f,
        var startY: Float = 0f,
        var ctrlX: Float = 0f,
        var ctrlY: Float = 0f,
        var endX: Float = 0f,
        var endY: Float = 0f,
        var isPeripheral: Boolean = false
    )
    private val axonConnections = ArrayList<DynamicAxon>()
    private val axonPath = Path()
    private val chipRect = RectF()

    // Synaptic Action Potential Pulses
    private class SynapticPulse {
        var connectionIdx: Int = 0
        var progress: Float = 0f
        var speed: Float = 0.015f
        var size: Float = 5f
        var active: Boolean = false
        var currentX: Float = 0f
        var currentY: Float = 0f
    }
    private val pulsePoolSize = 30
    private val pulses = Array(pulsePoolSize) { SynapticPulse() }

    // Animation & Touch State
    private var isAnimating = false
    private var lastFrameTime = 0L
    private var animationTime = 0f
    private var touchRippleX = -1f
    private var touchRippleY = -1f
    private var touchRippleRadius = 0f

    // Clock formatters
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    private var cachedTimeStr = ""
    private var cachedDateStr = ""
    private var lastClockUpdate = 0L

    init {
        updateClockStrings()
        randomizeLocation()
    }

    fun randomizeLocation() {
        centerPhaseX = Random.nextFloat() * 20f
        centerPhaseY = Random.nextFloat() * 20f
    }

    private fun updateClockStrings() {
        val now = Date()
        cachedTimeStr = timeFormat.format(now)
        cachedDateStr = dateFormat.format(now).uppercase()
        lastClockUpdate = SystemClock.uptimeMillis()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        baseCenterX = w * 0.5f
        baseCenterY = h * 0.44f

        innerRadius = w * 0.23f
        outerRadius = w * 0.38f

        buildBaseTopology()
    }

    private fun buildBaseTopology() {
        axonConnections.clear()
        peripheralNodes.clear()

        // 1. Build Inner to Outer Axon pairs
        for (i in 0 until innerNodeCount) {
            val targetBase = (i * 2) % outerNodeCount
            val targets = intArrayOf(
                (targetBase - 2 + outerNodeCount) % outerNodeCount,
                targetBase,
                (targetBase + 3) % outerNodeCount
            )

            for (targetIdx in targets) {
                axonConnections.add(
                    DynamicAxon(
                        innerIdx = i,
                        outerIdx = targetIdx,
                        peripheralIdx = -1,
                        isPeripheral = false
                    )
                )
            }
        }

        // 2. Build 9 Dynamic Wandering Peripheral Nodes (Top & Bottom sectors)
        val random = Random(42)
        // 4 Upper peripheral clusters (-140° to -40°)
        val topAngles = doubleArrayOf(-2.4, -1.9, -1.2, -0.7)
        for ((idx, baseA) in topAngles.withIndex()) {
            val parentIdx = (idx * 3 + 2) % outerNodeCount
            val pNode = DynamicPeripheralNode(
                baseAngle = baseA,
                currentAngle = baseA,
                baseDistance = outerRadius + 85f,
                orbitSpeed = 0.02f + (idx % 2) * 0.015f,
                phase = idx * 1.5f,
                size = 32f,
                isChip = (idx % 2 == 0),
                parentOuterIdx = parentIdx
            )
            peripheralNodes.add(pNode)

            // Connect to parent outer node
            axonConnections.add(
                DynamicAxon(
                    innerIdx = -1,
                    outerIdx = parentIdx,
                    peripheralIdx = peripheralNodes.size - 1,
                    isPeripheral = true
                )
            )
        }

        // 5 Lower peripheral clusters (40° to 140°)
        val botAngles = doubleArrayOf(0.7, 1.2, 1.57, 1.9, 2.4)
        for ((idx, baseA) in botAngles.withIndex()) {
            val parentIdx = (idx * 3 + 18) % outerNodeCount
            val pNode = DynamicPeripheralNode(
                baseAngle = baseA,
                currentAngle = baseA,
                baseDistance = outerRadius + 80f,
                orbitSpeed = -0.018f - (idx % 2) * 0.012f,
                phase = (idx + 4) * 1.3f,
                size = 30f,
                isChip = (idx % 2 == 1),
                parentOuterIdx = parentIdx
            )
            peripheralNodes.add(pNode)

            axonConnections.add(
                DynamicAxon(
                    innerIdx = -1,
                    outerIdx = parentIdx,
                    peripheralIdx = peripheralNodes.size - 1,
                    isPeripheral = true
                )
            )
        }

        // 3. Initialize Pulses
        initPulses()
    }

    private fun initPulses() {
        if (axonConnections.isEmpty()) return
        val random = Random(System.currentTimeMillis())
        for (pulse in pulses) {
            pulse.connectionIdx = random.nextInt(axonConnections.size)
            pulse.progress = random.nextFloat()
            pulse.speed = 0.009f + random.nextFloat() * 0.015f
            pulse.size = 4.2f + random.nextFloat() * 3.5f
            pulse.active = true
        }
    }

    fun startAnimation() {
        if (!isAnimating) {
            isAnimating = true
            lastFrameTime = SystemClock.uptimeMillis()
            postInvalidateOnAnimation()
        }
    }

    fun stopAnimation() {
        isAnimating = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        randomizeLocation()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = SystemClock.uptimeMillis()
        if (now - lastClockUpdate > 1000L) {
            updateClockStrings()
        }

        // Advance simulation time
        if (isAnimating) {
            val dt = if (lastFrameTime > 0) (now - lastFrameTime) / 1000f else 0.016f
            lastFrameTime = now
            animationTime += dt

            // Animate touch ripple
            if (touchRippleRadius > 0f) {
                touchRippleRadius += dt * 320f
                if (touchRippleRadius > width * 0.7f) {
                    touchRippleRadius = 0f
                    touchRippleX = -1f
                }
            }
        }

        // 1. Update all dynamic wandering coordinates (Core + Inner + Outer + Peripheral + Axons)
        updateDynamicCoordinates()

        // 2. Draw Cybernetic Grid Background
        drawCyberGrid(canvas)

        // 3. Draw HUD Framing
        drawCyberHUD(canvas)

        // 4. Draw Dynamically Morphing Axon Filaments
        drawAxonCurves(canvas)

        // 5. Draw Traveling Synaptic Pulses
        drawSynapticPulses(canvas)

        // 6. Draw Morphing Synaptic Nodes
        drawSynapticNodes(canvas)

        // 7. Draw Floating Peripheral Chips & Spider Ganglions
        drawPeripheralClusters(canvas)

        // 8. Draw Central Glowing Clock & Date (moves with the dynamic core)
        drawCentralClock(canvas)

        // Request next frame
        if (isAnimating && visibility == VISIBLE) {
            postInvalidateOnAnimation()
        }
    }

    /**
     * Updates all node coordinates smoothly across changing locations every frame.
     * ZERO object allocation — pure register math!
     */
    private fun updateDynamicCoordinates() {
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Core Center Wandering (smooth organic Lissajous drift)
        val driftX = sin(animationTime * 0.32f + centerPhaseX) * (w * 0.07f) + cos(animationTime * 0.18f) * (w * 0.035f)
        val driftY = cos(animationTime * 0.25f + centerPhaseY) * (h * 0.05f) + sin(animationTime * 0.14f) * (h * 0.025f)
        currentCenterX = baseCenterX + driftX
        currentCenterY = baseCenterY + driftY

        // 2. Inner Ring Nodes (slow clockwise rotation + undulating radial wave)
        val innerAngleOffset = (animationTime * 0.08)
        val innerStep = (Math.PI * 2.0 / innerNodeCount)
        for (i in 0 until innerNodeCount) {
            val baseAngle = i * innerStep - Math.PI / 2.0 + innerAngleOffset
            val wobbleAngle = baseAngle + sin(animationTime * 1.4f + i * 1.1f) * 0.06f
            val dynamicRadius = innerRadius + sin(animationTime * 2.2f + i * 0.8f) * 14f
            innerNodeX[i] = (currentCenterX + cos(wobbleAngle) * dynamicRadius).toFloat()
            innerNodeY[i] = (currentCenterY + sin(wobbleAngle) * dynamicRadius).toFloat()
        }

        // 3. Outer Ring Nodes (slow counter-clockwise rotation + breathing harmonic wave)
        val outerAngleOffset = (-animationTime * 0.05)
        val outerStep = (Math.PI * 2.0 / outerNodeCount)
        for (i in 0 until outerNodeCount) {
            val baseAngle = i * outerStep - Math.PI / 2.0 + outerAngleOffset
            val wobbleAngle = baseAngle + cos(animationTime * 0.95f + i * 0.7f) * 0.05f
            val dynamicRadius = outerRadius + cos(animationTime * 1.7f + i * 0.85f) * 20f + sin(animationTime * 0.75f + i * 1.8f) * 10f
            outerNodeX[i] = (currentCenterX + cos(wobbleAngle) * dynamicRadius).toFloat()
            outerNodeY[i] = (currentCenterY + sin(wobbleAngle) * dynamicRadius).toFloat()
        }

        // 4. Peripheral Nodes (drifting along dynamic orbital arcs)
        for (node in peripheralNodes) {
            val angleWobble = sin(animationTime * 0.45f + node.phase) * 0.28
            node.currentAngle = node.baseAngle + angleWobble + (animationTime * node.orbitSpeed)

            val distWobble = cos(animationTime * 1.3f + node.phase) * 26f
            val dist = node.baseDistance + distWobble

            node.x = (currentCenterX + cos(node.currentAngle) * dist).toFloat()
            node.y = (currentCenterY + sin(node.currentAngle) * dist).toFloat()
        }

        // 5. Update Axon Curves geometry to connect to newly shifted node coordinates
        for (axon in axonConnections) {
            if (axon.isPeripheral) {
                val outerN = axon.outerIdx
                val pIdx = axon.peripheralIdx
                val pNode = peripheralNodes[pIdx]

                axon.startX = outerNodeX[outerN]
                axon.startY = outerNodeY[outerN]
                axon.endX = pNode.x
                axon.endY = pNode.y

                val midX = (axon.startX + axon.endX) * 0.5f
                val midY = (axon.startY + axon.endY) * 0.5f
                axon.ctrlX = midX + 22f * sin(animationTime * 1.5f + pIdx)
                axon.ctrlY = midY - 18f * cos(animationTime * 1.5f + pIdx)
            } else {
                axon.startX = innerNodeX[axon.innerIdx]
                axon.startY = innerNodeY[axon.innerIdx]
                axon.endX = outerNodeX[axon.outerIdx]
                axon.endY = outerNodeY[axon.outerIdx]

                val midX = (axon.startX + axon.endX) * 0.5f
                val midY = (axon.startY + axon.endY) * 0.5f

                // Dynamic twisting curvature
                val perpX = -(axon.endY - axon.startY) * 0.28f
                val perpY = (axon.endX - axon.startX) * 0.28f
                val twistBreath = sin(animationTime * 1.8f + axon.innerIdx) * 0.05f

                axon.ctrlX = midX + perpX * (1f + twistBreath)
                axon.ctrlY = midY + perpY * (1f + twistBreath)
            }
        }
    }

    private fun drawCyberGrid(canvas: Canvas) {
        val step = 70f
        val w = width.toFloat()
        val h = height.toFloat()

        var x = 0f
        while (x <= w) {
            canvas.drawLine(x, 0f, x, h, gridPaint)
            x += step
        }

        var y = 0f
        while (y <= h) {
            canvas.drawLine(0f, y, w, y, gridPaint)
            y += step
        }
    }

    private fun drawCyberHUD(canvas: Canvas) {
        val pad = 48f
        val top = 88f
        val bracketLen = 32f

        // Top-Left Cyber Bracket
        canvas.drawLine(pad, top, pad + bracketLen, top, hudPaint)
        canvas.drawLine(pad, top, pad, top + bracketLen, hudPaint)

        // Top-Right Cyber Bracket
        val right = width - pad
        canvas.drawLine(right, top, right - bracketLen, top, hudPaint)
        canvas.drawLine(right, top, right, top + bracketLen, hudPaint)

        // Header Title: [ LOCK SCREEN ]
        canvas.drawText("LOCK SCREEN", width * 0.5f, top + 16f, hudTextPaint)
    }

    private fun drawAxonCurves(canvas: Canvas) {
        val pulseBreath = (sin(animationTime * 2.0) * 0.15f + 0.85f)

        for (conn in axonConnections) {
            axonPath.reset()
            axonPath.moveTo(conn.startX, conn.startY)
            axonPath.quadTo(conn.ctrlX, conn.ctrlY, conn.endX, conn.endY)

            if (conn.isPeripheral) {
                canvas.drawPath(axonPath, axonHighlightPaint)
            } else {
                axonBasePaint.alpha = (45 * pulseBreath).toInt()
                canvas.drawPath(axonPath, axonBasePaint)
            }
        }
    }

    private fun drawSynapticPulses(canvas: Canvas) {
        val totalConns = axonConnections.size
        if (totalConns == 0) return

        for (pulse in pulses) {
            if (!pulse.active) continue

            pulse.progress += pulse.speed
            if (pulse.progress >= 1.0f) {
                pulse.progress = 0f
                pulse.connectionIdx = (pulse.connectionIdx + 7) % totalConns
                pulse.speed = 0.009f + (pulse.connectionIdx % 5) * 0.003f
            }

            val conn = axonConnections[pulse.connectionIdx]
            val t = pulse.progress
            val oneMinusT = 1f - t

            // Quadratic Bezier: B(t) = (1-t)^2 * P0 + 2*(1-t)*t * P1 + t^2 * P2
            val px = oneMinusT * oneMinusT * conn.startX + 2f * oneMinusT * t * conn.ctrlX + t * t * conn.endX
            val py = oneMinusT * oneMinusT * conn.startY + 2f * oneMinusT * t * conn.ctrlY + t * t * conn.endY

            pulse.currentX = px
            pulse.currentY = py

            canvas.drawCircle(px, py, pulse.size * 2.2f, pulseGlowPaint)
            canvas.drawCircle(px, py, pulse.size, pulsePaint)
        }
    }

    private fun drawSynapticNodes(canvas: Canvas) {
        val breath = sin(animationTime * 3.0).toFloat() * 1.5f

        // Inner Moving Nodes
        for (i in 0 until innerNodeCount) {
            val nx = innerNodeX[i]
            val ny = innerNodeY[i]

            var extraGlow = 0f
            if (touchRippleRadius > 0f) {
                val dist = sqrt((nx - touchRippleX) * (nx - touchRippleX) + (ny - touchRippleY) * (ny - touchRippleY))
                if (Math.abs(dist - touchRippleRadius) < 70f) {
                    extraGlow = 6f
                }
            }

            canvas.drawCircle(nx, ny, 7f + breath + extraGlow, nodeGlowPaint)
            canvas.drawCircle(nx, ny, 3.5f + (extraGlow * 0.4f), nodeCorePaint)
        }

        // Outer Moving Nodes
        for (i in 0 until outerNodeCount) {
            val nx = outerNodeX[i]
            val ny = outerNodeY[i]

            var extraGlow = 0f
            if (touchRippleRadius > 0f) {
                val dist = sqrt((nx - touchRippleX) * (nx - touchRippleX) + (ny - touchRippleY) * (ny - touchRippleY))
                if (Math.abs(dist - touchRippleRadius) < 70f) {
                    extraGlow = 7f
                }
            }

            val isMajorNode = (i % 3 == 0)
            val baseRadius = if (isMajorNode) 5.5f else 3.8f

            canvas.drawCircle(nx, ny, baseRadius * 2.2f + extraGlow, nodeGlowPaint)
            canvas.drawCircle(nx, ny, baseRadius + (extraGlow * 0.3f), nodeCorePaint)
        }
    }

    private fun drawPeripheralClusters(canvas: Canvas) {
        for (node in peripheralNodes) {
            if (node.isChip) {
                val half = node.size * 0.5f
                chipRect.set(node.x - half, node.y - half, node.x + half, node.y + half)
                canvas.drawRect(chipRect, chipFillPaint)
                canvas.drawRect(chipRect, chipPaint)

                // Inner core dot
                canvas.drawCircle(node.x, node.y, 4f, nodeCorePaint)
            } else {
                // Cybernetic spider/ganglion cluster: rotating mini-legs
                canvas.drawCircle(node.x, node.y, 9f, nodeGlowPaint)
                canvas.drawCircle(node.x, node.y, 4.5f, nodeCorePaint)

                val legLen = 14f
                for (a in 0 until 6) {
                    val angle = a * (Math.PI / 3.0) + animationTime * 0.6
                    val lx = (node.x + cos(angle) * legLen).toFloat()
                    val ly = (node.y + sin(angle) * legLen).toFloat()
                    canvas.drawLine(node.x, node.y, lx, ly, axonHighlightPaint)
                    canvas.drawCircle(lx, ly, 2f, nodeCorePaint)
                }
            }
        }
    }

    private fun drawCentralClock(canvas: Canvas) {
        // Digital Hour:Minute follows the moving core center
        val timeY = currentCenterY + 14f
        canvas.drawText(cachedTimeStr, currentCenterX, timeY, timeTextPaint)

        // Subtitle Date follows the moving core center
        val dateY = timeY + 54f
        canvas.drawText(cachedDateStr, currentCenterX, dateY, dateTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            touchRippleX = event.x
            touchRippleY = event.y
            touchRippleRadius = 10f
            return true
        }
        return super.onTouchEvent(event)
    }
}
