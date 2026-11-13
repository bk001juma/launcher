package com.example.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.launcher.data.AppCategory
import com.example.launcher.data.AppInfo
import kotlin.math.*

class ConstellationUniverseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onAppClick: ((AppInfo) -> Unit)? = null

    private var allApps: List<AppInfo> = emptyList()
    private var displayedApps: List<AppInfo> = emptyList()

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        letterSpacing = 0.2f
    }

    private val hexStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val hexGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val hexFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x26131D2E.toInt()
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 2f, 0xCC000000.toInt())
    }

    private data class Node(
        val app: AppInfo,
        val cx: Float,
        val cy: Float,
        val radius: Float = 42f,
        val category: AppCategory
    )

    private val nodes = mutableListOf<Node>()
    private val iconCache = mutableMapOf<String, Bitmap>()

    fun setApps(apps: List<AppInfo>) {
        this.allApps = apps
        this.displayedApps = apps
        iconCache.clear()
        recalculateLayout()
        invalidate()
    }

    fun filter(query: String) {
        val q = query.trim().lowercase()
        displayedApps = if (q.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.lowercase().contains(q) }
        }
        recalculateLayout()
        invalidate()
    }

    fun filterByLetter(letter: Char) {
        val l = letter.lowercaseChar()
        displayedApps = allApps.filter { it.label.lowercase().startsWith(l) }
        recalculateLayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateLayout()
    }

    private fun recalculateLayout() {
        nodes.clear()
        if (width <= 0 || displayedApps.isEmpty()) return

        val grouped = displayedApps.groupBy { it.category }
        val categoriesInOrder = listOf(
            AppCategory.SYSTEM,
            AppCategory.MEDIA,
            AppCategory.SOCIAL,
            AppCategory.TOOLS
        )

        val marginX = 60f
        val startY = 160f // space for top search bar
        var currentY = startY

        val hexRadius = 42f
        val hexGapX = hexRadius * 2.3f
        val hexGapY = hexRadius * 2.2f

        for (cat in categoriesInOrder) {
            val appsInCat = grouped[cat] ?: continue
            if (appsInCat.isEmpty()) continue

            // Reserve height for title
            currentY += 50f
            val sectionStartY = currentY

            val cols = 4
            for (idx in appsInCat.indices) {
                val row = idx / cols
                val col = idx % cols

                // Stagger alternate rows slightly for honeycomb constellation appearance
                val staggerOffset = if (row % 2 == 1) hexRadius * 1.15f else 0f
                val nx = marginX + hexRadius + (col * hexGapX) + staggerOffset
                val ny = sectionStartY + hexRadius + (row * hexGapY)

                if (nx < width - marginX) {
                    nodes.add(Node(appsInCat[idx], nx, ny, hexRadius, cat))
                }
            }

            val totalRows = (appsInCat.size + cols - 1) / cols
            currentY = sectionStartY + (totalRows * hexGapY) + 70f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (nodes.isEmpty()) return

        // 1. Draw Section Titles and Constellation Connecting Lines
        val groupedNodes = nodes.groupBy { it.category }

        for ((cat, catNodes) in groupedNodes) {
            val color = cat.colorHex.toInt()
            val glowColor = cat.glowHex.toInt()

            // Draw Section Title
            titlePaint.color = color
            val firstNode = catNodes.firstOrNull()
            if (firstNode != null) {
                canvas.drawText(cat.title, 60f, firstNode.cy - firstNode.radius - 28f, titlePaint)
            }

            // Draw constellation lines between neighboring nodes
            linePaint.color = glowColor
            for (i in catNodes.indices) {
                val a = catNodes[i]
                for (j in i + 1 until min(i + 3, catNodes.size)) {
                    val b = catNodes[j]
                    val dist = hypot((a.cx - b.cx).toDouble(), (a.cy - b.cy).toDouble())
                    if (dist < a.radius * 3.5f) {
                        canvas.drawLine(a.cx, a.cy, b.cx, b.cy, linePaint)
                    }
                }
            }

            // Draw each Hexagonal Node
            for (node in catNodes) {
                drawHexagonNode(canvas, node, color, glowColor)
            }
        }
    }

    private fun drawHexagonNode(canvas: Canvas, node: Node, color: Int, glowColor: Int) {
        val path = createHexagonPath(node.cx, node.cy, node.radius)

        // Draw hexagon fill
        canvas.drawPath(path, hexFillPaint)

        // Draw outer glow and stroke
        hexGlowPaint.color = glowColor
        canvas.drawPath(path, hexGlowPaint)

        hexStrokePaint.color = color
        canvas.drawPath(path, hexStrokePaint)

        // Draw app icon inside hexagon
        canvas.save()
        canvas.translate(node.cx, node.cy)
        val iconSize = (node.radius * 1.15f).toInt()
        val half = iconSize / 2
        node.app.icon.setBounds(-half, -half, half, half)
        node.app.icon.draw(canvas)
        canvas.restore()

        // Draw label below node
        canvas.drawText(node.app.label, node.cx, node.cy + node.radius + 28f, labelPaint)
    }

    private fun createHexagonPath(cx: Float, cy: Float, radius: Float): Path {
        val path = Path()
        for (i in 0 until 6) {
            val angleDeg = 60f * i - 30f // flat top or pointy top
            val rad = Math.toRadians(angleDeg.toDouble())
            val x = cx + (radius * cos(rad)).toFloat()
            val y = cy + (radius * sin(rad)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun getOrCreateIconBitmap(app: AppInfo, size: Int): Bitmap? {
        val key = app.packageName
        if (iconCache.containsKey(key)) return iconCache[key]

        return try {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            app.icon.setBounds(0, 0, size, size)
            app.icon.draw(c)
            iconCache[key] = bmp
            bmp
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val clicked = nodes.find {
                val d = hypot((event.x - it.cx).toDouble(), (event.y - it.cy).toDouble())
                d <= it.radius * 1.4f
            }
            if (clicked != null) {
                onAppClick?.invoke(clicked.app)
                return true
            }
        }
        return true
    }
}

