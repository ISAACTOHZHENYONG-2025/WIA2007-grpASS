package my.edu.um.rainready

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import my.edu.um.rainready.ui.RainReadyApp
import my.edu.um.rainready.ui.theme.RainReadyTheme
import org.maplibre.android.MapLibre
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Must run before any MapView is created.
        MapLibre.getInstance(applicationContext)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )

        setContent {
            val baseContext = LocalContext.current

            val preferences = remember {
                baseContext.getSharedPreferences(
                    "rainready_settings",
                    Context.MODE_PRIVATE
                )
            }

            var language by remember {
                mutableStateOf(
                    preferences.getString("language", "en") ?: "en"
                )
            }

            DisposableEffect(preferences) {
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key == "language") {
                            language = preferences.getString("language", "en") ?: "en"
                        }
                    }

                preferences.registerOnSharedPreferenceChangeListener(listener)

                onDispose {
                    preferences.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            val currentConfiguration = LocalConfiguration.current

            val localizedConfiguration = remember(currentConfiguration, language) {
                Configuration(currentConfiguration).apply {
                    setLocale(Locale.forLanguageTag(language))
                }
            }

            val localizedContext = remember(baseContext, localizedConfiguration) {
                baseContext.createConfigurationContext(localizedConfiguration)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
                // The translated context is not the activity, so screens that ask for a permission
                // (location for the route check, camera for reports) need the activity named here.
                LocalActivityResultRegistryOwner provides this@MainActivity,
            ) {
                RainReadyTheme {
                    RainReadyApp()
                }
            }
        }
    }
}