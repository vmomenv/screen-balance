package io.momen.screenbalance

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
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
    private lateinit var guardStatePill: TextView
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
        LiquidGlass.applyWindowChrome(this)
        setContentView(createContent())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun createContent(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(36))
        }
        page.addView(hero(), layoutParams(bottom = 18))
        page.addView(durationPanel(), layoutParams(bottom = 18))
        page.addView(passPanel(), layoutParams(bottom = 18))
        page.addView(appsPanel(), layoutParams(bottom = 18))
        page.addView(executionPanel())
        scroll.addView(page)
        return LiquidGlass.root(this, scroll)
    }

    private fun hero(): View = LiquidGlass.glassPanel(this, 24).apply {
        addView(LiquidGlass.eyebrow(this@MainActivity, "SCREEN BALANCE  /  使用节律"))
        addView(
            LiquidGlass.heading(this@MainActivity, "屏衡", 42f),
            layoutParams(top = 2),
        )
        addView(
            LiquidGlass.body(
                this@MainActivity,
                "把连续使用变成可见的节奏。达到上限后返回桌面，并进入五分钟冷静期。",
            ),
            layoutParams(top = 4),
        )

        val guardRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        guardStatePill = LiquidGlass.pill(this@MainActivity, "读取状态")
        guardRow.addView(
            guardStatePill,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        guardSwitch = CheckBox(this@MainActivity).apply {
            text = "启用使用守护"
            textSize = 16f
            setTextColor(LiquidGlass.INK)
            buttonTintList = ColorStateList.valueOf(LiquidGlass.ACCENT)
            setOnCheckedChangeListener { _, checked ->
                preferences.setGuardEnabled(checked)
                refresh()
            }
        }
        guardRow.addView(
            guardSwitch,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(10)
            },
        )
        addView(guardRow, layoutParams(top = 18))

        addView(
            LiquidGlass.eyebrow(this@MainActivity, "SYSTEM LINK  /  系统连接"),
            layoutParams(top = 20),
        )
        accessibilityStatus = LiquidGlass.body(this@MainActivity, "")
        addView(accessibilityStatus, layoutParams(top = 6))
        shizukuStatus = LiquidGlass.body(this@MainActivity, "")
        addView(shizukuStatus, layoutParams(top = 6))
        addView(
            LiquidGlass.primaryButton(this@MainActivity, "前往开启无障碍权限") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            layoutParams(top = 14),
        )
        addView(
            LiquidGlass.secondaryButton(this@MainActivity, "启动或授权 Shizuku") {
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
    }

    private fun durationPanel(): View = LiquidGlass.glassPanel(this, 22).apply {
        addView(LiquidGlass.eyebrow(this@MainActivity, "01  /  TIME LIMIT"))
        addView(
            LiquidGlass.heading(this@MainActivity, "连续使用上限", 24f),
            layoutParams(top = 4),
        )
        durationLabel = LiquidGlass.heading(this@MainActivity, "", 28f).apply {
            setTextColor(LiquidGlass.ACCENT)
        }
        addView(durationLabel, layoutParams(top = 12))
        durationSlider = LiquidGlass.slider(this@MainActivity).apply {
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
        addView(durationSlider, layoutParams(top = 4))
        addView(
            LiquidGlass.body(this@MainActivity, "范围：1 秒至 3 分钟，每格 1 秒。实时保存，不需要额外确认。"),
            layoutParams(top = 2),
        )
    }

    private fun passPanel(): View = LiquidGlass.glassPanel(this, 22).apply {
        addView(LiquidGlass.eyebrow(this@MainActivity, "02  /  TEMPORARY PASS"))
        addView(
            LiquidGlass.heading(this@MainActivity, "必要操作，短暂放行", 24f),
            layoutParams(top = 4),
        )
        passStatus = LiquidGlass.body(this@MainActivity, "")
        addView(passStatus, layoutParams(top = 8))
        addView(
            LiquidGlass.eyebrow(this@MainActivity, "快捷扫码"),
            layoutParams(top = 18),
        )
        addView(
            LiquidGlass.body(this@MainActivity, "点击后自动临时放行 1 分钟，并直接打开对应应用的扫码页。"),
            layoutParams(top = 4),
        )
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                LiquidGlass.secondaryButton(this@MainActivity, "抖音扫一扫") {
                    openScanShortcut(DOUYIN_PACKAGE, DOUYIN_SCAN_URI, "抖音")
                },
                rowButtonParams(end = 4),
            )
            addView(
                LiquidGlass.secondaryButton(this@MainActivity, "哔哩哔哩扫一扫") {
                    openScanShortcut(BILIBILI_PACKAGE, BILIBILI_SCAN_URI, "哔哩哔哩")
                },
                rowButtonParams(start = 4),
            )
        }, layoutParams(top = 10))
        passDurationLabel = LiquidGlass.heading(this@MainActivity, "", 20f).apply {
            setTextColor(LiquidGlass.ACCENT)
        }
        addView(passDurationLabel, layoutParams(top = 18))
        passDurationSlider = LiquidGlass.slider(this@MainActivity).apply {
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
        addView(passDurationSlider, layoutParams(top = 4))
        addView(
            LiquidGlass.body(this@MainActivity, "范围：1 至 10 分钟，每格 1 分钟。"),
            layoutParams(top = 2),
        )
        startPassButton = LiquidGlass.primaryButton(this@MainActivity, "临时放行") {
            startTemporaryPass()
        }
        addView(startPassButton, layoutParams(top = 14))
        endPassButton = LiquidGlass.secondaryButton(this@MainActivity, "立即恢复守护") {
            preferences.endTemporaryPass()
            refresh()
        }
        addView(endPassButton, layoutParams(top = 8))
    }

    private fun appsPanel(): View = LiquidGlass.glassPanel(this, 22).apply {
        addView(LiquidGlass.eyebrow(this@MainActivity, "03  /  TARGET APPS"))
        addView(
            LiquidGlass.heading(this@MainActivity, "把注意力留给重要的事", 24f),
            layoutParams(top = 4),
        )
        addView(
            LiquidGlass.body(
                this@MainActivity,
                "所有勾选应用共用连续使用计时；同一应用的双开也会受限。微信仅在视频号页面计时，聊天、扫码和付款不受限制。",
            ),
            layoutParams(top = 8, bottom = 14),
        )
        addView(LiquidGlass.eyebrow(this@MainActivity, "推荐受限应用"))
        FocusGuardPreferences.RECOMMENDED_APPS.forEach { target ->
            val checkBox = protectedCheckBox(target.label).apply {
                setOnCheckedChangeListener { _, checked ->
                    preferences.setProtected(target.packageName, checked)
                }
            }
            targetChecks[target.packageName] = checkBox
            addView(checkBox, layoutParams(bottom = 2))
        }

        addView(
            LiquidGlass.eyebrow(this@MainActivity, "其他受限应用"),
            layoutParams(top = 16),
        )
        customAppsContainer = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
        }
        addView(customAppsContainer, layoutParams(top = 6))
        addView(
            LiquidGlass.secondaryButton(this@MainActivity, "从手机选择应用") {
                startActivity(Intent(this@MainActivity, AppSelectionActivity::class.java))
            },
            layoutParams(top = 12),
        )
    }

    private fun executionPanel(): View = LiquidGlass.glassPanel(this, 22).apply {
        addView(LiquidGlass.eyebrow(this@MainActivity, "04  /  EXECUTION LOGIC"))
        addView(
            LiquidGlass.heading(this@MainActivity, "冷静期，不是惩罚", 24f),
            layoutParams(top = 4),
        )
        addView(
            LiquidGlass.body(
                this@MainActivity,
                "到达上限后，屏衡会通过 Shizuku 强制停止触发上限的应用。冷静期内再次打开对应受限页面，会立即再次停止。",
            ),
            layoutParams(top = 8),
        )
    }

    private fun refresh() {
        val guardEnabled = preferences.isGuardEnabled()
        guardSwitch.isChecked = guardEnabled
        guardStatePill.text = if (guardEnabled) "守护在线" else "守护暂停"

        val durationMillis = preferences.guardDurationMillis()
        durationSlider.progress = ((durationMillis - GuardDuration.MIN_MILLIS) /
            GuardDuration.STEP_MILLIS).toInt()
        durationLabel.text = "当前：${GuardDuration.format(durationMillis)}"

        val accessibilityEnabled = isAccessibilityServiceEnabled()
        accessibilityStatus.text = if (accessibilityEnabled) {
            "无障碍权限：已开启，守护会在后台生效。"
        } else {
            "无障碍权限：未开启。必须开启后才能识别前台应用并执行拦截。"
        }
        accessibilityStatus.setTextColor(
            if (accessibilityEnabled) LiquidGlass.ACCENT else LiquidGlass.WARNING,
        )
        val shizuku = ShizukuAccess.status(this)
        shizukuStatus.text = when (shizuku) {
            ShizukuStatus.AUTHORIZED -> "Shizuku：已授权，可强制停止前台与后台进程。"
            ShizukuStatus.NEEDS_PERMISSION -> "Shizuku：等待授权。点击下方按钮并允许屏衡。"
            ShizukuStatus.NOT_RUNNING -> "Shizuku：未运行。点击下方按钮启动后返回。"
            ShizukuStatus.NOT_INSTALLED -> "Shizuku：未安装，无法强制停止其他应用。"
        }
        shizukuStatus.setTextColor(
            if (shizuku == ShizukuStatus.AUTHORIZED) LiquidGlass.ACCENT else LiquidGlass.WARNING,
        )

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

    private fun openScanShortcut(packageName: String, uri: String, label: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(packageName)
        }
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "未安装$label", Toast.LENGTH_SHORT).show()
            return
        }

        preferences.startTemporaryPass(TemporaryPassDuration.MIN_MINUTES)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            preferences.endTemporaryPass()
            Toast.makeText(this, "无法打开$label 扫码页", Toast.LENGTH_SHORT).show()
        }
        refresh()
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
            customAppsContainer.addView(LiquidGlass.body(this, "尚未选择其他应用。"))
            return
        }

        selectedApps.forEach { (packageName, label) ->
            customAppsContainer.addView(
                protectedCheckBox(label + System.lineSeparator() + packageName).apply {
                    isChecked = true
                    setOnCheckedChangeListener { _, checked ->
                        if (!checked) {
                            preferences.setProtected(packageName, false)
                            refreshCustomApps()
                        }
                    }
                },
                layoutParams(bottom = 2),
            )
        }
    }

    private fun protectedCheckBox(value: String) = CheckBox(this).apply {
        text = value
        textSize = 15f
        setTextColor(LiquidGlass.INK)
        buttonTintList = ColorStateList.valueOf(LiquidGlass.ACCENT)
        setPadding(0, dp(5), 0, dp(5))
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

    private fun layoutParams(
        top: Int = 0,
        bottom: Int = 0,
        start: Int = 0,
        end: Int = 0,
    ) = LiquidGlass.layoutParams(this, top, bottom, start, end)

    private fun rowButtonParams(start: Int = 0, end: Int = 0) =
        LiquidGlass.weightedLayoutParams(this, start, end)

    private fun dp(value: Int): Int = LiquidGlass.dp(this, value)

    private companion object {
        const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
        const val DOUYIN_SCAN_URI = "snssdk1128://scan"
        const val BILIBILI_PACKAGE = "tv.danmaku.bili"
        const val BILIBILI_SCAN_URI = "bilibili://qrscan"
    }
}
