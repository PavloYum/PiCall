package dev.picall.android.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import dev.picall.android.MainActivity
import dev.picall.android.network.PiCallApi
import dev.picall.android.network.Session

class PiCallService : Service() {
    private var api: PiCallApi? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Подключение…"))
        val prefs = getSharedPreferences("picall", MODE_PRIVATE)
        val token = prefs.getString("token", null)
        if (token == null) { stopSelf(); return }
        val session = Session(prefs.getString("id", "")!!, prefs.getString("name", "")!!, token)
        api = PiCallApi(session).also { client ->
            client.connect(
                onPresence = { _, _ -> updateNotification("В сети · ${session.piCallId}") },
                onError = { updateNotification("Переподключение…") },
                onReady = { updateNotification("В сети · ${session.piCallId}") },
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CALL) intent.getStringExtra(EXTRA_TARGET)?.let { api?.call(it) }
        return START_STICKY
    }

    override fun onDestroy() { api?.close(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Связь PiCall", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("PiCall")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) = getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))

    companion object {
        const val ACTION_CALL = "dev.picall.android.CALL"
        const val EXTRA_TARGET = "target"
        private const val CHANNEL_ID = "picall_connection"
        private const val NOTIFICATION_ID = 1001
    }
}
