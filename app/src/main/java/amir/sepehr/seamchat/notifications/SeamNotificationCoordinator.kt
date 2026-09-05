package amir.sepehr.seamchat.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import amir.sepehr.seamchat.MainActivity

/** Central notification owner for SEAM CHAT. */
class SeamNotificationCoordinator(private val context: Context) {
    companion object {
        const val CHANNEL_MESSAGES = "seam_chat_messages"
        const val EXTRA_CONVERSATION_ID = "seam.conversation_id"
        private const val CHANNEL_NAME = "Messages"
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_MESSAGES, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New SEAM CHAT messages"
            })
        }
    }

    fun showMessage(conversationId: Long, senderName: String, preview: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CONVERSATION_ID, conversationId.toString())
        }
        val pendingIntent = PendingIntent.getActivity(context, conversationId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderName)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(conversationId.hashCode(), notification)
    }

    fun dismissConversation(conversationId: Long) = manager.cancel(conversationId.hashCode())
}
