package io.momen.screenbalance

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.ceil

class MainActivity : Activity() {
    private val preferences by lazy { FocusGuardPreferences(this) }
    private val targetChecks = mutableMapOf<String, CheckBox>()

    private lateinit var guardSwitch: CheckBox
    private lateinit var accessibilityStatus: TextView
    private lateinit var shizukuStatus: TextView
    private lateinit var passStatus: TextView
    private lateinit var passDurationLabel: TextView
    private lateinit var passDurationSlider: SeekBar
    private lateinit var startPassButton: Button
    private lateinit var endPassButton: Button
    private lateinit var durationLabel: TextView
    private lateinit var durationSlider: SeekBar
    private lateinit var customAppsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        setContentView(createContent())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun createContent(): View {
        return ScrollView(this).apply {
            setBackgroundColor(COLOR_BACKGROUND)
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(24), dp(20), dp(32))

                    addView(titleView("屏衡"))
                    addView(
                        bodyView("自定义连续使用上限。到时会返回桌面，并对触发上限的应用启用五分钟冷静期。"),
                        layoutParams(top = 8, bottom = 20),
                    )

                    addView(card().apply {
                        addView(sectionTitle("守护状态"))
                        guardSwitch = CheckBox(this@MainActivity).apply {
                            text = "启用使用守护"
                            textSize = 16f
                            setOnCheckedChangeListener { _, checked ->
                                preferences.setGuardEnabled(checked)
                                refresh()
                            }
                        }
                        addView(guardSwitch, layoutParams(top = 8))
                        accessibilityStatus = bodyView("")
                        addView(accessibilityStatus, layoutParams(top = 8))
                        shizukuStatus = bodyView("")
                        addView(shizukuStatus, layoutParams(top = 8))
                        addView(
                            primaryButton("前往开启无障碍权限") {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            layoutParams(top = 12),
                        )
                        addView(
                            secondaryButton("启动或授权 Shizuku") {
                                when (ShizukuAccess.status(this@MainActivity)) {
                                    ShizukuStatus.AUTHORIZED -> Unit
                                    ShizukuStatus.NEEDS_PERMISSION -> ShizukuAccess.requestPermission()
                                    ShizukuStatus.NOT_INSTALLED,
                                    ShizukuStatus.NOT_RUNNING,
                                    -> ShizukuAccess.openManager(this@MainActivity)
                                }
                            },
                            layoutParams(top = 8),
                        )
                    }, layoutParams(bottom = 16))

                    addView(card().apply {
                        addView(sectionTitle("守护时间"))
                        durationLabel = sectionTitle("")
                        addView(durationLabel, layoutParams(top = 10))
                        durationSlider = SeekBar(this@MainActivity).apply {
                            max = ((GuardDuration.MAX_MILLIS - GuardDuration.MIN_MILLIS) /
                                GuardDuration.STEP_MILLIS).toInt()
                            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(
                                    seekBar: SeekBar,
                                    progress: Int,
                                    fromUser: Boolean,
                                ) {
                                    val durationMillis = GuardDuration.MIN_MILLIS +
                                        progress * GuardDuration.STEP_MILLIS
                                    durationLabel.text = "当前：${GuardDuration.format(durationMillis)}"
                                    if (fromUser) {
                                        preferences.setGuardDurationMillis(durationMillis)
                                    }
                                }

                                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                            })
                        }
                        addView(durationSlider, layoutParams(top = 6))
                        addView(bodyView("范围：1 秒至 3 分钟，每格 1 秒。"))
                    }, layoutParams(bottom = 16))

