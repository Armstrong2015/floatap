package app.floatap.android.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.floatap.android.FloatApp
import app.floatap.android.Prefs
import app.floatap.android.domain.Job
import app.floatap.android.domain.JobDraft
import app.floatap.android.domain.Step
import app.floatap.android.domain.WaitMode
import app.floatap.android.domain.checkDraft
import app.floatap.android.domain.parseHourMinute
import app.floatap.android.overlay.PickerBus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StepUi(
    val name: String = "点击",
    val x: Int = -1,
    val y: Int = -1,
    val displayWidth: Int = 0,
    val displayHeight: Int = 0,
    val rotation: Int = 0,
    val intervalText: String = "500",
    val randomInterval: Boolean = false,
    val intervalMaxText: String = "",
)

data class EditUi(
    val ready: Boolean = false,
    val name: String = "",
    val steps: List<StepUi> = listOf(StepUi()),
    val loopText: String = "",
    val durationText: String = "60",
    val clickLimitText: String = "",
    val useScheduleStart: Boolean = false,
    val startHourText: String = "09",
    val startMinuteText: String = "00",
    val useScheduleEnd: Boolean = false,
    val endHourText: String = "18",
    val endMinuteText: String = "00",
    val countdown: Int = 3,
    val pauseOnTouch: Boolean = true,
    val error: String? = null,
    val warning: String? = null,
)

private fun minutesToHourMinute(minutes: Int): Pair<String, String> {
    val clamped = minutes.coerceIn(0, 23 * 60 + 59)
    return "%02d".format(clamped / 60) to "%02d".format(clamped % 60)
}

private fun Step.toUi(): StepUi {
    val ranged = waitMaxMs > waitMinMs
    return StepUi(
        name = name,
        x = x,
        y = y,
        displayWidth = displayWidth,
        displayHeight = displayHeight,
        rotation = rotation,
        intervalText = waitMinMs.toString(),
        randomInterval = ranged,
        intervalMaxText = if (ranged) waitMaxMs.toString() else "",
    )
}

class EditViewModel(app: Application, private val initialId: Long) : AndroidViewModel(app) {
    private val repository = (app as FloatApp).repository
    private val prefs = Prefs(app)
    private var currentId = initialId

    var state by mutableStateOf(EditUi())
        private set

    init {
        viewModelScope.launch {
            if (initialId == 0L) {
                val count = repository.count()
                state = state.copy(ready = true, name = "作业${count + 1}")
            } else {
                val job = repository.get(initialId)
                if (job == null) {
                    state = state.copy(ready = true, error = "找不到这份作业")
                } else {
                    val startHm = job.scheduleStartMinutes?.let { minutesToHourMinute(it) }
                    val endHm = job.scheduleEndMinutes?.let { minutesToHourMinute(it) }
                    state = EditUi(
                        ready = true,
                        name = job.name,
                        steps = job.steps.map { it.toUi() }.ifEmpty { listOf(StepUi()) },
                        loopText = job.loopLimit?.toString().orEmpty(),
                        durationText = job.durationMs?.div(1000)?.toString().orEmpty(),
                        clickLimitText = job.clickLimit?.toString().orEmpty(),
                        useScheduleStart = job.scheduleStartMinutes != null,
                        startHourText = startHm?.first ?: "09",
                        startMinuteText = startHm?.second ?: "00",
                        useScheduleEnd = job.scheduleEndMinutes != null,
                        endHourText = endHm?.first ?: "18",
                        endMinuteText = endHm?.second ?: "00",
                        countdown = job.countdownSec,
                        pauseOnTouch = job.pauseOnUserTouch,
                    )
                }
            }
        }
    }

    fun consumePick() {
        val pick = PickerBus.pending ?: return
        PickerBus.pending = null
        val index = pick.stepIndex
        state = state.copy(
            steps = state.steps.mapIndexed { i, step ->
                if (i != index) step else step.copy(
                    x = pick.x,
                    y = pick.y,
                    displayWidth = pick.width,
                    displayHeight = pick.height,
                    rotation = pick.rotation,
                )
            },
            error = null,
        )
    }

