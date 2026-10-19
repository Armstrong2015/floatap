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
import android.os.SystemClock
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.floatap.android.domain.RunPhase
import kotlin.math.hypot

class ScreenOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var ball: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var menu: View? = null
    private var picker: View? = null
    private var pickChrome: View? = null
    private var note: View? = null
    private var menuReadyAt = 0L

    var onStart: () -> Unit = {}
    var onPause: () -> Unit = {}
    var onResume: () -> Unit = {}
    var onStop: () -> Unit = {}
    var onOpenApp: () -> Unit = {}
    var onPickRequest: () -> Unit = {}
    var onPrepareIdleMenu: () -> Unit = {}
    var onSelectJob: (Long, String) -> Unit = { _, _ -> }
    var onPick: (x: Int, y: Int) -> Unit = { _, _ -> }
    var onCancelPick: () -> Unit = {}
    var onOutsideTouch: (MotionEvent) -> Unit = {}

    fun showBall(phase: RunPhase) {
        if (ball == null) {
            val size = (56 * context.resources.displayMetrics.density).toInt()
            val view = View(context)
            view.background = circle("#0F766E")
            val params = overlayParams(size, size)
            params.flags = params.flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
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

    fun showNote(text: String) {
        hideNote()
        val view = TextView(context).apply {
            this.text = text
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            setBackgroundColor(0xF0141A1F.toInt())
            setPadding(36, 24, 36, 24)
        }
        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = systemBarPx("navigation_bar_height") + 48
        if (runCatching { wm.addView(view, params) }.isFailure) return
        note = view
        view.postDelayed({ hideNote() }, 1800)
    }

    fun hideNote() {
        note?.let { runCatching { wm.removeView(it) } }
        note = null
    }

    fun hideAll() {
        hideMenu()
        hideNote()
        hidePicker()
        ball?.let { runCatching { wm.removeView(it) } }
        ball = null
        ballParams = null
    }

    fun showPicker() {
        hidePicker()
        hideMenu()
        val dim = View(context)
        dim.setBackgroundColor(0x22000000)
        dim.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                onPick(event.rawX.toInt(), event.rawY.toInt())
            }
            true
        }
        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        wm.addView(dim, params)
        picker = dim
        showPickChrome()
    }

    private fun showPickChrome() {
        hidePickChrome(restoreBall = false)
        val density = context.resources.displayMetrics.density
        val anchor = ballParams
        ball?.takeIf { it.isAttachedToWindow }?.let { runCatching { wm.removeView(it) } }
        val cancel = TextView(context).apply {
            text = "取消选点"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(32, 20, 32, 20)
            background = roundedRect(0x33FFFFFF)
        }
        val handle = View(context).apply {
            val side = (36 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(side, side).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = (8 * density).toInt()
            }
            background = circle("#0F766E")
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = (8 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18 * density
                setColor(0xCC1B242C.toInt())
                setStroke((1 * density).toInt().coerceAtLeast(1), 0x55FFFFFF)
            }
            addView(handle)
            addView(cancel)
        }
        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = anchor?.x ?: 0
        params.y = anchor?.y ?: (context.resources.displayMetrics.heightPixels * 0.4f).toInt()
        attachPickDrag(row, params, cancel)
        wm.addView(row, params)
        pickChrome = row
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachPickDrag(view: View, params: WindowManager.LayoutParams, cancel: View) {
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
                    app.floatap.android.engine.RunBus.ignoreUserTouchUntil =
                        System.currentTimeMillis() + 700
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (hypot(dx, dy) > threshold) dragging = true
                    if (dragging) {
                        val screen = context.readDisplay()
                        val width = view.width.takeIf { it > 0 } ?: 0
                        val height = view.height.takeIf { it > 0 } ?: 0
                        params.x = (startX + dx.toInt()).coerceIn(0, (screen.width - width).coerceAtLeast(0))
                        params.y = (startY + dy.toInt()).coerceIn(0, (screen.height - height).coerceAtLeast(0))
                        wm.updateViewLayout(view, params)
                        ballParams?.let { anchor ->
                            anchor.x = params.x
                            anchor.y = params.y
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val insideCancel = event.x.toInt() in cancel.left..cancel.right &&
                        event.y.toInt() in cancel.top..cancel.bottom
                    if (!dragging && insideCancel) onCancelPick()
                    true
                }
                else -> false
            }
        }
    }

    private fun hidePickChrome(restoreBall: Boolean = true) {
        val chrome = pickChrome
        pickChrome = null
        chrome?.let { runCatching { wm.removeView(it) } }
        val anchor = ballParams
        val floating = ball
        if (restoreBall && floating != null && !floating.isAttachedToWindow && anchor != null) {
            runCatching { wm.addView(floating, anchor) }
        }
    }

    fun flash(x: Int, y: Int) {
        val density = context.resources.displayMetrics.density
        val size = (40 * density).toInt()
        val mark = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66FFB020)
                setStroke((3 * density).toInt(), 0xFFFFC14D.toInt())
            }
        }
        val params = overlayParams(size, size)
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params.gravity = Gravity.TOP or Gravity.START
        params.x = x - size / 2
        params.y = y - size / 2
        if (runCatching { wm.addView(mark, params) }.isFailure) return
        mark.animate()
            .scaleX(2.4f)
            .scaleY(2.4f)
            .alpha(0f)
            .setDuration(360)
            .withEndAction { runCatching { wm.removeView(mark) } }
            .start()
    }

    fun hidePicker() {
        hidePickChrome()
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
                    app.floatap.android.engine.RunBus.ignoreUserTouchUntil =
                        System.currentTimeMillis() + 700
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
                    if (dragging) snap(params, size) else {
                        view.post { onBallTap(params, size) }
                    }
                    true
                }
                MotionEvent.ACTION_OUTSIDE -> {
                    onOutsideTouch(event)
                    false
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

    private fun onBallTap(params: WindowManager.LayoutParams, size: Int) {
        if (menu != null) {
            hideMenu()
            return
        }
        when (app.floatap.android.engine.RunBus.phase.value) {
            RunPhase.RUNNING, RunPhase.ARMED -> showActionMenu(params, size, "暂停", onPause)
            RunPhase.PAUSED -> showActionMenu(params, size, "继续", onResume)
            RunPhase.IDLE -> onPrepareIdleMenu()
        }
    }

    private fun showActionMenu(
        params: WindowManager.LayoutParams,
        size: Int,
        primary: String,
        primaryAction: () -> Unit,
    ) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addButton(primary, selected = false) { hideMenu(); primaryAction() }
            addButton("停止", selected = false) { hideMenu(); onStop() }
        }
        placeMenu(glassPanel(row), params, size)
    }

    fun showIdleMenu(jobs: List<Pair<Long, String>>, selectedId: Long) {
        val anchor = ballParams ?: return
        val size = ball?.width?.takeIf { it > 0 } ?: (56 * context.resources.displayMetrics.density).toInt()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        if (jobs.isEmpty()) {
            column.addView(TextView(context).apply {
                text = "还没有作业，选点会新建一份"
                setTextColor(0xFFD0D7DE.toInt())
                textSize = 14f
                setPadding(32, 20, 32, 20)
                layoutParams = chipLayout()
            })
        } else {
            val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            jobs.forEach { (id, name) ->
                val selected = id == selectedId
                list.addButton(if (selected) "当前 $name" else name, selected) {
                    onSelectJob(id, name)
                }
            }
            val scroll = ScrollView(context).apply { addView(list) }
            val rowHeight = (72 * context.resources.displayMetrics.density).toInt()
            val height = (jobs.size * rowHeight).coerceAtMost((240 * context.resources.displayMetrics.density).toInt())
            column.addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height,
            ))
        }
        column.addButton("选点", selected = false) { hideMenu(); onPickRequest() }
        column.addButton("开始", selected = false) { hideMenu(); onStart() }
        column.addButton("打开", selected = false) { hideMenu(); onOpenApp() }
        placeMenu(glassPanel(column), anchor, size)
    }

    private fun placeMenu(content: View, anchor: WindowManager.LayoutParams, ballSize: Int) {
        hideMenu()
        val screen = context.readDisplay()
        val menuWidth = (220 * context.resources.displayMetrics.density).toInt()
        val panelX = if (anchor.x + ballSize + menuWidth > screen.width) {
            (anchor.x - menuWidth).coerceAtLeast(0)
        } else {
            anchor.x + ballSize
        }
        val panelY = anchor.y.coerceIn(0, (screen.height - ballSize).coerceAtLeast(0))
        content.isClickable = true
        val host = object : FrameLayout(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_UP && picker == null) hideMenu()
                return true
            }
        }
        host.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            leftMargin = panelX
            topMargin = panelY
        })
        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        wm.addView(host, params)
        menu = host
        menuReadyAt = SystemClock.uptimeMillis() + 400
    }

    private fun chip(label: String, selected: Boolean, click: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = (200 * context.resources.displayMetrics.density).toInt()
            minWidth = (88 * context.resources.displayMetrics.density).toInt()
            setPadding(32, 20, 32, 20)
            background = roundedRect(if (selected) 0xFF1F6B4A.toInt() else 0x33FFFFFF)
            layoutParams = chipLayout()
            isClickable = true
            setOnClickListener {
                if (SystemClock.uptimeMillis() < menuReadyAt) return@setOnClickListener
                click()
            }
        }
    }

    private fun chipLayout(): LinearLayout.LayoutParams {
        val gap = (4 * context.resources.displayMetrics.density).toInt()
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(gap, gap, gap, gap) }
    }

    private fun glassPanel(content: LinearLayout): LinearLayout {
        val density = context.resources.displayMetrics.density
        val pad = (8 * density).toInt()
        content.setPadding(pad, pad, pad, pad)
        content.minimumWidth = (168 * density).toInt()
        content.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18 * density
            setColor(0xCC1B242C.toInt())
            setStroke((1 * density).toInt().coerceAtLeast(1), 0x55FFFFFF)
        }
        return content
    }

    private fun roundedRect(color: Int): GradientDrawable {
        val radius = 8 * context.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    private fun LinearLayout.addButton(label: String, selected: Boolean, click: () -> Unit) {
        val view = chip(label, selected, click)
        val params = view.layoutParams as LinearLayout.LayoutParams
        if (orientation == LinearLayout.HORIZONTAL) {
            params.width = LinearLayout.LayoutParams.WRAP_CONTENT
        }
        addView(view, params)
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
