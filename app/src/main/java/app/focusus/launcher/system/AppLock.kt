package app.focusus.launcher.system

import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.focusus.launcher.core.Store

/** Uses the phone's own fingerprint, face or screen lock. focUS never sees or stores the credential. */
object AppLock {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Asks for fingerprint / face / PIN. If the phone has no screen lock, [failOpen] decides
     * whether to continue anyway.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        failOpen: Boolean = true,
        onSuccess: () -> Unit,
    ) {
        if (!available(activity)) {
            if (failOpen) onSuccess()
            else Toast.makeText(activity, "Set a screen lock on your phone to use app lock", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }
                },
            )
            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(AUTH)
            if (subtitle != null) builder.setSubtitle(subtitle)
            prompt.authenticate(builder.build())
        } catch (e: Exception) {
            if (failOpen) onSuccess()
            else Toast.makeText(activity, "Couldn't open the lock screen check", Toast.LENGTH_SHORT).show()
        }
    }

    /** Runs [action] directly, or after authentication when app lock is on. */
    fun guard(activity: FragmentActivity?, title: String, action: () -> Unit) {
        if (Store.value.prefs.appLock && activity != null) {
            authenticate(activity, title, failOpen = false, onSuccess = action)
        } else {
            action()
        }
    }
}
