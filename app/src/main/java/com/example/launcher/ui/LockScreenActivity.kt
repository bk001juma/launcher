package com.example.launcher.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.launcher.R
import kotlin.math.abs

class LockScreenActivity : AppCompatActivity() {

    private lateinit var lockScreenRoot: View
    private lateinit var neuralNetworkView: NeuralNetworkLockView
    private lateinit var unlockHandleContainer: View
    private lateinit var iconUnlockPadlock: TextView
    private lateinit var textSwipeToUnlock: TextView

    private var initialTouchY = 0f
    private var isDragging = false
    private var velocityTracker: VelocityTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureLockScreenFlags()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_lock_screen)

        lockScreenRoot = findViewById(R.id.lock_screen_root)
        neuralNetworkView = findViewById(R.id.neural_network_view)
        unlockHandleContainer = findViewById(R.id.unlock_handle_container)
        iconUnlockPadlock = findViewById(R.id.icon_unlock_padlock)
        textSwipeToUnlock = findViewById(R.id.text_swipe_to_unlock)

        startPulsingUnlockHint()
        setupSwipeToUnlock()

        // Lock screen disables back press from exiting
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Keep locked
            }
        })
    }

    private fun configureLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }

    private fun startPulsingUnlockHint() {
        val translateY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -12f, 0f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 1.0f, 0.7f)

        ObjectAnimator.ofPropertyValuesHolder(unlockHandleContainer, translateY, alpha).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun setupSwipeToUnlock() {
        lockScreenRoot.setOnTouchListener { _, event ->
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain()
            }
            velocityTracker?.addMovement(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialTouchY - event.rawY
                    if (deltaY > 20f) {
                        isDragging = true
                        // Translate upward and fade slightly
                        lockScreenRoot.translationY = -deltaY
                        val maxDrag = resources.displayMetrics.heightPixels * 0.45f
                        val progress = (deltaY / maxDrag).coerceIn(0f, 1f)
                        lockScreenRoot.alpha = 1f - (progress * 0.6f)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val deltaY = initialTouchY - event.rawY
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = velocityTracker?.yVelocity ?: 0f

                    val threshold = resources.displayMetrics.heightPixels * 0.22f
                    if (deltaY > threshold || velocityY < -1200f) {
                        // Confirmed Unlock!
                        unlockDevice()
                    } else {
                        // Spring back to rest position
                        lockScreenRoot.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(280)
                            .setInterpolator(OvershootInterpolator(1.2f))
                            .start()
                    }

                    velocityTracker?.recycle()
                    velocityTracker = null
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun unlockDevice() {
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        lockScreenRoot.animate()
            .translationY(-screenHeight)
            .alpha(0f)
            .setDuration(220)
            .withEndAction {
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguardManager != null && keyguardManager.isKeyguardLocked) {
                    keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            proceedToHome()
                        }

                        override fun onDismissError() {
                            proceedToHome()
                        }

                        override fun onDismissCancelled() {
                            // User cancelled PIN/biometrics challenge; restore lock screen
                            lockScreenRoot.animate()
                                .translationY(0f)
                                .alpha(1f)
                                .setDuration(240)
                                .start()
                        }
                    })
                } else {
                    proceedToHome()
                }
            }
            .start()
    }

    private fun proceedToHome() {
        val homeIntent = android.content.Intent(this, MainActivity::class.java).apply {
            action = android.content.Intent.ACTION_MAIN
            addCategory(android.content.Intent.CATEGORY_HOME)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        startActivity(homeIntent)
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    override fun onResume() {
        super.onResume()
        neuralNetworkView.startAnimation()
    }

    override fun onPause() {
        neuralNetworkView.stopAnimation()
        super.onPause()
    }
}
