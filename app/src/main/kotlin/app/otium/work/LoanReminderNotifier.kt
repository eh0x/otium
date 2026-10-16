package app.otium.work

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.otium.MainActivity
import app.otium.R
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.LoanReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

private const val CHANNEL_ID = "loans"
private const val NOTIFICATION_TAG = "loan"

/**
 * Reminds the owner, once and quietly, about a book that is out. No sound, never red, and no
 * "overdue": it says who has the book and since when. The borrower's name is about someone else,
 * so on a locked screen the notice says only that a book is out; the details show once the phone
 * is unlocked. When the owner has not allowed notifications it does nothing, as the Lent out
 * screen shows the same loans and reminder days.
 */
class LoanReminderNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun remind(reminder: LoanReminder) {
        if (!context.mayNotify()) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.loan_channel_name))
                .setDescription(context.getString(R.string.loan_channel_description))
                .build(),
        )
        manager.notify(NOTIFICATION_TAG, reminder.loanId.hashCode(), notificationFor(reminder))
    }

    private fun notificationFor(reminder: LoanReminder): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val since = LocalDate.ofEpochDay(reminder.lentOn)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val lent = reminder.direction == LoanDirection.LENT
        val title = context.getString(
            if (lent) R.string.loan_reminder_lent_title else R.string.loan_reminder_borrowed_title,
        )
        val text = context.getString(
            if (lent) R.string.loan_reminder_lent_text else R.string.loan_reminder_borrowed_text,
            reminder.counterparty,
            reminder.bookTitle,
            since,
        )
        val hidden = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.loan_reminder_public_title))
            .setContentText(context.getString(R.string.loan_reminder_public_text))
            .build()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden)
            .build()
    }
}
