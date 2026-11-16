package io.momen.screenbalance

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

internal object LiquidGlass {
    val BACKGROUND = Color.parseColor(
        "#07111E",
    )
    val SURFACE = Color.argb(58, 255, 255, 255)
    val SURFACE_STRONG = Color.argb(82, 255, 255, 255)
    val BORDER = Color.argb(108, 255, 255, 255)
    val INK = Color.parseColor("#F3F7FF")
    val MUTED = Color.parseColor("#AAB9CE")
    val ACCENT = Color.parseColor("#8DE5D0")
    val ACCENT_STRONG = Color.parseColor("#63CFFF")
    val WARNING = Color.parseColor("#FFD28A")
    val DANGER = Color.parseColor("#FF9DBA")
    val VIOLET = Color.parseColor("#C6A6FF")

    fun applyWindowChrome(activity: Activity) {
        activity.window.statusBarColor = BACKGROUND
        activity.window.navigationBarColor = BACKGROUND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.navigationBarDividerColor = Color.TRANSPARENT
        }
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = 0
    }

    fun root(context: Context, content: View): FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(BACKGROUND)
        addView(
            LiquidBackdropView(context),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun glassPanel(context: Context, padding: Int = 20): GlassPanel =
        GlassPanel(context).apply {
            setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding))
        }

    fun text(
        context: Context,
        value: String,
        size: Float,
        color: Int = INK,
        bold: Boolean = false,
    ): TextView = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = android.graphics.Typeface.create(
            "sans-serif",
            if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
        )
        includeFontPadding = true
    }

    fun body(context: Context, value: String): TextView = text(
        context = context,
        value = value,
        size = 15f,
        color = MUTED,
    ).apply {
        setLineSpacing(dp(context, 3).toFloat(), 1f)
    }

    fun eyebrow(context: Context, value: String): TextView = text(
        context = context,
        value = value,
        size = 11f,
        color = ACCENT_STRONG,
        bold = true,
    ).apply {
        letterSpacing = 0.12f
    }

    fun heading(context: Context, value: String, size: Float = 22f): TextView = text(
        context = context,
        value = value,
        size = size,
        color = INK,
        bold = true,
    )

    fun pill(context: Context, value: String): TextView = text(
        context = context,
        value = value,
        size = 12f,
        color = INK,
        bold = true,
    ).apply {
        gravity = android.view.Gravity.CENTER
        setPadding(dp(context, 12), dp(context, 7), dp(context, 12), dp(context, 7))
        background = pillBackground(context, Color.argb(60, 255, 255, 255), BORDER)
    }

    fun pillBackground(context: Context, fill: Int, stroke: Int = BORDER): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 20).toFloat()
            setColor(fill)
            setStroke(dp(context, 1), stroke)
        }

    fun primaryButton(context: Context, value: String, onClick: () -> Unit): Button =
        Button(context).apply {
            text = value
            isAllCaps = false
            textSize = 15f
            setTextColor(BACKGROUND)
            minHeight = dp(context, 52)
            setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(ACCENT_STRONG, ACCENT),
            ).apply {
                cornerRadius = dp(context, 17).toFloat()
            }
            setOnClickListener { onClick() }
        }

    fun secondaryButton(context: Context, value: String, onClick: () -> Unit): Button =
        Button(context).apply {
            text = value
            isAllCaps = false
            textSize = 15f
            setTextColor(INK)
            minHeight = dp(context, 50)
            setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 16).toFloat()
                setColor(Color.argb(40, 255, 255, 255))
                setStroke(dp(context, 1), BORDER)
            }
            setOnClickListener { onClick() }
        }

    fun slider(context: Context): SeekBar = SeekBar(context).apply {
        minHeight = dp(context, 42)
        splitTrack = false
        progressTintList = ColorStateList.valueOf(ACCENT_STRONG)
        thumbTintList = ColorStateList.valueOf(ACCENT)
    }

    fun layoutParams(
        context: Context,
        top: Int = 0,
        bottom: Int = 0,
        start: Int = 0,
        end: Int = 0,
    ) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        setMargins(dp(context, start), dp(context, top), dp(context, end), dp(context, bottom))
    }

    fun weightedLayoutParams(
        context: Context,
        start: Int = 0,
        end: Int = 0,
    ) = LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        1f,
    ).apply {
        setMargins(dp(context, start), 0, dp(context, end), 0)
    }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

internal class LiquidBackdropView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width == 0f || height == 0f) return

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            0f,
            width,
            height,
            intArrayOf(
                Color.parseColor("#09182A"),
                LiquidGlass.BACKGROUND,
                Color.parseColor("#11102A"),
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, paint)

        paint.shader = RadialGradient(
            width * 0.08f,
            height * 0.02f,
            width * 0.92f,
            intArrayOf(
                Color.argb(150, 82, 205, 255),
                Color.argb(35, 82, 205, 255),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(width * 0.08f, height * 0.02f, width * 0.92f, paint)

        paint.shader = RadialGradient(
            width * 0.96f,
            height * 0.58f,
            width * 0.78f,
            intArrayOf(
                Color.argb(110, 196, 126, 255),
                Color.argb(24, 196, 126, 255),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(width * 0.96f, height * 0.58f, width * 0.78f, paint)

        paint.shader = null
        paint.color = Color.argb(32, 255, 255, 255)
        canvas.drawRect(0f, 0f, width, 1f, paint)
    }
}

internal class GlassPanel(context: Context) : LinearLayout(context) {
    private val radius = LiquidGlass.dp(context, 26).toFloat()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        if (width == 0f || height == 0f) return

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height,
            Color.argb(82, 255, 255, 255),
            Color.argb(24, 255, 255, 255),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(0f, 0f, width, height, radius, radius, paint)

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = LiquidGlass.dp(context, 1).toFloat()
        paint.color = LiquidGlass.BORDER
        canvas.drawRoundRect(0.5f, 0.5f, width - 0.5f, height - 0.5f, radius, radius, paint)

        paint.color = Color.argb(82, 255, 255, 255)
        paint.strokeWidth = LiquidGlass.dp(context, 1).toFloat()
        canvas.drawLine(radius, 1f, width - radius, 1f, paint)
    }
}
