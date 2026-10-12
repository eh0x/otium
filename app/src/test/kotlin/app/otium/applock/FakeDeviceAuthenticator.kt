package app.otium.applock

import androidx.fragment.app.FragmentActivity

/** Answers every prompt with [result], and remembers what it was asked to show. */
class FakeDeviceAuthenticator(
    var availability: LockAvailability = LockAvailability.AVAILABLE,
    var result: AuthResult = AuthResult.Success,
) : DeviceAuthenticator {
    val prompts = mutableListOf<AuthPrompt>()

    override fun availability(): LockAvailability = availability

    override fun authenticate(
        activity: FragmentActivity,
        prompt: AuthPrompt,
        onResult: (AuthResult) -> Unit,
    ) {
        prompts += prompt
        onResult(result)
    }
}
