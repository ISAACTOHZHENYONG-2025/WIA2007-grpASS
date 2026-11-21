package my.edu.um.rainready.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import my.edu.um.rainready.UmAuth
import java.util.Locale

@Composable
fun SettingsScreen(onSignIn: () -> Unit = {}) {
    val auth = remember { FirebaseAuth.getInstance() }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Account")

        val email = signedInEmail

        if (email == null) {
            Button(onClick = onSignIn) {
                Text("Sign in with UM email")
            }
        } else {
            Text("Signed in as $email")

            if (hasStaffAccess) {
                Text("Staff access enabled")
            }

            OutlinedButton(onClick = { UmAuth.signOut() }) {
                Text("Sign out")
            }
        }
    }
}