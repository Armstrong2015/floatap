package app.floatap.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query(
        """
        SELECT jobs.id, jobs.name, jobs.updatedAt,
        (SELECT COUNT(*) FROM steps WHERE steps.jobId = jobs.id) AS stepCount
        FROM jobs ORDER BY updatedAt DESC
        """,
    )
    fun observe(): Flow<List<JobSummaryRow>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun job(id: Long): JobEntity?

    @Query("SELECT * FROM steps WHERE jobId = :id ORDER BY stepOrder")
    suspend fun steps(id: Long): List<StepEntity>

    @Insert
    suspend fun insertJob(job: JobEntity): Long

    @Update
    suspend fun updateJob(job: JobEntity)

    @Insert
    suspend fun insertSteps(steps: List<StepEntity>)

    @Query("DELETE FROM steps WHERE jobId = :id")
    suspend fun deleteSteps(id: Long)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteJob(id: Long)

    @Query("SELECT COUNT(*) FROM jobs")
    suspend fun count(): Int

    @Query("SELECT * FROM jobs ORDER BY updatedAt DESC")
    suspend fun allJobs(): List<JobEntity>
}

@Dao
interface RunDao {
    @Query("SELECT * FROM runs ORDER BY endedAt DESC LIMIT :limit")
    fun observe(limit: Int = 100): Flow<List<RunEntity>>

    @Insert
    suspend fun insert(run: RunEntity): Long

    @Query(
        """
        DELETE FROM runs WHERE id NOT IN (
            SELECT id FROM runs ORDER BY endedAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trim(keep: Int = 100)

    @Query("DELETE FROM runs")
    suspend fun clear()
}
