package app.otium.work

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.otium.MainActivity
import app.otium.R
import app.otium.domain.error.AppError
import app.otium.ui.common.messageRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val CHANNEL_ID = "backup"
private const val FAILURE_NOTIFICATION_ID = 1

/**
 * Tells the user their scheduled backup keeps failing. It is quiet: no sound, and only after the
 * failure policy says enough runs in a row failed. When the user has not allowed notifications it
 * does nothing, as the Backup screen shows the same state.
 */
class BackupNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun backupFailed(error: AppError) {
        val manager = NotificationManagerCompat.from(context)
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val denied = needsPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (denied || !manager.areNotificationsEnabled()) return

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.backup_channel_name))
                .setDescription(context.getString(R.string.backup_channel_description))
                .build(),
        )
        manager.notify(FAILURE_NOTIFICATION_ID, notificationFor(error))
    }

    private fun notificationFor(error: AppError): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.getString(error.messageRes())
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.backup_failed_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
