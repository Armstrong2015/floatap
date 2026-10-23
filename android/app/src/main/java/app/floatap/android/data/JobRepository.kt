package app.floatap.android.data

import app.floatap.android.domain.Job
import app.floatap.android.domain.JobSummary
import app.floatap.android.domain.MAX_JOBS
import app.floatap.android.domain.RunRecord
import app.floatap.android.domain.Step
import app.floatap.android.domain.StopReason
import app.floatap.android.domain.WaitMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class JobRepository(
    private val dao: JobDao,
    private val runDao: RunDao,
) {
    fun observe(): Flow<List<JobSummary>> {
        return dao.observe().map { rows ->
            rows.map { JobSummary(it.id, it.name, it.stepCount, it.updatedAt) }
        }
    }

    fun observeRuns(limit: Int = 100): Flow<List<RunRecord>> {
        return runDao.observe(limit).map { rows -> rows.map { it.toDomain() } }
    }

    suspend fun count(): Int = dao.count()

    suspend fun summaries(): List<JobSummary> = observe().first()

    suspend fun get(id: Long): Job? {
        val job = dao.job(id) ?: return null
        return job.toDomain(dao.steps(id).map { it.toDomain() })
    }

    suspend fun allJobs(): List<Job> {
        return dao.allJobs().map { entity ->
            entity.toDomain(dao.steps(entity.id).map { it.toDomain() })
        }
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

    suspend fun importJob(job: Job): Long {
        if (dao.count() >= MAX_JOBS) {
            error("作业已有 $MAX_JOBS 个，先删除或导出后再导入")
        }
        return save(job.copy(id = 0))
    }

    suspend fun delete(id: Long) {
        dao.deleteJob(id)
    }

    suspend fun addRun(record: RunRecord) {
        runDao.insert(record.toEntity())
        runDao.trim(100)
    }

    suspend fun clearRuns() {
        runDao.clear()
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
    colorX = null,
    colorY = null,
    colorArgb = null,
    colorTolerance = null,
    colorOnMismatch = null,
)

private fun RunEntity.toDomain() = RunRecord(
    id = id,
    jobId = jobId,
    jobName = jobName,
    startedAt = startedAt,
    endedAt = endedAt,
    stopReason = runCatching { StopReason.valueOf(stopReason) }.getOrDefault(StopReason.USER),
    clicks = clicks,
)

private fun RunRecord.toEntity() = RunEntity(
    id = id,
    jobId = jobId,
    jobName = jobName,
    startedAt = startedAt,
    endedAt = endedAt,
    stopReason = stopReason.name,
    clicks = clicks,
)
