package io.momen.screenbalance

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
    private var installedApps = emptyList<InstalledApp>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedPackages += preferences.customProtectedPackages()
        setContentView(createContent())
        loadApps()
    }

    private fun createContent(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(COLOR_BACKGROUND)
        setPadding(dp(20), dp(20), dp(20), dp(20))

        addView(titleView("选择其他受限应用"))
        addView(
            bodyView("从手机已安装且可启动的应用中选择。推荐应用仍在主界面单独勾选。"),
            layoutParams(top = 6, bottom = 14),
        )
        searchInput = EditText(this@AppSelectionActivity).apply {
            hint = "搜索应用名称或包名"
            isSingleLine = true
            setTextColor(COLOR_INK)
            setHintTextColor(COLOR_MUTED)
            background = roundedBackground(Color.WHITE, dp(12).toFloat())
            setPadding(dp(14), dp(10), dp(14), dp(10))
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
        addView(searchInput)

        progress = ProgressBar(this@AppSelectionActivity).apply {
            isIndeterminate = true
        }
        addView(
            progress,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(24)
            },
        )

        appList = LinearLayout(this@AppSelectionActivity).apply {
            orientation = LinearLayout.VERTICAL
        }
        addView(
            ScrollView(this@AppSelectionActivity).apply {
                addView(appList)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ).apply { topMargin = dp(12) },
        )

        addView(
            Button(this@AppSelectionActivity).apply {
                text = "保存选择"
                setTextColor(Color.WHITE)
                textSize = 16f
                isAllCaps = false
                background = roundedBackground(COLOR_ACCENT, dp(12).toFloat())
                setOnClickListener {
                    preferences.replaceCustomProtectedPackages(selectedPackages)
                    setResult(RESULT_OK)
                    finish()
                }
            },
            layoutParams(top = 12),
        )
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
            appList.addView(bodyView("没有匹配的应用"), layoutParams(top = 16))
            return
        }
        visibleApps.forEach { app ->
            val checkBox = CheckBox(this).apply {
                text = "${app.label}\n${app.packageName}"
                textSize = 16f
                setTextColor(COLOR_INK)
                isChecked = app.packageName in selectedPackages
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPackages += app.packageName
                    else selectedPackages -= app.packageName
                }
            }
            appList.addView(checkBox, layoutParams(bottom = 4))
        }
    }

    private fun titleView(text: String) = TextView(this).apply {
        this.text = text
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(COLOR_INK)
    }

    private fun bodyView(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(COLOR_MUTED)
    }

    private fun layoutParams(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { setMargins(0, dp(top), 0, dp(bottom)) }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val COLOR_BACKGROUND = Color.parseColor("#F7F8FA")
        val COLOR_INK = Color.parseColor("#1A1C1E")
        val COLOR_MUTED = Color.parseColor("#5D6470")
        val COLOR_ACCENT = Color.parseColor("#2457D6")
    }
}
