package app.floatap.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.floatap.android.ui.FloatAppRoot
import app.floatap.android.ui.FloatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FloatTheme {
                FloatAppRoot(startOnGuide = !Prefs(this).boundaryAccepted)
            }
        }
    }
}
