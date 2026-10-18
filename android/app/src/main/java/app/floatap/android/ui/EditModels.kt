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
import app.floatap.android.domain.checkDraft
import app.floatap.android.overlay.PickerBus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditUi(
    val ready: Boolean = false,
    val name: String = "",
    val x: Int = -1,
    val y: Int = -1,
    val displayWidth: Int = 0,
    val displayHeight: Int = 0,
    val rotation: Int = 0,
    val intervalText: String = "500",
    val durationText: String = "60",
    val clickLimitText: String = "",
    val countdown: Int = 3,
    val pauseOnTouch: Boolean = true,
    val error: String? = null,
    val warning: String? = null,
)

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
                    val step = job.steps.firstOrNull()
                    state = EditUi(
                        ready = true,
                        name = job.name,
                        x = step?.x ?: -1,
                        y = step?.y ?: -1,
                        displayWidth = step?.displayWidth ?: 0,
                        displayHeight = step?.displayHeight ?: 0,
                        rotation = step?.rotation ?: 0,
                        intervalText = (step?.waitMinMs ?: 500L).toString(),
                        durationText = job.durationMs?.div(1000)?.toString().orEmpty(),
                        clickLimitText = job.clickLimit?.toString().orEmpty(),
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
        state = state.copy(
            x = pick.x,
            y = pick.y,
            displayWidth = pick.width,
            displayHeight = pick.height,
            rotation = pick.rotation,
            error = null,
        )
    }

    fun savedId(): Long = currentId

    fun refreshPoint() {
        val id = currentId
        if (!state.ready || id == 0L) return
        viewModelScope.launch {
            val step = repository.get(id)?.steps?.firstOrNull() ?: return@launch
            if (currentId != id) return@launch
            state = state.copy(
                x = step.x,
                y = step.y,
                displayWidth = step.displayWidth,
                displayHeight = step.displayHeight,
                rotation = step.rotation,
            )
        }
    }

    fun update(block: (EditUi) -> EditUi) {
        state = block(state)
    }

    fun save(onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val ui = state
            val checked = checkDraft(
                JobDraft(
                    name = ui.name.trim(),
                    x = ui.x,
                    y = ui.y,
                    displayWidth = ui.displayWidth,
                    displayHeight = ui.displayHeight,
                    rotation = ui.rotation,
                    intervalMs = ui.intervalText.toLongOrNull() ?: -1L,
                    durationSec = ui.durationText.toLongOrNull(),
                    clickLimit = ui.clickLimitText.toIntOrNull(),
                    countdownSec = ui.countdown,
                    pauseOnUserTouch = ui.pauseOnTouch,
                    existingCount = repository.count(),
                    isNew = currentId == 0L,
                ),
            )
            if (checked.issue != null) {
                state = ui.copy(error = checked.issue.message, warning = null)
                return@launch
            }
            val id = repository.save(
                Job(
                    id = currentId,
                    name = ui.name.trim(),
                    steps = listOf(
                        Step(
                            order = 0,
                            name = "点击",
                            x = ui.x,
                            y = ui.y,
                            displayWidth = ui.displayWidth,
                            displayHeight = ui.displayHeight,
                            rotation = ui.rotation,
                            waitMinMs = checked.intervalMs,
                            waitMaxMs = checked.intervalMs,
                        ),
                    ),
                    durationMs = checked.durationMs,
                    clickLimit = ui.clickLimitText.toIntOrNull()?.takeIf { it > 0 },
                    countdownSec = ui.countdown,
                    pauseOnUserTouch = ui.pauseOnTouch,
                ),
            )
            currentId = id
            prefs.lastJobId = id
            prefs.lastJobName = ui.name.trim()
            state = ui.copy(
                error = null,
                warning = if (checked.intervalAdjusted) "已把间隔调整为 50 毫秒" else null,
                intervalText = checked.intervalMs.toString(),
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
}
