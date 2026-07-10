package com.example.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val result = biometricManager.canAuthenticate(authenticators)
        return result != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE && result != BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
    }

    fun isBiometricEnrolled(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val result = biometricManager.canAuthenticate(authenticators)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun getBiometricStatus(context: Context): String {
        val biometricManager = BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        return when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> "SUCCESS"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "NO_HARDWARE"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "HW_UNAVAILABLE"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "NONE_ENROLLED"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "SECURITY_UPDATE_REQUIRED"
            else -> "UNKNOWN"
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Ranisa Security",
        subtitle: String = "Verify your fingerprint to continue.",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(-1, e.message ?: "Authentication failed to start")
        }
    }

    fun runWithBiometric(
        context: Context,
        title: String,
        subtitle: String,
        action: () -> Unit
    ) {
        val sharedPrefs = context.getSharedPreferences("ranisa_prefs", Context.MODE_PRIVATE)
        val isBiometricEnabled = sharedPrefs.getBoolean("biometric_lock_enabled", false)
        if (!isBiometricEnabled) {
            action()
        } else {
            val activity = context as? androidx.fragment.app.FragmentActivity
            if (activity == null) {
                // If we don't have FragmentActivity, we cannot show the BiometricPrompt dialog.
                // Since this is a sensitive action, we must block it and show an error.
                android.widget.Toast.makeText(context, "Security verification failed: Activity context unavailable", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            authenticate(
                activity = activity,
                title = title,
                subtitle = subtitle,
                onSuccess = {
                    action()
                },
                onError = { _, errString ->
                    android.widget.Toast.makeText(context, "Verification failed: $errString", android.widget.Toast.LENGTH_SHORT).show()
                },
                onFailed = {
                    android.widget.Toast.makeText(context, "Verification failed. Please try again.", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
