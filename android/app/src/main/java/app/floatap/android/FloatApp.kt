package app.floatap.android

import android.app.Application
import app.floatap.android.data.FloatDatabase
import app.floatap.android.data.JobRepository

class FloatApp : Application() {
    lateinit var repository: JobRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = JobRepository(FloatDatabase.create(this).jobs())
    }
}