    fun savedId(): Long = currentId

    fun updateStep(index: Int, block: (StepUi) -> StepUi) {
        state = state.copy(steps = state.steps.mapIndexed { i, step -> if (i == index) block(step) else step })
    }

    fun addStep() {
        val previous = state.steps.lastOrNull() ?: StepUi()
        state = state.copy(
            steps = state.steps + previous.copy(
                name = "点击${state.steps.size + 1}",
                x = -1,
                y = -1,
                displayWidth = 0,
                displayHeight = 0,
                rotation = 0,
            ),
        )
    }

    fun moveStep(index: Int, delta: Int) {
        val target = index + delta
        val steps = state.steps.toMutableList()
        if (index !in steps.indices || target !in steps.indices) return
        val item = steps.removeAt(index)
        steps.add(target, item)
        state = state.copy(steps = steps)
    }

    fun deleteStep(index: Int) {
        if (state.steps.size <= 1 || index !in state.steps.indices) return
        state = state.copy(steps = state.steps.filterIndexed { i, _ -> i != index })
    }

    fun markPicking(index: Int) {
        prefs.lastStepIndex = index
    }

    fun update(block: (EditUi) -> EditUi) {
        state = block(state)
    }

    fun scheduleStartMinutesOrNull(ui: EditUi = state): Int? {
        if (!ui.useScheduleStart) return null
        return parseHourMinute(ui.startHourText, ui.startMinuteText)
    }

    fun scheduleEndMinutesOrNull(ui: EditUi = state): Int? {
        if (!ui.useScheduleEnd) return null
        return parseHourMinute(ui.endHourText, ui.endMinuteText)
    }

