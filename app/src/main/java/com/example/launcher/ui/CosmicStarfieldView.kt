package com.example.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class CosmicStarfieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Star(
        var x: Float,
        var y: Float,
        val radius: Float,
        val baseAlpha: Float,
        var currentAlpha: Float,
        val twinkleSpeed: Float,
        val color: Int
    )

    private val stars = mutableListOf<Star>()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var phase = 0f

    init {
        animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase = it.animatedValue as Float
                updateStars()
                invalidate()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        stars.clear()
        val count = 90
        val rnd = Random(42)

        val starColors = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFFFE082.toInt(),
            0xFF81D4FA.toInt(),
            0xFFFFCC80.toInt()
        )

        for (i in 0 until count) {
            val baseA = rnd.nextFloat() * 0.6f + 0.2f
            val r = rnd.nextFloat() * 2.2f + 0.8f
            stars.add(
                Star(
                    x = rnd.nextFloat() * w,
                    y = rnd.nextFloat() * h,
                    radius = r,
                    baseAlpha = baseA,
                    currentAlpha = baseA,
                    twinkleSpeed = rnd.nextFloat() * 2f + 1f,
                    color = starColors[rnd.nextInt(starColors.size)]
                )
            )
        }
        animator?.start()
    }

    private fun updateStars() {
        for (star in stars) {
            val wave = (Math.sin((phase * star.twinkleSpeed).toDouble()).toFloat() + 1f) / 2f
            star.currentAlpha = (star.baseAlpha * 0.4f + wave * star.baseAlpha * 0.6f).coerceIn(0.1f, 1f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (star in stars) {
            starPaint.color = star.color
            starPaint.alpha = (star.currentAlpha * 255).toInt()
            canvas.drawCircle(star.x, star.y, star.radius, starPaint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}

