package my.edu.um.rainready.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import my.edu.um.rainready.UmAuth

private fun translateAuthMessage(message: String, isMalay: Boolean): String {
    if (!isMalay) return message

    return when (message) {
        "Enter a valid UM email address." ->
            "Masukkan alamat e-mel UM yang sah."

        "Use a password with at least 6 characters." ->
            "Gunakan kata laluan sekurang-kurangnya 6 aksara."

        "Account could not be loaded. Try signing in." ->
            "Akaun tidak dapat dimuatkan. Cuba log masuk."

        "Account created. Check your email and verify it before signing in." ->
            "Akaun telah dibuat. Semak dan sahkan e-mel anda sebelum log masuk."

        "Account created, but verification email failed. Use Resend verification when available." ->
            "Akaun telah dibuat, tetapi e-mel pengesahan gagal dihantar. Gunakan Hantar semula pengesahan."

        "Could not create account. It may already exist, or the password may not meet the project policy." ->
            "Akaun tidak dapat dibuat. Akaun mungkin sudah wujud atau kata laluan tidak memenuhi syarat."

        "Enter your UM email and password." ->
            "Masukkan e-mel UM dan kata laluan anda."

        "Signed in successfully." ->
            "Log masuk berjaya."

        "Verify your email before signing in." ->
            "Sahkan e-mel anda sebelum log masuk."

        "Sign-in failed. Check your details and connection." ->
            "Log masuk gagal. Semak maklumat dan sambungan anda."

        "Could not load your account. Try again." ->
            "Akaun tidak dapat dimuatkan. Cuba lagi."

        "Your email is already verified. Tap Sign in." ->
            "E-mel anda sudah disahkan. Tekan Log masuk."

        "Verification email sent. Check your inbox." ->
            "E-mel pengesahan telah dihantar. Semak peti masuk anda."

        "Could not send the email. Try again later." ->
            "E-mel tidak dapat dihantar. Cuba lagi kemudian."

        "Check your email, password, and connection." ->
            "Semak e-mel, kata laluan dan sambungan anda."

        else -> message
    }
}

@Composable
fun SignInScreen(onBack: () -> Unit) {
    val isMalay = LocalConfiguration.current.locales[0].language == "ms"

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val canSubmit = !busy && email.isNotBlank() && password.isNotBlank()

    BackHandler(enabled = busy) {}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack, enabled = !busy) {
            Text(if (isMalay) "Kembali" else "Back")
        }

        Text(
            text = if (isMalay) "Log masuk UM" else "UM sign-in",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            if (isMalay) {
                "Log masuk untuk melaporkan atau mengesahkan bahaya di kampus. Peta boleh dilihat oleh semua."
            } else {
                "Sign in to report or confirm campus hazards. The map is open to everyone."
            }
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = {
                Text(if (isMalay) "E-mel UM" else "UM email")
            },
            supportingText = {
                Text(
                    if (isMalay) "@siswa.um.edu.my atau @um.edu.my"
                    else "@siswa.um.edu.my or @um.edu.my"
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = {
                Text(if (isMalay) "Kata laluan" else "Password")
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                busy = true
                message = ""
                UmAuth.signIn(email, password) { success, result ->
                    busy = false
                    message = result
                    if (success) password = ""
                }
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isMalay) "Log masuk" else "Sign in")
        }

        OutlinedButton(
            onClick = {
                busy = true
                message = ""
                UmAuth.createAccount(email, password) { result ->
                    busy = false
                    message = result
                }
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isMalay) "Buat akaun" else "Create account")
        }

        TextButton(
            onClick = {
                busy = true
                message = ""
                UmAuth.resendVerification(email, password) { result ->
                    busy = false
                    message = result
                }
            },
            enabled = canSubmit
        ) {
            Text(if (isMalay) "Hantar semula pengesahan" else "Resend verification")
        }

        if (busy) {
            Text(if (isMalay) "Sila tunggu…" else "Please wait…")
        }

        if (message.isNotBlank()) {
            Text(translateAuthMessage(message, isMalay))
        }
    }
}