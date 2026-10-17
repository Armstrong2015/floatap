package app.floatap.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [JobEntity::class, StepEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FloatDatabase : RoomDatabase() {
    abstract fun jobs(): JobDao

    companion object {
        fun create(context: Context): FloatDatabase {
            return Room.databaseBuilder(context, FloatDatabase::class.java, "floatap.db")
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
