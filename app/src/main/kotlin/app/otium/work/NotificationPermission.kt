package app.otium.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Whether a notification can be shown: on Android 13 and later the owner has allowed them, and
 * on every version they are not switched off for the app. Notices are courtesies, so when this
 * is false the caller does nothing; the screens show the same state.
 */
internal fun Context.mayNotify(): Boolean {
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val denied = needsPermission &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    return !denied && NotificationManagerCompat.from(this).areNotificationsEnabled()
}