                    addView(card().apply {
                        addView(sectionTitle("扫码、付款等必要操作"))
                        passStatus = bodyView("")
                        addView(passStatus, layoutParams(top = 8))
                        passDurationLabel = sectionTitle("")
                        addView(passDurationLabel, layoutParams(top = 14))
                        passDurationSlider = SeekBar(this@MainActivity).apply {
                            max = TemporaryPassDuration.MAX_MINUTES - TemporaryPassDuration.MIN_MINUTES
                            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(
                                    seekBar: SeekBar,
                                    progress: Int,
                                    fromUser: Boolean,
                                ) {
                                    val minutes = TemporaryPassDuration.MIN_MINUTES + progress
                                    updatePassDuration(minutes)
                                    if (fromUser) {
                                        preferences.setTemporaryPassMinutes(minutes)
                                    }
                                }

                                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                            })
                        }
                        addView(passDurationSlider, layoutParams(top = 6))
                        addView(bodyView("范围：1 至 10 分钟，每格 1 分钟。"))
                        startPassButton = primaryButton("临时放行") {
                            startTemporaryPass()
                        }
                        addView(startPassButton, layoutParams(top = 12))
                        endPassButton = secondaryButton("立即恢复守护") {
                            preferences.endTemporaryPass()
                            refresh()
                        }
                        addView(endPassButton, layoutParams(top = 8))
                    }, layoutParams(bottom = 16))

                    addView(card().apply {
                        addView(sectionTitle("受限应用"))
                        addView(
                            bodyView(
                                "所有勾选应用共用连续使用计时；同一应用的双开也会受限。"
                                    + "微信仅在视频号页面计时，聊天、扫码和付款不受限制。",
                            ),
                            layoutParams(top = 8, bottom = 12),
                        )
                        addView(sectionTitle("推荐受限应用"))
                        FocusGuardPreferences.RECOMMENDED_APPS.forEach { target ->
                            val checkBox = CheckBox(this@MainActivity).apply {
                                text = target.label
                                textSize = 16f
                                setOnCheckedChangeListener { _, checked ->
                                    preferences.setProtected(target.packageName, checked)
                                }
                            }
                            targetChecks[target.packageName] = checkBox
                            addView(checkBox)
                        }

                        addView(sectionTitle("其他受限应用"), layoutParams(top = 14))
                        customAppsContainer = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                        }
                        addView(customAppsContainer, layoutParams(top = 6))
                        addView(
                            secondaryButton("从手机选择应用") {
                                startActivity(
                                    Intent(this@MainActivity, AppSelectionActivity::class.java),
                                )
                            },
                            layoutParams(top = 10),
                        )
                    }, layoutParams(bottom = 16))

                    addView(card().apply {
                        addView(sectionTitle("执行方式"))
                        addView(
                            bodyView(
                                "到达上限后，屏衡会通过 Shizuku 强制停止触发上限的应用。"
                                    + "冷静期内再次打开对应受限页面，会立即再次停止。",
                            ),
                            layoutParams(top = 8),
                        )
                    })
                },
            )
        }
    }

    private fun refresh() {
        guardSwitch.isChecked = preferences.isGuardEnabled()
        val durationMillis = preferences.guardDurationMillis()
        durationSlider.progress = ((durationMillis - GuardDuration.MIN_MILLIS) /
            GuardDuration.STEP_MILLIS).toInt()
        durationLabel.text = "当前：${GuardDuration.format(durationMillis)}"
        accessibilityStatus.text = if (isAccessibilityServiceEnabled()) {
            "无障碍权限：已开启，守护会在后台生效。"
        } else {
            "无障碍权限：未开启。必须开启后才能识别前台应用并执行拦截。"
        }
        shizukuStatus.text = when (ShizukuAccess.status(this)) {
            ShizukuStatus.AUTHORIZED -> "Shizuku：已授权，可强制停止前台与后台进程。"
            ShizukuStatus.NEEDS_PERMISSION -> "Shizuku：等待授权。点击下方按钮并允许屏衡。"
            ShizukuStatus.NOT_RUNNING -> "Shizuku：未运行。点击下方按钮启动后返回。"
            ShizukuStatus.NOT_INSTALLED -> "Shizuku：未安装，无法强制停止其他应用。"
        }

        val passMinutes = preferences.temporaryPassMinutes()
        passDurationSlider.progress = passMinutes - TemporaryPassDuration.MIN_MINUTES
        updatePassDuration(passMinutes)
        val remainingPass = preferences.temporaryPassRemainingMillis()
        passStatus.text = if (remainingPass > 0L) {
            "当前已放行，剩余 ${ceil(remainingPass / 60_000.0).toInt()} 分钟。"
        } else {
            "未放行。点击下方按钮后，可完成扫码、付款或登录。"
        }
        endPassButton.isEnabled = remainingPass > 0L

        targetChecks.forEach { (packageName, checkBox) ->
            checkBox.isChecked = preferences.isProtected(packageName)
        }
        refreshCustomApps()
    }

    private fun updatePassDuration(minutes: Int) {
        passDurationLabel.text = "放行时长：${TemporaryPassDuration.format(minutes)}"
        startPassButton.text = "临时放行 ${TemporaryPassDuration.format(minutes)}"
    }

    private fun startTemporaryPass() {
        val minutes = preferences.temporaryPassMinutes()
        preferences.startTemporaryPass(minutes)
        Toast.makeText(
            this,
            "已临时放行 ${TemporaryPassDuration.format(minutes)}",
            Toast.LENGTH_SHORT,
        ).show()
        refresh()
    }

    private fun refreshCustomApps() {
        customAppsContainer.removeAllViews()
        val selectedApps = preferences.customProtectedPackages()
            .map { packageName -> packageName to preferences.labelFor(packageName) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { (_, label) -> label })
        if (selectedApps.isEmpty()) {
            customAppsContainer.addView(bodyView("尚未选择其他应用。"))
            return
        }

        selectedApps.forEach { (packageName, label) ->
            customAppsContainer.addView(
                CheckBox(this).apply {
                    text = "$label\n$packageName"
                    textSize = 16f
                    isChecked = true
                    setOnCheckedChangeListener { _, checked ->
                        if (!checked) {
                            preferences.setProtected(packageName, false)
                            refreshCustomApps()
                        }
                    }
                },
            )
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        return manager
            .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo.serviceInfo
                serviceInfo.packageName == packageName &&
                    serviceInfo.name == FocusGuardAccessibilityService::class.java.name
            }
    }

    private fun titleView(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(COLOR_INK)
        textSize = 32f
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(COLOR_INK)
        textSize = 19f
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun bodyView(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(COLOR_MUTED)
        textSize = 15f
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = roundedBackground(COLOR_SURFACE, dp(18).toFloat())
    }

    private fun primaryButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        isAllCaps = false
        textSize = 16f
        background = roundedBackground(COLOR_ACCENT, dp(12).toFloat())
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { onClick() }
    }

    private fun secondaryButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setTextColor(COLOR_ACCENT)
        isAllCaps = false
        textSize = 16f
        background = roundedBackground(COLOR_BACKGROUND, dp(12).toFloat())
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { onClick() }
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    private fun layoutParams(
        top: Int = 0,
        bottom: Int = 0,
    ) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        setMargins(0, dp(top), 0, dp(bottom))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val COLOR_BACKGROUND = Color.parseColor("#F7F8FA")
        val COLOR_SURFACE = Color.WHITE
        val COLOR_INK = Color.parseColor("#1A1C1E")
        val COLOR_MUTED = Color.parseColor("#5D6470")
        val COLOR_ACCENT = Color.parseColor("#2457D6")
    }
}
