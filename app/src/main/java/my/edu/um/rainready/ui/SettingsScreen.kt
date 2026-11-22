package my.edu.um.rainready.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import my.edu.um.rainready.UmAuth
import org.json.JSONObject
import java.util.Locale

private const val SETTINGS_FILE = "rainready_settings"
private const val LANGUAGE_KEY = "language"
private const val ZONES_KEY = "followed_zones"

private data class ZoneOption(
    val id: String,
    val name: String
)

/**
 * Reads the existing zone IDs and names from the map's zone file.
 */
private fun readZoneOptions(context: Context): List<ZoneOption> {
    return try {
        val json = context.assets.open("zones.geojson")
            .bufferedReader()
            .use { it.readText() }

        val features = JSONObject(json).getJSONArray("features")

        buildList {
            for (index in 0 until features.length()) {
                val properties = features.getJSONObject(index)
                    .getJSONObject("properties")

                val id = properties.optString("zoneId")
                val name = properties.optString("name")

                if (id.isNotBlank()) {
                    add(ZoneOption(id, name.ifBlank { id }))
                }
            }
        }.sortedBy { it.id.removePrefix("Z").toIntOrNull() ?: Int.MAX_VALUE }
    } catch (error: Exception) {
        emptyList()
    }
}

@Composable
fun SettingsScreen(onSignIn: () -> Unit = {}) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
    }
    val auth = remember { FirebaseAuth.getInstance() }

    var language by remember {
        mutableStateOf(preferences.getString(LANGUAGE_KEY, "en") ?: "en")
    }

    var followedZones by remember {
        mutableStateOf(
            preferences.getStringSet(ZONES_KEY, emptySet())?.toSet() ?: emptySet()
        )
    }

    val zoneOptions = remember { readZoneOptions(context) }
    val isMalay = language == "ms"

    var signedInEmail by remember { mutableStateOf<String?>(null) }
    var signedInUid by remember { mutableStateOf<String?>(null) }

    val uid = signedInUid
    var hasStaffAccess by remember(uid) { mutableStateOf(false) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            val email = user?.email
            val domain = email
                ?.substringAfterLast("@")
                ?.lowercase(Locale.ROOT)

            val isVerifiedUmUser = user?.isEmailVerified == true &&
                    domain in setOf("siswa.um.edu.my", "um.edu.my")

            signedInEmail = if (isVerifiedUmUser) email else null
            signedInUid = if (isVerifiedUmUser) user?.uid else null
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(uid) {
        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("admins")
                .document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener { document ->
                    if (auth.currentUser?.uid == uid) {
                        hasStaffAccess = document.exists()
                    }
                }
        }
    }

    fun selectLanguage(selected: String) {
        language = selected
        preferences.edit().putString(LANGUAGE_KEY, selected).apply()
    }

    fun toggleZone(zoneId: String, checked: Boolean) {
        val updated = if (checked) {
            followedZones + zoneId
        } else {
            followedZones - zoneId
        }

        followedZones = updated
        preferences.edit().putStringSet(ZONES_KEY, updated.toSet()).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (isMalay) "Tetapan" else "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = if (isMalay) "Akaun" else "Account",
            style = MaterialTheme.typography.titleMedium
        )

        val email = signedInEmail

        if (email == null) {
            Button(onClick = onSignIn) {
                Text(if (isMalay) "Log masuk dengan e-mel UM" else "Sign in with UM email")
            }
        } else {
            Text(
                if (isMalay) "Log masuk sebagai $email"
                else "Signed in as $email"
            )

            if (hasStaffAccess) {
                Text(
                    if (isMalay) "Akses kakitangan diaktifkan"
                    else "Staff access enabled"
                )
            }

            OutlinedButton(onClick = { UmAuth.signOut() }) {
                Text(if (isMalay) "Log keluar" else "Sign out")
            }
        }

        HorizontalDivider()

        Text(
            text = if (isMalay) "Bahasa" else "Language",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectLanguage("en") },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = language == "en",
                onClick = { selectLanguage("en") }
            )
            Text("English")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectLanguage("ms") },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = language == "ms",
                onClick = { selectLanguage("ms") }
            )
            Text("Bahasa Melayu")
        }

        HorizontalDivider()

        Text(
            text = if (isMalay) "Zon yang diikuti" else "Followed zones",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = if (isMalay) {
                "Pilih zon yang ingin dilihat. Jika tiada zon dipilih, semua zon akan dipaparkan."
            } else {
                "Choose the zones you want to see. If none are selected, all zones will be shown."
            },
            style = MaterialTheme.typography.bodyMedium
        )

        if (zoneOptions.isEmpty()) {
            Text(
                if (isMalay) "Senarai zon tidak dapat dimuatkan."
                else "Could not load the zone list."
            )
        } else {
            zoneOptions.forEach { zone ->
                val checked = zone.id in followedZones

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { toggleZone(zone.id, !checked) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { toggleZone(zone.id, it) }
                    )

                    Text("${zone.id}: ${zone.name}")
                }
            }
        }
    }
}