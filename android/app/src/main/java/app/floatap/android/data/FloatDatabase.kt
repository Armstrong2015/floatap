package app.floatap.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [JobEntity::class, StepEntity::class, RunEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class FloatDatabase : RoomDatabase() {
    abstract fun jobs(): JobDao
    abstract fun runs(): RunDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE steps ADD COLUMN colorX INTEGER")
                db.execSQL("ALTER TABLE steps ADD COLUMN colorY INTEGER")
                db.execSQL("ALTER TABLE steps ADD COLUMN colorArgb INTEGER")
                db.execSQL("ALTER TABLE steps ADD COLUMN colorTolerance INTEGER")
                db.execSQL("ALTER TABLE steps ADD COLUMN colorOnMismatch TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS runs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        jobId INTEGER,
                        jobName TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        endedAt INTEGER NOT NULL,
                        stopReason TEXT NOT NULL,
                        clicks INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): FloatDatabase {
            return Room.databaseBuilder(context, FloatDatabase::class.java, "floatap.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
