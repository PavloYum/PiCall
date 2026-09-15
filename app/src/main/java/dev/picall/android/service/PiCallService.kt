package dev.picall.android.service

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.picall.android.MainActivity
import dev.picall.android.call.CallPhase
import dev.picall.android.call.CallSession
import dev.picall.android.network.PiCallApi
import dev.picall.android.network.Session
import dev.picall.android.webrtc.RtcEngine
import kotlinx.coroutines.*
import org.json.JSONObject

class PiCallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var api: PiCallApi? = null
    private var rtc: RtcEngine? = null
    private var remoteId: String? = null
    private val pendingIce = mutableListOf<Triple<String, Int, String>>()
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startConnectionForeground("Подключение…")
        val prefs = getSharedPreferences("picall", MODE_PRIVATE)
        val token = prefs.getString("token", null) ?: run { stopSelf(); return }
        val session = Session(prefs.getString("id", "")!!, prefs.getString("name", "")!!, token)
        api = PiCallApi(session).also { client ->
            client.connect(
                onPresence = { _, _ -> updateStatus("В сети · ${session.piCallId}") },
                onError = { updateStatus("Переподключение…") },
                onReady = { updateStatus("В сети · ${session.piCallId}") },
                onSignal = { scope.launch { handleSignal(it) } },
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CALL -> intent.getStringExtra(EXTRA_TARGET)?.let {
                remoteId = it
                CallSession.show(it, CallPhase.OUTGOING)
                api?.call(it)
                updateStatus("Вызов $it…")
            }
            ACTION_ACCEPT -> acceptCall()
            ACTION_REJECT -> remoteId?.let { api?.signal("reject", it) }.also { finishCall() }
            ACTION_HANGUP -> remoteId?.let { api?.signal("hangup", it) }.also { finishCall() }
            ACTION_SPEAKER -> setSpeaker(intent.getBooleanExtra(EXTRA_SPEAKER, false))
        }
        return START_STICKY
    }

    private suspend fun handleSignal(message: JSONObject) {
        val from = message.optString("from")
        when (message.optString("type")) {
            "call" -> { remoteId = from; CallSession.show(from, CallPhase.INCOMING); showIncoming(from) }
            "accept" -> { remoteId = from; CallSession.show(from, CallPhase.CONNECTING); prepareRtc { engine -> engine.createOffer { api?.signal("offer", from) { put("sdp", it) } } } }
            "offer" -> { remoteId = from; CallSession.show(from, CallPhase.CONNECTING); prepareRtc { engine -> engine.answer(message.getString("sdp")) { api?.signal("answer", from) { put("sdp", it) } } } }
            "answer" -> rtc?.applyAnswer(message.getString("sdp"))
            "ice" -> {
                val ice = Triple(message.getString("sdpMid"), message.getInt("sdpMLineIndex"), message.getString("candidate"))
                rtc?.addIce(ice.first, ice.second, ice.third) ?: pendingIce.add(ice)
            }
            "reject", "hangup", "unavailable" -> finishCall()
        }
    }

    private fun acceptCall() {
        val target = remoteId ?: return
        CallSession.show(target, CallPhase.CONNECTING)
        prepareRtc { api?.signal("accept", target) }
        getSystemService(NotificationManager::class.java).cancel(INCOMING_ID)
    }

    private fun prepareRtc(ready: (RtcEngine) -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            remoteId?.let { api?.signal("reject", it) }
            finishCall()
            updateStatus("Разрешите микрофон в PiCall")
            return
        }
        if (!enableMicrophoneForeground()) {
            remoteId?.let { api?.signal("reject", it) }
            finishCall()
            updateStatus("Не удалось включить микрофон")
            return
        }
        prepareAudioRoute()
        rtc?.let { ready(it); return }
        val target = remoteId ?: return
        scope.launch {
            runCatching {
                val turn = requireNotNull(api).turnCredentials()
                RtcEngine(this@PiCallService, turn,
                    onIce = { mid, line, candidate -> api?.signal("ice", target) { put("sdpMid", mid); put("sdpMLineIndex", line); put("candidate", candidate) } },
                    onConnected = {
                        CallSession.show(target, CallPhase.CONNECTED)
                        updateStatus("Разговор · $target")
                    },
                    onDisconnected = { finishCall() },
                )
            }.onSuccess { engine ->
                rtc = engine
                pendingIce.forEach { engine.addIce(it.first, it.second, it.third) }
                pendingIce.clear()
                ready(engine)
            }.onFailure {
                remoteId?.let { id -> api?.signal("reject", id) }
                finishCall()
                updateStatus("Ошибка звонка: ${it.message}")
            }
        }
    }

    private fun finishCall() {
        val activeRtc = rtc
        rtc = null
        remoteId = null
        pendingIce.clear()
        activeRtc?.close()
        resetAudioRoute()
        CallSession.clear()
        getSystemService(NotificationManager::class.java).cancel(INCOMING_ID)
        updateStatus("В сети")
    }

    private fun showIncoming(from: String) {
        val accept = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).setAction(ACTION_ACCEPT), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val reject = PendingIntent.getService(this, 2, Intent(this, PiCallService::class.java).setAction(ACTION_REJECT), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CALL_CHANNEL)
            .setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle("Входящий звонок")
            .setContentText(from).setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true).addAction(0, "Ответить", accept).addAction(0, "Отклонить", reject).build()
        getSystemService(NotificationManager::class.java).notify(INCOMING_ID, notification)
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(STATUS_CHANNEL, "Связь PiCall", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CALL_CHANNEL, "Входящие звонки", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun statusNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val hangup = PendingIntent.getService(this, 3, Intent(this, PiCallService::class.java).setAction(ACTION_HANGUP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, STATUS_CHANNEL).setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("PiCall").setContentText(text).setContentIntent(open).setOngoing(true)
            .apply { if (remoteId != null) addAction(0, "Завершить", hangup) }.build()
    }

    private fun updateStatus(text: String) = getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, statusNotification(text))
    private fun setSpeaker(enabled: Boolean) {
        val audio = getSystemService(AudioManager::class.java)
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= 31) {
            val wantedType = if (enabled) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            audio.availableCommunicationDevices.firstOrNull { it.type == wantedType }?.let(audio::setCommunicationDevice)
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = enabled
        }
        CallSession.setSpeaker(enabled)
    }

    private fun prepareAudioRoute() {
        val audio = getSystemService(AudioManager::class.java)
        @Suppress("DEPRECATION")
        audio.requestAudioFocus(audioFocusListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        setSpeaker(false)
    }

    private fun resetAudioRoute() {
        val audio = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice()
        else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = false
        }
        @Suppress("DEPRECATION")
        audio.abandonAudioFocus(audioFocusListener)
        audio.mode = AudioManager.MODE_NORMAL
    }
    private fun enableMicrophoneForeground(): Boolean = runCatching {
        when {
            Build.VERSION.SDK_INT >= 34 -> startForeground(NOTIFICATION_ID, statusNotification("Соединение…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            Build.VERSION.SDK_INT >= 30 -> startForeground(NOTIFICATION_ID, statusNotification("Соединение…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else -> startForeground(NOTIFICATION_ID, statusNotification("Соединение…"))
        }
    }.isSuccess
    private fun startConnectionForeground(text: String) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, statusNotification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else startForeground(NOTIFICATION_ID, statusNotification(text))
    }
    override fun onDestroy() { rtc?.close(); resetAudioRoute(); CallSession.clear(); api?.close(); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_CALL = "dev.picall.android.CALL"
        const val ACTION_ACCEPT = "dev.picall.android.ACCEPT"
        const val ACTION_REJECT = "dev.picall.android.REJECT"
        const val ACTION_HANGUP = "dev.picall.android.HANGUP"
        const val ACTION_SPEAKER = "dev.picall.android.SPEAKER"
        const val EXTRA_TARGET = "target"
        const val EXTRA_SPEAKER = "speaker"
        private const val STATUS_CHANNEL = "picall_connection"
        private const val CALL_CHANNEL = "picall_calls"
        private const val NOTIFICATION_ID = 1001
        private const val INCOMING_ID = 1002
    }
}
