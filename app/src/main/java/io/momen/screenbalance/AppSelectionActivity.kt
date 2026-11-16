package io.momen.screenbalance

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

class AppSelectionActivity : Activity() {
    private val preferences by lazy { FocusGuardPreferences(this) }
    private val catalog by lazy { InstalledAppCatalog(this) }
    private val selectedPackages = linkedSetOf<String>()

    private lateinit var appList: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var progress: ProgressBar
    private lateinit var selectionSummary: TextView
    private var installedApps = emptyList<InstalledApp>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedPackages += preferences.customProtectedPackages()
        LiquidGlass.applyWindowChrome(this)
        setContentView(createContent())
        loadApps()
    }

    private fun createContent(): View {
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(24))
        }
        val intro = LiquidGlass.glassPanel(this, 22).apply {
            addView(LiquidGlass.eyebrow(this@AppSelectionActivity, "APP LIBRARY  /  选择范围"))
            addView(
                LiquidGlass.heading(this@AppSelectionActivity, "选择其他受限应用", 26f),
                layoutParams(top = 4),
            )
            addView(
                LiquidGlass.body(
                    this@AppSelectionActivity,
                    "从手机已安装且可启动的应用中选择。推荐应用仍在主界面单独勾选。",
                ),
                layoutParams(top = 6),
            )
            selectionSummary = LiquidGlass.pill(this@AppSelectionActivity, "0 个应用已加入守护")
            addView(selectionSummary, layoutParams(top = 14))
        }
        screen.addView(intro, layoutParams(bottom = 12))

        searchInput = EditText(this).apply {
            hint = "搜索应用名称或包名"
            isSingleLine = true
            setTextColor(LiquidGlass.INK)
            setHintTextColor(LiquidGlass.MUTED)
            textSize = 15f
            background = LiquidGlass.pillBackground(
                this@AppSelectionActivity,
                Color.argb(56, 255, 255, 255),
                LiquidGlass.BORDER,
            )
            setPadding(dp(16), dp(4), dp(16), dp(4))
            minHeight = dp(52)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    text: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    text: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    renderApps(text?.toString().orEmpty())
                }

                override fun afterTextChanged(text: Editable?) = Unit
            })
        }
        screen.addView(searchInput, layoutParams(bottom = 8))

        progress = ProgressBar(this).apply {
            isIndeterminate = true
        }
        screen.addView(progress, layoutParams(top = 2, bottom = 2))

        appList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        screen.addView(
            ScrollView(this).apply {
                isFillViewport = true
                addView(appList)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        screen.addView(
            LiquidGlass.primaryButton(this, "保存选择") {
                preferences.replaceCustomProtectedPackages(selectedPackages)
                setResult(RESULT_OK)
                finish()
            },
            layoutParams(top = 12),
        )
        return LiquidGlass.root(this, screen)
    }

    private fun loadApps() {
        Thread {
            val launchableApps = catalog.launchableApps()
                .filterNot { app ->
                    app.packageName in FocusGuardPreferences.RECOMMENDED_PACKAGES
                }
            val launchablePackages = launchableApps.mapTo(hashSetOf()) { app ->
                app.packageName
            }
            val unavailableSelections = selectedPackages
                .filterNot { packageName -> packageName in launchablePackages }
                .map { packageName ->
                    InstalledApp(
                        label = catalog.labelForPackage(packageName) ?: packageName,
                        packageName = packageName,
                    )
                }
            val apps = (launchableApps + unavailableSelections)
                .distinctBy { app -> app.packageName }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { app -> app.label })
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                installedApps = apps
                progress.visibility = View.GONE
                renderApps(searchInput.text?.toString().orEmpty())
            }
        }.start()
    }

    private fun renderApps(query: String) {
        if (!::appList.isInitialized) return
        val normalizedQuery = query.trim()
        appList.removeAllViews()
        val visibleApps = installedApps.filter { app ->
            normalizedQuery.isEmpty() ||
                app.label.contains(normalizedQuery, ignoreCase = true) ||
                app.packageName.contains(normalizedQuery, ignoreCase = true)
        }
        if (visibleApps.isEmpty() && installedApps.isNotEmpty()) {
            appList.addView(
                LiquidGlass.body(this, "没有匹配的应用").apply {
                    setPadding(dp(4), dp(18), dp(4), dp(18))
                },
            )
            return
        }
        visibleApps.forEach { app ->
            val checkBox = CheckBox(this).apply {
                text = app.label + System.lineSeparator() + app.packageName
                textSize = 15f
                setTextColor(LiquidGlass.INK)
                buttonTintList = android.content.res.ColorStateList.valueOf(LiquidGlass.ACCENT)
                isChecked = app.packageName in selectedPackages
                setPadding(0, dp(4), 0, dp(4))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPackages += app.packageName
                    else selectedPackages -= app.packageName
                    updateSummary()
                }
            }
            appList.addView(
                LiquidGlass.glassPanel(this, 12).apply {
                    addView(checkBox)
                },
                layoutParams(bottom = 8),
            )
        }
        updateSummary()
    }

    private fun updateSummary() {
        if (::selectionSummary.isInitialized) {
            selectionSummary.text = "${selectedPackages.size} 个应用已加入守护"
        }
    }

    private fun layoutParams(
        top: Int = 0,
        bottom: Int = 0,
        start: Int = 0,
        end: Int = 0,
    ) = LiquidGlass.layoutParams(this, top, bottom, start, end)

    private fun dp(value: Int): Int = LiquidGlass.dp(this, value)
}