    fun save(allowMissingPoint: Boolean = false, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val ui = state
            val startMin = if (ui.useScheduleStart) {
                parseHourMinute(ui.startHourText, ui.startMinuteText) ?: -1
            } else {
                null
            }
            val endMin = if (ui.useScheduleEnd) {
                parseHourMinute(ui.endHourText, ui.endMinuteText) ?: -1
            } else {
                null
            }
            val checked = checkDraft(
                JobDraft(
                    name = ui.name.trim(),
                    x = -1,
                    y = -1,
                    displayWidth = 0,
                    displayHeight = 0,
                    rotation = 0,
                    intervalMs = 500,
                    durationSec = ui.durationText.toLongOrNull(),
                    clickLimit = ui.clickLimitText.toIntOrNull(),
                    countdownSec = ui.countdown,
                    pauseOnUserTouch = ui.pauseOnTouch,
                    existingCount = repository.count(),
                    isNew = currentId == 0L,
                    allowMissingPoint = allowMissingPoint,
                    loopLimit = ui.loopText.toIntOrNull(),
                    scheduleStartMinutes = startMin,
                    scheduleEndMinutes = endMin,
                    steps = ui.steps.map { step ->
                        app.floatap.android.domain.StepDraft(
                            name = step.name.trim(),
                            x = step.x,
                            y = step.y,
                            displayWidth = step.displayWidth,
                            displayHeight = step.displayHeight,
                            rotation = step.rotation,
                            intervalMs = step.intervalText.toLongOrNull() ?: -1L,
                            intervalMaxMs = if (step.randomInterval) step.intervalMaxText.toLongOrNull() ?: -1L else null,
                        )
                    },
                ),
            )
            if (checked.issue != null) {
                state = ui.copy(error = checked.message(), warning = null)
                return@launch
            }
            val id = repository.save(
                Job(
                    id = currentId,
                    name = ui.name.trim(),
                    steps = ui.steps.mapIndexed { index, step ->
                        val timing = checked.steps[index]
                        Step(
                            order = index,
                            name = timing.name,
                            x = step.x,
                            y = step.y,
                            displayWidth = step.displayWidth,
                            displayHeight = step.displayHeight,
                            rotation = step.rotation,
                            waitMode = if (timing.intervalMaxMs > timing.intervalMs) WaitMode.RANGE else WaitMode.FIXED,
                            waitMinMs = timing.intervalMs,
                            waitMaxMs = timing.intervalMaxMs,
                        )
                    },
                    durationMs = checked.durationMs,
                    clickLimit = ui.clickLimitText.toIntOrNull()?.takeIf { it > 0 },
                    loopLimit = checked.loopLimit,
                    scheduleStartMinutes = checked.scheduleStartMinutes,
                    scheduleEndMinutes = checked.scheduleEndMinutes,
                    countdownSec = ui.countdown,
                    pauseOnUserTouch = ui.pauseOnTouch,
                ),
            )
            currentId = id
            prefs.lastJobId = id
            prefs.lastJobName = ui.name.trim()
            val adjustedAt = checked.steps.indexOfFirst { it.adjusted }
            state = ui.copy(
                error = null,
                warning = when {
                    !checked.intervalAdjusted -> null
                    ui.steps.size == 1 -> "已把间隔调整为 50 毫秒"
                    else -> "已把第 ${adjustedAt + 1} 步的间隔调整为 50 毫秒"
                },
                steps = ui.steps.mapIndexed { index, step ->
                    val timing = checked.steps[index]
                    step.copy(
                        name = timing.name,
                        intervalText = timing.intervalMs.toString(),
                        intervalMaxText = if (step.randomInterval) timing.intervalMaxMs.toString() else "",
                    )
                },
            )
            onSaved(id)
        }
    }

    companion object {
        fun factory(id: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                error("Use create(modelClass, extras)")
            }

            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras,
            ): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                @Suppress("UNCHECKED_CAST")
                return EditViewModel(app, id) as T
            }
        }
    }
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as FloatApp).repository
    private val prefs = Prefs(app)
    val jobs = repository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var message by mutableStateOf<String?>(null)
        private set

    fun delete(id: Long) {
        if (app.floatap.android.engine.RunBus.activeJobId == id &&
            app.floatap.android.engine.RunBus.phase.value != app.floatap.android.domain.RunPhase.IDLE
        ) {
            message = "先停止这份作业再删除"
            return
        }
        viewModelScope.launch {
            repository.delete(id)
            if (prefs.lastJobId == id) {
                prefs.lastJobId = 0L
                prefs.lastJobName = ""
            }
        }
    }

    fun clearMessage() {
        message = null
    }

    suspend fun exportJobsJson(ids: Collection<Long>): String {
        if (ids.isEmpty()) error("请先勾选要导出的作业")
        val selected = ids.toSet()
        val jobs = repository.allJobs().filter { it.id in selected }
        if (jobs.isEmpty()) error("勾选的作业已经不在了")
        val array = org.json.JSONArray()
        for (job in jobs) {
            array.put(org.json.JSONObject(app.floatap.android.domain.jobToJson(job)))
        }
        val root = org.json.JSONObject()
        root.put("format", 1)
        root.put("jobs", array)
        return root.toString(2)
    }

    suspend fun importJson(text: String): Int {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) {
            throw app.floatap.android.domain.JobJsonException("不是有效的作业 JSON")
        }
        val toImport = mutableListOf<app.floatap.android.domain.Job>()
        val obj = org.json.JSONObject(trimmed)
        if (obj.has("jobs")) {
            val arr = obj.getJSONArray("jobs")
            for (i in 0 until arr.length()) {
                toImport += app.floatap.android.domain.jobFromJson(arr.getJSONObject(i).toString())
            }
        } else {
            toImport += app.floatap.android.domain.jobFromJson(trimmed)
        }
        if (toImport.isEmpty()) error("文件里没有作业")
        if (repository.count() + toImport.size > app.floatap.android.domain.MAX_JOBS) {
            error("导入后会超过 ${app.floatap.android.domain.MAX_JOBS} 份作业，请先删除一些再导入")
        }
        val taken = repository.allJobs().map { it.name }.toMutableSet()
        var count = 0
        for (job in toImport) {
            val name = app.floatap.android.domain.allocateJobName(job.name, taken)
            repository.importJob(job.copy(name = name))
            count += 1
        }
        return count
    }
}

class RunsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as FloatApp).repository
    val runs = repository.observeRuns().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() {
        viewModelScope.launch { repository.clearRuns() }
    }
}
