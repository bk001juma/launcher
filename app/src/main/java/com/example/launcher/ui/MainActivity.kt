package com.example.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.launcher.R
import com.example.launcher.data.AppInfo
import com.example.launcher.data.AppRepository
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var repo: AppRepository

    // Screen 1: Solar Orbit Home
    private lateinit var homeScreen: View
    private lateinit var solarOrbitView: SolarOrbitView
    private lateinit var homeSearchBar: View
    private lateinit var dockLayout: LinearLayout
    private lateinit var customWallpaperView: ImageView
    private lateinit var wallpaperThemeTint: View

    // Custom Wallpaper Picker Launcher
    private val wallpaperPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            saveAndApplyCustomWallpaper(uri)
        }
    }

    // Screen 2: Constellation Universe Drawer
    private lateinit var drawerScreen: View
    private lateinit var drawerSearchInput: EditText
    private lateinit var constellationView: ConstellationUniverseView
    private lateinit var astrolabeScroller: AstrolabeScrollerView

    private var allApps: List<AppInfo> = emptyList()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var swipeDetector: GestureDetector

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = loadInstalledApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = AppRepository(this)

        initViews()
        setupListeners()
        setupGestures()

        loadInstalledApps()
    }

    private fun initViews() {
        homeScreen = findViewById(R.id.home_screen)
        solarOrbitView = findViewById(R.id.solar_orbit_view)
        homeSearchBar = findViewById(R.id.home_search_bar)
        dockLayout = findViewById(R.id.dock_layout)
        customWallpaperView = findViewById(R.id.custom_wallpaper_view)
        wallpaperThemeTint = findViewById(R.id.wallpaper_theme_tint)

        loadSavedWallpaper()

        drawerScreen = findViewById(R.id.drawer_screen)
        drawerSearchInput = findViewById(R.id.drawer_search_input)
        constellationView = findViewById(R.id.constellation_view)
        astrolabeScroller = findViewById(R.id.astrolabe_scroller)
    }

    private fun setupListeners() {
        // Launch app on planetary orb tap
        solarOrbitView.onAppClick = { app ->
            repo.launch(app)
        }

        // Long press on Sun or orbital field opens Customization Menu (Apps + Wallpaper)
        solarOrbitView.onLongClickSolar = {
            showCosmicCustomizationMenu()
        }

        // Setup freely draggable custom search bar
        setupDraggableSearchBar()

        // Drawer constellation node click
        constellationView.onAppClick = { app ->
            closeDrawer()
            repo.launch(app)
        }

        // Astrolabe A-Z scroller letter tap/drag
        astrolabeScroller.onLetterSelected = { letter ->
            constellationView.filterByLetter(letter)
        }

        // Universe search filter
        drawerSearchInput.doAfterTextChanged { text ->
            constellationView.filter(text?.toString().orEmpty())
        }

        // Back button closes drawer
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerScreen.visibility == View.VISIBLE) {
                    closeDrawer()
                }
            }
        })
    }

    private fun setupDraggableSearchBar() {
        val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

        // Restore saved custom position if user previously moved it
        homeSearchBar.post {
            val savedX = prefs.getFloat("search_x", -1f)
            val savedY = prefs.getFloat("search_y", -1f)
            if (savedX >= 0 && savedY >= 0) {
                homeSearchBar.x = savedX
                homeSearchBar.y = savedY
            }
        }

        var dX = 0f
        var dY = 0f
        var touchDownX = 0f
        var touchDownY = 0f
        var isDraggingBar = false

        homeSearchBar.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    isDraggingBar = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val moveDist = Math.hypot((event.rawX - touchDownX).toDouble(), (event.rawY - touchDownY).toDouble())
                    if (moveDist > 16.0) {
                        if (!isDraggingBar) {
                            isDraggingBar = true
                            v.animate().scaleX(1.04f).scaleY(1.04f).alpha(0.85f).setDuration(100).start()
                        }
                        val parent = v.parent as View
                        val maxX = (parent.width - v.width).toFloat().coerceAtLeast(0f)
                        val maxY = (parent.height - v.height).toFloat().coerceAtLeast(0f)

                        v.x = (event.rawX + dX).coerceIn(0f, maxX)
                        v.y = (event.rawY + dY).coerceIn(0f, maxY)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDraggingBar) {
                        v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(120).start()
                        prefs.edit()
                            .putFloat("search_x", v.x)
                            .putFloat("search_y", v.y)
                            .apply()
                    } else {
                        // Quick tap opens search drawer
                        openDrawer(focusSearch = true)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingBar) {
                        v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(120).start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupGestures() {
        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x

                // Vertical swipe gesture
                if (abs(dy) > abs(dx) * 1.4f && abs(vy) > 600f) {
                    if (dy < 0 && drawerScreen.visibility != View.VISIBLE) {
                        // Swipe Up -> Open Constellation Universe
                        openDrawer()
                        return true
                    } else if (dy > 0 && drawerScreen.visibility == View.VISIBLE) {
                        // Swipe Down -> Close Drawer back to Solar Orbit
                        closeDrawer()
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun loadInstalledApps() {
        executor.execute {
            val apps = repo.loadApps()
            val dockPkgs = repo.defaultDockPackages()
            mainHandler.post {
                if (!isDestroyed) {
                    bindData(apps, dockPkgs)
                }
            }
        }
    }

    private fun bindData(apps: List<AppInfo>, dockPackages: List<String>) {
        allApps = apps

        // 1. Bind Solar Orbit (Home) with user's customized apps or defaults
        val savedSolar = getSavedSolarPackages()
        val solarApps = if (savedSolar.isNotEmpty()) {
            apps.filter { savedSolar.contains(it.packageName) }
        } else {
            apps.take(10)
        }
        solarOrbitView.setApps(solarApps)

        // 2. Bind Planetary Dock (Bottom 5 apps)
        populateDock(apps, dockPackages)

        // 3. Bind Constellation Universe (Drawer)
        constellationView.setApps(apps)
    }

    private fun getSavedSolarPackages(): Set<String> {
        val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("solar_packages", null) ?: emptySet()
    }

    private fun saveSolarPackages(packages: Set<String>) {
        val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("solar_packages", packages).apply()
    }

    private fun showSolarAppSelectionDialog() {
        if (allApps.isEmpty()) return

        val savedPackages = getSavedSolarPackages()
        val appNames = allApps.map { it.label }.toTypedArray()
        val checkedItems = BooleanArray(allApps.size) { i ->
            savedPackages.contains(allApps[i].packageName)
        }

        val selectedSet = savedPackages.toMutableSet()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🪐 Choose Solar System Apps")
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                val pkg = allApps[which].packageName
                if (isChecked) {
                    selectedSet.add(pkg)
                } else {
                    selectedSet.remove(pkg)
                }
            }
            .setPositiveButton("Save & Orbit") { _, _ ->
                saveSolarPackages(selectedSet)
                val newSolarApps = if (selectedSet.isNotEmpty()) {
                    allApps.filter { selectedSet.contains(it.packageName) }
                } else {
                    allApps.take(10)
                }
                solarOrbitView.setApps(newSolarApps)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset Default") { _, _ ->
                saveSolarPackages(emptySet())
                solarOrbitView.setApps(allApps.take(10))
            }
            .show()
    }

    private fun showCosmicCustomizationMenu() {
        val options = arrayOf(
            "🪐 Choose Solar System Apps",
            "🖼️ Set Custom Wallpaper (Theme Harmonized)",
            "↺ Reset Default Space Wallpaper"
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("✨ Cosmic Customization")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSolarAppSelectionDialog()
                    1 -> wallpaperPickerLauncher.launch("image/*")
                    2 -> resetDefaultWallpaper()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveAndApplyCustomWallpaper(uri: Uri) {
        executor.execute {
            try {
                val file = java.io.File(filesDir, "custom_wallpaper.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                mainHandler.post {
                    loadSavedWallpaper()
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadSavedWallpaper() {
        val file = java.io.File(filesDir, "custom_wallpaper.jpg")
        if (file.exists()) {
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                customWallpaperView.setImageBitmap(bitmap)
                applyCosmicThemeColorFilter(customWallpaperView)
                customWallpaperView.visibility = View.VISIBLE
                wallpaperThemeTint.visibility = View.VISIBLE
            }
        } else {
            customWallpaperView.visibility = View.GONE
            wallpaperThemeTint.visibility = View.GONE
        }
    }

    private fun applyCosmicThemeColorFilter(imageView: ImageView) {
        // Harmonize ANY photo colors into the cosmic space palette (deep blue, space navy, gold contrast)
        val cm = android.graphics.ColorMatrix().apply {
            // 1. Soften high daylight saturation so it doesn't fight the glowing sun
            setSaturation(0.35f)

            // 2. Cosmic color grading: dark midnight depth with golden starlight warmth
            val cosmicScale = android.graphics.ColorMatrix(floatArrayOf(
                0.28f, 0f, 0f, 0f, -25f,   // Subdue red/magenta
                0f, 0.32f, 0f, 0f, -25f,   // Subdue green/neon
                0f, 0f, 0.58f, 0f, 10f,    // Deep space midnight blue enhancement
                0f, 0f, 0f, 1f, 0f         // Alpha intact
            ))
            postConcat(cosmicScale)
        }
        imageView.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
    }

    private fun resetDefaultWallpaper() {
        val file = java.io.File(filesDir, "custom_wallpaper.jpg")
        if (file.exists()) file.delete()
        loadSavedWallpaper()
    }

    private fun populateDock(apps: List<AppInfo>, dockPackages: List<String>) {
        dockLayout.removeAllViews()

        val dockApps = dockPackages.mapNotNull { pkg ->
            apps.firstOrNull { it.packageName == pkg }
        }.take(5)

        val finalDockApps = if (dockApps.isNotEmpty()) {
            dockApps
        } else {
            apps.take(5)
        }

        val inflater = LayoutInflater.from(this)
        for (app in finalDockApps) {
            val itemView = inflater.inflate(R.layout.item_dock_app, dockLayout, false)
            val iconView = itemView.findViewById<ImageView>(R.id.dock_icon)

            iconView.setImageDrawable(app.icon)

            // Spherical planetary rim for dock icons
            val glowBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x33121C2B)
                setStroke(3, 0x99FFD54F.toInt())
            }
            iconView.background = glowBg
            val padding = (4 * resources.displayMetrics.density).toInt()
            iconView.setPadding(padding, padding, padding, padding)

            itemView.setOnClickListener {
                repo.launch(app)
            }
            dockLayout.addView(itemView)
        }
    }

    private fun openDrawer(focusSearch: Boolean = false) {
        drawerScreen.visibility = View.VISIBLE
        drawerScreen.alpha = 0f
        drawerScreen.translationY = drawerScreen.height * 0.15f

        drawerScreen.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(240)
            .withEndAction {
                if (focusSearch) {
                    drawerSearchInput.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(drawerSearchInput, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            .start()
    }

    private fun closeDrawer() {
        if (drawerScreen.visibility != View.VISIBLE) return

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(drawerSearchInput.windowToken, 0)
        drawerSearchInput.setText("")
        constellationView.filter("")

        drawerScreen.animate()
            .alpha(0f)
            .translationY(drawerScreen.height * 0.15f)
            .setDuration(180)
            .withEndAction {
                drawerScreen.visibility = View.GONE
            }
            .start()
    }

    override fun onResume() {
        super.onResume()
        solarOrbitView.startAnimation()
    }

    override fun onPause() {
        solarOrbitView.stopAnimation()
        super.onPause()
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
        // Home button resets to Solar Orbit home
        if (intent.action == Intent.ACTION_MAIN) {
            closeDrawer()
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
