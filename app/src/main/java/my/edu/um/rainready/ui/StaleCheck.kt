package my.edu.um.rainready.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import my.edu.um.rainready.model.millisUntilStale

/**
 * True once the data last refreshed at [updatedAtMillis] is more than 15 minutes old (FR2.1),
 * so the screen can show the grey "Can't get new reports" banner.
 *
 * It turns true on its own while the screen sits untouched: a local timer wakes up just after
 * the 15 minutes are up. No Firebase reads are involved. Android can pause that timer while the
 * phone sleeps, so the check also runs again whenever the app comes back to the front.
 * New data (a new [updatedAtMillis]) starts the count again. Always false while there is no data.
 */
@Composable
fun rememberIsStale(updatedAtMillis: Long?, clock: () -> Long = System::currentTimeMillis): Boolean {
    fun checkNow() = updatedAtMillis != null && millisUntilStale(updatedAtMillis, clock()) == 0L

    var stale by remember(updatedAtMillis) { mutableStateOf(checkNow()) }

    LaunchedEffect(updatedAtMillis) {
        if (updatedAtMillis == null) return@LaunchedEffect
        while (!stale) {
            delay(millisUntilStale(updatedAtMillis, clock()))
            stale = checkNow()
        }
    }

    LifecycleResumeEffect(updatedAtMillis) {
        stale = checkNow()
        onPauseOrDispose {}
    }

    return stale
}
