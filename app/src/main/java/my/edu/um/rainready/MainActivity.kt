package my.edu.um.rainready

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import my.edu.um.rainready.ui.RainReadyApp
import org.maplibre.android.MapLibre
import my.edu.um.rainready.ui.theme.RainReadyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(applicationContext) // must run before any MapView is created
        // The app is light-only, so keep dark status and navigation bar icons even in system dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            RainReadyTheme {
                RainReadyApp()
            }
        }
    }
}
