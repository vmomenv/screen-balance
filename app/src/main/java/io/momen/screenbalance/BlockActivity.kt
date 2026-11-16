package io.momen.screenbalance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.ceil

class BlockActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var blockedUntilMillis = 0L
    private lateinit var countdown: TextView

    private val ticker = object : Runnable {
        override fun run() {
            val remainingMillis = (blockedUntilMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            countdown.text = if (remainingMillis > 0L) {
                "冷静期剩余 ${ceil(remainingMillis / 60_000.0).toInt()} 分钟"
            } else {
                "冷静期已结束"
            }
            if (remainingMillis > 0L) {
                handler.postDelayed(this, 1_000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockedUntilMillis = intent.getLongExtra(EXTRA_BLOCKED_UNTIL, 0L)
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL) ?: "受限应用"
        val guardDurationMillis = intent.getLongExtra(
            EXTRA_GUARD_DURATION,
            GuardDuration.DEFAULT_MILLIS,
        )
        LiquidGlass.applyWindowChrome(this)

        val card = LiquidGlass.glassPanel(this, 28).apply {
            addView(LiquidGlass.eyebrow(this@BlockActivity, "COOLDOWN  /  先离开屏幕"))
            addView(
                LiquidGlass.heading(this@BlockActivity, "本次使用已结束", 31f),
                layoutParams(top = 8),
            )
            addView(
                LiquidGlass.body(
                    this@BlockActivity,
                    appLabel + " 已连续使用 " + GuardDuration.format(guardDurationMillis) +
                        System.lineSeparator() + "先离开屏幕，稍后再回来。",
                ),
                layoutParams(top = 12),
            )
            countdown = LiquidGlass.heading(this@BlockActivity, "", 22f).apply {
                setTextColor(LiquidGlass.WARNING)
                gravity = Gravity.CENTER
            }
            addView(countdown, layoutParams(top = 22))
            addView(
                LiquidGlass.primaryButton(this@BlockActivity, "回到桌面") {
                    goHome()
                },
                layoutParams(top = 26),
            )
        }
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(24), dp(18), dp(24))
            addView(
                card,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        setContentView(LiquidGlass.root(this, screen))
    }

    override fun onStart() {
        super.onStart()
        handler.post(ticker)
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        super.onStop()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }

    private fun layoutParams(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        topMargin = dp(top)
    }

    private fun dp(value: Int): Int = LiquidGlass.dp(this, value)

    companion object {
        const val EXTRA_APP_LABEL = "app_label"
        const val EXTRA_BLOCKED_UNTIL = "blocked_until"
        const val EXTRA_GUARD_DURATION = "guard_duration"
    }
}
