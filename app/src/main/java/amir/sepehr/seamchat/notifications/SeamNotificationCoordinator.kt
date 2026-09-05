package amir.sepehr.seamchat.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Single application-level owner for chat notifications.
 * Inspired by the controller-style notification architecture used by
 * Telegram Android, but implemented independently for SEAM CHAT.
 */
class SeamNotificationCoordinator(private val context: Context) {
    companion object {
        const val CHANNEL_MESSAGES = "seam_chat_messages"
        private const val CHANNEL_NAME = "Messages"
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MESSAGES,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "New SEAM CHAT messages"
                }
            )
        }
    }

    fun showMessage(conversationId: Long, senderName: String, preview: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderName)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(conversationId.hashCode(), notification)
    }

    fun dismissConversation(conversationId: Long) {
        manager.cancel(conversationId.hashCode())
    }
}
