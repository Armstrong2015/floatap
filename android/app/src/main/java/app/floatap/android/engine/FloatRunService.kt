package app.floatap.android.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.floatap.android.FloatApp
import app.floatap.android.MainActivity
import app.floatap.android.Prefs
import app.floatap.android.R
import app.floatap.android.a11y.ClickBridge
import app.floatap.android.domain.RunPhase
import app.floatap.android.domain.Step
import app.floatap.android.domain.StopReason
import app.floatap.android.domain.displayMatches
import app.floatap.android.isAccessibilityEnabled
import app.floatap.android.overlay.PickerBus
import app.floatap.android.overlay.ScreenOverlay
import app.floatap.android.overlay.readDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

class FloatRunService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var runJob: Job? = null
    private var overlay: ScreenOverlay? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var pausedFrom = RunPhase.RUNNING

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_PICK -> beginPick()
                ACTION_START -> beginRun(intent.getLongExtra(EXTRA_JOB_ID, 0L))
                ACTION_PAUSE -> pauseInternal()
                ACTION_RESUME -> resumeInternal()
                ACTION_STOP -> stopRun(StopReason.USER)
                ACTION_SHOW_BALL -> {
                    promote("点屏幕边缘的圆点，可以选点或停止")
                    showBall()
                    if (intent.getBooleanExtra(EXTRA_COLLAPSE, false)) {
                        RunBus.collapseHost.value = true
                    }
                }
                else -> Unit
            }
        } catch (error: Exception) {
            android.util.Log.e(TAG, "service action failed", error)
            RunBus.message.value = "悬浮层没有打开：${error.javaClass.simpleName}"
            runCatching { promote("浮序仍在运行") }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ClickBridge.gate.close()
        runJob?.cancel()
        scope.cancel()
        overlay?.hideAll()
        releaseWake()
        super.onDestroy()
    }

    private fun onMain(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    private fun showBall() {
        val windows = windows()
        windows.showBall(RunBus.phase.value)
    }

    private fun beginPick() {
        try {
            promote("点按要记录的位置")
            val windows = windows()
            windows.showBall(RunBus.phase.value)
            windows.onPick = { x, y ->
                onMain {
                    windows.hidePicker()
                    savePick(x, y)
                }
            }
            windows.onCancelPick = {
                onMain { windows.hidePicker() }
            }
            windows.showPicker()
        } catch (error: Exception) {
            android.util.Log.e(TAG, "picker failed", error)
            RunBus.message.value = "选点层没有打开：${error.javaClass.simpleName}"
        }
    }

    private fun savePick(x: Int, y: Int) {
        scope.launch {
            try {
                val prefs = Prefs(this@FloatRunService)
                val display = readDisplay()
                val summaries = (application as FloatApp).repository.summaries()
                val selected = summaries.firstOrNull { it.id == prefs.lastJobId }
                val repository = (application as FloatApp).repository
                if (selected == null && summaries.isNotEmpty()) {
                    windows().showNote("先在圆点里点一份作业")
                    return@launch
                }
                if (selected == null) {
                    val name = "作业${repository.count() + 1}"
                    val id = repository.save(
                        app.floatap.android.domain.Job(
                            name = name,
                            steps = listOf(pointStep(x, y, display.width, display.height, display.rotation)),
                            durationMs = 60_000,
                            clickLimit = null,
                            countdownSec = 3,
                        ),
                    )
                    prefs.lastJobId = id
                    prefs.lastJobName = name
                    rememberPick(x, y, display.width, display.height, display.rotation)
                    windows().showNote("已新建「$name」并记下 $x, $y")
                    return@launch
                }
                val job = repository.get(selected.id)
                if (job == null) {
                    windows().showNote("这份作业已经不在了")
                    return@launch
                }
                val steps = job.steps.toMutableList()
                val point = pointStep(x, y, display.width, display.height, display.rotation)
                if (steps.isEmpty()) steps += point else steps[0] = steps[0].copy(
                    x = point.x,
                    y = point.y,
                    displayWidth = point.displayWidth,
                    displayHeight = point.displayHeight,
                    rotation = point.rotation,
                )
                repository.save(job.copy(steps = steps))
                prefs.lastJobId = job.id
                prefs.lastJobName = job.name
                rememberPick(x, y, display.width, display.height, display.rotation)
                windows().showNote("已记下「${job.name}」$x, $y")
            } catch (error: Exception) {
                android.util.Log.e(TAG, "save pick failed", error)
                windows().showNote("选点没有记下")
            }
        }
    }

    private fun pointStep(x: Int, y: Int, width: Int, height: Int, rotation: Int) = Step(
        order = 0,
        name = "点击",
        x = x,
        y = y,
        displayWidth = width,
        displayHeight = height,
        rotation = rotation,
        waitMinMs = 500,
        waitMaxMs = 500,
    )

    private fun rememberPick(x: Int, y: Int, width: Int, height: Int, rotation: Int) {
        PickerBus.pending = app.floatap.android.overlay.PickResult(x, y, width, height, rotation)
    }

    private fun beginRun(jobId: Long) {
        promote("准备点击")
        runJob?.cancel()
        ClickBridge.gate.close()
        val runId = SystemClock.elapsedRealtime()
        RunBus.activeJobId = jobId
        Prefs(this).lastJobId = jobId
        runJob = scope.launch {
            try {
                execute(jobId, runId)
            } catch (_: kotlinx.coroutines.CancellationException) {
                ClickBridge.gate.close()
            } catch (_: Exception) {
                stopRun(StopReason.PERMISSION, cancelLoop = false)
            }
        }
    }

    private suspend fun execute(jobId: Long, runId: Long) {
        val repository = (application as FloatApp).repository
        val job = repository.get(jobId)
        if (job == null || job.steps.isEmpty()) {
            stopRun(StopReason.PERMISSION, cancelLoop = false)
            return
        }
        val step = job.steps.first()
        RunBus.pauseOnUserTouch = job.pauseOnUserTouch
        if (!isAccessibilityEnabled() || !displayMatches(step, readDisplay())) {
            val detail = if (!isAccessibilityEnabled()) {
                "辅助功能还没打开，浮序无法点击。请到设置里打开「浮序」。"
            } else {
                "屏幕尺寸和标定时不一致，请重新选点"
            }
            stopRun(
                if (!isAccessibilityEnabled()) StopReason.PERMISSION else StopReason.DISPLAY_CHANGED,
                cancelLoop = false,
            )
            RunBus.message.value = detail
            windows().showNote(detail)
            return
        }
        setPhase(RunPhase.ARMED)
        var seconds = job.countdownSec
        notify("即将运行「${job.name}」")
        while (seconds > 0) {
            when (RunBus.phase.value) {
                RunPhase.ARMED -> {
                    notify("「${job.name}」$seconds 秒后开始")
                    delay(1000)
                    if (RunBus.phase.value == RunPhase.ARMED) seconds -= 1
                }
                RunPhase.PAUSED -> delay(200)
                else -> return
            }
        }
        while (RunBus.phase.value == RunPhase.PAUSED) delay(200)
        if (RunBus.phase.value != RunPhase.ARMED) return
        setPhase(RunPhase.RUNNING)
        ClickBridge.gate.open(runId)
        var progress = Progress()
        val stepCount = job.steps.size
        var index = 0
        val waitRandom = java.util.Random()
        while (RunBus.phase.value == RunPhase.RUNNING || RunBus.phase.value == RunPhase.PAUSED) {
            val current = job.steps[index]
            val wait = waitMillis(current.waitMinMs, current.waitMaxMs, waitRandom)
            android.util.Log.i(TAG, "wait ${wait}ms")
            holdWake(wait)
            waitReady(wait)
            if (RunBus.phase.value == RunPhase.PAUSED) continue
            if (RunBus.phase.value != RunPhase.RUNNING) break
            if (!isAccessibilityEnabled()) {
                stopRun(StopReason.PERMISSION, cancelLoop = false)
                return
            }
            if (!displayMatches(current, readDisplay())) {
                stopRun(StopReason.DISPLAY_CHANGED, cancelLoop = false)
                return
            }
            val now = SystemClock.elapsedRealtime()
            val reason = nextStop(job.durationMs, job.clickLimit, job.loopLimit, progress, now)
            if (reason != null) {
                stopRun(reason, cancelLoop = false)
                return
            }
            overlay?.flash(current.x, current.y)
            val ok = ClickBridge.tap(current.x, current.y, runId)
            if (!ClickBridge.gate.allows(runId)) return
            if (RunBus.phase.value != RunPhase.RUNNING) continue
            if (!ok) {
                if (!isAccessibilityEnabled()) {
                    stopRun(StopReason.PERMISSION, cancelLoop = false)
                    return
                }
                delay(150)
                continue
            }
            val first = progress.firstClickElapsed ?: SystemClock.elapsedRealtime()
            val clicks = progress.clicks + 1
            val nextIndex = if (index + 1 < stepCount) index + 1 else 0
            val loops = if (nextIndex == 0) progress.loops + 1 else progress.loops
            progress = Progress(clicks, loops, first)
            index = nextIndex
            notify("「${job.name}」已点击 $clicks 次")
            val after = nextStop(
                job.durationMs,
                job.clickLimit,
                job.loopLimit,
                progress,
                SystemClock.elapsedRealtime(),
            )
            if (job.loopLimit != null && loops >= job.loopLimit && nextIndex == 0) {
                stopRun(StopReason.LOOP_LIMIT, cancelLoop = false)
                return
            }
            if (after == StopReason.CLICK_LIMIT || after == StopReason.DURATION) {
                stopRun(after, cancelLoop = false)
                return
            }
        }
    }

    private suspend fun waitReady(total: Long) {
        var remaining = total
        var sawPause = false
        while (remaining > 0) {
            when (RunBus.phase.value) {
                RunPhase.PAUSED -> {
                    sawPause = true
                    delay(100)
                }
                RunPhase.RUNNING -> {
                    if (sawPause) {
                        sawPause = false
                        remaining = total
                    }
                    val slice = min(remaining, 100L)
                    delay(slice)
                    if (RunBus.phase.value == RunPhase.RUNNING) remaining -= slice
                }
                else -> return
            }
        }
    }

    private fun onOutsideTouch(event: android.view.MotionEvent) {
        if (!RunBus.pauseOnUserTouch) return
        val phase = RunBus.phase.value
        if (phase != RunPhase.RUNNING && phase != RunPhase.ARMED) return
        val touchedAt = event.eventTime
        if (touchedAt in ClickBridge.ownGestureStartedAt..ClickBridge.ownGestureUntil) return
        if (System.currentTimeMillis() < RunBus.ignoreUserTouchUntil) return
        pauseInternal()
    }

    private fun pauseInternal() {
        val phase = RunBus.phase.value
        if (phase == RunPhase.RUNNING || phase == RunPhase.ARMED) {
            pausedFrom = phase
            setPhase(RunPhase.PAUSED)
            notify("已暂停")
        }
    }

    private fun resumeInternal() {
        if (RunBus.phase.value == RunPhase.PAUSED) {
            setPhase(pausedFrom)
        }
    }

    private fun stopRun(reason: StopReason, cancelLoop: Boolean = true) {
        ClickBridge.gate.close()
        if (cancelLoop) runJob?.cancel()
        runJob = null
        releaseWake()
        setPhase(RunPhase.IDLE)
        RunBus.activeJobId = 0L
        val text = when (reason) {
            StopReason.USER -> "已停止"
            StopReason.DURATION -> "到达设定时长，已停止"
            StopReason.CLICK_LIMIT -> "到达点击次数，已停止"
            StopReason.LOOP_LIMIT -> "循环完成，已停止"
            StopReason.DISPLAY_CHANGED -> "屏幕尺寸变了，已停止"
            StopReason.PERMISSION -> "无法继续点击，已停止"
            else -> "已停止"
        }
        RunBus.message.value = text
        demote()
    }

    private fun setPhase(phase: RunPhase) {
        RunBus.phase.value = phase
        overlay?.updateBall(phase)
    }

    private fun windows(): ScreenOverlay {
        val existing = overlay
        if (existing != null) return existing
        return ScreenOverlay(this).also { created ->
            created.onPrepareIdleMenu = { showIdleMenu() }
            created.onSelectJob = { id, name ->
                Prefs(this).lastJobId = id
                Prefs(this).lastJobName = name
                showIdleMenu()
            }
            created.onStart = {
                val id = Prefs(this).lastJobId
                if (id == 0L) windows().showNote("先选择一份作业") else beginRun(id)
            }
            created.onPause = { pauseInternal() }
            created.onOutsideTouch = { event -> onOutsideTouch(event) }
            created.onResume = { resumeInternal() }
            created.onStop = { stopRun(StopReason.USER) }
            created.onOpenApp = { openEditor(Prefs(this).lastJobId) }
            created.onPickRequest = { beginPick() }
            overlay = created
        }
    }

    private fun showIdleMenu() {
        scope.launch {
            val jobs = (application as FloatApp).repository.summaries()
            windows().showIdleMenu(
                jobs.map { it.id to it.name },
                Prefs(this@FloatRunService).lastJobId,
            )
        }
    }

    private fun openEditor(jobId: Long) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            if (jobId != 0L) putExtra(MainActivity.EXTRA_EDIT_JOB, jobId)
        }
        try {
            startActivity(intent)
        } catch (error: Exception) {
            android.util.Log.e(TAG, "open editor failed", error)
            windows().showNote("没有打开编辑页")
        }
    }

    private fun promote(text: String) {
        ensureChannel()
        val notification = buildNotification(text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (typed: Exception) {
            android.util.Log.w(TAG, "typed foreground start failed", typed)
            startForeground(NOTIFICATION_ID, notification)
        }
        windows()
    }

    private fun demote() {
        stopForeground(STOP_FOREGROUND_DETACH)
        overlay?.updateBall(RunPhase.IDLE)
    }

    private fun notify(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "作业运行", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatRunService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("浮序")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "停止", stop)
            .build()
    }

    private fun holdWake(timeoutMs: Long) {
        val pm = getSystemService(PowerManager::class.java)
        val lock = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "floatap:run").also {
            wakeLock = it
        }
        if (lock.isHeld) lock.release()
        lock.acquire(timeoutMs.coerceAtLeast(1L))
    }

    private fun releaseWake() {
        val lock = wakeLock ?: return
        if (lock.isHeld) lock.release()
    }

    companion object {
        private const val CHANNEL_ID = "floatap-run"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_START = "app.floatap.android.START"
        private const val ACTION_PAUSE = "app.floatap.android.PAUSE"
        private const val ACTION_RESUME = "app.floatap.android.RESUME"
        private const val ACTION_STOP = "app.floatap.android.STOP"
        private const val ACTION_PICK = "app.floatap.android.PICK"
        private const val ACTION_SHOW_BALL = "app.floatap.android.BALL"
        private const val EXTRA_JOB_ID = "job_id"
        private const val EXTRA_COLLAPSE = "collapse"
        private const val TAG = "Floatap"

        fun startJob(context: Context, jobId: Long) {
            val intent = Intent(context, FloatRunService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_JOB_ID, jobId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun pick(context: Context) {
            val intent = Intent(context, FloatRunService::class.java).setAction(ACTION_PICK)
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)

        fun showBall(context: Context, collapse: Boolean = false) {
            val intent = Intent(context, FloatRunService::class.java)
                .setAction(ACTION_SHOW_BALL)
                .putExtra(EXTRA_COLLAPSE, collapse)
            ContextCompat.startForegroundService(context, intent)
        }

        private fun send(context: Context, action: String) {
            val intent = Intent(context, FloatRunService::class.java).setAction(action)
            context.startService(intent)
        }
    }
}
