package com.example.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class AstrolabeScrollerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onLetterSelected: ((Char) -> Unit)? = null

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()
    private var selectedIndex = -1

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0xFFFFD54F.toInt()
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = 0x33FFA000.toInt()
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFD54F.toInt()
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(8f, 0f, 0f, 0xFFFFD54F.toInt())
    }

    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0x66FFD54F.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        // Center the astrolabe wheel at the bottom-right corner
        val cx = width.toFloat()
        val cy = height.toFloat()

        val outerRadius = width * 0.95f
        val letterRadius = outerRadius * 0.82f
        val innerRadius = outerRadius * 0.65f

        // Draw glowing celestial astrolabe arcs
        canvas.drawCircle(cx, cy, outerRadius, glowPaint)
        canvas.drawCircle(cx, cy, outerRadius, arcPaint)
        canvas.drawCircle(cx, cy, innerRadius, innerRingPaint)

        // The arc spans roughly from 180 degrees (left) to 270 degrees (top)
        val startAngle = 185f
        val sweepAngle = 80f
        val step = sweepAngle / (alphabet.size - 1)

        for (i in alphabet.indices) {
            val angle = Math.toRadians((startAngle + i * step).toDouble())
            val lx = cx + (letterRadius * cos(angle)).toFloat()
            val ly = cy + (letterRadius * sin(angle)).toFloat()

            val isSelected = i == selectedIndex
            val paint = if (isSelected) selectedTextPaint else textPaint

            canvas.drawText(alphabet[i].toString(), lx, ly + 8f, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width.toFloat()
        val cy = height.toFloat()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - cx
                val dy = event.y - cy
                val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

                // Map angle to letter
                val startAngle = -175f // around 185 normalized
                val normalized = if (angleDeg < 0) angleDeg + 360f else angleDeg
                val rel = normalized - 185f

                if (rel in 0f..85f) {
                    val idx = ((rel / 80f) * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                    if (idx != selectedIndex) {
                        selectedIndex = idx
                        onLetterSelected?.invoke(alphabet[idx])
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selectedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

