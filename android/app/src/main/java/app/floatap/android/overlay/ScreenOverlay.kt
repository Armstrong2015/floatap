package app.floatap.android.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.os.Build
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.floatap.android.domain.RunPhase
import kotlin.math.hypot

class ScreenOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var ball: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var menu: View? = null
    private var picker: View? = null

    var onStart: () -> Unit = {}
    var onPause: () -> Unit = {}
    var onResume: () -> Unit = {}
    var onStop: () -> Unit = {}
    var onOpenApp: () -> Unit = {}
    var onPickRequest: () -> Unit = {}
    var onPick: (x: Int, y: Int) -> Unit = { _, _ -> }
    var onCancelPick: () -> Unit = {}

    fun showBall(phase: RunPhase) {
        if (ball == null) {
            val size = (56 * context.resources.displayMetrics.density).toInt()
            val view = View(context)
            view.background = circle("#0F766E")
            val params = overlayParams(size, size)
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = (context.resources.displayMetrics.heightPixels * 0.4f).toInt()
            attachDrag(view, params, size)
            wm.addView(view, params)
            ball = view
            ballParams = params
        }
        paintBall(phase)
    }

    fun updateBall(phase: RunPhase) {
        if (ball == null) showBall(phase) else paintBall(phase)
    }

    fun hideMenu() {
        menu?.let { runCatching { wm.removeView(it) } }
        menu = null
    }

    fun hideAll() {
        hideMenu()
        ball?.let { runCatching { wm.removeView(it) } }
        ball = null
        ballParams = null
        hidePicker()
    }

    fun showPicker() {
        hidePicker()
        val root = FrameLayout(context)
        val dim = View(context)
        dim.setBackgroundColor(0x33000000)
        root.addView(dim, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        dim.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                onPick(event.rawX.toInt(), event.rawY.toInt())
            }
            true
        }
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xF0141A1F.toInt())
            val nav = systemBarPx("navigation_bar_height")
            setPadding(24, 24, 24, 24 + nav)
            isClickable = true
        }
        val label = TextView(context).apply {
            text = "点按要记录的位置"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val cancel = chip("取消") { onCancelPick() }
        bar.addView(label)
        bar.addView(cancel)
        root.addView(bar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ))
        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        wm.addView(root, params)
        picker = root
    }

    fun hidePicker() {
        picker?.let { runCatching { wm.removeView(it) } }
        picker = null
    }

    private fun paintBall(phase: RunPhase) {
        val color = when (phase) {
            RunPhase.IDLE -> "#0F766E"
            RunPhase.ARMED -> "#9A6700"
            RunPhase.RUNNING -> "#1F6FEB"
            RunPhase.PAUSED -> "#6E7781"
        }
        ball?.background = circle(color)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(view: View, params: WindowManager.LayoutParams, size: Int) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val threshold = 8 * context.resources.displayMetrics.density
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (hypot(dx, dy) > threshold) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        wm.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) snap(params, size) else toggleMenu(params, size)
                    true
                }
                else -> false
            }
        }
    }

    private fun snap(params: WindowManager.LayoutParams, size: Int) {
        val screen = context.readDisplay()
        val center = params.x + size / 2
        params.x = if (center < screen.width / 2) 0 else screen.width - size
        params.y = params.y.coerceIn(0, (screen.height - size).coerceAtLeast(0))
        ball?.let { wm.updateViewLayout(it, params) }
    }

    private fun toggleMenu(params: WindowManager.LayoutParams, size: Int) {
        if (menu != null) {
            hideMenu()
            return
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xF0141A1F.toInt())
            setPadding(8, 8, 8, 8)
        }
        when (app.floatap.android.engine.RunBus.phase.value) {
            RunPhase.RUNNING, RunPhase.ARMED -> {
                row.addButton("暂停") { hideMenu(); onPause() }
                row.addButton("停止") { hideMenu(); onStop() }
            }
            RunPhase.PAUSED -> {
                row.addButton("继续") { hideMenu(); onResume() }
                row.addButton("停止") { hideMenu(); onStop() }
            }
            RunPhase.IDLE -> {
                row.addButton("选点") { hideMenu(); onPickRequest() }
                row.addButton("开始") { hideMenu(); onStart() }
                row.addButton("打开") { hideMenu(); onOpenApp() }
            }
        }
        val menuParams = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        menuParams.gravity = Gravity.TOP or Gravity.START
        menuParams.x = params.x + size
        menuParams.y = params.y
        wm.addView(row, menuParams)
        menu = row
    }

    private fun chip(label: String, click: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            setPadding(32, 20, 32, 20)
            isClickable = true
            setOnClickListener { click() }
        }
    }

    private fun LinearLayout.addButton(label: String, click: () -> Unit) {
        addView(chip(label, click))
    }

    private fun circle(color: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(android.graphics.Color.parseColor(color))
        }
    }

    private fun systemBarPx(name: String): Int {
        val id = context.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).also { params ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
}
