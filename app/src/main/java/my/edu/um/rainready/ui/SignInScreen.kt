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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import my.edu.um.rainready.UmAuth

@Composable
fun SignInScreen(onBack: () -> Unit) {
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
            Text("Back")
        }

        Text(
            text = "UM sign-in",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Sign in to report or confirm campus hazards. The map is open to everyone.")

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("UM email") },
            supportingText = { Text("@siswa.um.edu.my or @um.edu.my") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
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
            Text("Sign in")
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
            Text("Create account")
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
            Text("Resend verification")
        }

        if (busy) Text("Please wait…")
        if (message.isNotBlank()) Text(message)
    }
}
