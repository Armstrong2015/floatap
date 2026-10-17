package app.floatap.android.data

import app.floatap.android.domain.Job
import app.floatap.android.domain.JobSummary
import app.floatap.android.domain.Step
import app.floatap.android.domain.WaitMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class JobRepository(private val dao: JobDao) {
    fun observe(): Flow<List<JobSummary>> {
        return dao.observe().map { rows ->
            rows.map { JobSummary(it.id, it.name, it.stepCount, it.updatedAt) }
        }
    }

    suspend fun count(): Int = dao.count()

    suspend fun get(id: Long): Job? {
        val job = dao.job(id) ?: return null
        return job.toDomain(dao.steps(id).map { it.toDomain() })
    }

    suspend fun save(job: Job): Long {
        val now = System.currentTimeMillis()
        val entity = job.toEntity(now)
        val id = if (job.id == 0L) {
            dao.insertJob(entity)
        } else {
            dao.updateJob(entity)
            dao.deleteSteps(job.id)
            job.id
        }
        dao.insertSteps(job.steps.map { it.toEntity(id) })
        return id
    }

    suspend fun delete(id: Long) {
        dao.deleteJob(id)
    }
}

private fun JobEntity.toDomain(steps: List<Step>) = Job(
    id = id,
    name = name,
    steps = steps,
    durationMs = durationMs,
    clickLimit = clickLimit,
    loopLimit = loopLimit,
    scheduleStartMinutes = scheduleStartMinutes,
    scheduleEndMinutes = scheduleEndMinutes,
    countdownSec = countdownSec,
    pauseOnUserTouch = pauseOnUserTouch,
    updatedAt = updatedAt,
)

private fun Job.toEntity(now: Long) = JobEntity(
    id = id,
    name = name,
    updatedAt = now,
    durationMs = durationMs,
    clickLimit = clickLimit,
    loopLimit = loopLimit,
    scheduleStartMinutes = scheduleStartMinutes,
    scheduleEndMinutes = scheduleEndMinutes,
    countdownSec = countdownSec,
    pauseOnUserTouch = pauseOnUserTouch,
)

private fun StepEntity.toDomain() = Step(
    id = id,
    order = stepOrder,
    name = name,
    x = x,
    y = y,
    displayWidth = displayWidth,
    displayHeight = displayHeight,
    rotation = rotation,
    waitMode = if (waitMode == WaitMode.RANGE.name) WaitMode.RANGE else WaitMode.FIXED,
    waitMinMs = waitMinMs,
    waitMaxMs = waitMaxMs,
)

private fun Step.toEntity(jobId: Long) = StepEntity(
    id = 0,
    jobId = jobId,
    stepOrder = order,
    name = name,
    x = x,
    y = y,
    displayWidth = displayWidth,
    displayHeight = displayHeight,
    rotation = rotation,
    waitMode = waitMode.name,
    waitMinMs = waitMinMs,
    waitMaxMs = waitMaxMs,
)
