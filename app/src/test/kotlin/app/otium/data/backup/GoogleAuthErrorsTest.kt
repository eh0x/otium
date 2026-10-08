package app.otium.data.backup

import app.otium.domain.error.AppError
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleAuthErrorsTest {

    private fun errorFor(code: Int) = ApiException(Status(code)).toAppError()

    @Test
    fun `no network is offline`() {
        assertEquals(AppError.Offline, errorFor(CommonStatusCodes.NETWORK_ERROR))
    }

    @Test
    fun `a slow answer is a timeout`() {
        assertEquals(AppError.Timeout, errorFor(CommonStatusCodes.TIMEOUT))
    }

    @Test
    fun `a missing sign-in asks the user to sign in again`() {
        assertEquals(AppError.AuthExpired, errorFor(CommonStatusCodes.SIGN_IN_REQUIRED))
    }

    @Test
    fun `a setup mistake stays visible with its code`() {
        val error = errorFor(CommonStatusCodes.DEVELOPER_ERROR)

        assertTrue(error is AppError.Unexpected && "10" in error.message)
    }
}
