package app.otium.applock

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject

/** Whether this phone can confirm its owner at all. */
enum class LockAvailability {
    AVAILABLE,

    /** No PIN, pattern or password is set, so neither biometrics nor a credential can be asked. */
    NO_SCREEN_LOCK,
}

/** How asking the owner to confirm went. */
sealed interface AuthResult {
    data object Success : AuthResult

    /** The owner closed the prompt. Nothing to report; they can try again. */
    data object Cancelled : AuthResult

    /** The screen lock went away since the app lock was turned on. */
    data object NoScreenLock : AuthResult

    /** The system refused for now, for example after too many attempts. */
    data class Failed(val message: String) : AuthResult
}

/** The words the system prompt shows. */
data class AuthPrompt(val title: String, val subtitle: String)

/** Confirms the phone's owner with the system prompt. Behind an interface so tests can fake it. */
interface DeviceAuthenticator {
    fun availability(): LockAvailability

    /** Shows the system prompt over [activity] and reports the outcome once to [onResult]. */
    fun authenticate(activity: FragmentActivity, prompt: AuthPrompt, onResult: (AuthResult) -> Unit)
}

/**
 * The system prompt, accepting the face, a fingerprint, or the phone's PIN, pattern or password.
 *
 * `BIOMETRIC_WEAK` (Class 2) lets face unlock in on the many phones whose face sensor is not Class
 * 3; with `DEVICE_CREDENTIAL` the screen lock is always there as a fallback, so the owner cannot be
 * locked out by a sensor. This pair is supported from API 26; `DEVICE_CREDENTIAL` alone and
 * `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` are not on Android 10 and lower. The lock guards the
 * screen, not a key, so no `CryptoObject` is used. Confirmation is not required, as Android's
 * guidance suggests for viewing content: a recognised face opens the app straight away.
 */
class BiometricDeviceAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceAuthenticator {

    override fun availability(): LockAvailability {
        val biometric = BiometricManager.from(context).canAuthenticate(ALLOWED)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        // Android 10 and lower answer for the credential only through the keyguard.
        val secure = biometric == BiometricManager.BIOMETRIC_SUCCESS ||
            keyguard?.isDeviceSecure == true
        return if (secure) LockAvailability.AVAILABLE else LockAvailability.NO_SCREEN_LOCK
    }

    override fun authenticate(
        activity: FragmentActivity,
        prompt: AuthPrompt,
        onResult: (AuthResult) -> Unit,
    ) {
        val cipher = try {
            getOrCreateSecretKey()
            getCipher().apply { init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey()) }
        } catch (e: Exception) {
            onResult(AuthResult.Failed(e.message ?: "Failed to initialize secure authentication"))
            return
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authenticatedCipher = result.cryptoObject?.cipher
                if (authenticatedCipher == null) {
                    onResult(AuthResult.Failed("Missing cryptographic proof"))
                    return
                }
                try {
                    authenticatedCipher.doFinal(AUTH_CHALLENGE)
                    onResult(AuthResult.Success)
                } catch (e: Exception) {
                    onResult(AuthResult.Failed(e.message ?: "Cryptographic authentication failed"))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(errorCode.toResult(errString.toString()))
            }

            // A face or finger not recognised: the prompt stays open for another try.
            override fun onAuthenticationFailed() = Unit
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(prompt.title)
            .setSubtitle(prompt.subtitle)
            .setAllowedAuthenticators(ALLOWED)
            .setConfirmationRequired(false)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun Int.toResult(message: String): AuthResult = when (this) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED,
        -> AuthResult.Cancelled

        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> AuthResult.NoScreenLock

        else -> AuthResult.Failed(message)
    }

    private fun getCipher(): Cipher =
        Cipher.getInstance(
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}",
        )

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ALLOWED = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        const val KEY_ALIAS = "app.otium.applock.biometric_key"
        val AUTH_CHALLENGE = "applock-auth-challenge".toByteArray()
    }
}

/** The activity behind a Compose context, which the system prompt is shown over. */
tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
