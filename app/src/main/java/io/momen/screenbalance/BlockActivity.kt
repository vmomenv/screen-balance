package io.momen.screenbalance

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
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

        window.statusBarColor = COLOR_DARK
        window.navigationBarColor = COLOR_DARK
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(28), dp(28), dp(28), dp(28))
                setBackgroundColor(COLOR_DARK)

                addView(TextView(this@BlockActivity).apply {
                    text = "本次使用已结束"
                    setTextColor(Color.WHITE)
                    textSize = 30f
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(this@BlockActivity).apply {
                    text = "$appLabel 已连续使用 ${GuardDuration.format(guardDurationMillis)}。\n先离开屏幕，稍后再回来。"
                    setTextColor(COLOR_SUBTLE)
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setLineSpacing(dp(5).toFloat(), 1f)
                }, params(top = 18))
                countdown = TextView(this@BlockActivity).apply {
                    setTextColor(COLOR_NOTICE)
                    textSize = 18f
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(countdown, params(top = 24))
                addView(Button(this@BlockActivity).apply {
                    text = "回到桌面"
                    isAllCaps = false
                    textSize = 17f
                    setTextColor(COLOR_DARK)
                    background = roundedBackground(Color.WHITE, dp(12).toFloat())
                    setPadding(dp(18), dp(10), dp(18), dp(10))
                    setOnClickListener { goHome() }
                }, params(top = 28))
            },
        )
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

    private fun params(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        setMargins(0, dp(top), 0, 0)
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_APP_LABEL = "app_label"
        const val EXTRA_BLOCKED_UNTIL = "blocked_until"
        const val EXTRA_GUARD_DURATION = "guard_duration"

        private val COLOR_DARK = Color.parseColor("#10131A")
        private val COLOR_SUBTLE = Color.parseColor("#CDD4E2")
        private val COLOR_NOTICE = Color.parseColor("#9DC1FF")
    }
}
