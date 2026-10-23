package app.floatap.android.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val updatedAt: Long,
    val durationMs: Long?,
    val clickLimit: Int?,
    val loopLimit: Int?,
    val scheduleStartMinutes: Int?,
    val scheduleEndMinutes: Int?,
    val countdownSec: Int,
    val pauseOnUserTouch: Boolean,
)

@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jobId")],
)
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val stepOrder: Int,
    val name: String,
    val x: Int,
    val y: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val rotation: Int,
    val waitMode: String,
    val waitMinMs: Long,
    val waitMaxMs: Long,
    val colorX: Int? = null,
    val colorY: Int? = null,
    val colorArgb: Int? = null,
    val colorTolerance: Int? = null,
    val colorOnMismatch: String? = null,
)

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long?,
    val jobName: String,
    val startedAt: Long,
    val endedAt: Long,
    val stopReason: String,
    val clicks: Int,
)

data class JobSummaryRow(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val stepCount: Int,
)
