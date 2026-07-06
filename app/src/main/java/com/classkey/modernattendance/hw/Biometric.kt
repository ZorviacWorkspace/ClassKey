package com.classkey.modernattendance.hw

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Launches the Android system biometric prompt (fingerprint / face / device credential).
 * ClassKey never sees or stores raw biometric data — only the success/failure result,
 * exactly as Android security policy requires.
 *
 * Covers: available, none enrolled, no hardware, hardware unavailable,
 * success, failure, cancel, lockout (system messages surface through onError).
 */
fun runBiometric(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Unit
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            onError("No fingerprint or screen lock is set up. Add one in phone Settings > Security, then retry.")
            return
        }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
            onError("This phone has no biometric hardware. Set a screen lock (PIN/pattern) to verify instead.")
            return
        }
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
            onError("Biometric hardware is busy or unavailable. Try again in a moment.")
            return
        }
        else -> {
            onError("Biometric/device verification is not available on this phone right now.")
            return
        }
    }

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Covers cancel, lockout, lockout-permanent, timeout — Android supplies the message.
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                onError("Fingerprint not recognised. Try again.")
            }
        }
    )

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(authenticators)
        .build()

    prompt.authenticate(info)
}
