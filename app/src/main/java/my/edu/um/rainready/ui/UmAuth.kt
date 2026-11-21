package my.edu.um.rainready

import android.util.Patterns
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

object UmAuth {
    private val auth = FirebaseAuth.getInstance()

    private fun normalized(email: String): String =
        email.trim().lowercase(Locale.ROOT)

    private fun validEmail(email: String): Boolean {
        val domain = email.substringAfterLast("@", "")
        return Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                domain in setOf("siswa.um.edu.my", "um.edu.my")
    }

    fun createAccount(
        email: String,
        password: String,
        onResult: (String) -> Unit
    ) {
        val cleanEmail = normalized(email)

        if (!validEmail(cleanEmail)) {
            onResult("Enter a valid UM email address.")
            return
        }

        if (password.length < 6) {
            onResult("Use a password with at least 6 characters.")
            return
        }

        auth.createUserWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    onResult("Account could not be loaded. Try signing in.")
                    return@addOnSuccessListener
                }

                user.sendEmailVerification()
                    .addOnSuccessListener {
                        auth.signOut()
                        onResult(
                            "Account created. Check your email and verify " +
                                    "it before signing in."
                        )
                    }
                    .addOnFailureListener {
                        auth.signOut()
                        onResult(
                            "Account created, but verification email failed. " +
                                    "Use Resend verification when available."
                        )
                    }
            }
            .addOnFailureListener {
                onResult(
                    "Could not create account. It may already exist, " +
                            "or the password may not meet the project policy."
                )
            }
    }

    fun signIn(
        email: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanEmail = normalized(email)

        if (!validEmail(cleanEmail) || password.isBlank()) {
            onResult(false, "Enter your UM email and password.")
            return
        }

        auth.signInWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { result ->
                if (result.user?.isEmailVerified == true) {
                    onResult(true, "Signed in successfully.")
                } else {
                    auth.signOut()
                    onResult(false, "Verify your email before signing in.")
                }
            }
            .addOnFailureListener {
                onResult(
                    false,
                    "Sign-in failed. Check your details and connection."
                )
            }
    }
    fun resendVerification(
        email: String,
        password: String,
        onResult: (String) -> Unit
    ) {
        val cleanEmail = normalized(email)

        if (!validEmail(cleanEmail) || password.isBlank()) {
            onResult("Enter your UM email and password.")
            return
        }

        auth.signInWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { result ->
                val user = result.user

                when {
                    user == null -> {
                        auth.signOut()
                        onResult("Could not load your account. Try again.")
                    }

                    user.isEmailVerified -> {
                        auth.signOut()
                        onResult("Your email is already verified. Tap Sign in.")
                    }

                    else -> {
                        user.sendEmailVerification().addOnCompleteListener { task ->
                            auth.signOut()
                            onResult(
                                if (task.isSuccessful) {
                                    "Verification email sent. Check your inbox."
                                } else {
                                    "Could not send the email. Try again later."
                                }
                            )
                        }
                    }
                }
            }
            .addOnFailureListener {
                onResult("Check your email, password, and connection.")
            }
    }
    fun signOut() {
        auth.signOut()
    }
}

