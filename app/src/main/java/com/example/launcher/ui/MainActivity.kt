package com.example.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.launcher.R
import com.example.launcher.data.AppInfo
import com.example.launcher.data.AppRepository
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Home screen.
 *  - iPhone-style: paged icon grid + dock + page dots.
 *  - Pixel-style: swipe up to open searchable app drawer.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var repo: AppRepository
    private lateinit var pager: ViewPager2
    private lateinit var dots: LinearLayout
    private lateinit var dock: RecyclerView
    private lateinit var drawer: View
    private lateinit var drawerList: RecyclerView
    private lateinit var search: EditText

    private lateinit var pagerAdapter: HomePagerAdapter
    private lateinit var dockAdapter: AppAdapter
    private lateinit var drawerAdapter: AppAdapter

    private var allApps: List<AppInfo> = emptyList()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = loadApps()
    }

    private lateinit var swipeDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repo = AppRepository(this)

        pager = findViewById(R.id.pager)
        dots = findViewById(R.id.page_dots)
        dock = findViewById(R.id.dock)
        drawer = findViewById(R.id.drawer)
        drawerList = findViewById(R.id.drawer_list)
        search = findViewById(R.id.search)

        val homeColumns = resources.getInteger(R.integer.home_columns)
        pagerAdapter = HomePagerAdapter(homeColumns, repo::launch)
        pager.adapter = pagerAdapter
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateDots(position)
        })

        dockAdapter = AppAdapter(emptyList(), showLabels = false, onClick = repo::launch)
        dock.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        dock.adapter = dockAdapter

        drawerAdapter = AppAdapter(emptyList()) { closeDrawer(); repo.launch(it) }
        drawerList.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.drawer_columns))
        drawerList.adapter = drawerAdapter
        search.doAfterTextChanged { text ->
            val q = text?.toString().orEmpty().trim().lowercase()
            drawerAdapter.submit(if (q.isEmpty()) allApps else allApps.filter { it.label.lowercase().contains(q) })
        }

        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                if (abs(dy) > abs(dx) * 1.5f && abs(vy) > 800f) {
                    if (dy < 0 && drawer.visibility != View.VISIBLE) openDrawer()
                    else if (dy > 0 && drawer.visibility == View.VISIBLE && !drawerList.canScrollVertically(-1)) closeDrawer()
                    return true
                }
                return false
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawer.visibility == View.VISIBLE) closeDrawer() else pager.currentItem = 0
            }
        })

        loadApps()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
    }

    override fun onStop() {
        unregisterReceiver(packageReceiver)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button pressed while already on home: reset state.
        if (intent.action == Intent.ACTION_MAIN) {
            closeDrawer()
            pager.currentItem = 0
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun loadApps() {
        executor.execute {
            val apps = repo.loadApps()
            val dockPkgs = repo.defaultDockPackages()
            main.post { if (!isDestroyed) bind(apps, dockPkgs) }
        }
    }

    private fun bind(apps: List<AppInfo>, dockPackages: List<String>) {
        allApps = apps
        val dockApps = dockPackages.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
        val homeApps = apps - dockApps.toSet()
        val columns = resources.getInteger(R.integer.home_columns)
        val rows = resources.getInteger(R.integer.home_rows)

        dockAdapter.submit(dockApps)
        dock.layoutManager = GridLayoutManager(this, dockApps.size.coerceAtLeast(1))
        pagerAdapter.submit(homeApps, columns * rows)
        drawerAdapter.submit(apps)
        buildDots(pagerAdapter.itemCount)
        updateDots(pager.currentItem)
    }

    private fun buildDots(count: Int) {
        dots.removeAllViews()
        val size = (8 * resources.displayMetrics.density).toInt()
        repeat(count) {
            val dot = ImageView(this).apply {
                setImageResource(android.R.drawable.presence_invisible)
                layoutParams = LinearLayout.LayoutParams(size * 2, size * 2)
            }
            dots.addView(dot)
        }
        dots.visibility = if (count > 1) View.VISIBLE else View.INVISIBLE
    }

    private fun updateDots(selected: Int) {
        for (i in 0 until dots.childCount) dots.getChildAt(i).alpha = if (i == selected) 1f else 0.35f
    }

    private fun openDrawer() {
        drawer.alpha = 0f
        drawer.visibility = View.VISIBLE
        drawer.translationY = drawer.height * 0.2f
        drawer.animate().alpha(1f).translationY(0f).setDuration(200).start()
    }

    private fun closeDrawer() {
        if (drawer.visibility != View.VISIBLE) return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
        search.setText("")
        drawer.animate().alpha(0f).translationY(drawer.height * 0.2f).setDuration(150)
            .withEndAction { drawer.visibility = View.GONE }.start()
    }
}

