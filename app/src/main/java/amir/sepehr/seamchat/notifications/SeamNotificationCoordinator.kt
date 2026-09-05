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

/** Production-oriented notification coordinator for SEAM Chat. */
class SeamNotificationCoordinator(private val context: Context) {
    companion object {
        const val CHANNEL_MESSAGES = "seam_chat_messages"
        const val EXTRA_CONVERSATION_ID = "seam.conversation_id"
        private const val CHANNEL_NAME = "SEAM Chat messages"
        private const val GROUP_KEY = "seam_chat_conversations"
        private const val SUMMARY_ID = 900001
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_MESSAGES,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming messages and replies from SEAM Chat"
                enableVibration(true)
                setShowBadge(true)
            })
        }
    }

    fun showMessage(conversationId: Long, senderName: String, preview: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val notificationId = stableId(conversationId)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CONVERSATION_ID, conversationId.toString())
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val safePreview = preview.ifBlank { "New message" }
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderName.ifBlank { "SEAM Chat" })
            .setContentText(safePreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(safePreview))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        postSummary()
    }

    private fun postSummary() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val summary = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("SEAM Chat")
            .setContentText("New messages")
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_ID, summary)
    }

    fun dismissConversation(conversationId: Long) {
        NotificationManagerCompat.from(context).cancel(stableId(conversationId))
    }

    fun dismissAll() {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun stableId(conversationId: Long): Int =
        (conversationId xor (conversationId ushr 32)).toInt().coerceAtLeast(1)
}
