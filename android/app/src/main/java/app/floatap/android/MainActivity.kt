package app.floatap.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.floatap.android.ui.FloatAppRoot
import app.floatap.android.ui.FloatTheme
import app.floatap.android.ui.OpenBus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        publishEdit(intent)
        enableEdgeToEdge()
        setContent {
            FloatTheme {
                FloatAppRoot(startOnGuide = !Prefs(this).boundaryAccepted)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishEdit(intent)
    }

    private fun publishEdit(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_EDIT_JOB, 0L) ?: 0L
        if (id != 0L) OpenBus.editJobId.value = id
    }

    companion object {
        const val EXTRA_EDIT_JOB = "edit_job_id"
    }
}
