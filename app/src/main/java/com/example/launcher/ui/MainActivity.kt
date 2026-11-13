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
import android.widget.TextView
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

    // Default Launcher Role Picker Launcher
    private val defaultLauncherResult = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        if (isDefaultLauncher()) {
            android.widget.Toast.makeText(this, "✨ Cosmic Launcher is now your default home!", android.widget.Toast.LENGTH_SHORT).show()
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
        override fun onReceive(context: Context?, intent: Intent?) {
            repo.clearCache()
            loadInstalledApps(isInstantCacheBound = false, forceReload = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = AppRepository(this)

        initViews()
        setupListeners()
        setupGestures()

        // 1. Instant Cache Binding: 0ms latency on launch / re-entry
        val cached = repo.getCachedApps()
        val cachedDock = repo.getCachedDockPackages()
        if (!cached.isNullOrEmpty()) {
            bindData(cached, cachedDock ?: emptyList())
        }

        // 2. Progressive Fast-Loader (instant Solar apps, background Drawer)
        loadInstalledApps(isInstantCacheBound = (!cached.isNullOrEmpty()))

        checkFirstTimeDefaultLauncherPrompt()
        startLockScreenService()
    }

    private fun startLockScreenService() {
        try {
            val serviceIntent = Intent(this, com.example.launcher.service.LockScreenService::class.java)
            startService(serviceIntent)
        } catch (_: Exception) {}
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
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (drawerScreen.visibility != View.VISIBLE) {
                    startActivity(Intent(this@MainActivity, LockScreenActivity::class.java))
                    return true
                }
                return false
            }

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

    private fun loadInstalledApps(isInstantCacheBound: Boolean = false, forceReload: Boolean = false) {
        val savedSolar = getSavedSolarPackages()

        executor.execute {
            // Stage 1: Ultra-fast Solar Orbits population (<15ms) if orbits are empty
            if (!isInstantCacheBound || forceReload) {
                val fastSolarApps = repo.loadSolarApps(savedSolar)
                mainHandler.post {
                    if (!isDestroyed && (solarOrbitView.getAppsCount() == 0 || forceReload)) {
                        solarOrbitView.setApps(fastSolarApps)
                    }
                }
            }

            // Stage 2: Full background catalog indexing for Constellation Drawer & Dock
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

        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_Launcher_BottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_solar_apps_picker, null)
        bottomSheet.setContentView(view)
        bottomSheet.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        bottomSheet.setOnShowListener { dialog ->
            val d = dialog as com.google.android.material.bottomsheet.BottomSheetDialog
            d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
        }

        val savedPackages = getSavedSolarPackages()
        val badge = view.findViewById<TextView>(R.id.selected_count_badge)
        val searchInput = view.findViewById<EditText>(R.id.picker_search_input)
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.picker_recycler_view)
        val btnSave = view.findViewById<TextView>(R.id.btn_save_orbits)
        val btnReset = view.findViewById<TextView>(R.id.btn_reset_defaults)

        val initialCount = if (savedPackages.isNotEmpty()) savedPackages.size else 10
        badge.text = "$initialCount selected"

        val adapter = SolarAppPickerAdapter(allApps, savedPackages) { count ->
            badge.text = "$count selected"
        }
        recyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 4)
        recyclerView.adapter = adapter

        searchInput.doAfterTextChanged {
            adapter.filter(it?.toString().orEmpty())
        }

        btnSave.setOnClickListener {
            saveSolarPackages(adapter.selectedPackages)
            val newSolarApps = if (adapter.selectedPackages.isNotEmpty()) {
                allApps.filter { adapter.selectedPackages.contains(it.packageName) }
            } else {
                allApps.take(10)
            }
            solarOrbitView.setApps(newSolarApps)
            bottomSheet.dismiss()
        }

        btnReset.setOnClickListener {
            saveSolarPackages(emptySet())
            solarOrbitView.setApps(allApps.take(10))
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showCosmicCustomizationMenu() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_Launcher_BottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_cosmic_customization, null)
        bottomSheet.setContentView(view)
        bottomSheet.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        bottomSheet.setOnShowListener { dialog ->
            val d = dialog as com.google.android.material.bottomsheet.BottomSheetDialog
            d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
        }

        // Configure Default Launcher Card
        val isDefault = isDefaultLauncher()
        val textStatus = view.findViewById<TextView>(R.id.text_default_launcher_status)
        val badge = view.findViewById<TextView>(R.id.badge_default_launcher)
        val icon = view.findViewById<TextView>(R.id.icon_default_launcher)

        if (isDefault) {
            icon.text = "⭐"
            textStatus.text = "Active default home screen"
            textStatus.setTextColor(0xFF80DEEA.toInt())
            badge.text = "ACTIVE"
            badge.setTextColor(0xFF80DEEA.toInt())
        } else {
            icon.text = "🏠"
            textStatus.text = "Tap to set as your primary home app"
            textStatus.setTextColor(0xFFFFD54F.toInt())
            badge.text = "SET DEFAULT"
            badge.setTextColor(0xFFFFD54F.toInt())
        }

        view.findViewById<View>(R.id.card_set_default_launcher).setOnClickListener {
            bottomSheet.dismiss()
            requestDefaultLauncher()
        }

        view.findViewById<View>(R.id.card_neural_lockscreen).setOnClickListener {
            bottomSheet.dismiss()
            startActivity(Intent(this, LockScreenActivity::class.java))
        }

        view.findViewById<View>(R.id.card_choose_solar_apps).setOnClickListener {
            bottomSheet.dismiss()
            showSolarAppSelectionDialog()
        }

        view.findViewById<View>(R.id.card_custom_wallpaper).setOnClickListener {
            bottomSheet.dismiss()
            wallpaperPickerLauncher.launch("image/*")
        }

        view.findViewById<View>(R.id.card_reset_space).setOnClickListener {
            bottomSheet.dismiss()
            resetDefaultWallpaper()
        }

        bottomSheet.show()
    }

    private fun isDefaultLauncher(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_HOME) == true
        } else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == packageName
        }
    }

    private fun requestDefaultLauncher() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
                    defaultLauncherResult.launch(intent)
                    return
                } else {
                    android.widget.Toast.makeText(this, "Cosmic Launcher is already your default home", android.widget.Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        // Fallback for Android 9 or below, or if RoleManager is not supported
        try {
            val homeSettingsIntent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
            if (homeSettingsIntent.resolveActivity(packageManager) != null) {
                defaultLauncherResult.launch(homeSettingsIntent)
                return
            }
        } catch (_: Exception) {}

        try {
            val manageAppsIntent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            if (manageAppsIntent.resolveActivity(packageManager) != null) {
                defaultLauncherResult.launch(manageAppsIntent)
                return
            }
        } catch (_: Exception) {}

        try {
            defaultLauncherResult.launch(Intent(android.provider.Settings.ACTION_SETTINGS))
        } catch (_: Exception) {}
    }

    private fun checkFirstTimeDefaultLauncherPrompt() {
        val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val hasPrompted = prefs.getBoolean("has_prompted_default_home", false)
        if (!hasPrompted && !isDefaultLauncher()) {
            prefs.edit().putBoolean("has_prompted_default_home", true).apply()
            mainHandler.postDelayed({
                if (!isDestroyed && !isFinishing && !isDefaultLauncher()) {
                    showSetDefaultLauncherDialog()
                }
            }, 1200)
        }
    }

    private fun showSetDefaultLauncherDialog() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_Launcher_BottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_set_default_launcher, null)
        bottomSheet.setContentView(view)
        bottomSheet.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        bottomSheet.setOnShowListener { dialog ->
            val d = dialog as com.google.android.material.bottomsheet.BottomSheetDialog
            d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
        }

        view.findViewById<View>(R.id.btn_default_cancel).setOnClickListener {
            bottomSheet.dismiss()
        }

        view.findViewById<View>(R.id.btn_default_confirm).setOnClickListener {
            bottomSheet.dismiss()
            requestDefaultLauncher()
        }

        bottomSheet.show()
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

        // Guard against any empty orbit state on re-entry
        if (solarOrbitView.getAppsCount() == 0) {
            val cached = repo.getCachedApps()
            if (!cached.isNullOrEmpty()) {
                bindData(cached, repo.getCachedDockPackages() ?: emptyList())
            } else {
                loadInstalledApps()
            }
        }
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
