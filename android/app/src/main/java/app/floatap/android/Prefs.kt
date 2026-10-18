package app.floatap.android

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("floatap", Context.MODE_PRIVATE)

    var boundaryAccepted: Boolean
        get() = sp.getBoolean(KEY_BOUNDARY, false)
        set(value) = sp.edit().putBoolean(KEY_BOUNDARY, value).apply()

    var lastJobId: Long
        get() = sp.getLong(KEY_LAST_JOB, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_JOB, value).apply()

    var lastJobName: String
        get() = sp.getString(KEY_LAST_JOB_NAME, "").orEmpty()
        set(value) = sp.edit().putString(KEY_LAST_JOB_NAME, value).apply()

    companion object {
        private const val KEY_BOUNDARY = "boundary_accepted"
        private const val KEY_LAST_JOB = "last_job_id"
        private const val KEY_LAST_JOB_NAME = "last_job_name"
    }
}
